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
package org.simplejta.tm.log;

import java.util.LinkedList;

import org.simplejta.tm.BranchTransaction;
import org.simplejta.tm.GlobalTransaction;
import org.simplejta.tm.SimpleTransactionManager;

import jakarta.transaction.SystemException;


/**
 * <p>
 * TransactionLog: Is the interface for the Log system used by SimpleTransactionManager. It is used
 * to save the status of prepared transactions, and for recovery when the system is restarted after a 
 * crash.  
 * </p> 
 *
 * @author Dibyendu Majumdar
 * @since 26.Oct.2004
 */
public interface TransactionLog {
	public void insertTransaction(GlobalTransaction gt) throws SystemException;
	public void updateTransaction(GlobalTransaction gt, boolean includeBranches) throws SystemException;
	public void updateBranchTransaction(GlobalTransaction gt, BranchTransaction bt) throws SystemException;
	public void deleteTransaction(GlobalTransaction gt) throws SystemException;
	public LinkedList recoverTransactions(SimpleTransactionManager tm) throws SystemException;
	public void destroy();
}