package org.simplejta.tests.crashtest;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import junit.framework.TestCase;

import org.simplejta.tests.derby.DerbyManager;
import org.simplejta.tm.ut.SimpleUserTransaction;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CrashTest3 extends TestCase {

	/**
	 * Spring Application Context
	 */
	ClassPathXmlApplicationContext context;

	public CrashTest3(String arg0) {
		super(arg0);
	}

	protected void setUp() throws Exception {
		DerbyManager.initializeDerby();
		context = new ClassPathXmlApplicationContext(
				"classpath*:testConfig.xml");
	}

	protected void tearDown() throws Exception {
		// Shutdown the Spring Context
		context.close();

		// Shutdown Derby
		try {
			DerbyManager.shutdownDerby();
		} catch (Exception e) {
		}
	}

	public void testSetupCrash1() throws Exception {
		try {
			SimpleUserTransaction ut = (SimpleUserTransaction) context
					.getBean("UserTransaction");
			DataSource ds1 = (DataSource) context.getBean("DataSource1");
			DataSource ds2 = (DataSource) context.getBean("DataSource2");
			DataSource rawDs1 = (DataSource) context.getBean("RawDataSource1");
			DataSource rawDs2 = (DataSource) context.getBean("RawDataSource2");

			ut.getSimpleTransactionManager().setCrashTesting(3);

			Connection rawConn1 = rawDs1.getConnection();
			Statement stmtx = rawConn1.createStatement();
			stmtx.executeUpdate("DELETE FROM dept WHERE deptno = 50");
			stmtx.close();
			rawConn1.commit();
			rawConn1.close();

			Connection rawConn2 = rawDs2.getConnection();
			Statement stmty = rawConn2.createStatement();
			stmty.executeUpdate("DELETE FROM dept WHERE deptno = 51");
			rawConn2.commit();
			rawConn2.close();

			ut.begin();

			/* get connections */
			Connection conn1 = ds1.getConnection();
			Connection conn2 = ds2.getConnection();

			/* do work */
			System.err.println("inserting data");
			Statement stmt1 = conn1.createStatement();
			stmt1
					.executeUpdate("INSERT INTO dept VALUES (50, 'BSD', 'LONDON')");

			Statement stmt2 = conn2.createStatement();
			stmt2
					.executeUpdate("INSERT INTO dept VALUES (51, 'BSD', 'LONDON')");

			conn1.close();
			conn2.close();

			/* commit */
			System.err.println("commiting inserts");
			ut.commit();
		} catch (RuntimeException e) {
			e.printStackTrace();
			if (!"Crashed".equals(e.getMessage())) {
				throw e;
			}
		}
	}

	public void tesVerifyCrash1() throws Exception {
		DataSource rawDs1 = (DataSource) context.getBean("RawDataSource1");
		DataSource rawDs2 = (DataSource) context.getBean("RawDataSource2");

		Connection rawConn1 = rawDs1.getConnection();
		Statement stmtx = rawConn1.createStatement();
		int count = stmtx.executeUpdate("DELETE FROM dept WHERE deptno = 50");
		assertEquals(count, 0);
		stmtx.close();
		rawConn1.commit();
		rawConn1.close();

		Connection rawConn2 = rawDs2.getConnection();
		Statement stmty = rawConn2.createStatement();
		count = stmty.executeUpdate("DELETE FROM dept WHERE deptno = 51");
		assertEquals(count, 0);
		rawConn2.commit();
		rawConn2.close();
	}

}
