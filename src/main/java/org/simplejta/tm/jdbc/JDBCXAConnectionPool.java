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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.Xid;

import org.apache.log4j.Logger;
import org.simplejta.tm.GlobalTransaction;
import org.simplejta.tm.Resource;
import org.simplejta.tm.ResourceEvent;
import org.simplejta.tm.ResourceEventListener;
import org.simplejta.tm.ResourceFactory;
import org.simplejta.tm.SimpleTransactionManager;
import org.simplejta.util.Messages;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * <p>
 * JDBCXAConnectionPool implements a Pool Manager for XA Connections.
 * </p>
 * 
 * <p>
 * Implementation Notes: Normally, the Pool is supposed to be implemented so
 * that when a connection is opened - it is enlisted with the global
 * transaction, and when it is closed, it is delisted.
 * </p>
 * 
 * <p>
 * Most web applications make extensive use of connection pooling. Within the
 * same transaction, the application may open/close connections several times.
 * To make this process efficient we want to avoid having to delist/enlist
 * connections every time they are opened/closed. We do this as follows:
 * </p>
 * 
 * <p>
 * Firstly, we do not delist resources when connections are closed. Instead we
 * wait until the transaction issues a commit or rollback. When a connection is
 * closed, we keep it reserved for the transaction that has enlisted the
 * resource. Should the transaction request another connection for the same
 * resource, we can supply the reserved connection rather than acquiring a new
 * one.
 * </p>
 * 
 * <p>
 * The drawback to this approach is that the pooling logic is more complicated,
 * because we have to maintain not only lists of active and unused connections,
 * but also a list of closed (but enlisted) connections. Moreover, we have to
 * cater for the fact that a transaction may have multiple closed connections at
 * a point in time.
 * </p>
 * 
 * @author Dibyendu Majumdar
 * @since 27.Oct.2004
 */
