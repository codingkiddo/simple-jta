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

import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.log4j.Logger;
import org.simplejta.tm.xid.XidFactory;
import org.simplejta.util.Messages;

/**
 * <tt>Resource</tt> is a container that holds a reference to <tt>XAResource</tt> and its
 * associated Connection or Session object.
 * <p>The <tt>Resource</tt> object associates a resourceFactoryName
 * to the resource. The resourceFactoryName maps to the JDBC or JMS
 * Connection Pool implementation. The resourceFactoryName is stored in the
 * transaction logs, enabling the transaction manager to retrieve the
 * appropriate factory implementation during recovery.
 * <p>The <tt>Resource</tt> class also enforces the rule that only one
 * transaction can be associated with an <tt>XAResource</tt> at any point in
 * time.
 * 
 * @author Dibyendu Majumdar
 * @since 11.Oct.2004
 */
public class Resource implements XAResource {

	static Logger log = Logger.getLogger(Resource.class);

	/**
	 * The name of the Resource Factory that created this resource instance.
	 * The name must refer to the bean Id of an implementation of
	 * {@link ResourceFactory}.
	 * <p>The resourceFactoryName is recorded in the SimpleJTA transaction
	 * logs, enabling the transaction manager to obtain the appropriate
	 * instance of Resource during recovery. 
	 */
	private String resourceFactoryName;

	/**
	 * User supplied object. Used to be the XAConnection object. Changed to
	 * generic object because the JMS XAConnection and JDBC
	 * XAConnection/XASession objects are not compatible. A generic object
	 * allows this class to be used for both JDBC and JMS datasources.
	 */
	private Object identity;

	/**
	 * The XAResource object.
	 */
	private XAResource xares;

	/**
	 * The global transaction this resource is associated with currently. If
	 * null, this resource is free for reuse by other transactions. Note that
	 * this Xid is not the Xid for the branch, instead it is a modified version
	 * that ignores the branch qualifier.
	 */
	private Xid currentXid;

	/**
	 * List of listeners who want to know when this resource is enlisted
	 * (started) by a transaction, and when it is delisted (ended).
	 */
	private ArrayList resourceListeners = new ArrayList();

	/**
	 * Some databases such as Oracle do not support joins.
	 */
	boolean joinSupported = false;

	/**
	 * XAResource specs say that a resource can be reused in other transactions
	 * once it has been disassociated with a transaction (using end()). However,
	 * if the vendor does not support this properly, we need to hold on to the
	 * resource until the transaction is completed.
	 */
	boolean reuseResourceAfterEnd = false;

	/**
	 * refCount indicates that the resource is currently in use. If refCount is
	 * 0, this resource can be closed. Every time a resource is involved in a
	 * transaction, refCount is incremented, and when the transaction completes,
	 * refCount is decremented.
	 */
	int refCount = 0;

	/**
	 * This is used to determine when to recycle the connection object. Every
	 * time a connection is used, this count is incremented. If a connection has
	 * been used a specified number of times, it is recycled.
	 */
	int useCount = 0;

	/**
	 * Used to determine when to recycle the connection object.
	 */
	long birthTime = System.currentTimeMillis();

	/**
	 * Initialises a new Resource object.
	 * 
	 * @param resourceFactoryName
	 *            The name of the Resource Factory that created this resource
	 *            instance. The name must refer to the bean Id of an
	 *            implementation of {@link ResourceFactory}.
	 * @param xares
	 *            XAResource object
	 * @param o
	 *            A connection or session object, such as XAConnection, that is
	 *            associated with the XAResource object.
	 */
	public Resource(String resourceFactoryName, XAResource xares, Object o) {
		this.resourceFactoryName = resourceFactoryName;
		this.xares = xares;
		this.identity = o;
	}

	/**
	 * Returns the name of the Resource Factory that created this resource instance.
	 * The name refers to the bean Id of an implementation of
	 * {@link ResourceFactory}.
	 * <p>The resourceFactoryName is recorded in the SimpleJTA transaction
	 * logs, enabling the transaction manager to obtain the appropriate
	 * instance of Resource during recovery. 
	 */
	public synchronized final String getResourceFactoryName() {
		return resourceFactoryName;
	}

	/**
	 * Returns the underlying XAResource object.
	 */
	public synchronized final XAResource getXAResource() {
		return xares;
	}

