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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * PoolingDataSource implements a simple Pooling Manager for a
 * ConnectionPoolDataSource. Note that the user/password used is fixed either in
 * the constructor or the first time getConnection(user,password) is invoked. At
 * present, the implementation uses a simple stack based approach to pooling.
 * Connections are recycled every so often. 
 * TODO: The recycling thresholds need to be configurable 
 * TODO: Set a maximum limit on number of connections.
 * 
 * @author Dibyendu Majumdar
 * @since 09.Jan.2005
 * @see javax.sql.ConnectionPoolDataSource
 */
public class PoolingDataSource implements ConnectionEventListener, DataSource {

	private static Logger log = LogManager.getLogger(PoolingDataSource.class);

	private ConnectionPoolDataSource cpds = null;

	private LinkedList freeConnections = new LinkedList();

	private HashMap activeConnections = new HashMap();

	private String user;

	private String password;
	
	private boolean shuttingDown = false;

	private static int MAX_RESOURCE_LIFE = 1000 * 60 * 60;

	private static int MAX_RESOURCE_USECOUNT = 3000;

	/**
	 * Construct a PoolingDataSource - user/password will be set on the first
	 * call to getConnection(user, password).
	 */
	public PoolingDataSource(ConnectionPoolDataSource cpds) {
		super();
		this.cpds = cpds;
	}

	/**
	 * Construct a PoolingDataSource with a specific user/password.  
	 */
	public PoolingDataSource(ConnectionPoolDataSource cpds, String user,
			String password) {
		super();
		this.cpds = cpds;
		this.user = user;
		this.password = password;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.DataSource#getConnection()
	 */
	public Connection getConnection() throws SQLException {
		if (shuttingDown) {
			throw new SQLException(Messages.ESHUTDOWN);
		}
		if (user == null || password == null) {
			throw new SQLException(Messages.EARGUP);
		}
		Connection conn = null;
		ConnectionHolder holder = null;
		PooledConnection pconn = null;
		synchronized (freeConnections) {
			if (!freeConnections.isEmpty()) {
				holder = (ConnectionHolder) freeConnections.removeLast();
				long curTime = System.currentTimeMillis();
				if ((curTime - holder.birthTime) > MAX_RESOURCE_LIFE
						|| holder.useCount > MAX_RESOURCE_USECOUNT) {
					if (log.isDebugEnabled())
						log.debug("SIMPLJTA-PoolingDataSource: Shutting down resource "
										+ holder.getPooledConnection());
					shutdownConnection(holder);
					holder = null;
				} else {
					holder.useCount++;
				}
				if (holder != null) {
					pconn = holder.getPooledConnection();
					conn = pconn.getConnection();
					activeConnections.put(pconn, holder);
					if (log.isDebugEnabled())
						log.debug("SIMPLEJTA-PoolingDataSource: Reusing a pooled connection");
				}
			}
		}
		if (conn == null) {
			pconn = cpds.getPooledConnection(user, password);
			conn = pconn.getConnection();
			pconn.addConnectionEventListener(this);
			synchronized (freeConnections) {
				holder = new ConnectionHolder(pconn);
				activeConnections.put(pconn, holder);
			}
			if (log.isDebugEnabled())
				log.debug("SIMPLEJTA-PoolingDataSource: Acquired new pooled connection");
		}
		return conn;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.DataSource#getConnection(java.lang.String,
	 *      java.lang.String)
	 */
	public Connection getConnection(String arg0, String arg1)
			throws SQLException {
		if (user == null || password == null) {
			user = arg0;
			password = arg1;
		}
		return getConnection();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.DataSource#getLoginTimeout()
	 */
	public int getLoginTimeout() throws SQLException {
		return cpds.getLoginTimeout();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.DataSource#getLogWriter()
	 */
	public PrintWriter getLogWriter() throws SQLException {
		return cpds.getLogWriter();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.DataSource#setLoginTimeout(int)
	 */
	public void setLoginTimeout(int arg0) throws SQLException {
		cpds.setLoginTimeout(arg0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.DataSource#setLogWriter(java.io.PrintWriter)
	 */
	public void setLogWriter(PrintWriter arg0) throws SQLException {
		cpds.setLogWriter(arg0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.ConnectionEventListener#connectionClosed(javax.sql.ConnectionEvent)
	 */
	public void connectionClosed(ConnectionEvent event) {
		if (log.isDebugEnabled())
			log.debug("SIMPLEJTA-PoolingDataSource: Connection closed, adding to list of idle connections");
		PooledConnection pconn = (PooledConnection) event.getSource();
		synchronized (freeConnections) {
			ConnectionHolder holder = (ConnectionHolder) activeConnections
					.remove(pconn);
			if (holder != null) {
				freeConnections.add(holder);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.sql.ConnectionEventListener#connectionErrorOccurred(javax.sql.ConnectionEvent)
	 */
	public void connectionErrorOccurred(ConnectionEvent event) {
		PooledConnection pconn = (PooledConnection) event.getSource();
		pconn.removeConnectionEventListener(this);
		synchronized (freeConnections) {
			activeConnections.remove(pconn);
		}
		SqlUtil.close(pconn);
	}

	private void shutdownConnection(ConnectionHolder holder) {
		PooledConnection pconn = holder.getPooledConnection();
		pconn.removeConnectionEventListener(this);
		SqlUtil.close(pconn);
	}

	/**
	 * Shutdown the PoolingDataSource. Close unused connections.
	 * Note that active connections are not closed.
	 */
	public void destroy() {
		shuttingDown = true;
		synchronized (freeConnections) {
			Iterator i = freeConnections.iterator();
			while (i.hasNext()) {
				ConnectionHolder holder = (ConnectionHolder) i.next();
				shutdownConnection(holder);
				i.remove();
			}
		}
	}

	@Override
	public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		// TODO Auto-generated method stub
		return false;
	}
}

class ConnectionHolder {

	PooledConnection pconn;

	long birthTime = System.currentTimeMillis();

	int useCount = 0;

	ConnectionHolder(PooledConnection pconn) {
		this.pconn = pconn;
	}

	public final PooledConnection getPooledConnection() {
		return pconn;
	}
}