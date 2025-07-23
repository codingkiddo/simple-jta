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

/**
 * Defines constants used by other classes in this package.
 * @author Dibyendu Majumdar
 * @since 11.Oct.2004
 */
interface Constants {

    static final int RS_CLOSED = 0;
	static final int RS_OK = 1;

	static final int TX_NONE = 0;
	/* Following states are of active branches */
	static final int TX_ACTIVE = 1;
	static final int TX_IDLE_SUCCESS = 2;
	static final int TX_IDLE_FAILED = 3;
	static final int TX_IDLE_SUSPENDED = 3;
	static final int TX_PREPARED = 4;
	static final int TX_ROLLBACK_ONLY = 5;
	/* Following are states of completed branches */
	static final int TX_HEURISTICALLY_COMPLETED = 6;
	static final int TX_ROLLEDBACK = 7;
	static final int TX_ROLLEDBACK_HEURISTICALLY = 8;
	static final int TX_READONLY = 9;
	static final int TX_COMMITTED = 10;
	static final int TX_COMMITTED_HEURISTICALLY = 11; 
	
	static final int VOTE_COMMIT = 1;
	static final int VOTE_ROLLBACK = 2;

	static final int ER_RM_FAILED = 1;
	static final int ER_RM_ERROR = 2;
	static final int ER_INVALID_XID = 3;
	static final int ER_UNEXPECTED = 4;
	static final int ER_HEUR_COMPLETED = 5;
	static final int ER_HEUR_COMMIT = 6;
	static final int ER_HEUR_ROLLBACK = 7;
	static final int ER_ROLLBACK = 8;
	static final int ER_READONLY = 9;
	static final int ER_RETRY = 10;
}
