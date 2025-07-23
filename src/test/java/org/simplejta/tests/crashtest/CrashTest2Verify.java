package org.simplejta.tests.crashtest;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import junit.framework.TestCase;

import org.simplejta.tests.derby.DerbyManager;
import org.simplejta.tm.ut.SimpleUserTransaction;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CrashTest2Verify extends TestCase {

	/**
	 * Spring Application Context
	 */
	ClassPathXmlApplicationContext context;

	public CrashTest2Verify(String arg0) {
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

	public void testVerifyCrash1() throws Exception {
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