class JDBCXAConnectionPool implements ConnectionEventListener,
		ResourceEventListener, InitializingBean, DisposableBean,
		ResourceFactory, BeanNameAware {

	private static Logger log = Logger.getLogger(JDBCXAConnectionPool.class);

	private JDBCDataSourceAdaptor dataSourceAdaptor;

	/**
	 * The instance of SimpleTransactionManager that will manage our
	 * transactions.
	 */
	SimpleTransactionManager transactionManager = null;
	
	Map connectionProperties = null;

	/**
	 * The real XADataSource that manages connections to the database.
	 */
	XADataSource ds;

	String name;

	/**
	 * The URL for connecting to the database.
	 */
	String url;

	/**
	 * Database user id.
	 */
	String user;

	/**
	 * Password for database user id.
	 */
	String password;

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

	int connCount = 0;

	// TODO Following should be configurable
	private static int MAX_RESOURCE_LIFE = 1000 * 60 * 60;

	private static int MAX_RESOURCE_USECOUNT = 1000;

	private static int MAX_IDLE_CONNECTIONS = 5;

	private static int IDLE_CONNECTIONS_TIMEOUT = 5 * 1000;

	private static int MAX_CONNECTIONS = -1;

	int maxIdleConnections = MAX_IDLE_CONNECTIONS;

	int idleConnectionsTimeout = IDLE_CONNECTIONS_TIMEOUT;

	int maxResourceLife = MAX_RESOURCE_LIFE;

	int maxResourceUseCount = MAX_RESOURCE_USECOUNT;

	int maxConnections = MAX_CONNECTIONS;

	long lastIdleConnectionsReview = 0;

	JDBCXAConnectionPool(SimpleTransactionManager tm, XADataSource ds,
			String url, String user, String password) {
		this.transactionManager = tm;
		this.ds = ds;
		this.url = url;
		this.user = user;
		this.password = password;
		// System.err.println("Instance of " + getClass().getName() + " created");
	}

	public JDBCXAConnectionPool() {
	}

	/**
	 * @return Returns the ds.
	 */
	public final XADataSource getXADatasource() {
		return ds;
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
				log.debug("SIMPLEJTA-XAPoolManager: Adding delisted resource "
						+ event.getResource() + " to available connections");
			addToAvailableConnections(event.getResource());
		}
	}

	/**
	 * Resource is already on activeConnections list so we do nothing here.
	 */
	public void resourceEnlisted(ResourceEvent event) {
	}

	/**
	 * Close idle connections that exceed maxIdleConnections setting. To avoid
	 * thrashing, perform this at intervals of idleConnectionsTimeout.
	 */
	private void closeIdleConnections() {
		long now = System.currentTimeMillis();
		if (lastIdleConnectionsReview == 0) {
			lastIdleConnectionsReview = now;
			return;
		}
		if (now - lastIdleConnectionsReview < idleConnectionsTimeout) {
			return;
		}
		if (log.isDebugEnabled()) {
			log.debug("SIMPLEJTA-XAPoolManager: Closing "
					+ (availableConnections.size() - maxIdleConnections)
					+ " idle connections");
		}
		while (availableConnections.size() > maxIdleConnections) {
			Resource res = (Resource) availableConnections.pop();
			if (res != null) {
				shutdownResource(res);
			}
		}
		lastIdleConnectionsReview = now;
	}

	/**
	 * Get a resource. The supplied Xid is used to find a resource that may
	 * already be enlisted (but unused) for the given transaction.
	 * 
	 * @param xid
	 *            The transaction for which a resource is being requested.
	 * @return A resource
	 * @throws SQLException
	 */
	public Resource getResource(Xid xid) throws SQLException {
		XAConnection xaconn;
		Resource res = null;
		synchronized (availableConnections) {
			if (xid != null) {
				res = popResourceFromClosedList(xid);
				if (res != null && log.isDebugEnabled())
					log.debug("SIMPLEJTA-XAPoolManager: Reusing resource "
							+ res
							+ " from list of closed (enlisted) connections");
			}
			if (res == null && !availableConnections.isEmpty()) {
				closeIdleConnections();
				while (res == null && !availableConnections.isEmpty()) {
					res = (Resource) availableConnections.pop();
					long curTime = System.currentTimeMillis();
					if ((curTime - res.getBirthTime()) > maxResourceLife
							|| res.getUseCount() > maxResourceUseCount) {
						if (log.isDebugEnabled()) {
							log
									.debug("SIMPLEJTA-XAPoolManager: Shutting down expired resource "
											+ res);
						}
						shutdownResource(res);
						res = null;
					} else {
						res.incrUseCount();
					}
				}
				if (res != null && log.isDebugEnabled())
					log.debug("SIMPLEJTA-XAPoolManager: Reusing resource "
							+ res
							+ " from list of available (unused) connections");
			}
		}
		if (res == null) {
			xaconn = ds.getXAConnection();
			xaconn.addConnectionEventListener(this);
			res = new Resource(getName(), xaconn.getXAResource(), xaconn);
			res.setJoinSupported(dataSourceAdaptor.joinSupported());
			res.setReuseResourceAfterEnd(dataSourceAdaptor
					.reuseAfterEnd());
			connCount++;
			if (log.isDebugEnabled())
				log
						.debug("SIMPLEJTA-XAPoolManager: Created new physical connection "
								+ res);
		} else {
			xaconn = (XAConnection) res.getIdentity();
		}
		if (log.isDebugEnabled())
			log.debug("SIMPLEJTA-XAPoolManager: GETRESOURCE " + xaconn);
		synchronized (availableConnections) {
			if (log.isDebugEnabled())
				log.debug("SIMPLEJTA-XAPoolManager: Adding resource " + res
						+ " to list of active (enlisted) connections");
			activeConnections.put(xaconn, res);
		}
		return res;
	}

	/**
	 * Get an unused resource instance.
	 * 
	 * @throws SQLException
	 */
	public Resource getResource() throws SQLException {
		return getResource(null);
	}

	/**
	 * Get a database connection. The underlying resource is enlisted with the
	 * global transaction.
	 * 
	 * @return
	 * @throws IllegalStateException
	 * @throws RollbackException
	 * @throws SystemException
	 * @throws SQLException
	 */
	public Connection getConnection() throws IllegalStateException,
			RollbackException, SystemException, SQLException {

		GlobalTransaction t = (GlobalTransaction) transactionManager
				.getTransaction();
		Xid xid = null;
		if (t == null) {
			// Instead of throwing an exception here, we could assume that the
			// resource
			// will be used for a local transaction.
			// This will only work if the XADataSource supports
			// global and local transactions on the same connection - the
			// Oracle one does.

			throw new IllegalStateException(Messages.ENOASSOC);
		} else {
			xid = t.getXid();
		}

		Resource res = getResource(xid);
		XAConnection xaconn = (XAConnection) res.getIdentity();

		if (t != null) {
			res.addResourceListener(this);
			t.enlistResource(res);
		}

		// Get a logical connection
		// Oracle 10g driver fails here
		// It does not allow getting a logical connection after enlisting a
		// resource
		Connection conn = xaconn.getConnection();

		return conn;
	}

	/**
	 * When a connection is closed, two things can happen. If the connection is
	 * part of a global transaction, then it may still be enlisted, in which
	 * case we move it to the map of closedConnections. Otherwise, we move it to
	 * the list of available (unused) connections.
	 * 
	 * @see javax.sql.ConnectionEventListener#connectionClosed(javax.sql.ConnectionEvent)
	 */
	public void connectionClosed(ConnectionEvent event) {
		XAConnection xaconn = (XAConnection) event.getSource();
		if (log.isDebugEnabled())
			log.debug("SIMPLEJTA-XAPoolManager: CLOSED " + xaconn);
		synchronized (availableConnections) {
			Resource res = (Resource) activeConnections.remove(xaconn);
			if (res != null) {
				Xid xid = res.getCurrentXid();
				if (xid == null) {
					if (log.isDebugEnabled())
						log
								.debug("SIMPLEJTA-XAPoolManager: Closed resource "
										+ res
										+ " is added to the list of available (unused) connections");
					addToAvailableConnections(res);
				} else {
					addResourceToClosedList(xid, res);
					if (log.isDebugEnabled())
						log
								.debug("SIMPLEJTA-XAPoolManager: Closed resource "
										+ res
										+ " is added to the list of closed (enlisted) connections");
				}
			}
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
	 * FIXME: This is broken and needs fixing
	 * 
	 * @see javax.sql.ConnectionEventListener#connectionErrorOccurred(javax.sql.ConnectionEvent)
	 */
	public void connectionErrorOccurred(ConnectionEvent event) {
		XAConnection xaconn = (XAConnection) event.getSource();
		log.error("SIMPLEJTA-XAPoolManager: Error occurred on connection "
				+ xaconn);
		log.error("SIMPLEJTA-XAPoolManager: Error details: ", event
				.getSQLException());
	}

	private void shutdownResource(Resource res) {
		res.clearResourceListeners();
		XAConnection xaconn = (XAConnection) res.getIdentity();
		xaconn.removeConnectionEventListener(this);
		try {
			if (log.isDebugEnabled())
				log
						.debug("SIMPLEJTA-XAPoolManager: Closing physical connection ("
								+ xaconn.toString() + ")");
			xaconn.close();
		} catch (SQLException e) {
		}
		connCount--;
	}

	/**
	 * Close all connections that are not in use. Connections that are active or
	 * enlisted are not closed.
	 */
	public void destroy() {
		if (log.isDebugEnabled())
			log.debug("SIMPLEJTA-XAPoolManager: There are " + connCount
					+ " connections in the pool that is being shutdown");
		synchronized (availableConnections) {
			Resource res = null;
			while (!availableConnections.isEmpty()) {
				res = (Resource) availableConnections.pop();
				shutdownResource(res);
			}
		}
		if (connCount != 0)
			log
					.warn("SIMPLEJTA-XAPoolManager: There are active connections in the pool that is being shutdown");
	}

	/**
	 * @return Returns the password.
	 */
	public final String getPassword() {
		return password;
	}

	/**
	 * @return Returns the url.
	 */
	public final String getUrl() {
		return url;
	}

	/**
	 * @return Returns the user.
	 */
	public final String getUser() {
		return user;
	}

	public int getIdleConnectionsTimeout() {
		return idleConnectionsTimeout;
	}

	public void setIdleConnectionsTimeout(int idleConnectionsTimeout) {
		this.idleConnectionsTimeout = idleConnectionsTimeout;
	}

	public int getMaxConnections() {
		return maxConnections;
	}

	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	public int getMaxIdleConnections() {
		return maxIdleConnections;
	}

	public void setMaxIdleConnections(int maxIdleConnections) {
		this.maxIdleConnections = maxIdleConnections;
	}

	public int getMaxResourceLife() {
		return maxResourceLife;
	}

	public void setMaxResourceLife(int maxResourceLife) {
		this.maxResourceLife = maxResourceLife;
	}

	public int getMaxResourceUseCount() {
		return maxResourceUseCount;
	}

	public void setMaxResourceUseCount(int maxResourceUseCount) {
		this.maxResourceUseCount = maxResourceUseCount;
	}

	public JDBCDataSourceAdaptor getDataSourceAdaptor() {
		return dataSourceAdaptor;
	}

	public void setDataSourceAdaptor(JDBCDataSourceAdaptor dataSourceAdaptor) {
		this.dataSourceAdaptor = dataSourceAdaptor;
	}

	public SimpleTransactionManager getTransactionManager() {
		return transactionManager;
	}

	public void setTransactionManager(
			SimpleTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public void afterPropertiesSet() throws Exception {
//		System.err.println("Instance " + this.toString() + " of "
//				+ getClass().getName() + " created");
		ds = dataSourceAdaptor.createDataSource(connectionProperties);
	}

	public void setBeanName(String arg0) {
		this.name = arg0;
	}

	public String getName() {
		return name;
	}

	public String toString() {
		return "JDBCXAConnectionPool(name=" + getName() + ")";
	}

	public void setConnectionProperties(Map connectionProperties) {
		this.connectionProperties = connectionProperties;
	}
	
}