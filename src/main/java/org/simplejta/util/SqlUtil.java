/*
 * SimpleJTA - A Simple Java Transaction Manager (http://www.simplejta.org/)
 * Copyright 2005 Dibyendu Majumdar
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package org.simplejta.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Map;

import javax.sql.PooledConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * SqlUtil implements a few useful SQL helpers.
 *
 * @author Dibyendu Majumdar
 * @since Dec 21, 2004
 */
public class SqlUtil {

	private static Logger log = LogManager.getLogger(SqlUtil.class);
			
	public static void close(PooledConnection con) {
		if (con == null)
			return;
		try {
			con.close();
		} catch (SQLException e) {
			log.error(Messages.ECLOSECONN, e);
		}
	}

	public static void close(Connection con) {
		if (con == null)
			return;
		try {
			con.close();
		} catch (SQLException e) {
			log.error(Messages.ECLOSECONN, e);
		}
	}

	public static void close(Statement st) {
		if (st == null)
			return;
		try {
			st.close();
		} catch (SQLException e) {
			log.error(Messages.ECLOSEST, e);
		}
	}

	public static void close(ResultSet rs) {
		if (rs == null)
			return;
		try {
			rs.close();
		} catch (SQLException e) {
			log.error(Messages.ECLOSERS, e);
		}
	}

	/**
	 * Creates a datasource of the specified type. The properties supplied in the hash map
	 * are assumed to match corresponding setter methods in the datasource implementation.
	 * 
	 * @param className Class of the desired datasource implementation.
	 * @param props A set of properties for initializing the datasource.
	 * @throws SQLException 
	 */
	public static Object createDataSource(String className, Map props) throws SQLException {
	    Object ds = null;
		try {
			Class cl = ClassUtils.forName(className);
	        if (log.isDebugEnabled()) {
	            log.debug("SIMPLEJTA-SqlUtil: Creating an instance of " + className);
	        }
			ds = cl.newInstance();
			Iterator i = props.entrySet().iterator();
			while (i.hasNext()) {
			    Map.Entry e = (Map.Entry) i.next();
			    String methodName = (String) e.getKey();
			    methodName = "set" + methodName.substring(0,1).toUpperCase() + 
			    	methodName.substring(1);
			    Object value = e.getValue();
		        ClassUtils.invokeMethod(cl, ds, methodName, value);
		        if (log.isDebugEnabled()) {
		            log.debug("SIMPLEJTA-SqlUtil: Executed " + className + "." + methodName + "(" + value.toString() + ")");
		        }
			}
		} catch (Throwable e) {
			throw (SQLException) new SQLException("Unable to create datasource of type " + className).initCause(e);
		}
	    return ds;
	}
}
