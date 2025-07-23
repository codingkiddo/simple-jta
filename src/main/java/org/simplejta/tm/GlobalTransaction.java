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

import java.util.Iterator;
import java.util.LinkedList;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.simplejta.tm.log.TransactionLog;
import org.simplejta.tm.xid.XidFactory;
import org.simplejta.util.ExceptionUtil;
import org.simplejta.util.Messages;

import jakarta.transaction.HeuristicCommitException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * <p>
 * GlobalTransaction implements the JTA Transaction interface.
 * </p>
 * 
 * @author Dibyendu Majumdar
 * @since 11.Oct.2004
 */
public class GlobalTransaction implements Status, Constants, Transaction {

    /**
     * Maximum number of branches this transaction can support FIXME: Document
     * max limit on number of branches
     */
    private static final int MAX_BRANCHES = 20;

    private static Logger log = LogManager.getLogger(GlobalTransaction.class);

    /**
     * Represents current <code>state</code> of the transaction
     */
    int state;

    /**
     * The Transaction Branches.
     */
    BranchTransaction branches[] = new BranchTransaction[MAX_BRANCHES];

    /**
     * Number of branches in this transaction.
     */
    int n_branches = 0;

    /**
     * <code>gxid</code> is the Xid allocated for the Global Transaction. This
     * xid is used to determine identity/equality and in log messages.
     */
    Xid gxid;

    /**
     * <code>tid</code> is a serial number allocated to the transaction. It is
     * to help save the transaction to the transaction log. <code>tid</code>
     * can be used as an indexing mechanism.
     */
    long tid;

    /**
     * <code>recovering</code> is set when we are in recovery mode (ie at
     * restart).
     */
    boolean recovering = false;

    /**
     * <code>logged</code> indicates whether the transaction has been recorded
     * in the Transaction Log.
     */
    boolean logged = false;

    /**
     * The transaction manager that owns this transaction.
     */
    SimpleTransactionManager tm = null;

    /**
     * Transaction Log that we are using to log transactions for recovery.
     */
    TransactionLog tlog = null;

    /**
     * List of registered Synchronization objects.
     */
    LinkedList syncList = new LinkedList();

    /**
     * Flag to indicate that this object has been disposed.
     */
    boolean disposed = false;

    /**
     * Useful for testing two phase commits and recovery Avoids one-phase commit
     * optimization.
     */
    static boolean forceTwoPhase = false;

    /**
     * Useful for forcing crashes during commit.
     */
    static int crashTesting = 0; //  1 or 2 or 3 or 4;

    /**
     * Construct a new Global Transaction.
     * 
     * @param tm
     *            The SimpleTransactionManager instance that owns this
     *            transaction.
     */
    GlobalTransaction(SimpleTransactionManager tm) {
        state = STATUS_ACTIVE;
        gxid = XidFactory.createGlobalXid(tm);
        this.tm = tm;
        this.tlog = tm.getLog();
        recovering = false;
        if (log.isDebugEnabled()) {
            log.debug("SIMPLEJTA-GlobalTransaction: NEW GLOBAL TRANSACTION: "
                    + gxid);
        }
        crashTesting = tm.getCrashTesting();
    }

    /**
     * Contruct a Global Transaction from a known state. Used during recovery.
     */
    public GlobalTransaction(SimpleTransactionManager tm, long tid, Xid gxid,
            int state) {
        this.tm = tm;
        this.tlog = tm.getLog();
        this.tid = tid;
        this.gxid = gxid;
        this.state = state;
        recovering = true;
    }

    /**
     * Get a branch transaction by position
     */
    public BranchTransaction getBranch(int i) {
        if (i < 0 || i >= n_branches) {
            return null;
        }
        return branches[i];
    }

    /**
     * Find a branch transaction that has seen this resource before.
     */
    BranchTransaction find(Resource res) throws SystemException {
        int i = 0;
        for (i = 0; i < n_branches; i++) {
            if (branches[i].find(res) != null) {
                return branches[i];
            }
        }
        return null;
    }

    /**
     * Find a branch that is using the same underlying Resource Manager as this
     * resource.
     */
    BranchTransaction findSameRM(Resource res) throws SystemException {
        int i = 0;
        for (i = 0; i < n_branches; i++) {
            if (branches[i].isSameRM(res)) {
                return branches[i];
            }
        }
        return null;
    }

