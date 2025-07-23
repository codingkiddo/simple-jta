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
import java.util.HashMap;
import java.util.Map;

import javax.sql.XADataSource;

import org.simplejta.tm.jdbc.JDBCDataSourceAdaptor;
import org.simplejta.util.SqlUtil;

public class DerbyEmbeddedDataSourceAdaptor implements JDBCDataSourceAdaptor {

	String databaseName;
	String user;
	String password;
	
	public XADataSource createDataSource() throws SQLException {
		XADataSource ds;

		HashMap props = new HashMap();
		props.put("databaseName", databaseName);
		props.put("user", user);
		props.put("password", password);
		ds = (XADataSource) SqlUtil.createDataSource(
				"org.apache.derby.jdbc.EmbeddedXADataSource", props);
		return ds;
	}
	
	public XADataSource createDataSource(Map properties) throws SQLException {
		XADataSource ds;
		ds = (XADataSource) SqlUtil.createDataSource(
				"org.apache.derby.jdbc.EmbeddedXADataSource", properties);
		return ds;
	}
	

	public boolean joinSupported() {
		return false;
	}

	public boolean reuseAfterEnd() {
		return false;
	}

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

}
