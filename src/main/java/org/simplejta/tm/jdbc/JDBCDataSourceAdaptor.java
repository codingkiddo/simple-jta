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
package org.simplejta.tm.jdbc;

import java.sql.SQLException;
import java.util.Map;

import javax.sql.XADataSource;

import org.simplejta.tm.ResourceFactoryAdaptor;

/**
 * JDBCDataSourceAdaptor defines the contract between SimpleJTA and
 * vendor supplied XA datasources. To add support for a new database type,
 * following steps are required:
 * <ol>
 * <li>Implement the JDBCDataSourceAdaptor interface.</li>
 * <li>Register your implementation of JDBCDataSourceAdaptor in
 * the SimpleJTA configuration file.</li>
 * <li>Finally, define a JDBCXAConnectionPool bean, and set the
 * dataSourceAdaptor property to your implementation of 
 * JDBCDataSourceAdaptor.</li>
 * </ol>
 * @see org.simplejta.tm.jdbc.adaptors.OracleDataSourceAdaptor
 * @see org.simplejta.tm.jdbc.adaptors.DerbyEmbeddedDataSourceAdaptor
 * @author Dibyendu Majumdar
 * @since 20 June 2006
 */
public interface JDBCDataSourceAdaptor extends ResourceFactoryAdaptor {

	/**
	 * Creates XADataSource using specified properties
	 * @param properties Connection properties
	 * @return Newly created XADataSource object.
	 * @throws SQLException Thrown if there is an error creating the datasource.
	 */
	XADataSource createDataSource(Map properties) throws SQLException;
	
}
