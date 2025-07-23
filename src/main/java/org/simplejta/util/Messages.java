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

/**
 * @author Dibyendu Majumdar
 * @since 6 April 2005
 */
public interface Messages {
	public final String ECLOSECONN = "SIMPLEJTA-E001: Error closing Connection";
	public final String ECLOSERS = "SIMPLEJTA-E002: Error closing ResultSet";
	public final String ECLOSEST = "SIMPLEJTA-E003: Error closing Statement";
	public final String EARGUP = "SIMPLEJTA-E004: User or password has not been set";
	public final String ELOADCLASS = "SIMPLEJTA-E005: Error loading Class ";
	public final String ECREATEXID = "SIMPLEJTA-E006: Error creating Xid";
	public final String EARGCONFIG = "SIMPLEJTA-E007: Configuration parameter not set:";
	public final String EDRIVER = "SIMPLEJTA-E008: Unknown JDBC Driver:";
	public final String ECREATELOG = "SIMPLEJTA-E009: Error creating Transaction Log";
	public final String EOPENCONN = "SIMPLEJTA-E010: Error opening Connection";
	public final String ECREATEDS = "SIMPLEJTA-E011: Error creating DataSource";
	public final String EUNEXPECTED = "SIMPLEJTA-E999: Unexpected error";
	public final String ELOGXID = "SIMPLEJTA-E012: Failed to log transaction";
	public final String ELOGREAD = "SIMPLEJTA-E013: Failed to retrieve transaction data from log";
	public final String EBRANCHACTIVE = "SIMPLEJTA-E014: Cannot start Transaction Branch because it is already active";
	public final String EROLLBACKONLY = "SIMPLEJTA-E015: Transaction Branch has been marked rollback only by Resource";
	public final String EBRANCHJOIN = "SIMPLEJTA-E016: Transaction Branch cannot be joined by Resource:";
	public final String ERESOURCERESUME = "SIMPLEJTA-E017: Cannot resume Resource:";
	public final String ERESOURCESUSPEND = "SIMPLEJTA-E018: Cannot suspend Resource:";
	public final String ERESOURCEEND = "SIMPLEJTA-E019: Cannot end (successfully) association with Resource:";
	public final String ERESOURCEFAIL = "SIMPLEJTA-E020: Cannot end (fail) association with Resource:";
	public final String EBRANCHPREPARE = "SIMPLEJTA-E021: Transaction Branch cannot be prepared";
	public final String EBRANCHCOMMIT2PC = "SIMPLEJTA-E022: Transaction Branch cannot be committed (two-phase)";
	public final String EBRANCHHEURCOMP = "SIMPLEJTA-E023: Transaction Branch has been heuristically completed";
	public final String EBRANCHHEURRB = "SIMPLEJTA-E024: Transaction Branch has been heuristically rolled back";
	public final String EROLLEDBACK = "SIMPLEJTA-E025: Transaction Branch has been rolled back";
	public final String EBRANCHCOMMIT1PC = "SIMPLEJTA-E026: Transaction Branch cannot be committed (one-phase)";
	public final String EBRANCHHEURCOMM = "SIMPLEJTA-E027: Transaction Branch has been heuristically committed";
	public final String EBRANCHFORGET = "SIMPLEJTA-E028: Cannot forget Transaction Branch";
	public final String ETOOMANYBRANCHES = "SIMPLEJTA-E029: Cannot create new Transaction Branch: too many branches";
	public final String ETRXROLLBACKONLY = "SIMPLEJTA-E030: Cannot enlist resource because transaction has been marked Rollback Only:";
	public final String ETRXENLISTINACTIVE = "SIMPLEJTA-E031: Cannot enlist resource because transaction is not active:";
	public final String ENOTRESOURCE = "SIMPLEJTA-E032: This Transaction Manager can only en(de)list resources created by ";
	public final String ETRXDELISTINACTIVE = "SIMPLEJTA-E033: Cannot delist resource because transaction is not active:";
	public final String EROLLEDBACKONLY = "SIMPLEJTA-E034: Transaction rolled back because it was marked for Rollback Only";
	public final String ECOMMITINACTIVE = "SIMPLEJTA-E035: Transaction cannot be committed because it is not active";
	public final String ECOMMIT1PC = "SIMPLEJTA-E036: Cannot perform One Phase Commit on Transaction";
	public final String EROLLEDBACKERROR = "SIMPLEJTA-E037: Transaction rolled back due to one or more exceptions";
	public final String EHEURMIXED = "SIMPLEJTA-E038: Transaction completed with Heuristic Mixed status";
	public final String EHEURRB = "SIMPLEJTA-E039: Transaction completed with Heuristic Rollback status";
	public final String EILLEGAL = "SIMPLEJTA-E040: Transaction caused Illegal State Exception";
	public final String EHEURCOMM = "SIMPLEJTA-E041: Transaction completed with Heuristic Commit status";
	public final String ERESOLVE = "SIMPLEJTA-E042: Error occured while trying to resolve transaction:";
	public final String ESYNCINACTIVE = "SIMPLEJTA-E043: Cannot add synchronization object to transaction because current state is not ACTIVE";
	public final String EMARKROLLBACKONLY = "SIMPLEJTA-E044: Cannot mark transaction for rollback because current state is not active";
	public final String EUNKNOWNRESOURCETYPE = "SIMPLEJTA-E045: Unable to recover resource of unknown type";
	public final String ENESTEDTRX = "SIMPLEJTA-E046: Nested transactions are not supported";
	public final String EINVALIDXID = "SIMPLEJTA-E047: Invalid Transaction object - not a subclass of ";
	public final String EALREADYASSOC = "SIMPLEJTA-E048: A Transaction is already associated with current thread";
	public final String ENOASSOC = "SIMPLEJTA-E049: No transaction associated with current thread";
	public final String ENOTMGR = "SIMPLEJTA-E050: SimpleTransactionManager instance does not exist for TMID: ";
	public final String EMISSINGCONFIG = "SIMPLEJTA-E051: Configuration parameter has not been defined: ";
	public final String ETMGRDUP = "SIMPLEJTA-E052: An instance of SimpleTransactionManager already exists for TMID: ";
	public final String EALREADYHASXID = "SIMPLEJTA-E053: There is already a transaction associated with the resource";
	public final String EXIDMISMATCH = "SIMPLEJTA-E054: The transaction associated with the resource does not match the supplied Xid";
	public final String ELOADRESOURCE = "SIMPLEJTA-E055: Failed to load resource";
	public final String ENOTRESOURCEFACTORY = "SIMPLEJTA-E056: Specified class is not a ResourceFactory";
	public final String EMISSINGCLASSLOADER = "SIMPLEJTA-E057: Thread's Context ClassLoader is null";
	public final String ESHUTDOWN = "SIMPLEJTA-E058: Resource is being shutdown, unable to allocate new object";
	public final String EINITDS = "SIMPLEJTA-E059: DataSource has not been initialized";
	public final String EINITJMS = "SIMPLEJTA-E060: JMS ConnectionFactory has not been initialized";
	public final String EGETJMSFACTORY = "SIMPLEJTA-E061: Error occurred while attempting to retrieve a JMSConnectionResourceFactory";
	public final String EENLISTJMS = "SIMPLEJTA-E062: Error occurred while attempting to enlist a JMS Resource";
	public final String ECREATEJMS = "SIMPLEJTA-E063: Error occurred while creating a JMS Connection factory of type";
	public final String ETIMEOUTRECOVERY = "SIMPLEJTA-E064: Timeout while waiting for TransactionManager to recover"; 
}
