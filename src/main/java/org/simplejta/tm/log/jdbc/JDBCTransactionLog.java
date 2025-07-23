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
package org.simplejta.tm.log.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;

import javax.transaction.SystemException;
import javax.transaction.xa.Xid;

import org.simplejta.tm.BranchTransaction;
import org.simplejta.tm.GlobalTransaction;
import org.simplejta.tm.Resource;
import org.simplejta.tm.SimpleTransactionManager;
import org.simplejta.tm.log.TransactionLog;
import org.simplejta.tm.xid.XidFactory;
import org.simplejta.util.Messages;
import org.simplejta.util.SqlUtil;
import org.springframework.beans.factory.DisposableBean;


/**
 * <p>
 * JDBCTransactionLog: Implements TransactionLog using an database. Two tables
 * are used - sjta_transactions, and sjta_transaction_branches.
 * </p>
 * 
 * <p>
 * Note on thread safety - this class must be thread safe. Hence, connections,
 * statements, etc. are released immediately after use. The class relies upon
 * connection pooling to improve efficiency.
 * </p>
 * 
 * @author Dibyendu Majumdar
 * @since 8.Nov.2004
 */
public class JDBCTransactionLog implements TransactionLog, DisposableBean {

	static final String SQL_INSERT_BT = "INSERT INTO sjta_transaction_branches (TID, BID, FORMATID, GTID, BQUAL, STATE, URL, USERID, PASSWORD, TYPEID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	static final String SQL_UPDATE_GT = "UPDATE sjta_transactions SET STATE = ? WHERE TID = ?";

	static final String SQL_UPDATE_BT = "UPDATE sjta_transaction_branches SET STATE = ? WHERE TID = ? AND BID = ?";

	static final String SQL_DELETE_GT = "DELETE FROM sjta_transactions WHERE TID = ?";

	static final String SQL_DELETE_BT = "DELETE FROM sjta_transaction_branches WHERE TID = ?";

	static final String SQL_SELECT_GT = "SELECT TID, FORMATID, GTID, BQUAL, STATE FROM sjta_transactions WHERE TMID = ?";

	static final String SQL_SELECT_BT = "SELECT FORMATID, GTID, BQUAL, STATE, URL, USERID, PASSWORD, TYPEID FROM sjta_transaction_branches WHERE TID = ? ORDER BY BID";

	// TODO Look at ways of marking transactions as needing recovery - so that
	// we can
	// always obtain a list of recoverable transactions at startup even if
	// other transactions have been added in the meantime.

	/**
	 * <code>tmid</code> identifies the instance of a
	 * SimpleTransactionManager. To allow multiple instances of the transaction
	 * manager to share the same transaction log, each transaction manager must
	 * be assigned a unique id.
	 */
	// protected String tmid;

	/**
	 * Our instance of SimpleTransactionManager.
	 */
	//protected SimpleTransactionManager tm = null;
	
	JDBCTransactionLogAdaptor jdbcTransactionLogAdaptor;
	
	/**
	 * To be implemented by derived class. Must insert row into sjta_transactions
	 * and initialize tid by calling gt.setTid().
	 * 
	 * @param gt
	 * @throws SystemException
	 */
	public void insertGlobalTransaction(GlobalTransaction gt, Connection conn)
			throws SystemException {
		jdbcTransactionLogAdaptor.insertGlobalTransaction(gt, conn);
	}

	public Connection getConnection() throws SystemException {
		return jdbcTransactionLogAdaptor.getConnection();
	}

	public JDBCTransactionLog() {	
	}
	
//	protected JDBCTransactionLog(SimpleTransactionManager tm) throws SystemException {
//		//this.tm = tm;
//		this.tmid = tm.getTmid();
//	}

	/**
	 * Insert a new transaction. The transaction will be assigned a numeric
	 * transaction id.
	 */
	public void insertTransaction(GlobalTransaction gt) throws SystemException {
		boolean okay = false;
		Connection conn = getConnection();
		try {
			insertGlobalTransaction(gt, conn);
			for (int i = 0; i < gt.getNumBranches(); i++) {
				BranchTransaction bt = gt.getBranch(i);
				insertTransactionBranch(gt, bt, conn);
			}
			okay = true;
		} finally {
			try {
				if (okay) {
					conn.commit();
				} else {
					conn.rollback();
				}
			} catch (SQLException e) {
				SystemException se = new SystemException(Messages.ELOGXID);
				se.initCause(e);
				throw se;
			} finally {
				SqlUtil.close(conn);
			}
		}
	}

