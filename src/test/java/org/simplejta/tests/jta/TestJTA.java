/*
 * Created on 08-May-2005
 */
package org.simplejta.tests.jta;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;
import javax.transaction.UserTransaction;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.simplejta.tests.derby.DerbyManager;
import org.simplejta.util.SqlUtil;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * JTATests excercises SimpleJTA.
 * It starts multiple concurrent threads.
 * Each thread obtains two connections, preferably from different databases.
 * Each thread performs following actions:
 * 	T1		Conn1	2 Inserts (Conn1 is closed and opened between each insert - this tests logical connections).
 * 			Conn1	1 Insert
 *  T2		Conn1	1 Insert
 *  		Conn2	1 Insert
 * Both transactions are committed.
 * After the thread completes, it deletes the inserted rows, and verifies the result.
 * The number of threads can be configured.
 * A thread can be configured to run more than 1 iteration. This can be used to setup long running tests.
 * Each thread is allocated a distinct range of test data (5 depts).
 * 	
 * @author Dibyendu Majumdar
 */
public class TestJTA extends TestCase {

	/**
	 * Spring Application Context
	 */
	ClassPathXmlApplicationContext context;
	
	/**
	 * Number of threads to run.
	 */
    int numThreads = 20;
    
    /**
     * Number of iterations to run in each thread.
     */
    int numIterations = 2;
    
    /**
     * Test data range for each thread.
     * Cannot be changed.
     */
    final int numInserts = 5;

    Exception exception = null;

    protected void setUp() throws Exception {
        DerbyManager.initializeDerby();
        context = new ClassPathXmlApplicationContext("classpath*:testConfig.xml");
    }

    public void testCase1() throws Exception {
        Thread[] threads = new Thread[numThreads];
        int x = 0;
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(new TRunner(this, new TThreadSpecific(this, x), numIterations));
            x += numInserts;
        }
        for (int t = 0; t < threads.length; t++) {
            threads[t].start();
        }
        for (int t = 0; t < threads.length; t++) {
            threads[t].join();
        }
        if (exception != null) {
            throw exception;
        }
    }

    protected void tearDown() throws Exception {
        System.out.println("Shutting down TransactionManager");

        // Shutdown the Spring Context
        context.close();
        
        // Shutdown Derby
        try {
            DerbyManager.shutdownDerby();
        } catch (Exception e) {
        }
    }

    public TestJTA(String arg0) {
        super(arg0);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( TestJTA.class );
    }


    /**
     * Saves the specified exception. Only one exception can be saved.
     * @param e Exception to be saved
     */
    public void setException(Exception e) {
        if (exception == null)
            exception = e;
    }

	public ClassPathXmlApplicationContext getContext() {
		return context;
	}
}

class TRunner implements Runnable {

    TThreadSpecific unit;

    TestJTA owner;

    int iterations;

    public TRunner(TestJTA owner, TThreadSpecific unit, int iterations) {
        this.unit = unit;
        this.iterations = iterations;
        this.owner = owner;
    }

    public void run() {
        try {
            unit.init();
            for (int i = 0; i < iterations; i++) {
                unit.execute();
            }
        } catch (Exception e) {
            owner.setException(e);
        } finally {
            try {
				unit.cleanup();
			} catch (Exception e) {
				owner.setException(e);
			}
        }
    }

}

class TStatement {

    String testStatement;

    String cleanupStatement;

    int processedCount = 0;

    int cleanedCount = 0;

    public TStatement() {
    }

    public TStatement(String testStatement, String cleanupStatement) {
        this.testStatement = testStatement;
        this.cleanupStatement = cleanupStatement;
    }

    /**
     * @return Returns the deleteStatement.
     */
    public final String getCleanupStatement() {
        return cleanupStatement;
    }

    /**
     * @param deleteStatement
     *            The deleteStatement to set.
     */
    public final void setCleanupStatement(String deleteStatement) {
        this.cleanupStatement = deleteStatement;
    }

    /**
     * @return Returns the insertStatement.
     */
    public final String getTestStatement() {
        return testStatement;
    }

    /**
     * @param insertStatement
     *            The insertStatement to set.
     */
    public final void setTestStatement(String insertStatement) {
        this.testStatement = insertStatement;
    }

    /**
     * @return Returns the insertCount.
     */
    public final int getProcessedCount() {
        return processedCount;
    }

    /**
     * @param insertCount
     *            The insertCount to set.
     */
    public final void setProcessedCount(int insertCount) {
        this.processedCount = insertCount;
    }

    /**
     * @return Returns the deleteCount.
     */
    public final int getCleanedCount() {
        return cleanedCount;
    }

    /**
     * @param deleteCount
     *            The deleteCount to set.
     */
    public final void setCleanedCount(int deleteCount) {
        this.cleanedCount = deleteCount;
    }
}

/**
 * ThreadSpecificTest contains the test logic for a single thread.
 * Each thread obtains two connections, preferably from different databases.
 * Each thread performs following actions:
 * 	T1		Conn1	2 Inserts (Conn1 is closed and opened between each insert - this tests logical connections).
 * 			Conn1	1 Insert
 *  T2		Conn1	1 Insert
 *  		Conn2	1 Insert
 * Both transactions are committed.
 * After the thread completes, it deletes the inserted rows, and verifies the result.
 * The number of threads can be configured.
 * A thread can be configured to run more than 1 iteration. This can be used to setup long running tests.
 * Each thread is allocated a distinct range of test data (5 depts).
 * 
 * @author Dibyendu Majumdar
 */
class TThreadSpecific {

	TestJTA owner;
	
    protected DataSource ds1;

