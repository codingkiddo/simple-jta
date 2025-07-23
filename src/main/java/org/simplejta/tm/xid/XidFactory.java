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
package org.simplejta.tm.xid;

import javax.transaction.SystemException;
import javax.transaction.xa.Xid;

import org.simplejta.tm.SimpleTransactionManager;

/**
 * XidFactory is responsible for creating Xids.
 * 
 * @author Dibyendu Majumdar
 * @since 8 July, 2004
 */
public class XidFactory {
	
	public static Xid createGlobalXid(SimpleTransactionManager tm) {
		return new SimpleXidImpl(tm);
	}

	public static Xid createBranchXid(Xid globalXid, int btrid) throws SystemException {
		return new SimpleXidImpl(globalXid, btrid);
	}
	
	public static Xid createXid(int format, byte[] gtrid, byte[] bqual) throws SystemException {
	    return new SimpleXidImpl(format, gtrid, bqual);
	}
	
	public static Xid createGlobalXidFromBranchXid(Xid branchXid) throws SystemException {
		return new SimpleXidImpl(branchXid, SimpleXidImpl.GLOBAL_BID);
	}
}
