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

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.transaction.SystemException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.log4j.Logger;
import org.simplejta.tm.GlobalTransaction;
import org.simplejta.tm.Resource;
import org.simplejta.tm.ResourceEvent;
import org.simplejta.tm.ResourceEventListener;
import org.simplejta.tm.ResourceFactory;
import org.simplejta.tm.SimpleTransactionManager;
import org.simplejta.tm.jms.wrapper.JMSConnection;
import org.simplejta.util.Messages;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * @author Dibyendu Majumdar
 * @since 14 May 2005
 */
public class JMSXAConnectionPool implements ResourceFactory,
		ResourceEventListener, InitializingBean, DisposableBean, BeanNameAware {

	private static Logger log = Logger.getLogger(JMSXAConnectionPool.class);

	private JMSConnectionFactoryAdaptor jmsConnectionFactoryAdaptor;

	String beanName;

	/**
	 * The instance of SimpleTransactionManager that will manage our
	 * transactions.
	 */
	SimpleTransactionManager transactionManager = null;

	/**
	 * Type Id
	 */
	protected String typeId;

	XAConnectionFactory factory = null;

	/**
	 * Pool of unused Resources/Connections available for reuse.
	 */
	Stack availableConnections = new Stack();

	/**
	 * A map of connections that are in use by the application. Assumed to be
	 * enlisted by some transaction.
	 */
	HashMap activeConnections = new HashMap();

	/**
	 * When connections are closed by the application, we move them to this map.
	 * Resources in this map are enlisted by some transaction, and therefore can
	 * only be reused by the same transaction. Once the transaction delists the
	 * resource, it is moved to availableResources.
	 */
	HashMap closedConnections = new HashMap();
	
	Map connectionProperties = null;

	private static int MAX_RESOURCE_LIFE = 1000 * 60 * 60;

	private static int MAX_RESOURCE_USECOUNT = 1000;

	public JMSXAConnectionPool() {
	}

	public void connectionClosed(Resource resource) {
		synchronized (availableConnections) {
			ConnectionHolder holder = (ConnectionHolder) resource.getIdentity();
			XAConnection xaconn = holder.getXaconn();
			Resource res = (Resource) activeConnections.remove(xaconn);
			if (res != null) {
				Xid xid = res.getCurrentXid();
				if (xid == null) {
					if (log.isDebugEnabled())
						log
								.debug("SIMPLEJTA-JMSConnectionPool: Closed resource "
										+ res
										+ " is added to the list of available (unused) connections");
					addToAvailableConnections(res);
				} else {
					addResourceToClosedList(xid, res);
					if (log.isDebugEnabled())
						log
								.debug("SIMPLEJTA-JMSConnectionPool: Closed resource "
										+ res
										+ " is added to the list of closed (enlisted) connections");
				}
			}
		}
	}

	public void resourceEnlisted(ResourceEvent event) {
	}

	Resource getResourceInternal(Xid xid) throws JMSException {
		XAConnection xaconn;
		XASession session;
		XAResource xares;
		Resource res = null;
		synchronized (availableConnections) {
			if (xid != null) {
				res = popResourceFromClosedList(xid);
				if (res != null && log.isDebugEnabled())
					log.debug("SIMPLEJTA-JMSConnectionPool: Reusing resource "
							+ res
							+ " from list of closed (enlisted) connections");
			}
			if (res == null && !availableConnections.isEmpty()) {
				res = (Resource) availableConnections.pop();
				if (res != null) {
					long curTime = System.currentTimeMillis();
					if ((curTime - res.getBirthTime()) > MAX_RESOURCE_LIFE
							|| res.getUseCount() > MAX_RESOURCE_USECOUNT) {
						shutdownResource(res);
						res = null;
					} else {
						res.incrUseCount();
					}
				}
				if (res != null && log.isDebugEnabled())
					log.debug("SIMPLEJTA-JMSConnectionPool: Reusing resource "
							+ res
							+ " from list of available (unused) connections");
			}
		}
		if (res == null) {
			XAConnectionFactory qcf = (XAConnectionFactory) factory;

			try {
				xaconn = qcf.createXAConnection();
				session = xaconn.createXASession();
				xares = session.getXAResource();
			} catch (JMSException e) {
				// log.error("Error", e);
				// if (e.getLinkedException() != null) {
				// log.error("Error", e.getLinkedException());
				// }
				throw e;
			}
			res = new Resource(getName(), xares, new ConnectionHolder(xaconn,
					session));
			res.setJoinSupported(jmsConnectionFactoryAdaptor.joinSupported());
			res.setReuseResourceAfterEnd(jmsConnectionFactoryAdaptor.reuseAfterEnd());
			if (log.isDebugEnabled())
				log.debug("SIMPLEJTA-JMSConnectionPool: Create new resource "
						+ res);
		} else {
			ConnectionHolder holder = (ConnectionHolder) res.getIdentity();
			xaconn = holder.getXaconn();
		}
		synchronized (availableConnections) {
			if (log.isDebugEnabled())
				log.debug("SIMPLEJTA-JMSConnectionPool: Adding resource " + res
						+ " to list of active (enlisted) connections");
			activeConnections.put(xaconn, res);
		}
		if (log.isDebugEnabled())
			log.debug("SIMPLEJTA-JMSConnectionPool: GETRESOURCE " + xaconn);
		return res;
	}

	/**
	 * Get an unused resource instance.
	 * 
	 * @throws SQLException
	 */
	public Resource getResourceInternal() throws JMSException {
		return getResourceInternal(null);
	}

	public Resource getResource() throws IllegalStateException, SQLException,
			SystemException {
		try {
			return getResourceInternal();
		} catch (JMSException e) {
			throw (SystemException) new SystemException().initCause(e);
		}
	}

	public Resource getResource(Xid xid) throws IllegalStateException,
			SQLException, SystemException {
		try {
			return getResourceInternal(xid);
		} catch (JMSException e) {
			throw (SystemException) new SystemException().initCause(e);
		}
	}

	public Connection createConnection() throws JMSException {
		GlobalTransaction t = null;
		Xid xid = null;
		try {
			t = (GlobalTransaction) transactionManager.getTransaction();
		} catch (SystemException e) {
			throw (JMSException) new JMSException(Messages.ENOASSOC)
					.initCause(e);
		}
		if (t == null) {
			throw new JMSException(Messages.ENOASSOC);
		}
		xid = t.getXid();
		Resource res = getResourceInternal(xid);
		res.addResourceListener(this);
		try {
			t.enlistResource(res);
		} catch (Exception e) {
			throw (JMSException) new JMSException(Messages.EENLISTJMS)
					.initCause(e);
		}
		ConnectionHolder holder = (ConnectionHolder) res.getIdentity();
		return new JMSConnection(this, holder.getXaconn(), holder.getSession(),
				res);
	}

	/**
	 * Obtain a QueueConnection. Note that user and password are ignored.
	 * 
	 * @see javax.jms.QueueConnectionFactory#createQueueConnection(java.lang.String,
	 *      java.lang.String)
	 */
	public Connection createConnection(String user, String password)
			throws JMSException {
		return createConnection();
	}

	/**
	 * Caller must synchronize
	 */
	private void addToAvailableConnections(Resource resource) {
		if (!availableConnections.contains(resource)) {
			availableConnections.push(resource);
		}
	}

	/**
	 * Invoked when a resource is delisted. The resource is moved to the list of
	 * available resources. This makes it available for general reuse. Note that
	 * this is only invoked for connections that have been elisted by us. For
	 * connections that have not been enlisted by us (such as those used by
	 * SimpleOracleXAJmsQueueConnectionFactory) are not handled here - they are
	 * expected to be managed by whoever enlisted them. MT note: This method is
	 * called with the GlobalTransaction locked
	 */
	public void resourceDelisted(ResourceEvent event) {
		synchronized (availableConnections) {
			removeResourceFromClosedList(event.getXid(), event.getResource());
			if (log.isDebugEnabled())
				log
						.debug("SIMPLEJTA-JMSConnectionPool: Adding delisted resource "
								+ event.getResource()
								+ " to available connections");
			addToAvailableConnections(event.getResource());
		}
	}

	/**
	 * Add a resource to the list of closed connections. This list is maintained
	 * for each transaction xid.
	 * 
	 * @param xid
	 * @param res
	 */
	private void addResourceToClosedList(Xid xid, Resource res) {
		LinkedList list = (LinkedList) closedConnections.get(xid);
		if (list == null) {
			list = new LinkedList();
			closedConnections.put(xid, list);
		}
		list.add(res);
	}

	/**
	 * Remove a resource from the list of closed connections. This list is
	 * maintained for each transaction xid.
	 * 
	 * @param xid
	 * @param res
	 */
	private boolean removeResourceFromClosedList(Xid xid, Resource res) {
		LinkedList list = (LinkedList) closedConnections.get(xid);
		if (list == null) {
			return false;
		}
		boolean removed = list.remove(res);
		if (list.isEmpty()) {
			closedConnections.remove(xid);
		}
		return removed;
	}

	/**
	 * Obtain a resource from the list of closed connections. This list is
	 * maintained for each transaction xid.
	 * 
	 * @param xid
	 */
	private Resource popResourceFromClosedList(Xid xid) {
		LinkedList list = (LinkedList) closedConnections.get(xid);
		if (list == null || list.isEmpty()) {
			return null;
		}
		return (Resource) list.removeFirst();
	}

	/**
	 * Close a QueueConnection
	 */
	private void shutdownResource(Resource resource) {
		if (log.isDebugEnabled())
			log.debug("SIMPLEJTA-JMSConnectionPool: Shutting down resource "
					+ resource);
		ConnectionHolder holder = (ConnectionHolder) resource.getIdentity();
		XAConnection connection = holder.getXaconn();
		XASession session = holder.getSession();
		resource.clearResourceListeners();
		if (session != null) {
			try {
				session.close();
			} catch (Exception e) {
				log
						.warn(
								"SIMPLEJTA-JMSConnectionPool: Error occurred while attempting to close Session",
								e);
			}
		}
		if (connection != null) {
			try {
				connection.close();
			} catch (Exception e) {
				log
						.warn(
								"SIMPLEJTA-JMSConnectionPool: Error occurred while attempting to close Connection",
								e);
			}
		}
	}

	public void destroy() {
		if (log.isDebugEnabled()) {
			log.debug("SIMPLEJTA-JMSConnectionPool: Shutting down");
		}
		synchronized (availableConnections) {
			while (!availableConnections.isEmpty()) {
				Resource resource = (Resource) availableConnections.pop();
				if (resource != null) {
					shutdownResource(resource);
				}
			}
		}
	}

	public Resource getResource(String url, String user, String password)
			throws IllegalStateException, SQLException, SystemException {
		try {
			return getResourceInternal();
		} catch (JMSException e) {
			throw (SystemException) new SystemException("").initCause(e);
		}
	}

	public Resource getResource(String url, String user, String password,
			Xid xid) throws IllegalStateException, SQLException,
			SystemException {
		try {
			return getResourceInternal(xid);
		} catch (JMSException e) {
			throw (SystemException) new SystemException("").initCause(e);
		}
	}

	public void afterPropertiesSet() throws Exception {
		this.factory = jmsConnectionFactoryAdaptor.createXAConnectionFactory(connectionProperties);
	}

	public void setBeanName(String arg0) {
		this.beanName = arg0;
	}
	
	public String getName() {
		return beanName;
	}

	public void setConnectionProperties(Map connectionProperties) {
		this.connectionProperties = connectionProperties;
	}

	public void setConnectionFactoryAdaptor(
			JMSConnectionFactoryAdaptor jmsConnectionFactoryAdaptor) {
		this.jmsConnectionFactoryAdaptor = jmsConnectionFactoryAdaptor;
	}

	public void setTransactionManager(SimpleTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}
	
	
}	


/**
 * @author Dibyendu Majumdar
 * @since 14-May-2005
 */
class ConnectionHolder {

	XAConnection xaconn;

	XASession session;

	long birthTime = System.currentTimeMillis();

	int useCount = 0;

	public ConnectionHolder(XAConnection xaconn, XASession session) {
		this.xaconn = xaconn;
		this.session = session;
	}

	public final long getBirthTime() {
		return birthTime;
	}

	public final XASession getSession() {
		return session;
	}

	public final int getUseCount() {
		return useCount;
	}

	public final XAConnection getXaconn() {
		return xaconn;
	}
	
	
}