    /**
     * Add a new branch to the branches array.
     */
    public void addBranch(BranchTransaction branch) throws SystemException {
        if (n_branches == MAX_BRANCHES) {
            throw new SystemException(Messages.ETOOMANYBRANCHES);
        }
        branches[n_branches++] = branch;
    }

    /**
     * Enlist the specified resource with this transaction. This method must be
     * MT safe because JTA allows multiple threads to concurrently use the same
     * transaction.
     * 
     * @param res
     *            Resource to be enlisted
     * @return true, if resource was enlisted successfully, otherwise false.
     * @throws SystemException -
     *             Thrown if the transaction manager encounters an unexpected
     *             error condition.
     * @throws IllegalStateException -
     *             Thrown if the transaction in the target object is in the
     *             prepared state or the transaction is inactive.
     * @throws RollbackException -
     *             Thrown to indicate that the transaction has been marked for
     *             rollback only.
     * @see javax.transaction.Transaction#enlistResource(javax.transaction.xa.XAResource)
     */
    public synchronized boolean enlistResource(Resource res)
            throws SystemException, IllegalStateException, RollbackException {

        if (state == Status.STATUS_MARKED_ROLLBACK) {
            throw new RollbackException(Messages.ETRXROLLBACKONLY);
        }

        if (state != Status.STATUS_ACTIVE) {
            throw new IllegalStateException(Messages.ETRXENLISTINACTIVE);
        }

        // First check if we have seen this resource before
        BranchTransaction branch = find(res);
        if (branch != null) {
            if (log.isDebugEnabled()) {
                log.debug("SIMPLEJTA-GlobalTransaction: Branch already exists for Resource "
                        + res + " Global Xid=" + gxid + ", hence reusing");
            }
            ResourceReference resource = branch.find(res);
            if (resource.isActive()) {
                // Nothing to do
                return true;
            }
            if (resource.isSuspended()) {
                try {
                    // Resource is in suspended state, lets resume it.
                    branch.resume(res);
                }
                catch (Exception e) {
                    state = Status.STATUS_MARKED_ROLLBACK;
                    throw (SystemException) new SystemException(
                            Messages.EUNEXPECTED
                                    + " while attempting to resume a Resource").initCause(e);
                }
                return true;
            }
            return false;
        }
        // Okay - this is a new resource - but is it from a resource manager we
        // have seen before?
        // Note that Oracle does not support joining an existing branch - so for
        // Oracle findSameRM() will always be false.
        // With Derby also we create a new Branch.
        branch = findSameRM(res);
        if (branch != null) {
            if (log.isDebugEnabled()) {
                log.debug("SIMPLEJTA-GlobalTransaction: Branch found for the same Resource "
                        + res
                        + " Global Xid="
                        + gxid
                        + ", hence joining existing branch");
            }
            try {
                branch.join(res);
            }
            catch (Exception e) {
                state = Status.STATUS_MARKED_ROLLBACK;
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED
                                + " while attempting to join a transaction branch").initCause(e);
            }
            return true;
        }
        if (log.isDebugEnabled()) {
            log.debug("SIMPLEJTA-GlobalTransaction: Starting new Transaction Branch for Resource "
                    + res + ",Global Xid=" + gxid);
        }
        // Create a new branch for this resource.
        // Always happens in case of Oracle, but if the resource manager is
        // the same as an existing branch, then prepare() will return READONLY.
        branch = new BranchTransaction(this, XidFactory.createBranchXid(gxid,
                n_branches + 1), res, n_branches);
        try {
            branch.start();
        }
        catch (Exception e) {
            state = Status.STATUS_MARKED_ROLLBACK;
            throw (SystemException) new SystemException(Messages.EUNEXPECTED
                    + " while attempting to start a transaction branch").initCause(e);
        }
        addBranch(branch);
        return true;
    }

    /**
     * Enlist the resource specified with this transaction.
     * 
     * @param res
     *            Resource to be enlisted
     * @return true, if resource was enlisted successfully, otherwise false.
     * @throws SystemException
     *             Thrown if the transaction manager encounters an unexpected
     *             error condition.
     * @throws IllegalStateException
     *             Thrown if the transaction in the target object is in the
     *             prepared state or the transaction is inactive.
     * @throws RollbackException
     *             Thrown to indicate that the transaction has been marked for
     *             rollback only.
     * @see javax.transaction.Transaction#enlistResource(javax.transaction.xa.XAResource)
     */
    public boolean enlistResource(XAResource xares) throws SystemException,
            IllegalStateException, RollbackException {

        if (xares instanceof Resource) {
            return enlistResource((Resource) xares);
        }
        throw new SystemException(Messages.ENOTRESOURCE
                + Resource.class.getName());
    }

    /**
     * Disassociate the resource specified from this transaction. Must be MT
     * safe as JTA allows multiple threads to concurrently use the same
     * transaction object.
     * 
     * @param res
     *            Resource to be delisted
     * @param flag
     *            One of the values of TMSUCCESS, TMSUSPEND, or TMFAIL.
     * @return true, if resource was delisted successfully, otherwise false.
     * @throws SystemException
     *             Thrown if the transaction manager encounters an unexpected
     *             error condition.
     * @throws IllegalStateException
     *             Thrown if the transaction in the target object is not active.
     * @see javax.transaction.Transaction#delistResource(javax.transaction.xa.XAResource,
     *      int)
     */
    public synchronized boolean delistResource(Resource res, int flag)
            throws IllegalStateException, SystemException {

        if (state != Status.STATUS_ACTIVE
                && state != Status.STATUS_MARKED_ROLLBACK) {
            throw new IllegalStateException(
                    Messages.ETRXDELISTINACTIVE);
        }

        // First check if we have seen this resource before
        BranchTransaction branch = find(res);
        if (branch != null) {
            try {
                if (flag == XAResource.TMSUCCESS) {
                    branch.endSuccessfully(res);
                }
                else if (flag == XAResource.TMFAIL) {
                    branch.fail(res);
                    state = Status.STATUS_MARKED_ROLLBACK;
                }
                else {
                    branch.suspend(res);
                }
            }
            catch (Exception e) {
                state = Status.STATUS_MARKED_ROLLBACK;
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED + " while attempting to end "
                                + res + " with flag " + flag).initCause(e);
            }
            return false;
        }
        return false;
    }

    /**
     * Disassociate the resource specified from this transaction.
     * 
     * @param res
     *            Resource to be delisted
     * @param flag
     *            One of the values of TMSUCCESS, TMSUSPEND, or TMFAIL.
     * @return true, if resource was delisted successfully, otherwise false.
     * @throws SystemException
     *             Thrown if the transaction manager encounters an unexpected
     *             error condition.
     * @throws IllegalStateException
     *             Thrown if the transaction in the target object is not active.
     * @see javax.transaction.Transaction#delistResource(javax.transaction.xa.XAResource,
     *      int)
     */
    public boolean delistResource(XAResource xares, int flag)
            throws IllegalStateException, SystemException {

        if (xares instanceof Resource) {
            return delistResource((Resource) xares, flag);
        }
        throw new SystemException(
                Messages.ENOTRESOURCE
                        + Resource.class.getName());
    }

    /**
     * Commit the transaction. We record all exceptions carefully so that when
     * errors occur, it is possible to find out what caused the problem. Must be
     * MT safe. If multiple threads are using the same transaction object then
     * it is the caller's responsibility to wait for all those threads to
     * complete their work before calling commit(). If this is not done, result
     * will be undefined.
     * 
     * @see javax.transaction.Transaction#commit()
     * @throws RollbackException
     *             Thrown to indicate that the transaction has been rolled back
     *             rather than committed.
     * @throws HeuristicMixedException
     *             Thrown to indicate that a heuristic decision was made and
     *             that some relevant updates have been committed while others
     *             have been rolled back.
     * @throws HeuristicRollbackException
     *             Thrown to indicate that a heuristic decision was made and
     *             that all relevant updates have been rolled back.
     * @throws SecurityException
     *             Thrown to indicate that the thread is not allowed to commit
     *             the transaction.
     * @throws IllegalStateException
     *             Thrown if the transaction in the target object is inactive.
     * @throws SystemException
     *             Thrown if the transaction manager encounters an unexpected
     *             error condition.
     */
    public synchronized void commit() throws IllegalStateException,
            SystemException, RollbackException, HeuristicMixedException,
            HeuristicRollbackException {

        int nHeurRBErr = 0;
        int nHeurMixedErr = 0;
        int nRBErr = 0;
        int nSysErr = 0;
        int nIllegalStateErr = 0;

        if (state == STATUS_MARKED_ROLLBACK) {
            rollback();
            throw new RollbackException(
                    Messages.EROLLEDBACKONLY);
        }

        if (!recovering && state != STATUS_ACTIVE) {
            throw new IllegalStateException(
                    Messages.ECOMMITINACTIVE);
        }

        Exception mulex = null;

        if (n_branches == 1 && !forceTwoPhase) {
            // do one phase commit since only one branch is involved
            if (!branches[0].canOnePhaseCommit()) {
                throw new IllegalStateException(
                        Messages.ECOMMIT1PC);
            }
            try {
                state = STATUS_COMMITTING;
                doBeforeCompletion();
                branches[0].endSuccessfully();
                branches[0].commitOnePhase();
                state = STATUS_COMMITTED;
                doAfterCompletion();
            }
            catch (Exception e) {
                mulex = ExceptionUtil.chainException(mulex, e);
                if (e instanceof SystemException)
                    nSysErr++;
                else if (e instanceof HeuristicMixedException)
                    nHeurMixedErr++;
                else if (e instanceof HeuristicRollbackException)
                    nHeurRBErr++;
                else if (e instanceof IllegalStateException)
                    nIllegalStateErr++;
                else if (e instanceof RollbackException) {
                    state = STATUS_ROLLEDBACK;
                    doAfterCompletion();
                    throw (RollbackException) e;
                }
            }
        }
        else {
            int i = 0;
            int vote = VOTE_COMMIT;
            if (state == STATUS_ACTIVE) {
                // This state is possible only during normal operations
                // When in recovery, status will always be STATUS_PREPARING
                // phase 1 - invoke end() on all transaction branches
                state = STATUS_PREPARING;
                doBeforeCompletion();
                for (i = 0; i < n_branches && vote == VOTE_COMMIT; i++) {
                    try {
                        branches[i].endSuccessfully();
                    }
                    catch (Exception e) {
                        vote = VOTE_ROLLBACK;
                        mulex = ExceptionUtil.chainException(mulex, e);
                        if (e instanceof RollbackException) {
                            nRBErr++;
                        }
                        else if (e instanceof SystemException) {
                            nSysErr++;
                        }
                        else if (e instanceof IllegalStateException) {
                            nIllegalStateErr++;
                        }
                    }
                }

                // It is useful to crash the system here during testing
                if (crashTesting == 1) {
                    System.err.println("SIMPLEJTA-GlobalTransaction: System being crashed after invoking end() on all branches for testing purposes");
                    log.error("SIMPLEJTA-GlobalTransaction: System being crashed after invoking end() on all branches for testing purposes");
                    // System.exit(1);
                    throw new RuntimeException("Crashed");
                }

                if (!recovering && vote == VOTE_COMMIT) {
                    // a logged transaction will always have at least
                    // STATUS_PREPARING
                    tlog.insertTransaction(this);
                    logged = true;
                }
                // phase 2 - invoke prepare() on all branches
                for (i = 0; i < n_branches && vote == VOTE_COMMIT; i++) {
                    try {
                        int rc = branches[i].prepare();
                        if (rc != VOTE_COMMIT) {
                            vote = VOTE_ROLLBACK;
                        }
                    }
                    catch (Exception e) {
                        vote = VOTE_ROLLBACK;
                        mulex = ExceptionUtil.chainException(mulex, e);
                        if (e instanceof SystemException) {
                            nSysErr++;
                        }
                        else if (e instanceof IllegalStateException) {
                            nIllegalStateErr++;
                        }
                    }
                }
            }

            // phase 3 - commit or rollback
            if (vote == VOTE_ROLLBACK) {
                try {
                    rollback();
                }
                catch (Exception e) {
                    mulex = ExceptionUtil.chainException(mulex, e);
                }
                throw (RollbackException) new RollbackException(
                        Messages.EROLLEDBACKERROR).initCause(mulex);
            }
            else {
                int n_committed = 0;
                int n_errors = 0;
                // It is useful to crash the system here during testing
                if (crashTesting == 2) {
                    System.err.println("SIMPLEJTA-GlobalTransaction: System being crashed after invoking prepare() on all branches for testing purposes");
                    log.error("SIMPLEJTA-GlobalTransaction: System being crashed after invoking prepare() on all branches for testing purposes");
                    // System.exit(1);
                    throw new RuntimeException("Crashed");
                }
                state = STATUS_COMMITTING;
                if (logged) {
                    // This is what will determine whether we will commit or
                    // rollback during recovery
                    tlog.updateTransaction(this, false /*
                                                        * no need to update
                                                        * branches
                                                        */);
                }
                // It is useful to crash the system here during testing
                if (crashTesting == 3) {
                    System.err.println("SIMPLEJTA-GlobalTransaction: System being crashed after setting status to COMITTING for testing purposes");
                    log.error("SIMPLEJTA-GlobalTransaction: System being crashed after setting status to COMITTING for testing purposes");
                    //System.exit(1);
                    throw new RuntimeException("Crashed");
                }
                for (i = 0; i < n_branches; i++) {
                    try {
                        if (branches[i].getStatus() != TX_READONLY) {
                            branches[i].commitTwoPhase();
                            n_committed++;
                        }
                    }
                    catch (Exception e) {
                        mulex = ExceptionUtil.chainException(mulex, e);
                        if (e instanceof SystemException)
                            nSysErr++;
                        else if (e instanceof HeuristicMixedException)
                            nHeurMixedErr++;
                        else if (e instanceof HeuristicRollbackException)
                            nHeurRBErr++;
                        else if (e instanceof IllegalStateException)
                            nIllegalStateErr++;
                        else if (e instanceof RollbackException)
                            nRBErr++;
                        n_errors++;
                    }
                    // It is useful to crash the system here during testing
                    if (crashTesting == 4) {
                        System.err.println("SIMPLEJTA-GlobalTransaction: System being crashed after one branch has been committed");
                        log.error("SIMPLEJTA-GlobalTransaction: System being crashed after one branch has been committed");
                        System.exit(1);
                    }
                    if (n_committed == 0 && n_errors > 0) {
                        // If we haven't committed any branch yet and have
                        // encountered an error
                        // then try rolling back.
                        try {
                            rollback();
                        }
                        catch (Exception e) {
                            mulex = ExceptionUtil.chainException(mulex, e);
                        }
                        throw (RollbackException) new RollbackException(
                                Messages.EROLLEDBACKERROR).initCause(mulex);
                    }
                }
                state = STATUS_COMMITTED;
                forget();
                if (logged) {
                    tlog.deleteTransaction(this);
                    logged = false;
                }
                doAfterCompletion();
            }
        }

        if (nHeurMixedErr > 0 || nHeurRBErr > 0 || nRBErr > 0 || nSysErr > 0
                || nIllegalStateErr > 0) {
            state = STATUS_UNKNOWN;
            doAfterCompletion();
        }
        dispose();

        if (nHeurMixedErr > 0) {
            throw (HeuristicMixedException) new HeuristicMixedException(
                    Messages.EHEURMIXED).initCause(mulex);
        }
        else if (nHeurRBErr > 0) {
            throw (HeuristicRollbackException) new HeuristicRollbackException(
                    Messages.EHEURRB).initCause(mulex);
        }
        else if (nRBErr > 0) {
            throw (RollbackException) new RollbackException(
                    Messages.EROLLEDBACKERROR).initCause(mulex);
        }
        else if (nSysErr > 0) {
            throw (SystemException) new SystemException(
                    Messages.EUNEXPECTED + " while attempting to commit").initCause(mulex);
        }
        else if (nIllegalStateErr > 0) {
            throw (IllegalStateException) new IllegalStateException(
                    Messages.EILLEGAL).initCause(mulex);
        }
    }

    /**
     * Rollback a transaction. We try to complete the rollback even when
     * exceptions occur because what else is there to do? We take care to
     * preserve all exceptions and throw them at the end. Must be MT safe. If
     * multiple threads are using the same transaction object then all such
     * threads must complete their owrk before this method is called. Calling
     * rollback() from one thread while other threads are actively using the
     * transaction object will cause the other threads to encounter an error.
     * 
     * @see javax.transaction.Transaction#rollback()
     * @throws IllegalStateException
     * @throws SystemException
     *             Thrown if the transaction manager encounters an unexpected
     *             error condition.
     */
    public synchronized void rollback() throws SystemException,
            IllegalStateException {
        int nHeurCommErr = 0;
        int nHeurMixedErr = 0;
        int nSysErr = 0;
        int nIllegalStateErr = 0;
        int i = 0;

        Exception mulex = null;

        if (state == STATUS_ROLLEDBACK || state == STATUS_COMMITTED
                || state == STATUS_UNKNOWN || state == STATUS_NO_TRANSACTION) {
            return;
        }

        state = STATUS_ROLLING_BACK;

        if (logged) {
            // Rollback may have been invoked after preparing, therefore we log
            // the status
            // so that during recovery the correct action can be determined.
            // Normal rollbacks are not logged because of presumed abort
            // protocol
            tlog.updateTransaction(this, false);
        }
        boolean need_rollback = true;
        if (recovering) {
            // we only need to rollback if at least one branch was
            // prepared successfully
            need_rollback = false;
            for (i = 0; i < n_branches; i++) {
                if (branches[i].getStatus() == TX_PREPARED) {
                    need_rollback = true;
                    break;
                }
            }
        }
        if (!recovering) {
            // we only need to do this during normal operation
            // this is not a superfluous step because when we
            // end the branch, the resource is marked for reuse.
            for (i = 0; i < n_branches; i++) {
                try {
                    if (!branches[i].isEnded())
                        branches[i].endFailed();
                }
                catch (Exception e) {
                    if (e instanceof RollbackException) {
                        // Okay because we are rolling back anyway
                        continue;
                    }
                    mulex = ExceptionUtil.chainException(mulex, e);
                    if (e instanceof SystemException) {
                        nSysErr++;
                    }
                    else if (e instanceof IllegalStateException) {
                        nIllegalStateErr++;
                    }
                }
            }
        }
        if (need_rollback) {
            for (i = 0; i < n_branches; i++) {
                if (recovering && branches[i].getStatus() != TX_PREPARED) {
                    // during recovery we only rollback those branches that are
                    // prepared
                    continue;
                }
                try {
                    branches[i].rollback();
                }
                catch (Exception e) {
                    mulex = ExceptionUtil.chainException(mulex, e);
                    if (e instanceof HeuristicCommitException)
                        nHeurCommErr++;
                    else if (e instanceof HeuristicMixedException)
                        nHeurMixedErr++;
                    else if (e instanceof SystemException)
                        nSysErr++;
                    else if (e instanceof IllegalStateException)
                        nIllegalStateErr++;
                }
            }
            forget();
        }
        state = STATUS_ROLLEDBACK;
        if (logged) {
            tlog.deleteTransaction(this);
            logged = false;
        }
        doAfterCompletion();
        dispose();
        if (nHeurMixedErr > 0) {
            throw (SystemException) new SystemException(
                    Messages.EHEURMIXED).initCause(mulex);
        }
        else if (nHeurCommErr > 0) {
            throw (SystemException) new SystemException(
                    Messages.EHEURCOMM).initCause(mulex);
        }
        else if (nSysErr > 0) {
            throw (SystemException) new SystemException(
                    Messages.EUNEXPECTED + " while attempting to rollback transaction").initCause(mulex);
        }
        else if (nIllegalStateErr > 0) {
            throw (IllegalStateException) new IllegalStateException(
                    Messages.EUNEXPECTED + " while attempting to rollback transaction").initCause(mulex);
        }
    }

    /**
     * Forget all the heuristically completed branches. We ignore errors here
     * (except for logging them).
     */
    void forget() {
        int i = 0;
        for (i = 0; i < n_branches; i++) {
            if (branches[i].heuristicallyCompleted()) {
                try {
                    branches[i].forget();
                }
                catch (SystemException e) {
                    log.warn(
                            "SIMPLEJTA-GlobalTransaction: Error occurred while attempting to forget transaction branch",
                            e);
                }
                catch (IllegalStateException ex) {
                    log.warn(
                            "SIMPLEJTA-GlobalTransaction: Error occurred while attempting to forget transaction branch",
                            ex);
                }
            }
        }
    }

    /**
     * Resolve a transaction during recovery based upon recorded status.
     */
    void resolve() throws SystemException {
        try {
            if (state == STATUS_COMMITTING) {
                log.info("SIMPLEJTA-GlobalTransaction: Resolving transaction "
                        + this + " - committing");
                commit();
            }
            else {
                log.info("SIMPLEJTA-GlobalTransaction: Resolving transaction "
                        + this + " - rolling back");
                rollback();
            }
        }
        catch (SystemException e1) {
            throw e1;
        }
        catch (Exception e) {
            throw (SystemException) new SystemException(
                    Messages.ERESOLVE + this).initCause(e);
        }
    }

    /**
     * Get the status of this transaction. Must be MT safe.
     * 
     * @see javax.transaction.Transaction#getStatus()
     */
    public synchronized int getStatus() throws SystemException {
        return state;
    }

    /**
     * Get the status without threatening to throw an exception - internal use
     * only.
     */
    int getStatusInternal() {
        return state;
    }

    /**
     * Set the status of this transaction - for internal use only.
     */
    void setStatusInternal(int state) {
        this.state = state;
    }

    /**
     * Register a synchronization object.
     * 
     * @see javax.transaction.Transaction#registerSynchronization(javax.transaction.Synchronization)
     */
    public synchronized void registerSynchronization(Synchronization arg)
            throws RollbackException, IllegalStateException, SystemException {
        if (state != STATUS_ACTIVE) {
            throw new IllegalStateException(
                    Messages.ESYNCINACTIVE);
        }
        syncList.add(arg);
    }

    /**
     * Set the transaction so that only possible outcome is rollback.
     * 
     * @see javax.transaction.Transaction#setRollbackOnly()
     */
    public synchronized void setRollbackOnly() throws IllegalStateException,
            SystemException {
        if (state != STATUS_ACTIVE) {
            throw new IllegalStateException(
                    Messages.EMARKROLLBACKONLY);
        }
        state = STATUS_MARKED_ROLLBACK;
    }

    /**
     * Get the transaction id allocated to this transaction. This is a numeric
     * id used for identifying log records.
     */
    public long getTid() {
        return tid;
    }

    /**
     * Set the transaction id - internal use only.
     */
    public void setTid(long i) {
        tid = i;
    }

    /**
     * Get the global transaction xid.
     */
    public Xid getXid() {
        return gxid;
    }

    /**
     * Get the number of branches in this transaction.
     */
    public int getNumBranches() {
        return n_branches;
    }

    /**
     * Execute before completion actions.
     */
    private void doBeforeCompletion() {
        try {
            Iterator i = syncList.iterator();
            while (i.hasNext()) {
                Synchronization sync = (Synchronization) i.next();
                sync.beforeCompletion();
            }
        }
        catch (Exception e) {
            log.warn(
                    "SIMPLEJTA-GlobalTransaction: Error occurred in Before Completion",
                    e);
        }
    }

    /**
     * Execute after completion actions. Ensure these are executed only once.
     */
    private void doAfterCompletion() {
        try {
            Iterator i = syncList.iterator();
            while (i.hasNext()) {
                Synchronization sync = (Synchronization) i.next();
                sync.afterCompletion(state);
            }
        }
        catch (Exception e) {
            log.warn(
                    "SIMPLEJTA-GlobalTransaction: Error occurred in After Completion",
                    e);
        }
        syncList.clear();
    }

    /**
     * Dispose this object, meant to aid garbage collection.
     */
    private void dispose(boolean beingFinalized) {
        if (disposed) {
            return;
        }
        if (!beingFinalized) {
            if (log.isDebugEnabled())
                log.debug("SIMPLEJTA-GlobalTransaction: DISPOSING GLOBAL TRANSACTION: "
                        + gxid);
        }
        for (int i = 0; i < n_branches; i++) {
            branches[i].dispose();
            branches[i] = null;
        }
        n_branches = 0;
        gxid = null;
        tlog = null;
        syncList.clear();
        syncList = null;
        state = STATUS_NO_TRANSACTION;
        disposed = true;
    }

    private void dispose() {
        dispose(false);
    }

    protected void finalize() throws Throwable {
        dispose(true);
        super.finalize();
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof GlobalTransaction))
            return false;
        GlobalTransaction other = (GlobalTransaction) obj;
        if (other == this)
            return true;
        return gxid.equals(other.gxid);
    }

    public int hashCode() {
        if (gxid == null)
            return 0;
        return gxid.hashCode();
    }

    public String toString() {
        return "GlobalTransaction(xid=" + gxid + "state=" + state + ",numBranches=" + n_branches
                + ")";
    }

	public SimpleTransactionManager getTransactionManager() {
		return tm;
	}
    
    
}