	/**
	 * Insert a transaction branch.
	 * 
	 * @param gt
	 *            Global Transaction
	 * @param bt
	 *            Branch Transaction
	 * @throws SystemException
	 */
	private void insertTransactionBranch(GlobalTransaction gt,
			BranchTransaction bt, Connection conn) throws SystemException {
		long tid = gt.getTid();
		int bid = bt.getBid();
		PreparedStatement stmt = null;
		try {
			stmt = conn.prepareStatement(SQL_INSERT_BT);
			stmt.setLong(1, tid);
			stmt.setInt(2, bid);
			stmt.setInt(3, bt.getXid().getFormatId());
			stmt.setBytes(4, bt.getXid().getGlobalTransactionId());
			stmt.setBytes(5, bt.getXid().getBranchQualifier());
			stmt.setInt(6, bt.getStatus());
			stmt.setString(7, null);
		    stmt.setString(8, null);
		    stmt.setString(9, null);
			stmt.setString(10, bt.getResource().getResource().getResourceFactoryName());
			int n = stmt.executeUpdate();
			if (n != 1) {
				throw new SQLException(
						Messages.EUNEXPECTED + " Unexpected result while attempting to run "
								+ SQL_INSERT_BT);
			}
		} catch (SQLException e) {
			SystemException se = new SystemException(Messages.ELOGXID);
			se.initCause(e);
			throw se;
		} finally {
			SqlUtil.close(stmt);
		}
	}

	/**
	 * Update the status of an existing global transaction. If
	 * <code>includeBranches</code> is true, update status of all branches as
	 * well.
	 */
	public void updateTransaction(GlobalTransaction gt, boolean includeBranches)
			throws SystemException {
		boolean okay = false;
		Connection conn = getConnection();
		try {
			PreparedStatement stmt = null;
			try {
				stmt = conn.prepareStatement(SQL_UPDATE_GT);
				stmt.setInt(1, gt.getStatus());
				stmt.setLong(2, gt.getTid());
				int n = stmt.executeUpdate();
				if (n != 1) {
					throw new SQLException(
							Messages.EUNEXPECTED + " Unexpected result while attempting to run "
									+ SQL_UPDATE_GT);
				}
			} catch (SQLException e) {
				SystemException se = new SystemException(Messages.ELOGXID);
				se.initCause(e);
				throw se;
			} finally {
				SqlUtil.close(stmt);
			}

			if (includeBranches) {
				for (int i = 0; i < gt.getNumBranches(); i++) {
					BranchTransaction bt = gt.getBranch(i);
					updateTransactionBranch(gt, bt, conn);
				}
			}
			okay = true;
		} finally {
			try {
				if (okay) {
					conn.commit();
				} else {
					conn.rollback();
				}
			} catch (SQLException e) {
				SystemException se = new SystemException(Messages.ELOGXID);
				se.initCause(e);
				throw se;
			} finally {
				SqlUtil.close(conn);
			}
		}
	}

	/**
	 * Update status of a Transaction Branch.
	 * 
	 * @param gt
	 * @param bt
	 * @throws SystemException
	 */
	private void updateTransactionBranch(GlobalTransaction gt,
			BranchTransaction bt, Connection conn) throws SystemException {
		long tid = gt.getTid();
		int bid = bt.getBid();
		PreparedStatement stmt = null;
		try {
			stmt = conn.prepareStatement(SQL_UPDATE_BT);
			stmt.setInt(1, bt.getStatus());
			stmt.setLong(2, tid);
			stmt.setInt(3, bid);
			int n = stmt.executeUpdate();
			if (n != 1) {
				throw new SQLException(
						Messages.EUNEXPECTED + " Unexpected result while attempting to run "
								+ SQL_UPDATE_BT);
			}
		} catch (SQLException e) {
			SystemException se = new SystemException(Messages.ELOGXID);
			se.initCause(e);
			throw se;
		} finally {
			SqlUtil.close(stmt);
		}
	}

	/**
	 * Update status of a Transaction Branch.
	 */
	public void updateBranchTransaction(GlobalTransaction gt,
			BranchTransaction bt) throws SystemException {
		boolean okay = false;
		Connection conn = getConnection();
		try {
			updateTransactionBranch(gt, bt, conn);
			okay = true;
		} finally {
			try {
				if (okay) {
					conn.commit();
				} else {
					conn.rollback();
				}
			} catch (SQLException e) {
				SystemException se = new SystemException(Messages.ELOGXID);
				se.initCause(e);
				throw se;
			} finally {
				SqlUtil.close(conn);
			}
		}
	}