	/**
	 * Returns the associated user connection/session object.
	 */
	public synchronized final Object getIdentity() {
		return identity;
	}

	/**
	 * Returns the Xid of the Global Transaction with which the resource
	 * is currently enlisted.
	 */
	public synchronized final Xid getCurrentXid() {
		return currentXid;
	}

	/**
	 * Sets current Xid. Also invokes ResourceEvent listeners.
	 */
	public synchronized final void setCurrentXid(Xid xid) {
		if (xid == null && currentXid != null) {
			Xid savedXid = currentXid;
			currentXid = null;
			notifyReleaseEvent(savedXid);
		} else if (xid != null && currentXid == null) {
			currentXid = xid;
			notifyAcquireEvent(xid);
		}
	}

	/**
	 * Verifies that the resource is not attached to any transaction.
	 * 
	 * @throws IllegalStateException
	 */
	private void assertNullTransaction() throws IllegalStateException {
		if (currentXid != null) {
			throw new IllegalStateException(Messages.EALREADYHASXID
					+ ", CurrentXid=" + currentXid + ", Resource=" + this);
		}
	}

	/**
	 * Verifies that the resource is attached to our global transaction.
	 * 
	 * @param xid
	 * @throws IllegalStateException
	 */
	private void assertTransaction(Xid xid) throws IllegalStateException {
		if (currentXid == null || !currentXid.equals(xid)) {
			throw new IllegalStateException(Messages.EXIDMISMATCH
					+ ", CurrentXid=" + currentXid + ", Resource=" + this
					+ ", Xid=" + xid);
		}
	}

	/**
	 * Adds a ResourceEvent listener. Such listeners are notified when the
	 * resource joins or leaves a transaction.
	 * 
	 * @param listener
	 *            ResourceEventListener to be added.
	 */
	public synchronized void addResourceListener(ResourceEventListener listener) {
		if (resourceListeners.contains(listener)) {
			return;
		}
		resourceListeners.add(listener);
	}

	/**
	 * Notifies all registered ResourceEventListeners that this Resource has been
	 * disassociated with specified transaction.
	 * 
	 * @param xid
	 *            Transaction with which association has ended.
	 */
	private void notifyReleaseEvent(Xid xid) {
		Iterator i = resourceListeners.iterator();
		ResourceEvent event = new ResourceEvent(this, xid);
		while (i.hasNext()) {
			ResourceEventListener listener = (ResourceEventListener) i.next();
			listener.resourceDelisted(event);
		}
	}

	/**
	 * Notify all registered ResourceEventListeners that this Resource has been
	 * associated with specified transaction.
	 * 
	 * @param xid
	 *            Transaction with which association has started.
	 */
	private void notifyAcquireEvent(Xid xid) {
		Iterator i = resourceListeners.iterator();
		ResourceEvent event = new ResourceEvent(this, xid);
		while (i.hasNext()) {
			ResourceEventListener listener = (ResourceEventListener) i.next();
			listener.resourceEnlisted(event);
		}
	}

	/**
	 * Clear the list of ResourceEventListeners.
	 */
	public synchronized void clearResourceListeners() {
		resourceListeners.clear();
	}

	/**
	 * Commits the transaction.
	 * 
	 * @param xid
	 *            Transaction to be committed
	 * @param onePhase
	 *            Whether to perform one-phase commit
	 * @throws javax.transaction.xa.XAException
	 */
	public synchronized void commit(Xid xid, boolean onePhase)
			throws XAException {
		if (log.isDebugEnabled()) {
			String t = onePhase ? "ONE-PHASE" : "TWO-PHASE";
			log.debug("SIMPLEJTA-Resource: COMMIT " + t + " "
					+ identity.toString());
		}
		xares.commit(xid, onePhase);
	}

