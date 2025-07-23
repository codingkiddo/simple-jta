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
package org.simplejta.tm.log.jdbc.adaptors;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.ConnectionPoolDataSource;
import javax.transaction.SystemException;

import org.simplejta.tm.GlobalTransaction;
import org.simplejta.tm.log.jdbc.JDBCTransactionLogAdaptor;
import org.simplejta.util.ClassUtils;
import org.simplejta.util.Messages;
import org.simplejta.util.PoolingDataSource;
import org.simplejta.util.SqlUtil;
import org.springframework.beans.factory.InitializingBean;

/**
 * <p>
 * <code>OracleTransactionLog</code> implements the transaction log using an
 * Oracle datasource.
 * </p>
 * 
 * @author Dibyendu Majumdar
 * @since 8.Nov.2004
 */
public class OracleTransactionLogAdaptor implements JDBCTransactionLogAdaptor,
		InitializingBean {

	static final String SQL_GET_SEQ = "SELECT sjta_tidseq.nextval FROM dual";

	static final String SQL_INSERT_GT = "INSERT INTO sjta_transactions (TID, FORMATID, GTID, BQUAL, STATE, TMID) VALUES (?, ?, ?, ?, ?, ?)";

	// TODO: Move to JDBCTransactionLog
	PoolingDataSource source = null;

	String url;

	String user;

	String password;

	// TODO: Move to JDBCTransactionLog
	public Connection getConnection() throws SystemException {
		try {
			Connection conn = source.getConnection();
			return conn;
		} catch (SQLException e) {
			SystemException ex = new SystemException(Messages.EOPENCONN);
			ex.initCause(e);
			throw ex;
		}
	}

	public OracleTransactionLogAdaptor() {
	}

	private long allocateTid(Connection conn) throws SystemException {
		Statement stmt = null;
		ResultSet rs = null;
		long tid = 0;
		try {
			stmt = conn.createStatement();
			rs = stmt.executeQuery(SQL_GET_SEQ);
			if (rs.next()) {
				tid = rs.getLong(1);
			} else {
				throw new SQLException(Messages.EUNEXPECTED
						+ " No data returned from " + SQL_GET_SEQ);
			}
		} catch (SQLException e) {
			SystemException se = new SystemException(Messages.EUNEXPECTED
					+ " Failed to allocate new tid");
			se.initCause(e);
			throw se;
		} finally {
			SqlUtil.close(rs);
			SqlUtil.close(stmt);
		}
		return tid;
	}

	public void insertGlobalTransaction(GlobalTransaction gt, Connection conn)
			throws SystemException {
		PreparedStatement stmt = null;
		try {
			long tid = allocateTid(conn);
			gt.setTid(tid);
			stmt = conn.prepareStatement(SQL_INSERT_GT);
			stmt.setLong(1, gt.getTid());
			stmt.setInt(2, gt.getXid().getFormatId());
			stmt.setBytes(3, gt.getXid().getGlobalTransactionId());
			stmt.setBytes(4, gt.getXid().getBranchQualifier());
			stmt.setInt(5, gt.getStatus());
			stmt.setString(6, gt.getTransactionManager().getTmid());
			int n = stmt.executeUpdate();
			if (n != 1) {
				throw new SQLException(Messages.EUNEXPECTED
						+ " Unexpected result while attempting to run "
						+ SQL_INSERT_GT);
			}
		} catch (SQLException e) {
			SystemException se = new SystemException(Messages.ELOGXID);
			se.initCause(e);
			throw se;
		} finally {
			SqlUtil.close(stmt);
		}
	}

	public void destroy() {
		if (source != null) {
			source.destroy();
			source = null;
		}
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public void afterPropertiesSet() throws Exception {
		ConnectionPoolDataSource ds = null;
		try {
			Class cl = ClassUtils
					.forName("oracle.jdbc.pool.OracleConnectionPoolDataSource");
			ds = (ConnectionPoolDataSource) cl.newInstance();
			ClassUtils.invokeMethod(cl, ds, "setURL", url);
			ClassUtils.invokeMethod(cl, ds, "setUser", user);
			ClassUtils.invokeMethod(cl, ds, "setPassword", password);
		} catch (Throwable e) {
			throw (SQLException) new SQLException(Messages.ECREATEDS)
					.initCause(e);
		}
		source = new PoolingDataSource(ds, user, password);
	}

}