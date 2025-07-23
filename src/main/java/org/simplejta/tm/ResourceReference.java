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

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * ResourceReference holds a reference to a Resource object. It also
 * holds state information to validate/decide actions 
 * that can be taken safely with the resource. Each time a ResourceReference
 * is associated with a Resource, the reference counter within the
 * Resource is incremented. When a ResourceReference is disposed, the
 * reference counter is decremented. The Resource reuse algorithm
 * relies upon this mechanism to determine when to reuse a Resource.
 * 
 * @author Dibyendu Majumdar
 * @since 21.Jan.2004
 */
public class ResourceReference {

	/*
	 * IMPLEMENTATION NOTE: We need this wrapper object because a resource is
	 * free to be used in other transactions once its associated with a
	 * transaction is ended. However, the transaction itself still needs to
	 * maintain status/reference for the resource because the resource may be
	 * resumed or started again.
	 */
	
	private final static int STATE_INACTIVE = 1;

	private final static int STATE_ACTIVE = 2;

	private final static int STATE_SUSPENDED = 3;

	private final static int STATE_ENDED = 4;

	private final static int STATE_FAILED = 5;
	
	private final static int STATE_PREPARED = 6;

	private final static int ACTION_START = 1;

	private final static int ACTION_JOIN = 2;

	private final static int ACTION_SUSPEND = 3;

	private final static int ACTION_RESUME = 4;

	private final static int ACTION_END = 5;

	private final static int ACTION_FAIL = 6;

	private final static int ACTION_COMMIT_ONEPHASE = 7;

	private final static int ACTION_PREPARE = 8;

	private final static int ACTION_COMMIT_TWOPHASE = 9;

	private final static int ACTION_ROLLBACK = 10;

	/**
	 * State of this resource - initial state is Inactive. This causes a
	 * problem during recovery - see the flag recoveryMode for how this is
	 * handled.
	 */
	private State state = new State(STATE_INACTIVE, false);

	private Resource resource = null;
	
	boolean enlisted = false;

	static class State {
		int state;

		boolean joined;

		State(int state, boolean joined) {
			this.state = state;
			this.joined = joined;
		}

		State(int state) {
			this(state, false);
		}

		State(State state) {
			this(state.getState(), state.isJoined());
		}

		final boolean isJoined() {
			return joined;
		}

		final void setJoined(boolean joined) {
			this.joined = joined;
		}

		final int getState() {
			return state;
		}

		final void setState(int state) {
			this.state = state;
		}

		final String getStateText(int state) {
			switch (state) {
			case STATE_INACTIVE:
				return "Inactive";
			case STATE_ACTIVE:
				return "Active";
			case STATE_SUSPENDED:
				return "Suspended";
			case STATE_ENDED:
				return "Ended";
			case STATE_FAILED:
				return "Failed";
			case STATE_PREPARED:
				return "Failed";
			default:
				return "Unknown";
			}
		}

		public String toString() {
			return "State(" + getStateText(state) + ",joined=" + joined + ")";
		}
	}

	private String getActionText(int action) {
		switch (action) {
		case ACTION_START:
			return "Start";
		case ACTION_JOIN:
			return "Join";
		case ACTION_SUSPEND:
			return "Suspend";
		case ACTION_RESUME:
			return "Resume";
		case ACTION_END:
			return "End";
		case ACTION_FAIL:
			return "Fail";
		case ACTION_COMMIT_ONEPHASE:
			return "One Phase Commit";
		case ACTION_PREPARE:
			return "Prepare";
		case ACTION_COMMIT_TWOPHASE:
			return "Two Phase Commit";
		case ACTION_ROLLBACK:
			return "Rollback";
		default:
			return "Unknown";
		}
	}

