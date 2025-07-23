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
package org.simplejta.tm;

/**
 * ResourceFactoryAdaptor is the super interface for {@link org.simplejta.tm.jdbc.JDBCDataSourceAdaptor} and
 * {@link org.simplejta.tm.jms.JMSConnectionFactoryAdaptor} interfaces. 
 *  
 * @author Dibyendu
 * @see org.simplejta.tm.jdbc.JDBCDataSourceAdaptor
 * @see org.simplejta.tm.jms.JMSConnectionFactoryAdaptor
 */
public interface ResourceFactoryAdaptor {
	/**
	 * Specifies how joins are to be handled by SimpleJTA. If the XADataSource
	 * allows transaction branches to join other branches, then this method
	 * should return true. If this is not known, it is safe to return false.
	 * Oracle and Derby do not support joins.
	 * 
	 * @return A boolean value indicating whether the XADataSource supports
	 *         joins.
	 */
	boolean joinSupported();

	/**
	 * Specifies whether to reuse XA connections after XAResource.end() or wait
	 * until the the transaction finishes.
	 * <p>
	 * The XA specification allows a connection to be reused immediately after
	 * the transaction it is associated with has ended (ie, XAResourc.end() has
	 * been invoked). This is not correctly implemented in some XADataSource
	 * implementations. If in doubt, return false. If this is set to false,
	 * connections are reused after the transaction completes, i.e., either
	 * commits or rolls back.
	 * 
	 * @return A boolean value to indicate if the XA connections should be
	 *         reused once the transaction branch to which they are associated
	 *         has ended.
	 */
	boolean reuseAfterEnd();

}
