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
package org.simplejta.tm.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.transaction.SystemException;

import org.simplejta.tm.SimpleTransactionManager;
import org.simplejta.util.Messages;

/**
 * <p>
 * <code>JMSConnectionFactory</code> implements the JMS ConnectionFactory
 * interface. Sub-classes are expected to set the typeid correctly.
 * Alternatively, typeid can be set after contructing an instance of this class.
 * 
 * @author Dibyendu Majumdar
 * @since 15-May-2005
 */
public class SimpleJTAJMSConnectionFactory implements ConnectionFactory {

	String beanFactory;

	String transactionManager;

	String connectionPool;

	protected transient JMSXAConnectionPool cachedConnectionPool;

	public SimpleJTAJMSConnectionFactory() {
		super();
	}

	private JMSXAConnectionPool getFactory()
			throws InstantiationException, IllegalAccessException,
			SystemException {
		JMSXAConnectionPool connectionPool;
		SimpleTransactionManager tm = SimpleTransactionManager
				.getTransactionManager(beanFactory, transactionManager);
		try {
			connectionPool = (JMSXAConnectionPool) tm
					.getResourceFactory(getConnectionPool());
		} catch (Exception ex) {
			throw (SystemException) new SystemException().initCause(ex);
		}
		return connectionPool;
	}

	private synchronized JMSXAConnectionPool getCachedFactory() throws JMSException {
		if (cachedConnectionPool == null) {
			try {
				cachedConnectionPool = getFactory();
			} catch (Exception ex) {
				throw (JMSException) new JMSException(Messages.EGETJMSFACTORY)
						.initCause(ex);
			}
		}
		return cachedConnectionPool;
	}

	public Connection createConnection() throws JMSException {
		if (transactionManager == null || beanFactory == null || connectionPool == null) {
			throw new JMSException(Messages.EINITJMS);
		}
		return getCachedFactory().createConnection();
	}

	public Connection createConnection(String arg0, String arg1)
			throws JMSException {
		return createConnection();
	}

	public final String getTransactionManager() {
		return transactionManager;
	}

	public final void setTransactionManager(String tmid) {
		this.transactionManager = tmid;
	}

	public String getConnectionPool() {
		return connectionPool;
	}

	public void setConnectionPool(String typeid) {
		this.connectionPool = typeid;
	}

	public String getBeanFactory() {
		return beanFactory;
	}

	public void setBeanFactory(String key) {
		this.beanFactory = key;
	}
}