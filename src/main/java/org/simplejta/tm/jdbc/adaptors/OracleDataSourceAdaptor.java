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
package org.simplejta.tm.jdbc.adaptors;

import java.sql.SQLException;
import java.util.Map;

import javax.sql.XADataSource;

import org.simplejta.tm.jdbc.JDBCDataSourceAdaptor;
import org.simplejta.util.ClassUtils;
import org.simplejta.util.Messages;

public class OracleDataSourceAdaptor implements JDBCDataSourceAdaptor {

	String url;
	String user;
	String password;
	
	public XADataSource createDataSource() throws SQLException {
        XADataSource ds = null;
		try {
			Class cl = ClassUtils.forName("oracle.jdbc.xa.client.OracleXADataSource");
			ds = (XADataSource) cl.newInstance();
			ClassUtils.invokeMethod(cl, ds, "setURL", url);
			ClassUtils.invokeMethod(cl, ds, "setUser", user);
			ClassUtils.invokeMethod(cl, ds, "setPassword", password);
		} catch (Throwable e) {
			throw (SQLException) new SQLException(Messages.ECREATEDS + " oracle.jdbc.xa.client.OracleXADataSource").initCause(e);
		}
        return ds;
	}

	public XADataSource createDataSource(Map properties) throws SQLException {
        XADataSource ds = null;
		try {
			Class cl = ClassUtils.forName("oracle.jdbc.xa.client.OracleXADataSource");
			ds = (XADataSource) cl.newInstance();
			ClassUtils.invokeMethod(cl, ds, "setURL", properties.get("URL"));
			ClassUtils.invokeMethod(cl, ds, "setUser", properties.get("User"));
			ClassUtils.invokeMethod(cl, ds, "setPassword", properties.get("Password"));
		} catch (Throwable e) {
			throw (SQLException) new SQLException(Messages.ECREATEDS + " oracle.jdbc.xa.client.OracleXADataSource").initCause(e);
		}
        return ds;
	}

	public boolean joinSupported() {
		return false;
	}

	public boolean reuseAfterEnd() {
		return false;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getURL() {
		return url;
	}

	public void setURL(String url) {
		this.url = url;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

}