    protected DataSource ds2;

    protected Connection rawConn1;

    protected Connection rawConn2;

    protected UserTransaction ut = null;

    TStatement ts1 = new TStatement();

    TStatement ts2 = new TStatement();

    TStatement ts3 = new TStatement();

    TStatement ts4 = new TStatement();

    TStatement ts5 = new TStatement();

    TStatement[] all_tests = new TStatement[] { ts1, ts2, ts3, ts4, ts5 };

    TStatement[] conn1_deletes = new TStatement[] { ts1, ts3, ts4 };

    TStatement[] conn2_deletes = new TStatement[] { ts2, ts5 };

    public TThreadSpecific(TestJTA owner, int startDeptNo) throws Exception {
    	this.owner = owner;
        initTestStatements(startDeptNo);
        initConnections();
    }

    private void initTestStatements(int startDeptNo) {
        for (int i = startDeptNo; (i - startDeptNo) < all_tests.length; i++) {
            all_tests[i - startDeptNo]
                    .setTestStatement("INSERT INTO dept VALUES (" + i
                            + ", 'BSD', 'LONDON')");
            all_tests[i - startDeptNo]
                    .setCleanupStatement("DELETE FROM dept WHERE deptno = " + i);
        }
    }

    private void initConnections() throws Exception {
    	ds1 = (DataSource) owner.context.getBean("DataSource1");
    	ds2 = (DataSource) owner.context.getBean("DataSource2");
    	DataSource rawDs1 = (DataSource) owner.context.getBean("RawDataSource1");
        rawConn1 = rawDs1.getConnection();
    	DataSource rawDs2 = (DataSource) owner.context.getBean("RawDataSource2");
        rawConn2 = rawDs2.getConnection();
        ut = (UserTransaction) owner.context.getBean("UserTransaction");
    }

    /**
     * Perform cleanup before each test.
     * The cleanup action is executed using a regular JDBC (raw) connection.
     */
    private void beforeTest(Connection conn, TStatement[] tests)
            throws Exception {
        for (int i = 0; i < tests.length; i++) {
            Utils.executeDelete(conn, tests[i]);
            tests[i].setCleanedCount(0);
        }
        conn.commit();
    }

    /**
     * Validate and cleanup after each test.
     * This action is executed using a regular JDBC (raw) connection.
     * An exception is thrown if validation fails.
     */
    private void validate(Connection conn, TStatement[] tests)
            throws Exception {
        for (int i = 0; i < tests.length; i++) {
            Utils.executeDelete(conn, tests[i]);
            conn.commit();
            if (tests[i].getProcessedCount() != tests[i].getCleanedCount()) {
                throw new Exception("Test " + tests[i].getTestStatement()
                        + " failed");
            }
        }
    }

    public void init() throws Exception {
        beforeTest(rawConn1, conn1_deletes);
        beforeTest(rawConn2, conn2_deletes);
    }

    /**
     * Execute the tests.
     */
    public void execute() throws Exception {

        System.err.println(Thread.currentThread().getName() + ": Starting new transaction");
        ut.begin();

        /* get connections */
        Connection conn1 = ds1.getConnection();
        Thread.yield();
        Connection conn2 = ds2.getConnection();
        Thread.yield();

        /* do work */
        Utils.executeInsert(conn1, ts1);
        Thread.yield();
        Utils.executeInsert(conn2, ts2);
        Thread.yield();

        conn1.close();
        conn2.close();

        conn1 = ds1.getConnection();
        Thread.yield();

        /* do work */
        Utils.executeInsert(conn1, ts3);
        Thread.yield();

        conn1.close();

        /* commit */
        System.err.println(Thread.currentThread().getName() + ": Commiting inserts");
        ut.commit();
        Thread.yield();

        /* start new transaction */
        System.err.println(Thread.currentThread().getName() + ": Starting new transaction");
        ut.begin();

        conn1 = ds1.getConnection();
        Thread.yield();
        conn2 = ds2.getConnection();
        Thread.yield();

        /* do work */
        Utils.executeInsert(conn1, ts4);
        Thread.yield();
        Utils.executeInsert(conn2, ts5);
        Thread.yield();

        conn1.close();
        conn2.close();

        /* commit */
        System.err.println(Thread.currentThread().getName() + ": Commiting second batch of inserts");
        ut.commit();

        /* validate */
        System.err.println(Thread.currentThread().getName() + ": Validating results.");

        validate(rawConn1, conn1_deletes);
        validate(rawConn2, conn2_deletes);

        System.err.println(Thread.currentThread().getName() + ": Test completed OK.");
    }

    public void cleanup() throws Exception {
        SqlUtil.close(rawConn1);
        SqlUtil.close(rawConn2);
    }
}

class Utils {

    public static void executeInsert(Connection conn, TStatement st)
            throws Exception {
        Statement stmt = conn.createStatement();
        try {
            int cnt = stmt.executeUpdate(st.getTestStatement());
            System.out.println(Thread.currentThread().getName() + ": Executed " + st.getTestStatement()
                    + " - no of rows inserted=" + cnt);
            st.setProcessedCount(cnt);
        } finally {
            SqlUtil.close(stmt);
        }
    }

    public static void executeDelete(Connection conn, TStatement st)
            throws Exception {
        Statement stmt = conn.createStatement();
        try {
            int cnt = stmt.executeUpdate(st.getCleanupStatement());
            System.out.println(Thread.currentThread().getName() + ": Executed " + st.getCleanupStatement()
                    + " - no of rows deleted=" + cnt);
            st.setCleanedCount(cnt);
        } finally {
            SqlUtil.close(stmt);
        }
    }
}
