/*
 * Created on: Nov 8, 2004
 * Author: Dibyendu Majumdar
 */
package org.simplejta.tests.derby;

import java.sql.DriverManager;

/**
 * <pre>
 *   History:
 *   Nov 8, 2004 DM Created using code sample obtained from
 *   &lt;a href=&quot;http://www-106.ibm.com/developerworks/db2/library/techarticle/dm-0408bader/index.html&quot;&gt;Integrating Cloudscape and Tomcat&lt;/a&gt;
 * </pre>
 * 
 * @author Dibyendu Majumdar
 */
public class DerbyManager {

	public static final String DRIVER_CLASSNAME = "org.apache.derby.jdbc.EmbeddedDriver";

	public static final String SHUTDOWN_URL = "jdbc:derby:;shutdown=true";

	public static final String SHUTDOWN_MESSAGE = "Apache Derby system shutdown.";

	public static final String MSG_INIT_SUCCESS = "Apache Derby JDBC driver loaded successfully";

	public static final String MSG_INIT_CLASS_NOT_FOUND = "The Apache Derby JDBC driver ("
			+ DRIVER_CLASSNAME
			+ ") could not be found.  Make sure the appropriate JAR"
			+ " files are available.";

	public static final String MSG_TERM_SUCCESS = "Apache Derby shutdown was successful.";

	public static final String MSG_TERM_FAILURE = "Unexpected Exception was caught from the Derby shutdown.";

	public static void initializeDerby() throws Exception {
		Class.forName(DRIVER_CLASSNAME);
		System.out.println(MSG_INIT_SUCCESS);
	}

	public static void shutdownDerby() throws Exception {
		System.out.println(SHUTDOWN_MESSAGE);
		DriverManager.getConnection(SHUTDOWN_URL);
	}
	
	public static void createDatabase(String dbpath) throws Exception {
	    System.out.println("Creating database " + dbpath);
	    DriverManager.getConnection("jdbc:derby:" + dbpath + ";create=true");
	}
	
}