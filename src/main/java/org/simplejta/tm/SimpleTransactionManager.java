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

import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.simplejta.tm.log.TransactionLog;
import org.simplejta.tm.xid.SimpleXidImpl;
import org.simplejta.util.Messages;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

/**
 * SimpleTransactionManager implements a simple JTA compatible Transaction
 * Manager that supports locally coordinated transactions.
 * <p>
 * MT notes - this object will be shared between multiple threads so it needs to
 * be thread-safe.
 * 
 * @author Dibyendu Majumdar
 * @since 12.Oct.2004
 */
public class SimpleTransactionManager implements TransactionManager,
		InitializingBean, BeanFactoryAware, DisposableBean, BeanNameAware {

	static public final String STM_TMGR_BEAN_FACTORY = "beanFactory";

	static public final String STM_TMGR_BEAN_ID = "transactionManager";

	/**
	 * Log4J Logger for generating log messages.
	 */
	private static Logger log = LogManager
			.getLogger(SimpleTransactionManager.class);

	/**
	 * A thread specific variable to hold the current active transaction for the
	 * thread.
	 */
	private static ThreadLocal currentTrx = new ThreadLocal();

	/**
	 * The transaction manager log. MT: Needs to be thread-safe.
	 */
	private TransactionLog transactionLog = null;

	/**
	 * Each instance of the transaction manager must have a unique id. This
	 * allows multiple instances of SimpleTransactionManager to share the same
	 * transaction log. Also, the id is embedded in Xids to allow easy
	 * identification of the transaction manager responsible for a particular
	 * xid.
	 */
	private String tmid = null;

	/**
	 * The time when this transaction manager was created. The birthTime is used
	 * to generate Xids.
	 * 
	 * @see org.simplejta.tm.xid.SimpleXidImpl SimpleXidImpl
	 */
	private long birthTime;

	/**
	 * The Spring BeanFactory that is managing us.
	 */
	private BeanFactory beanFactory;

	/**
	 * Setting this to a non-zero value forces a system crash.
	 */
	private int crashTesting = 0;

	/**
	 * Create a new instance of the transaction manager.
	 * 
	 * @throws SystemException
	 */
	SimpleTransactionManager() {
		birthTime = System.currentTimeMillis();
		log.info("SIMPLEJTA-SimpleTransactionManager: Instance of " + getClass().getName() + " created");
	}

	/**
	 * Invoked by Spring framework after setting properties.
	 * The initialisation of SimpleTransactionManager is completed by 
	 * performing restart recovery.
	 */
	public void afterPropertiesSet() throws Exception {
		doRecovery();
	}

	/**
	 * Create a new transaction and associate it with the current thread.
	 * 
	 * @throws NotSupportedException -
	 *             Thrown if the thread is already associated with a transaction
	 *             and the Transaction Manager implementation does not support
	 *             nested transactions.
	 * @throws SystemException -
	 *             Thrown if the transaction manager encounters an unexpected
	 *             error condition.
	 * @see javax.transaction.TransactionManager#begin()
	 */
	public void begin() throws NotSupportedException, SystemException {
		if (currentTrx.get() != null) {
			/*
			 * Nested transactions are not supported.
			 */
			throw new NotSupportedException(Messages.ENESTEDTRX);
		}
		GlobalTransaction gt = new GlobalTransaction(this);
		currentTrx.set(gt);
	}

	/**
	 * Suspend the transaction currently associated with the calling thread and
	 * return a Transaction object that represents the transaction context being
	 * suspended. If the calling thread is not associated with a transaction,
	 * the method returns a null object reference. When this method returns, the
	 * calling thread is not associated with a transaction.
	 * 
	 * @return Transaction object representing the suspended transaction.
	 * @throws SystemException -
	 *             Thrown if the transaction manager encounters an unexpected
	 *             error condition.
	 * @see javax.transaction.TransactionManager#suspend()
	 */
	public Transaction suspend() throws SystemException {
		GlobalTransaction gt = (GlobalTransaction) currentTrx.get();
		if (gt == null) {
			return null;
		}
		currentTrx.set(null);
		return gt;
	}

	/**
	 * Resume the transaction context association of the calling thread with the
	 * transaction represented by the supplied Transaction object. When this
	 * method returns, the calling thread is associated with the transaction
	 * context specified.
	 * 
	 * @param t -
	 *            The Transaction object that represents the transaction to be
	 *            resumed.
	 * @throws InvalidTransactionException -
	 *             Thrown if the parameter transaction object contains an
	 *             invalid transaction.
	 * @throws java.lang.IllegalStateException -
	 *             Thrown if the thread is already associated with another
	 *             transaction.
	 * @throws SystemException -
	 *             Thrown if the transaction manager encounters an unexpected
	 *             error condition.
	 * @see javax.transaction.TransactionManager#resume(javax.transaction.Transaction)
	 */
	public void resume(Transaction t) throws SystemException {
		if (t != null && !(t instanceof GlobalTransaction)) {
			throw new SystemException(Messages.EINVALIDXID
					+ GlobalTransaction.class.getName());
		}
		if (currentTrx.get() != null) {
			throw new IllegalStateException(Messages.EALREADYASSOC);
		}
		GlobalTransaction gt = (GlobalTransaction) t;
		currentTrx.set(gt);
	}

	/**
	 * Get the transaction object that represents the transaction context of the
	 * calling thread.
	 * 
	 * @return The Transaction object representing the transaction associated
	 *         with the calling thread.
	 * @throws SystemException -
	 *             Thrown if the transaction manager encounters an unexpected
	 *             error condition.
	 * @see javax.transaction.TransactionManager#getTransaction()
	 */
	public Transaction getTransaction() throws SystemException {
		GlobalTransaction gt = (GlobalTransaction) currentTrx.get();
		return gt;
	}

	/**
	 * Complete the transaction associated with the current thread. When this
	 * method completes, the thread is no longer associated with a transaction.
	 * 
	 * @throws RollbackException -
	 *             Thrown to indicate that the transaction has been rolled back
	 *             rather than committed.
	 * @throws HeuristicMixedException -
	 *             Thrown to indicate that a heuristic decision was made and
	 *             that some relevant updates have been committed while others
	 *             have been rolled back.
	 * @throws HeuristicRollbackException -
	 *             Thrown to indicate that a heuristic decision was made and
	 *             that all relevant updates have been rolled back.
	 * @throws java.lang.SecurityException -
	 *             Thrown to indicate that the thread is not allowed to commit
	 *             the transaction.
	 * @throws java.lang.IllegalStateException -
	 *             Thrown if the current thread is not associated with a
	 *             transaction.
	 * @throws SystemException -
	 *             Thrown if the transaction manager encounters an unexpected
	 *             error condition.
	 * @see javax.transaction.TransactionManager#commit()
	 */
	public void commit() throws RollbackException, HeuristicMixedException,
			HeuristicRollbackException, SecurityException,
			IllegalStateException, SystemException {
		GlobalTransaction gt = (GlobalTransaction) currentTrx.get();
		if (gt == null) {
			throw new IllegalStateException(Messages.ENOASSOC);
		}
		gt.commit();
		currentTrx.set(null);
	}

	/**
	 * Roll back the transaction associated with the current thread. When this
	 * method completes, the thread is no longer associated with a transaction.
	 * Note: We cleanup current transaction context even if the rollback throws
	 * an exception.
	 * 
	 * @throws java.lang.SecurityException -
	 *             Thrown to indicate that the thread is not allowed to roll
	 *             back the transaction.
	 * @throws java.lang.IllegalStateException -
	 *             Thrown if the current thread is not associated with a
	 *             transaction.
	 * @throws SystemException -
	 *             Thrown if the transaction manager encounters an unexpected
	 *             error condition.
	 * @see javax.transaction.TransactionManager#rollback()
	 */
	public void rollback() throws IllegalStateException, SecurityException,
			SystemException {
		GlobalTransaction gt = (GlobalTransaction) currentTrx.get();
		if (gt == null) {
			throw new IllegalStateException(Messages.ENOASSOC);
		}
		try {
			gt.rollback();
		} finally {
			// Cleanup, even if rollback threw an exception
			currentTrx.set(null);
		}
	}

	/**
	 * Obtain the status of the transaction associated with the current thread.
	 * 
	 * @return The transaction status. If no transaction is associated with the
	 *         current thread, this method returns the Status.NoTransaction
	 *         value.
	 * @throws SystemException -
	 *             Thrown if the transaction manager encounters an unexpected
	 *             error condition.
	 * @see javax.transaction.TransactionManager#getStatus()
	 */
	public int getStatus() throws SystemException {
		GlobalTransaction gt = (GlobalTransaction) currentTrx.get();
		if (gt == null) {
			return Status.STATUS_NO_TRANSACTION;
		}
		return gt.getStatus();
	}

	/**
	 * Modify the transaction associated with the current thread such that the
	 * only possible outcome of the transaction is to roll back the transaction.
	 * 
	 * @throws java.lang.IllegalStateException -
	 *             Thrown if the current thread is not associated with a
	 *             transaction.
	 * @throws SystemException -
	 *             Thrown if the transaction manager encounters an unexpected
	 *             error condition.
	 * @see javax.transaction.TransactionManager#setRollbackOnly()
	 */
	public void setRollbackOnly() throws IllegalStateException, SystemException {
		GlobalTransaction gt = (GlobalTransaction) currentTrx.get();
		if (gt == null) {
			return;
		}
		gt.setRollbackOnly();
	}

	/**
	 * Modify the timeout value that is associated with transactions started by
	 * subsequent invocations of the begin method. If an application has not
	 * called this method, the transaction service uses some default value for
	 * the transaction timeout.
	 * 
	 * @param seconds -
	 *            The value of the timeout in seconds. If the value is zero, the
	 *            transaction service restores the default value. If the value
	 *            is negative a SystemException is thrown.
	 * @throws SystemException -
	 *             Thrown if the transaction manager encounters an unexpected
	 *             error condition.
	 * @see javax.transaction.TransactionManager#setTransactionTimeout(int)
	 */
	public void setTransactionTimeout(int seconds) throws SystemException {
		// TODO Not implemented yet
	}

	/**
	 * Get a reference to a particular instance of SimpleTransactionManager. Note that
	 * if the SimpleTransactionManager instance does not exist, it will be
	 * created and initialized.
	 * <p>
	 * The caller is expected to invoke {@link SimpleTransactionManagerReference#release()}
	 * once they are finished with the instance. Failure to do so will cause the SimpleTransactionManager
	 * instance to live until the encosing Spring BeanFactory is destroyed.
	 * 
	 * @param props
	 *            List of properties that specify various configuration
	 *            parameters.
	 */
	public static SimpleTransactionManagerReference getTransactionManagerReference(
			Properties props) throws SystemException {
		String beanFactoryKey = props.getProperty(STM_TMGR_BEAN_FACTORY);
		assertNotNull(beanFactoryKey, STM_TMGR_BEAN_FACTORY);
		String beanId = props.getProperty(STM_TMGR_BEAN_ID);
		assertNotNull(beanId, STM_TMGR_BEAN_ID);
		return getTransactionManagerReference(beanFactoryKey, beanId);
	}

	/**
	 * Get a reference to a particular instance of SimpleTransactionManager. Note that
	 * if the SimpleTransactionManager instance does not exist, it will be
	 * created and initialized.
	 * 
	 * @param beanFactoryId The id of the Spring Beanfactory implementation in 
	 * 						the configuration file name SimpleJTA.xml. This configuration
	 * 						file must be in the classpath, and is equivalent to a
	 * 						beanRefContext.xml file.
	 * @param beanId The ID of the SimpleTransactionManager instance.
	 */
	public static SimpleTransactionManagerReference getTransactionManagerReference(
			String beanFactoryId, String beanId) throws SystemException {
		return new SimpleTransactionManagerReference(beanFactoryId, beanId);
	}

	/**
	 * Gets an instance of SimpleTransactionManager, creating it if necessary.
	 * 
	 * @param beanFactoryId The id of the Spring Beanfactory implementation in 
	 * 						the configuration file name SimpleJTA.xml. This configuration
	 * 						file must be in the classpath, and is equivalent to a
	 * 						beanRefContext.xml file.
	 * @param beanId The ID of the SimpleTransactionManager instance.
	 */
	public static SimpleTransactionManager getTransactionManager(String beanFactoryId,
			String beanId) throws SystemException {
		SimpleTransactionManagerReference ref = getTransactionManagerReference(
				beanFactoryId, beanId);
		SimpleTransactionManager tmgr = ref.getTransactionManager();
		ref.release();
		return tmgr;
	}

	/**
	 * Assert that a property has been set; throw an Exception if not.
	 * 
	 * @param value
	 *            If null, an Exception will be thrown
	 * @param name
	 *            Name of the property
	 * @throws SystemException
	 *             If the property value is null
	 */
	protected static void assertNotNull(String value, String name)
			throws SystemException {
		if (value == null) {
			throw new SystemException(Messages.EMISSINGCONFIG + name);
		}
	}

	/**
	 * Shutdowns the instance of SimpleTransactionManager.
	 */
	public void destroy() throws Exception {
		log.info("SIMPLEJTA-SimpleTransactionManager: Shutting down instance "
				+ this);
	}

	/**
	 * Hold data about the Resource being recovered.
	 */
	static class RecoveredResource {
		ResourceReference resource;

		Xid recoveredXids[];

		SimpleXidImpl rollbackXids[];

		boolean error = false;

		RecoveredResource(ResourceReference res) {
			resource = res;
		}
	}

	RecoveredResource findRecoveredResource(LinkedList list,
			ResourceReference resource) {
		Iterator i = list.iterator();
		while (i.hasNext()) {
			RecoveredResource rr = (RecoveredResource) i.next();
			try {
				if (rr.resource.getResource().isSameRM(resource.getResource())) {
//					System.err.println("Resource " + rr.resource.getResource()
//							+ " and " + resource.getResource()
//							+ " are the same");
					return rr;
				}
			} catch (XAException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * Obtain a distinct list of resources that we will be recovering. For each
	 * resource, identify the list of prepared xids.
	 * 
	 * @param list
	 *            List of GlobalTransaction objects
	 * @return
	 */
	protected LinkedList getRecoveredResources(LinkedList list) {
		LinkedList recoveredResources = new LinkedList();
		Iterator i = list.iterator();
		while (i.hasNext()) {
			GlobalTransaction gt = (GlobalTransaction) i.next();
			for (int b = 0; b < gt.getNumBranches(); b++) {
				BranchTransaction bt = gt.getBranch(b);
				ResourceReference resource = bt.getResource();
				RecoveredResource rr = findRecoveredResource(
						recoveredResources, resource);
				if (rr == null || rr.error) {
					rr = new RecoveredResource(resource);
					try {
						// TODO We try to get all the xids in one go.
						// The alternative is to call TMSTARTRSCAN and then
						// TMNOFLAGS until the
						// xid list is null or 0 length, or an exception is
						// thrown
						rr.recoveredXids = resource
								.recover(XAResource.TMSTARTRSCAN
										+ XAResource.TMENDRSCAN);
						if (rr.recoveredXids != null) {
							// Identify our Xids - the ones that we will need to
							// recover
							rr.rollbackXids = new SimpleXidImpl[rr.recoveredXids.length];
							for (int x = 0; x < rr.recoveredXids.length; x++) {
								Xid xid = rr.recoveredXids[x];
								if (xid.getFormatId() != SimpleXidImpl.SIMPLEXID_FORMAT) {
									continue;
								}
								try {
									SimpleXidImpl sxid = new SimpleXidImpl(xid);
									if (sxid.belongsTo(this)) {
										// This is one of ours
										rr.rollbackXids[x] = sxid;
									}
								} catch (SystemException e1) {
									log
											.warn(
													"SIMPLEJTA-SimpleTransactionManager: Error occurred while identifying recovery xid",
													e1);
									continue;
								}
							}
						}
					} catch (XAException e) {
						log
								.warn(
										"SIMPLEJTA-SimpleTransactionManager: Error occurred while obtaining list of recovery xids",
										e);
						rr.error = true;
					}
					recoveredResources.add(rr);
				}
			}
		}
		return recoveredResources;
	}

	/**
	 * Reconcile our list of GlobalTransactions with the xid lists obtained from
	 * Resource Managers.
	 * 
	 * @param list
	 *            List of GlobalTransaction objects
	 */
	protected LinkedList reconcileWithResourceManagers(LinkedList list) {
		LinkedList recoveredResources = getRecoveredResources(list);
		Iterator i = list.iterator();
		while (i.hasNext()) {
			boolean okay = true;
			GlobalTransaction gt = (GlobalTransaction) i.next();
			for (int b = 0; b < gt.getNumBranches(); b++) {
				BranchTransaction bt = gt.getBranch(b);
				ResourceReference resource = bt.getResource();
				RecoveredResource rr = findRecoveredResource(
						recoveredResources, resource);
				if (rr.error) {
					okay = false;
					break;
				} else {
					int x;
					SimpleXidImpl xid = null;
					for (x = 0; x < rr.rollbackXids.length; x++) {
						xid = rr.rollbackXids[x];
						if (xid == null) {
							continue;
						}
						if (bt.getXid().equals(xid)) {
							break;
						}
					}
					if (x == rr.recoveredXids.length) {
						// Resource does know about the xid that we have??
						// Check if we were going to commit this xid
						if (gt.getStatusInternal() == Status.STATUS_COMMITTING) {
							// The branch must have committed successfully,
							// because if it was a heuristic decision to
							// rollback then
							// we would have got the xid back.
							bt
									.setStatusInternal(BranchTransaction.TX_COMMITTED);
						} else {
							// TODO Do we need this?
							// gt.setStatusInternal(Status.STATUS_MARKED_ROLLBACK);
						}
					} else {
						// Okay we will handle this one, so let's
						// remove it from the list of rollback xids
						bt.setStatusInternal(BranchTransaction.TX_PREPARED);
						rr.rollbackXids[x] = null;
					}
				}
			}
			if (!okay) {
				// We cannot recover this GlobalTransaction because of
				// resource errors
				i.remove();
			}
		}
		return recoveredResources;
	}

	/**
	 * Rollback any transactions that did not appear in our transaction log, but
	 * are reported as prepared by the Resource Manager.
	 * 
	 * @param recoveredResources
	 */
	protected void rollbackRemainingXids(LinkedList recoveredResources) {
		Iterator i = recoveredResources.iterator();
		while (i.hasNext()) {
			RecoveredResource rr = (RecoveredResource) i.next();
			if (rr.error)
				continue;
			for (int x = 0; x < rr.rollbackXids.length; x++) {
				Xid xid = rr.rollbackXids[x];
				if (xid == null) {
					continue;
				}
				try {
					rr.resource.getResource().rollback(xid);
				} catch (XAException e) {
					if (e.errorCode == XAException.XA_HEURHAZ
							|| e.errorCode == XAException.XA_HEURMIX
							|| e.errorCode == XAException.XA_HEURCOM
							|| e.errorCode == XAException.XA_HEURRB) {

						try {
							rr.resource.getResource().forget(xid);
						} catch (XAException e1) {
							log
									.warn(
											"SIMPLEJTA-SimpleTransactionManager: Error occurred while attempting to forget transaction"
													+ xid, e1);
						}
					} else {
						log
								.warn(
										"SIMPLEJTA-SimpleTransactionManager: Error occurred while attempting to rollback transaction"
												+ xid, e);
					}
				}
			}
			rr.recoveredXids = null;
			rr.resource = null;
			rr.rollbackXids = null;
		}
		recoveredResources.clear();
	}
	
	/**
	 * Resolve those transactions that are identified as ours.
	 * 
	 * @param list
	 *            List of GlobalTransactions to be resolved
	 */
	protected void resolveOurTransactions(LinkedList list) {
		Iterator i = list.iterator();
		while (i.hasNext()) {
			GlobalTransaction gt = (GlobalTransaction) i.next();
			try {
				log
						.info("SIMPLEJTA-SimpleTransactionManager: Resolving transaction "
								+ gt.getXid());
				gt.resolve();
				transactionLog.deleteTransaction(gt);
			} catch (SystemException e) {
				log
						.error(
								"SIMPLEJTA-SimpleTransactionManager: Error occurred while resolving transaction "
										+ gt.getXid(), e);
			}
		}
	}

	/**
	 * Recover the TransactionManager after a system restart.
	 * 
	 * @throws SystemException
	 *             If there is an unexpected error
	 */
	protected synchronized void doRecovery() throws SystemException {
		log.info("SIMPLEJTA-SimpleTransactionManager: Starting recovery");
		LinkedList list = transactionLog.recoverTransactions(this);
		LinkedList recoveredResources = reconcileWithResourceManagers(list);
		resolveOurTransactions(list);
		rollbackRemainingXids(recoveredResources);
		log.info("SIMPLEJTA-SimpleTransactionManager: Recovery completed");
	}

	/**
	 * Get the Tranaction Log implementation.
	 * 
	 * @return Instance of TransactionLog interface
	 */
	public TransactionLog getLog() {
		return transactionLog;
	}

	/**
	 * Get the unique id allocated to this instance of SimpleTransactionManager.
	 * 
	 * @return ID of the Transaction Manager instance.
	 */
	public String getTmid() {
		return tmid;
	}

	/**
	 * Get the birth time of the SimpleTransactionManager instance.
	 * 
	 * @return Birth time of the Transaction Manager instance.
	 */
	public long getBirthTime() {
		return birthTime;
	}

	/**
	 * Compute a unique hash code for the Transaction Manager.
	 */
	public int hashCode() {
		if (tmid == null)
			return 0;
		return tmid.hashCode();
	}

	/**
	 * Return a human readable representation of the Transaction Manager.
	 */
	public String toString() {
		return "SimpleTransactionManager(tmid=" + tmid + ")";
	}

	public void setTransactionManagerId(String tmid) {
		this.tmid = tmid;
	}

	public void setBeanFactory(BeanFactory arg0) throws BeansException {
		this.beanFactory = arg0;
	}

	public TransactionLog getTransactionLog() {
		return transactionLog;
	}

	public void setTransactionLog(TransactionLog transactionLog) {
		this.transactionLog = transactionLog;
	}

	public org.simplejta.tm.ResourceFactory getResourceFactory(String resourceFactoryName) {
		return (org.simplejta.tm.ResourceFactory) beanFactory.getBean(resourceFactoryName);
	}

	/**
	 * Recover a Resource of the correct type. The typeId argument is used to
	 * determine the type of resource.
	 * 
	 * @param transactionManager
	 *            The SimpleTransactionManager instance that will manage the
	 *            recovered resource.
	 * @param resourceFactoryName
	 *            The type of resource.
	 * @param url
	 *            A parameter for the resource object.
	 * @param user
	 *            User credentials.
	 * @param password
	 *            User credentials.
	 * @param xid
	 *            The GlobalTransaction Xid to which affinity is desired.
	 * @throws SQLException
	 * @throws SystemException
	 */
	public Resource recoverResource(String resourceFactoryName, String url, String user,
			String password, Xid xid) throws SystemException {
		if (log.isDebugEnabled()) {
			log.debug("SIMPLEJTA-Resource: Recovering resource for TMGR.id=["
					+ getTmid() + "] TYPEID=[" + resourceFactoryName + "] URL=[" + url
					+ "] USER=[" + user + "]");
		}
		try {
			return getResourceFactory(resourceFactoryName).getResource(xid);
		} catch (Exception e) {
			throw (SystemException) new SystemException(Messages.EUNEXPECTED
					+ " while attempting to recover Resource").initCause(e);
		}
	}

	public void setBeanName(String beanName) {
		this.tmid = beanName;
	}

	public int getCrashTesting() {
		return crashTesting;
	}

	/**
	 * Forces a RuntimeException during commit processing. 1 - Exception raised
	 * after end() but before any logging. 2 - Exception raised after initial
	 * logging and prepare() but before commit decision is logged. 3 - Exception
	 * raised after commit decision is logged.
	 * 
	 * @param crashTesting
	 */
	public void setCrashTesting(int crashTesting) {
		this.crashTesting = crashTesting;
	}

}