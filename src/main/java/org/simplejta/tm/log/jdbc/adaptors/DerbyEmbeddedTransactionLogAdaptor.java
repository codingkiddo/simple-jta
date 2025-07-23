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
import java.util.HashMap;

import javax.sql.ConnectionPoolDataSource;
import javax.transaction.SystemException;

import org.simplejta.tm.GlobalTransaction;
import org.simplejta.tm.log.jdbc.JDBCTransactionLogAdaptor;
import org.simplejta.util.Messages;
import org.simplejta.util.PoolingDataSource;
import org.simplejta.util.SqlUtil;
import org.springframework.beans.factory.InitializingBean;

public class DerbyEmbeddedTransactionLogAdaptor implements
		JDBCTransactionLogAdaptor, InitializingBean {

	static final String SQL_INSERT_GT = "INSERT INTO sjta_transactions (FORMATID, GTID, BQUAL, STATE, TMID) VALUES (?, ?, ?, ?, ?)";

	// TODO: Move to JDBCTransactionLog
	PoolingDataSource source = null;

	String url;

	String user;

	String password;

	public DerbyEmbeddedTransactionLogAdaptor() {
		super();
	}

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

	public void insertGlobalTransaction(GlobalTransaction gt, Connection conn)
			throws SystemException {
		PreparedStatement insert_gt_stmt = null;
		try {
			insert_gt_stmt = conn.prepareStatement(SQL_INSERT_GT,
					Statement.RETURN_GENERATED_KEYS);
			insert_gt_stmt.setInt(1, gt.getXid().getFormatId());
			insert_gt_stmt.setBytes(2, gt.getXid().getGlobalTransactionId());
			insert_gt_stmt.setBytes(3, gt.getXid().getBranchQualifier());
			insert_gt_stmt.setInt(4, gt.getStatus());
			insert_gt_stmt.setString(5, gt.getTransactionManager().getTmid());
			int n = insert_gt_stmt.executeUpdate();
			if (n != 1) {
				throw new SQLException(Messages.EUNEXPECTED
						+ " Unexpected result while attempting to run "
						+ SQL_INSERT_GT);
			}
			ResultSet rs = null;
			try {
				rs = insert_gt_stmt.getGeneratedKeys();
				if (rs.next()) {
					long tid = rs.getLong(1);
					gt.setTid(tid);
				} else {
					throw new SQLException(Messages.EUNEXPECTED
							+ " Failed to obtain auto generated key from "
							+ SQL_INSERT_GT);
				}
			} finally {
				SqlUtil.close(rs);
			}
		} catch (SQLException e) {
			SystemException se = new SystemException(Messages.ELOGXID);
			se.initCause(e);
			throw se;
		} finally {
			SqlUtil.close(insert_gt_stmt);
		}
	}

	public void destroy() {
		if (source != null) {
			source.destroy();
			source = null;
		}
	}

	public void afterPropertiesSet() throws Exception {
		ConnectionPoolDataSource ds = null;
		String className = "org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource";
		HashMap props = new HashMap();

		props.put("databaseName", url);
		props.put("user", user);
		props.put("password", password);
		ds = (ConnectionPoolDataSource) SqlUtil.createDataSource(className,
				props);
		source = new PoolingDataSource(ds, user, password);
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
}