	private State checkState(State state, int action) {
		boolean valid = true;
		State nextState = new State(state);
		switch (action) {
		case ACTION_START:
			valid = (state.getState() == STATE_INACTIVE);
			nextState = new State(STATE_ACTIVE, false);
			break;
		case ACTION_JOIN:
			valid = (state.getState() == STATE_INACTIVE);
			nextState = new State(STATE_ACTIVE, true);
			break;
		case ACTION_SUSPEND:
			valid = (state.getState() == STATE_ACTIVE);
			nextState = new State(STATE_SUSPENDED, state.isJoined());
			break;
		case ACTION_RESUME:
			valid = (state.getState() == STATE_SUSPENDED);
			nextState = new State(STATE_ACTIVE, state.isJoined());
			break;
		case ACTION_END:
			valid = (state.getState() == STATE_ACTIVE || state.getState() == STATE_SUSPENDED);
			nextState = new State(STATE_ENDED, false);
			break;
		case ACTION_FAIL:
			valid = (state.getState() == STATE_ACTIVE || state.getState() == STATE_SUSPENDED);
			nextState = new State(STATE_FAILED, false);
			break;
		case ACTION_COMMIT_ONEPHASE:
			valid = (state.getState() == STATE_ENDED);
			nextState = new State(STATE_INACTIVE, false);
			break;
		case ACTION_PREPARE:
			valid = (state.getState() == STATE_ENDED);
			nextState = new State(STATE_PREPARED, false);
			break;
		case ACTION_COMMIT_TWOPHASE:
			valid = (state.getState() == STATE_PREPARED);
			nextState = new State(STATE_INACTIVE, false);
			break;
		case ACTION_ROLLBACK:
			valid = (state.getState() != STATE_INACTIVE);
			nextState = new State(STATE_INACTIVE, false);
			break;
		default:
			valid = false;
		}
		if (!valid) {
			throw new IllegalStateException("Requested action "
					+ getActionText(action) + " is invalid for state " + state);
		}
		return nextState;
	}

	public ResourceReference(Resource resource) {
		this.resource = resource;
		setEnlisted();
	}

	public ResourceReference(Resource resource, boolean recovering) {
		this(resource);
		if (recovering) {
			// override default state
			state = new State(STATE_PREPARED, false); 
		}
	}

	public final Resource getResource() {
		return resource;
	}
	
	public final void setResource(Resource resource) {
		this.resource = resource;
	}

	public void commit(Xid xid, boolean onePhase) throws XAException {
		int action = onePhase ? ACTION_COMMIT_ONEPHASE : ACTION_COMMIT_TWOPHASE;
		State newState = checkState(state, action);
		resource.commit(xid, onePhase);
		state = newState;
	}

	public void end(Xid xid, int flags) throws XAException {
		int action = ACTION_END;
		switch (flags) {
		case XAResource.TMSUSPEND:
			action = ACTION_SUSPEND;
			break;
		case XAResource.TMFAIL:
			action = ACTION_FAIL;
			break;
		}
		State newState = checkState(state, action);
		resource.end(xid, flags);
		state = newState;
	}

	public void forget(Xid xid) throws XAException {
		try {
			resource.forget(xid);
		}
		finally {
			/*
			 * Note that we reset state information even if there is an error.
			 * TODO: Review this - do we need some logic for retrying?
			 */
			state = new State(STATE_INACTIVE);
		}
	}

	public int prepare(Xid xid) throws XAException {
		State newState = checkState(state, ACTION_PREPARE);
		int vote = resource.prepare(xid);
		state = newState;
		return vote;
	}

	public Xid[] recover(int arg0) throws XAException {
		return resource.recover(arg0);
	}

	public void rollback(Xid xid) throws XAException {
		/*
		 * TODO: Review error handling because it may make sense to ignore state and do
		 * rollback anyway.
		 */
		State newState = checkState(state, ACTION_ROLLBACK);
		try {
			resource.rollback(xid);
		} finally {
			state = newState;
		}
	}

	public void start(Xid xid, int flags) throws XAException {
		int action = ACTION_START;
		switch (flags) {
		case XAResource.TMJOIN:
			action = ACTION_JOIN;
			break;
		case XAResource.TMRESUME:
			action = ACTION_RESUME;
			break;
		}
		State newState = checkState(state, action);
		resource.start(xid, flags);
		state = newState;
	}

	public boolean isJoined() {
		return state.isJoined();
	}

	public int getState() {
		return state.getState();
	}
	
	public boolean isSuspended() {
	    return getState() == STATE_SUSPENDED;
	}

	public boolean isActive() {
	    return getState() == STATE_ACTIVE;
	}
	
	public boolean isEnded() {
		return getState() == STATE_ENDED || getState() == STATE_FAILED;
	}
	
	public boolean isPrepared() {
		return getState() == STATE_PREPARED;
	}
	
	public String toString() {
		return "ResourceReference(resource=" + resource + ",state=" + state + ")";
	}
	
	public void dispose() {
	    if (enlisted) {
	        enlisted = false;
	        resource.decrEnlistCount();
	    }
	}
	
	public void setEnlisted() {
	    if (!enlisted) {
	        enlisted = true;
	        resource.incrEnlistCount();
	    }
	}
	
	public boolean isSameRM(ResourceReference res) throws XAException {
		return resource.isSameRM(res.getResource());
	}

	public boolean isSameRM(Resource res) throws XAException {
		return resource.isSameRM(res);
	}
	
	public boolean isSameRM(XAResource arg0) throws XAException {
		return resource.isSameRM(arg0);
	}
}