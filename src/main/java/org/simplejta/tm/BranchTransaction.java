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

import java.util.ArrayList;
import java.util.Iterator;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.simplejta.util.Messages;

import jakarta.transaction.HeuristicCommitException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;

/**
 * BranchTransaction handles XA Resource management for a single branch within a
 * Global Transaction.
 * <p>
 * At present, the resource that started the Branch is the one used for
 * prepare/rollback/commit/forget as well as for recovery. Subsequent resources
 * that may have joined this branch are stored in the joinedResources list - so
 * that these can be ended properly when the transaction ends.
 * <p>
 * TODO: A future enhancement will be to allow any of the enlisted resources to
 * perform prepare/rollback/commit steps. This way we could handle the situation
 * where one or more of the resources have failed, but we have at least one
 * working resource.
 * <p>
 * TODO: We do not do last resource 2-phase commit optimization yet. Do not confuse
 * this with 1-phase commit which is supported.
 * 
 * @author Dibyendu Majumdar
 * @since 11.Oct.2004
 */
public class BranchTransaction implements Constants {

    private static Logger log = LogManager.getLogger(BranchTransaction.class);

    /**
     * The Global Transaction to which this Branch belongs.
     */
    GlobalTransaction gt;

    /**
     * Branch Transaction id.
     */
    Xid xid;

    /**
     * The recoverable resource that handles this Branch transaction. This is
     * the first resource that started the branch.
     */
    ResourceReference resource;

    /**
     * The list of resources that have joined this branch. First resource in
     * this list is the resource that started the branch. 
     */
    ArrayList joinedResources = new ArrayList();

    /**
     * @deprecated Status of the Resource.
     */
    int resStatus;

    /**
     * Status of this Transaction Branch.
     */
    int trxStatus;

    /**
     * A numeric id for this Transaction Branch. Branch Ids are allocated
     * starting from 0.
     */
    int bid;

    /**
     * Maps XAException error code to different categories of errors,
     * for easier handling of error conditions.
     */
    int mapErrorCode(int errcode) {

        switch (errcode) {
        case XAException.XA_RETRY:
            return ER_RETRY;
        case XAException.XAER_RMFAIL:
            return ER_RM_FAILED;
        case XAException.XAER_RMERR:
            return ER_RM_ERROR;
        case XAException.XAER_NOTA:
            return ER_INVALID_XID;
        case XAException.XA_HEURHAZ:
        case XAException.XA_HEURMIX:
            return ER_HEUR_COMPLETED;
        case XAException.XA_HEURCOM:
            return ER_HEUR_COMMIT;
        case XAException.XA_HEURRB:
            return ER_HEUR_ROLLBACK;
        case XAException.XA_RBROLLBACK:
        case XAException.XA_RBCOMMFAIL:
        case XAException.XA_RBDEADLOCK:
        case XAException.XA_RBINTEGRITY:
        case XAException.XA_RBOTHER:
        case XAException.XA_RBTIMEOUT:
        case XAException.XA_RBTRANSIENT:
        case XAException.XA_RBPROTO:
            return ER_ROLLBACK;
        case XAException.XA_RDONLY:
            return ER_READONLY;
        case XAException.XAER_DUPID:
        default:
            return ER_UNEXPECTED;
        }
    }

    /**
     * Creates a new Branch Transaction, used during normal
     * operations.
     * 
     * @param gt
     *            GlobalTransaction object to which this branch will belong
     * @param xid
     *            The Xid allocated to this branch
     * @param res
     *            The resource that will be used to start and complete this
     *            branch.
     * @param bid
     *            The branch id
     */
    BranchTransaction(GlobalTransaction gt, Xid xid, Resource res, int bid) {
        this.gt = gt;
        this.xid = xid;
        this.bid = bid;
        this.resource = new ResourceReference(res);
        resStatus = RS_OK;
        trxStatus = TX_NONE;
    }

    /**
     * Creates a Branch Transaction with a known (PREPARED) status, used 
     * during Recovery.
     * 
     * @param gt
     *            GlobalTransaction object to which this branch will belong
     * @param xid
     *            The Xid allocated to this branch
     * @param res
     *            The resource that will be used to start and complete this
     *            branch.
     * @param bid
     *            The branch id
     * @param state
     *            The initial state of the branch
     */
    public BranchTransaction(GlobalTransaction gt, Xid xid, Resource res,
            int bid, int state) {
        this.gt = gt;
        this.xid = xid;
        // Resource will be set to prepared status
        this.resource = new ResourceReference(res, true /* recovering */);
        this.bid = bid;
        resStatus = RS_OK;
        trxStatus = state;
    }