	/**
	 * End the association of this resource with specified transaction.
	 * 
	 * @param xid
	 *            Transaction with which association has to be ended.
	 * @param flags
	 * @throws javax.transaction.xa.XAException
	 */
	public synchronized void end(Xid xid, int flags) throws XAException {
		Xid gxid = null;
		try {
			gxid = XidFactory.createGlobalXidFromBranchXid(xid);
		} catch (SystemException e) {
			throw (XAException) new XAException(
					"Unable to extract global xid from branch xid")
					.initCause(e);
		}
		assertTransaction(gxid);
		if (log.isDebugEnabled()) {
			log.debug("SIMPLEJTA-Resource: END(" + flags + ") "
					+ identity.toString());
		}
		xares.end(xid, flags);
		if (reuseResourceAfterEnd) {
			// The Resource will become available to other transactions at this
			// point. Note that this can cause problems with some vendors.
			setCurrentXid(null);
		}
	}

	public synchronized void forget(Xid xid) throws XAException {
		xares.forget(xid);
	}

	public synchronized int getTransactionTimeout() throws XAException {
		return xares.getTransactionTimeout();
	}

	public synchronized boolean isSameRM(XAResource arg0) throws XAException {
		return xares.isSameRM(arg0);
	}

	public synchronized boolean isSameRM(Resource res) throws XAException {
		return xares.isSameRM(res.xares);
	}

	public synchronized int prepare(Xid xid) throws XAException {
		if (log.isDebugEnabled()) {
			log.debug("SIMPLEJTA-Resource: PREPARE " + identity.toString());
		}
		return xares.prepare(xid);
	}

	public synchronized Xid[] recover(int arg0) throws XAException {
		return xares.recover(arg0);
	}

	public synchronized void rollback(Xid xid) throws XAException {
		if (log.isDebugEnabled()) {
			log.debug("SIMPLEJTA-Resource: ROLLBACK " + identity.toString());
		}
		xares.rollback(xid);
	}

	public synchronized boolean setTransactionTimeout(int arg0)
			throws XAException {
		return xares.setTransactionTimeout(arg0);
	}

	/**
	 * Starts a transaction branch and notes the Xid of the
	 * global transaction. 
	 */
	public synchronized void start(Xid xid, int flags) throws XAException {
		Xid gxid = null;
		try {
			/*
			 * Subtract the branch qualifier portion of the
			 * Xid.
			 */
			gxid = XidFactory.createGlobalXidFromBranchXid(xid);
		} catch (SystemException e) {
			throw (XAException) new XAException(
					"Unable to extract global xid from branch xid")
					.initCause(e);
		}
		assertNullTransaction();
		if (log.isDebugEnabled()) {
			log.debug("SIMPLEJTA-Resource: START(" + flags + ") "
					+ identity.toString());
		}
		xares.start(xid, flags);
		setCurrentXid(gxid);
	}

	public synchronized String toString() {
		return "Resource(resourceFactoryName=" + getResourceFactoryName() + ",xid="
				+ currentXid + ",identity=" + identity + ",refCount="
				+ refCount + ")";
	}

	public synchronized final boolean isJoinSupported() {
		return joinSupported;
	}

	public synchronized final void setJoinSupported(boolean joinSupported) {
		this.joinSupported = joinSupported;
	}

	public synchronized final boolean isReuseResourceAfterEnd() {
		return reuseResourceAfterEnd;
	}

	public synchronized final void setReuseResourceAfterEnd(
			boolean reuseResourceAfterEnd) {
		this.reuseResourceAfterEnd = reuseResourceAfterEnd;
	}

	public synchronized final int getRefCount() {
		return refCount;
	}

	/**
	 * Increments enlist reference count. This is done when the resource is
	 * enlisted to a transaction.
	 */
	public synchronized void incrEnlistCount() {
		refCount++;
		if (log.isDebugEnabled()) {
			log.debug("SIMPLEJTA-Resource: REFCOUNT=" + refCount + " "
					+ identity.toString());
		}
	}

	/**
	 * Decrements enlist reference count. When the transaction branch completes,
	 * this is invoked to notify that the resource is no longer associated with
	 * the transaction.
	 */
	public synchronized void decrEnlistCount() {
		refCount--;
		if (log.isDebugEnabled()) {
			log.debug("SIMPLEJTA-Resource: REFCOUNT=" + refCount + " "
					+ identity.toString());
		}
		if (!reuseResourceAfterEnd && refCount == 0) {
			// Delayed release
			setCurrentXid(null);
		}
	}

	public synchronized int getUseCount() {
		return useCount;
	}

	public synchronized void incrUseCount() {
		useCount++;
	}

	public synchronized long getBirthTime() {
		return birthTime;
	}
}