	/**
	 * Delete a transaction and its asoociated branches.
	 */
	public void deleteTransaction(GlobalTransaction gt) throws SystemException {
		boolean okay = false;
		Connection conn = getConnection();
		try {
			PreparedStatement stmt = null;
			try {
				stmt = conn.prepareStatement(SQL_DELETE_GT);
				stmt.setLong(1, gt.getTid());
				stmt.executeUpdate();
			} catch (SQLException e) {
				SystemException se = new SystemException(Messages.ELOGXID);
				se.initCause(e);
				throw se;
			} finally {
				SqlUtil.close(stmt);
			}
			try {
				stmt = conn.prepareStatement(SQL_DELETE_BT);
				stmt.setLong(1, gt.getTid());
				stmt.executeUpdate();
			} catch (SQLException e) {
				SystemException se = new SystemException(Messages.ELOGXID);
				se.initCause(e);
				throw se;
			} finally {
				SqlUtil.close(stmt);
			}
			okay = true;
		} finally {
			try {
				if (okay) {
					conn.commit();
				} else {
					conn.rollback();
				}
			} catch (SQLException e) {
				SystemException se = new SystemException(Messages.ELOGXID);
				se.initCause(e);
				throw se;
			} finally {
				SqlUtil.close(conn);
			}
		}
	}


	/**
	 * Obtain a list of all transactions that need to be recovered.
	 */
	public LinkedList recoverTransactions(SimpleTransactionManager tm) throws SystemException {
		LinkedList transactions = new LinkedList();
		PreparedStatement stTrx = null;
		ResultSet rsTrx = null;
		PreparedStatement stBranch = null;
		ResultSet rsBranch = null;
		// HashMap resources = new HashMap();
		Connection conn = getConnection();

		try {
			stTrx = conn.prepareStatement(SQL_SELECT_GT);
			stTrx.setString(1, tm.getTmid());
			rsTrx = stTrx.executeQuery();

			while (rsTrx.next()) {

				long tid = rsTrx.getLong(1);
				int formatId = rsTrx.getInt(2);
				byte[] gtrid = rsTrx.getBytes(3);
				byte[] bqual = rsTrx.getBytes(4);
				int state = rsTrx.getInt(5);
				Xid gxid = XidFactory.createXid(formatId, gtrid, bqual);
				GlobalTransaction gt = new GlobalTransaction(tm, tid, gxid, state);
				try {
					stBranch = conn.prepareStatement(SQL_SELECT_BT);
					stBranch.setLong(1, tid);
					rsBranch = stBranch.executeQuery();
					int bid = 0;
					while (rsBranch.next()) {
						formatId = rsBranch.getInt(1);
						gtrid = rsBranch.getBytes(2);
						bqual = rsBranch.getBytes(3);
						state = rsBranch.getInt(4);
						String url = rsBranch.getString(5);
						String user = rsBranch.getString(6);
						String password = rsBranch.getString(7);
						String typeId = rsBranch.getString(8);
						Resource res = tm.recoverResource(typeId, url, user, password,
								gxid);
						Xid xid = XidFactory.createXid(formatId, gtrid, bqual);
						BranchTransaction bt = new BranchTransaction(gt, xid,
								res, bid, state);
						gt.addBranch(bt);
						bid++;
					}
					transactions.add(gt);
				} finally {
					SqlUtil.close(rsBranch);
					SqlUtil.close(stBranch);
				}
			}
		} catch (SQLException e) {
			SystemException se = new SystemException(Messages.ELOGREAD);
			se.initCause(e);
			throw se;
		} finally {
			SqlUtil.close(rsTrx);
			SqlUtil.close(stTrx);
			SqlUtil.close(conn);
		}
		return transactions;
	}

	public void destroy() {
		jdbcTransactionLogAdaptor.destroy();
	}

	public JDBCTransactionLogAdaptor getJdbcTransactionLogAdaptor() {
		return jdbcTransactionLogAdaptor;
	}

	public void setJdbcTransactionLogAdaptor(
			JDBCTransactionLogAdaptor jdbcTransactionLogAdaptor) {
		this.jdbcTransactionLogAdaptor = jdbcTransactionLogAdaptor;
	}
	
	
}