    /**
     * Can we start the branch? A branch can only be started once.
     */
    boolean canStart() {
        return trxStatus == TX_NONE;
    }

    /**
     * Start the branch using the initial resource.
     * 
     * @throws IllegalStateException -
     *             Thrown if the branch is already active
     * @throws RollbackException -
     *             Thrown if the ResourceManager says that the branch has been
     *             rolled back
     * @throws SystemException -
     *             Thrown for any unexpected error
     */
    void start() throws IllegalStateException, RollbackException,
            SystemException {

        if (!canStart()) {
            throw new IllegalStateException(Messages.EBRANCHACTIVE
                    + ", Resource " + resource);
        }

        try {
            joinedResources.add(resource);
            resource.start(xid, XAResource.TMNOFLAGS);
            trxStatus = TX_ACTIVE;
        } catch (XAException e) {
            /*
             * Possible exceptions are XA_RB*, XAER_RMERR, XAER_RMFAIL,
             * XAER_DUPID, XAER_OUTSIDE, XAER_NOTA, XAER_INVAL, or XAER_PROTO
             */
            int err = mapErrorCode(e.errorCode);
            switch (err) {
            case ER_ROLLBACK:
            case ER_INVALID_XID:
                trxStatus = TX_ROLLBACK_ONLY;
                throw (RollbackException) new RollbackException(
                        Messages.EROLLBACKONLY).initCause(e);
            case ER_RM_FAILED:
                resStatus = RS_CLOSED;
            default:
                trxStatus = TX_ROLLBACK_ONLY;
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED
                                + " while attempting to start a Transaction Branch")
                        .initCause(e);
            }
        }
    }

    /**
     * Tests if specified resource can join the branch. It can if: a) It supports joins b) We
     * haven't seen it before c) It refers to the same underlying Resource
     * Manager.
     * 
     * @param resource1
     *            Resource to be joined
     */
    boolean canJoin(Resource resource1) {
        try {
            ResourceReference res = find(resource1);
            return trxStatus == TX_ACTIVE && resource1.isJoinSupported()
                    && res == null && resource.isSameRM(resource1);
        } catch (XAException e) {
            return false;
        }
    }

    /**
     * Add this resource to our list of joined resources and start the resource.
     * 
     * @param resource1
     *            Resource that is joining current branch
     * @throws IllegalStateException -
     *             Thrown if the resource is not eligible or if this branch is
     *             not active
     * @throws RollbackException -
     *             Thrown if the Resource Manager indicates that the branch has
     *             been rolled back
     * @throws SystemException -
     *             Thrown for any unexpected error condition
     */
    void join(Resource resource1) throws IllegalStateException,
            RollbackException, SystemException {

        if (!canJoin(resource1)) {
            throw new IllegalStateException(Messages.EBRANCHJOIN + resource1);
        }
        try {
            ResourceReference resource = new ResourceReference(resource1);
            joinedResources.add(resource);
            resource.start(xid, XAResource.TMJOIN);
        } catch (XAException e) {
            /*
             * Possible exceptions are XA_RB*, XAER_RMERR, XAER_RMFAIL,
             * XAER_DUPID, XAER_OUTSIDE, XAER_NOTA, XAER_INVAL, or XAER_PROTO
             */
            int err = mapErrorCode(e.errorCode);
            switch (err) {
            case ER_ROLLBACK:
            case ER_INVALID_XID:
                trxStatus = TX_ROLLBACK_ONLY;
                throw (RollbackException) new RollbackException(
                        Messages.EROLLBACKONLY).initCause(e);
            case ER_RM_FAILED:
            // Not required because our primary resource is still okay
            // resStatus = RS_CLOSED;
            default:
                trxStatus = TX_ROLLBACK_ONLY;
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED
                                + " while attempting to join a Transaction Branch")
                        .initCause(e);
            }
        }
    }

    /**
     * Tests if specified resource can be resumed. We can if: a) It is one that we have seen
     * before b) It was suspended
     */
    boolean canResume(ResourceReference res) {
        return trxStatus == TX_ACTIVE && res != null && res.isSuspended();
    }

    /**
     * Resume a previously suspended resource.
     * 
     * @param res
     *            Resource that is to resume its association with this branch.
     * @throws IllegalStateException -
     *             Thrown if the resource is not eligible or if this branch is
     *             not active
     * @throws RollbackException -
     *             Thrown if the Resource Manager indicates that the branch has
     *             been rolled back
     * @throws SystemException -
     *             Thrown for any unexpected error condition
     */
    void resume(Resource res) throws IllegalStateException, RollbackException,
            SystemException {

        ResourceReference resource = find(res);
        if (!canResume(resource)) {
            throw new IllegalStateException(Messages.ERESOURCERESUME + resource);
        }

        try {
            resource.start(xid, XAResource.TMRESUME);
        } catch (XAException e) {
            /*
             * Possible exceptions are XA_RB*, XAER_RMERR, XAER_RMFAIL,
             * XAER_DUPID, XAER_OUTSIDE, XAER_NOTA, XAER_INVAL, or XAER_PROTO
             */
            int err = mapErrorCode(e.errorCode);
            switch (err) {
            case ER_ROLLBACK:
            case ER_INVALID_XID:
                trxStatus = TX_ROLLBACK_ONLY;
                throw (RollbackException) new RollbackException(
                        Messages.EROLLBACKONLY).initCause(e);
            case ER_RM_FAILED:
                resStatus = RS_CLOSED;
            default:
                trxStatus = TX_ROLLBACK_ONLY;
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED
                                + " while attempting to resume a Resource")
                        .initCause(e);
            }
        }
    }

    /**
     * Tests if specified resource can be suspended.
     * 
     * @param resource
     *            Resource whose association with this branch is to be suspended
     */
    boolean canSuspend(ResourceReference resource) {
        return trxStatus == TX_ACTIVE && resource != null
                && resource.isActive();
    }

    /**
     * Suspends the specified resource.
     * 
     * @param res
     *            Resource that is to suspend its association with this branch.
     * @throws IllegalStateException -
     *             Thrown if the resource is not eligible or if this branch is
     *             not active
     * @throws RollbackException -
     *             Thrown if the Resource Manager indicates that the branch has
     *             been rolled back
     * @throws SystemException -
     *             Thrown for any unexpected error condition
     */
    void suspend(Resource res) throws IllegalStateException, RollbackException,
            SystemException {
        ResourceReference resource = find(res);
        if (!canSuspend(resource)) {
            throw new IllegalStateException(Messages.ERESOURCESUSPEND + res);
        }
        endOne(resource, XAResource.TMSUSPEND);
    }

    /**
     * Tests of specified resource can be delisted from the transaction branch.
     * 
     * @param resource
     *            Resource that is to end its association with this branch
     * @return
     */
    boolean canEndSuccessfully(ResourceReference resource) {
        return trxStatus == TX_ACTIVE && resource != null
                && (resource.isActive() || resource.isSuspended());
    }

    /**
     * End (successfully) the specified resource
     * 
     * @param res
     *            Resource that is to end its association with this branch.
     * @throws IllegalStateException -
     *             Thrown if the resource is not eligible or if this branch is
     *             not active
     * @throws RollbackException -
     *             Thrown if the Resource Manager indicates that the branch has
     *             been rolled back
     * @throws SystemException -
     *             Thrown for any unexpected error condition
     */
    void endSuccessfully(Resource res) throws IllegalStateException,
            RollbackException, SystemException {
        ResourceReference resource = find(res);
        if (!canEndSuccessfully(resource)) {
            throw new IllegalStateException(Messages.ERESOURCEEND + res);
        }
        endOne(resource, XAResource.TMSUCCESS);
    }

    /**
     * Tests if specified resource can be delited with failed status.
     */
    boolean canFail(ResourceReference resource) {
        return trxStatus == TX_ACTIVE && resource != null
                && (resource.isActive() || resource.isSuspended());
    }

    /**
     * End (fail) the specified resource
     * 
     * @param res
     *            Resource that is to end its association with this branch.
     * @throws IllegalStateException -
     *             Thrown if the resource is not eligible or if this branch is
     *             not active
     * @throws RollbackException -
     *             Thrown if the Resource Manager indicates that the branch has
     *             been rolled back
     * @throws SystemException -
     *             Thrown for any unexpected error condition
     */
    void fail(Resource res) throws IllegalStateException, RollbackException,
            SystemException {
        ResourceReference resource = find(res);
        if (!canFail(resource)) {
            throw new IllegalStateException(Messages.ERESOURCEFAIL + res);
        }
        endOne(resource, XAResource.TMFAIL);
        trxStatus = TX_ROLLBACK_ONLY;
    }

    /**
     * End this resource. This is the workhorse method used by the other
     * methods.
     * 
     * @param resource
     *            Resource that is to end its association with this branch.
     * @param flags -
     *            One of XAResource.TMSUCCESS, XAResource.TMSUSPEND or
     *            XAResource.TMFAIL
     * @throws IllegalStateException -
     *             Thrown if the resource is not eligible or if this branch is
     *             not active
     * @throws RollbackException -
     *             Thrown if the Resource Manager indicates that the branch has
     *             been rolled back
     * @throws SystemException -
     *             Thrown for any unexpected error condition
     */
    void endOne(ResourceReference resource, int flags)
            throws IllegalStateException, RollbackException, SystemException {

        try {
            resource.end(xid, flags);
        } catch (XAException e) {
            /*
             * Possible XAException values are XAER_RMERR, XAER_RMFAILED,
             * XAER_NOTA, XAER_INVAL, XAER_PROTO, or XA_RB*.
             */
            int err = mapErrorCode(e.errorCode);
            switch (err) {
            case ER_ROLLBACK:
            case ER_INVALID_XID:
                trxStatus = TX_ROLLBACK_ONLY;
                throw (RollbackException) new RollbackException(
                        Messages.EROLLBACKONLY).initCause(e);
            case ER_RM_FAILED:
                // FIXME:
                resStatus = RS_CLOSED;
                trxStatus = TX_ROLLBACK_ONLY;
            default:
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED
                                + " while attempting to end a Transaction Branch with flags:"
                                + flags).initCause(e);
            }
        }
    }

    /**
     * End the associations of all resources attached to this transaction
     * branch.
     * 
     * @param flags -
     *            One of XAResource.TMSUCCESS, XAResource.TMSUSPEND or
     *            XAResource.TMFAIL
     * @param newStatus -
     *            is the status of this branch upon successfully ending the
     *            resources associated with this branch.
     * @throws IllegalStateException -
     *             Thrown if the resource is not eligible or if this branch is
     *             not active
     * @throws RollbackException -
     *             Thrown if the Resource Manager indicates that the branch has
     *             been rolled back
     * @throws SystemException -
     *             Thrown for any unexpected error condition
     */
    void endAll(int flags, int newStatus) throws IllegalStateException,
            RollbackException, SystemException {

        // FIXME: Exception handling - make sure we do not lose information
        SystemException se = null;
        RollbackException re = null;
        IllegalStateException ie = null;
        Iterator i = joinedResources.iterator();
        while (i.hasNext()) {
            ResourceReference resource = (ResourceReference) i.next();
            try {
                if ((flags & XAResource.TMSUSPEND) != 0 && resource.isActive()) {
                    endOne(resource, flags);
                } else {
                    if (resource.isActive() || resource.isSuspended()) {
                        // XA specification says that if TMSUCCESS is used as
                        // and if the resource is suspended, then it is
                        // implicitly resumed.
                        // But what if TMFAIL is specified?
                        // FIXME: We may need to explicily resume
                        endOne(resource, flags);
                    }
                }
            } catch (IllegalStateException e) {
                if (ie == null)
                    ie = e;
            } catch (RollbackException e) {
                if (re == null)
                    re = e;
            } catch (SystemException e) {
                if (se == null)
                    se = e;
            }
        }
        if (re != null)
            throw re;
        else if (se != null)
            throw se;
        else if (ie != null)
            throw ie;

        trxStatus = newStatus;
    }

    /**
     * Tests if the transaction branch can be completed successfully.
     */
    boolean canEndSuccessfully() {
        return resStatus == RS_OK
                && (trxStatus == TX_ACTIVE || trxStatus == TX_IDLE_SUSPENDED);
    }

    /**
     * Tests whether the transaction branch is still active.
     */
    boolean isEnded() {
        return trxStatus != TX_ACTIVE && trxStatus != TX_IDLE_SUSPENDED;
    }

    /**
     * End this branch successfully. All joined resources will be ended as well.
     * 
     * @throws IllegalStateException -
     *             Thrown if the resource is not eligible or if this branch is
     *             not active
     * @throws RollbackException -
     *             Thrown if the Resource Manager indicates that the branch has
     *             been rolled back
     * @throws SystemException -
     *             Thrown for any unexpected error condition
     */
    void endSuccessfully() throws IllegalStateException, RollbackException,
            SystemException {
        endAll(XAResource.TMSUCCESS, TX_IDLE_SUCCESS);
    }

    /**
     * Tests if the transaction branch can be ended with failed status.
     */
    boolean canFail() {
        return resStatus == RS_OK
                && (trxStatus == TX_ACTIVE || trxStatus == TX_IDLE_SUSPENDED);
    }

    /**
     * Fail this branch. All joined resources will be failed.
     * 
     * @throws IllegalStateException -
     *             Thrown if the resource is not eligible or if this branch is
     *             not active
     * @throws RollbackException -
     *             Thrown if the Resource Manager indicates that the branch has
     *             been rolled back
     * @throws SystemException -
     *             Thrown for any unexpected error condition
     */
    void endFailed() throws IllegalStateException, RollbackException,
            SystemException {
        endAll(XAResource.TMFAIL, TX_ROLLBACK_ONLY);
    }

    /**
     * Tests whether the transaction branch can enter into prepared state. It can if all the joined
     * resources have been ended successfully.
     */
    boolean canPrepare() {
        return resStatus == RS_OK && trxStatus == TX_IDLE_SUCCESS;
    }

    /**
     * Prepare this branch.
     * 
     * @throws IllegalStateException -
     *             Thrown if the resource is not eligible or if this branch is
     *             not active
     * @throws SystemException -
     *             Thrown for any unexpected error condition
     */
    int prepare() throws java.lang.IllegalStateException, SystemException {

        int vote = VOTE_ROLLBACK;

        if (!canPrepare()) {
            throw new IllegalStateException(Messages.EBRANCHPREPARE);
        }

        try {
            int rc = resource.prepare(xid);
            if (rc == XAResource.XA_OK) {
                trxStatus = TX_PREPARED;
                vote = VOTE_COMMIT;
            } else {
                trxStatus = TX_READONLY;
                vote = VOTE_COMMIT;
            }
        } catch (XAException e) {
            /*
             * Possible exception values are: XA_RB*, XAER_RMERR, XAER_RMFAIL,
             * XAER_NOTA, XAER_INVAL, or XAER_PROTO.
             */
            int err = mapErrorCode(e.errorCode);
            switch (err) {
            case ER_ROLLBACK:
                trxStatus = TX_ROLLEDBACK;
                vote = VOTE_ROLLBACK;
                break;
            case ER_RM_FAILED:
                resStatus = RS_CLOSED;
                trxStatus = TX_ROLLBACK_ONLY;
            default:
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED
                                + " while attempting to prepare a Transaction Branch")
                        .initCause(e);
            }
        }
        return vote;
    }

    /**
     * Tests whether the transaction branch can be committed using two phase commit.
     */
    boolean canTwoPhaseCommit() {
        return resStatus == RS_OK && trxStatus == TX_PREPARED;
    }

    /**
     * Perform two phase commit.
     * 
     * @throws IllegalStateException
     * @throws HeuristicRollbackException
     * @throws SystemException
     * @throws HeuristicMixedException
     * @throws RollbackException
     */
    void commitTwoPhase() throws IllegalStateException,
            HeuristicRollbackException, SystemException,
            HeuristicMixedException, RollbackException {

        int retry_count = 0;

        if (trxStatus == TX_READONLY) {
            /*
             * If the resource returned ReadOnly during prepare(), this means
             * that the transaction is already committed.
             */
            trxStatus = TX_COMMITTED;
            if (log.isDebugEnabled())
                log.debug("SIMPLEJTA-BranchTransaction: Branch " + bid
                        + " of Global Transaction " + gt.getXid()
                        + " is READONLY");
            return;
        }

        if (trxStatus == TX_COMMITTED) {
            if (log.isDebugEnabled())
                log.debug("SIMPLEJTA-BranchTransaction: Branch " + bid
                        + " of Global Transaction " + gt.getXid()
                        + " is already COMMITTED");
            return;
        }

        if (!canTwoPhaseCommit()) {
            throw new IllegalStateException(Messages.EBRANCHCOMMIT2PC);
        }

        boolean retrying = true;
        while (retrying) {
            retrying = false;
            try {
                resource.commit(xid, false);
                trxStatus = TX_COMMITTED;
            } catch (XAException e) {
                /*
                 * Possible XAExceptions are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB,
                 * XA_HEURMIX, XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL,
                 * or XAER_PROTO.
                 */
                int err = mapErrorCode(e.errorCode);
                switch (err) {
                case ER_HEUR_COMMIT:
                    trxStatus = TX_COMMITTED_HEURISTICALLY;
                    break;
                case ER_HEUR_COMPLETED:
                    trxStatus = TX_HEURISTICALLY_COMPLETED;
                    throw (HeuristicMixedException) new HeuristicMixedException(
                            Messages.EBRANCHHEURCOMP).initCause(e);
                case ER_HEUR_ROLLBACK:
                    trxStatus = TX_ROLLEDBACK_HEURISTICALLY;
                    throw (HeuristicRollbackException) new HeuristicRollbackException(
                            Messages.EBRANCHHEURRB).initCause(e);
                case ER_RETRY:
                    if (retry_count < 30) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e1) {
                        }
                        retry_count++;
                        log
                                .warn("SIMPLEJTA-BranchTransaction: ER_RETRY while attempting to commit, retrying - attempt "
                                        + retry_count);
                        retrying = true;
                    }
                    break;
                case ER_RM_ERROR:
                    // FIXME Following is a workaround for Oracle bug in 9.2.0.1
                    if (retry_count < 20) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e1) {
                        }
                        retry_count++;
                        log
                                .warn("SIMPLEJTA-BranchTransaction: ER_RM_ERR while attempting to commit, retrying - attempt "
                                        + retry_count);
                        retrying = true;
                        break;
                    }
                    trxStatus = TX_ROLLEDBACK;
                    throw (RollbackException) new RollbackException(
                            Messages.EROLLEDBACK).initCause(e);
                case ER_RM_FAILED:
                    resStatus = RS_CLOSED;
                default:
                    throw (SystemException) new SystemException(
                            Messages.EUNEXPECTED
                                    + " while attempting to perform two-phase commit on Transaction Branch")
                            .initCause(e);
                }
            }
        }
    }

    /**
     * Tests whether transaction branch can be completed using onephase commit.
     */
    boolean canOnePhaseCommit() {
        return resStatus == RS_OK
                && (trxStatus == TX_ACTIVE || trxStatus == TX_IDLE_SUSPENDED || trxStatus == TX_IDLE_SUCCESS);
    }

    /**
     * Can we commit this branch?
     */
    boolean canCommit() {
        return canOnePhaseCommit() || canTwoPhaseCommit();
    }

    /**
     * Perform onephase commit.
     * 
     * @throws IllegalStateException
     * @throws HeuristicRollbackException
     * @throws SystemException
     * @throws HeuristicMixedException
     * @throws RollbackException
     */
    void commitOnePhase() throws IllegalStateException,
            HeuristicRollbackException, SystemException,
            HeuristicMixedException, RollbackException {

        if (!canOnePhaseCommit()) {
            throw new IllegalStateException(Messages.EBRANCHCOMMIT1PC);
        }

        try {
            if (resource.isActive() || resource.isSuspended()) {
                endOne(resource, XAResource.TMSUCCESS);
            }
            resource.commit(xid, true);
            trxStatus = TX_COMMITTED;
        } catch (XAException e) {
            /*
             * Possible XAExceptions are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB,
             * XA_HEURMIX, XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or
             * XAER_PROTO.
             */
            int err = mapErrorCode(e.errorCode);
            switch (err) {
            case ER_HEUR_COMMIT:
                trxStatus = TX_COMMITTED_HEURISTICALLY;
                /*
                 * Okay - because this matches expected result anyway. We need
                 * to mark it as heuristic though so that we can later on
                 * remember to invoke forget().
                 */
                break;
            case ER_HEUR_COMPLETED:
                trxStatus = TX_HEURISTICALLY_COMPLETED;
                throw (HeuristicMixedException) new HeuristicMixedException(
                        Messages.EBRANCHHEURCOMP).initCause(e);
            case ER_HEUR_ROLLBACK:
                trxStatus = TX_ROLLEDBACK_HEURISTICALLY;
                throw (HeuristicRollbackException) new HeuristicRollbackException(
                        Messages.EBRANCHHEURRB).initCause(e);
            case ER_ROLLBACK:
            case ER_RM_ERROR:
                trxStatus = TX_ROLLEDBACK;
                throw (RollbackException) new RollbackException(
                        Messages.EROLLEDBACK).initCause(e);
            case ER_RM_FAILED:
                resStatus = RS_CLOSED;
            default:
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED
                                + " while attempting to perform one-phase commit on Transaction Branch")
                        .initCause(e);
            }
        }
    }

    /**
     * Check whether we can rollback. We allow rollbacks in most cases.
     */
    boolean canRollback() {
        return resStatus == RS_OK
                && (trxStatus == TX_PREPARED || trxStatus == TX_ROLLBACK_ONLY
                        || trxStatus == TX_ACTIVE
                        || trxStatus == TX_IDLE_SUSPENDED
                        || trxStatus == TX_IDLE_SUCCESS || trxStatus == TX_IDLE_FAILED);
    }

    /**
     * Rollback the branch transaction.
     * 
     * @throws IllegalStateException
     * @throws HeuristicCommitException
     * @throws SystemException
     * @throws HeuristicMixedException
     */
    void rollback() throws IllegalStateException, HeuristicCommitException,
            SystemException, HeuristicMixedException {

        if (trxStatus == TX_ROLLEDBACK
                || trxStatus == TX_ROLLEDBACK_HEURISTICALLY
                || trxStatus == TX_COMMITTED
                || trxStatus == TX_COMMITTED_HEURISTICALLY
                || trxStatus == TX_HEURISTICALLY_COMPLETED) {
            return;
        }

        if (trxStatus == TX_READONLY) {
            trxStatus = TX_ROLLEDBACK;
            return;
        }

        if (!canRollback()) {
            //	throw new IllegalStateException(
            //			"Cannot rollback Transaction Branch");
            // We try to avoid throwing exceptions during rolback
            log
                    .warn("SIMPLEJTA-BranchTransaction: Unable to rollback branch transaction - status "
                            + trxStatus);
            return;
        }

        try {
            resource.rollback(xid);
            trxStatus = TX_ROLLEDBACK;
        } catch (XAException e) {
            /*
             * Possible XAExceptions are XA_HEURHAZ, XA_HEURCOM, XA_HEURRB,
             * XA_HEURMIX, XAER_RMERR, XAER_RMFAIL, XAER_NOTA, XAER_INVAL, or
             * XAER_PROTO.
             */
            int err = mapErrorCode(e.errorCode);
            switch (err) {
            case ER_HEUR_COMMIT:
                trxStatus = TX_COMMITTED_HEURISTICALLY;
                throw (HeuristicCommitException) new HeuristicCommitException(
                        Messages.EBRANCHHEURCOMM).initCause(e);
            case ER_HEUR_COMPLETED:
                trxStatus = TX_HEURISTICALLY_COMPLETED;
                throw (HeuristicMixedException) new HeuristicMixedException(
                        Messages.EBRANCHHEURCOMP).initCause(e);
            case ER_HEUR_ROLLBACK:
                trxStatus = TX_ROLLEDBACK_HEURISTICALLY;
                /*
                 * Okay - because this matches expected result anyway. We need
                 * to mark it as heuristic though so that we can later on
                 * remember to invoke forget().
                 */
                break;
            case ER_ROLLBACK:
                trxStatus = TX_ROLLEDBACK;
                break;
            case ER_RM_FAILED:
                resStatus = RS_CLOSED;
            default:
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED
                                + " while attempting to rollback Transaction Branch")
                        .initCause(e);
            }
        }
    }

    /**
     * Tests whether the transaction branch was heuristically completed.
     */
    boolean heuristicallyCompleted() {
        return trxStatus == TX_HEURISTICALLY_COMPLETED
                || trxStatus == TX_ROLLEDBACK_HEURISTICALLY
                || trxStatus == TX_COMMITTED_HEURISTICALLY;
    }

    /**
     * Checks if it okay to forget this branch.
     */
    boolean canForget() {
        return resStatus == RS_OK && heuristicallyCompleted();
    }

    /**
     * Ask the resource managers to forget this branch.
     * 
     * @throws IllegalStateException
     * @throws SystemException
     */
    void forget() throws IllegalStateException, SystemException {
        if (!canForget()) {
            throw new IllegalStateException(Messages.EBRANCHFORGET);
        }
        try {
            resource.forget(xid);
            trxStatus = TX_NONE;
        } catch (XAException e) {
            /*
             * Possible exception values are XAER_RMERR, XAER_RMFAIL, XAER_NOTA,
             * XAER_INVAL, or XAER_PROTO.
             */
            int err = mapErrorCode(e.errorCode);
            switch (err) {
            case ER_INVALID_XID:
                trxStatus = TX_NONE;
                break;
            case ER_RM_FAILED:
                resStatus = RS_CLOSED;
            default:
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED
                                + " while attempting to forget a Transaction Branch")
                        .initCause(e);
            }
        }
    }

    /**
     * Check if the given resource is using the same underlying resource manager
     * that our primary resource is using.
     * 
     * @param res
     * @throws SystemException
     */
    boolean isSameRM(Resource res) throws SystemException {
        try {
            return res.isJoinSupported() && resource.isSameRM(res);
        } catch (XAException e) {
            /*
             * Possible exception values are: XAER_RMERR, XAER_RMFAIL.
             */
            int err = mapErrorCode(e.errorCode);
            switch (err) {
            case ER_RM_FAILED:
                resStatus = RS_CLOSED;
            default:
                throw (SystemException) new SystemException(
                        Messages.EUNEXPECTED
                                + " while attempting compare an XA Resource")
                        .initCause(e);
            }
        }
    }

    /**
     * Checks if the specified resource is already enlisted with the transaction
     * branch.
     * 
     * @param res
     *            Resource to look for
     * @return True if the specified resource is found
     */
    ResourceReference find(Resource res) {
        Iterator i = joinedResources.iterator();
        while (i.hasNext()) {
            ResourceReference resource = (ResourceReference) i.next();
            if (resource.getResource() == res) {
                return resource;
            }
        }
        return null;
    }

    /**
     * Get our default resource
     * 
     * @return Returns the resource.
     */
    public final ResourceReference getResource() {
        return resource;
    }

    /**
     * @return Returns the Branch Transaction xid.
     */
    public final Xid getXid() {
        return xid;
    }

    /**
     * Returns the status of this Branch transaction.
     */
    public int getStatus() {
        return trxStatus;
    }

    /**
     * Sets the status of this Branch transaction.
     */
    public void setStatusInternal(int status) {
        trxStatus = status;
    }

    /**
     * Returns this Branch Transaction's id.
     */
    public int getBid() {
        return bid;
    }

    /**
     * Destroys the object that represents this branch, and releases all associated
     * resource objects.
     */
    public void dispose() {
        if (joinedResources != null) {
            Iterator i = joinedResources.iterator();
            while (i.hasNext()) {
                ResourceReference resourceStatus = (ResourceReference) i.next();
                resourceStatus.dispose();
            }
            joinedResources.clear();
            joinedResources = null;
        }
        resource = null;
        xid = null;
        gt = null;
    }

    /**
     * Checks if this object is equal to another object.
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof BranchTransaction))
            return false;
        BranchTransaction other = (BranchTransaction) obj;
        if (other == this)
            return true;
        return xid.equals(other.xid);
    }

    /**
     * Generates hash code, delegates to the Xid.
     */
    public int hashCode() {
        if (xid == null)
            return 0;
        return xid.hashCode();
    }

    /**
     * Provides information about this branch in human readable format
     */
    public String toString() {
        return "BranchTransaction(xid=" + xid + ",bid=" + bid + ",status="
                + trxStatus + ",resource=" + resource + ")";
    }
}