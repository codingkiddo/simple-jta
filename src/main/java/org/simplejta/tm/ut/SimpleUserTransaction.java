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
package org.simplejta.tm.ut;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

import org.simplejta.tm.SimpleTransactionManager;
import org.simplejta.tm.SimpleTransactionManagerReference;
import org.springframework.beans.factory.DisposableBean;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;

/**
 * SimpleUserTransaction is an implementation of UserTransaction
 * interface. It is hard-wired to {@link org.simplejta.tm.SimpleTransactionManager SimpleTransactionManager}.  
 *
 * @author Dibyendu Majumdar
 * @since 12.Oct.2004
 */
public class SimpleUserTransaction implements TransactionManager, UserTransaction, Serializable, Referenceable, DisposableBean {
   
	private static final long serialVersionUID = 2288542596353858412L;

	/**
	 * The TransactionManager which will manage this UserTransaction.
	 */
    transient SimpleTransactionManager tm = null;
    
    transient SimpleTransactionManagerReference tmRef = null;
    
    /**
     * Properties 
     */
    Properties props = new Properties();

    /**
     * Default constructor.
     */
    public SimpleUserTransaction() {
    }
    
    /**
     * Construct using the supplied set of properties 
     * @throws SystemException 
     */
    public SimpleUserTransaction(Properties props) throws SystemException {
    	this.props = props;
        init();
    }

    /**
     * Useful for Containers such as Spring that use setter methods to initialize an object
     */
    public void setProperties(Properties props) {
    	this.props = props;
    }

    /**
     * Initialize this object
     * Caller must synchronize.
     */
    private void init() throws SystemException {
    	if (tm == null) {
    		tmRef = SimpleTransactionManager.getTransactionManagerReference(props);
    		tm = tmRef.getTransactionManager();
    	}
    }
    
    /**
     * @see javax.transaction.UserTransaction#begin()
     */
    public synchronized void begin() throws NotSupportedException, SystemException {
    	if (tm == null)
    		init();
        tm.begin();
    }

    /** 
     * @see javax.transaction.UserTransaction#commit()
     */
    public synchronized void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
    	if (tm == null)
    		init();
        tm.commit();
    }

    /** 
     * @see javax.transaction.UserTransaction#rollback()
     */
    public synchronized void rollback() throws IllegalStateException, SecurityException, SystemException {
    	if (tm == null)
    		init();
        tm.rollback();
    }

    /** 
     * @see javax.transaction.UserTransaction#setRollbackOnly()
     */
    public synchronized void setRollbackOnly() throws IllegalStateException, SystemException {
    	if (tm == null)
    		init();
        tm.setRollbackOnly();
    }

    /** 
     * @see javax.transaction.UserTransaction#getStatus()
     */
    public synchronized int getStatus() throws SystemException {
    	if (tm == null)
    		init();
        return tm.getStatus();
    }

    /** 
     * @see javax.transaction.UserTransaction#setTransactionTimeout(int)
     */
    public synchronized void setTransactionTimeout(int arg0) throws SystemException {
    	if (tm == null)
    		init();
        tm.setTransactionTimeout(arg0);
    }

    /**
     * Returns the TransactionManager associated with this transaction.
     * @see org.simplejta.tm.SimpleTransactionManager SimpleTransactionManager 
     */
    public synchronized TransactionManager getTransactionManager() {
        return tm;
    }

    /**
     * Returns the TransactionManager associated with this transaction.
     */
    public synchronized SimpleTransactionManager getSimpleTransactionManager() {
        return tm;
    }

    /**
     * Shutdown the instance of TransactionManager associated with
     * this UserTransaction object. 
     * @see org.simplejta.tm.SimpleTransactionManager#destroy() SimpleTransactionManager.shutdown
     */
    public synchronized void destroy() throws Exception {
        if (tm != null) {
            tmRef.release();
            tmRef = null;
            tm = null;
        }
	}

	/**
     * Create a Reference suitable for use in JNDI
     */
    public Reference getReference() {
    	Reference ref = new Reference(this.getClass().getName(), SimpleUserTransactionFactory.class.getName(), null);
    	Iterator i = props.entrySet().iterator();
    	while (i.hasNext()) {
    		Map.Entry e = (Map.Entry) i.next();
    		ref.add(new StringRefAddr((String) e.getKey(), (String)e.getValue()));
    	}
    	return ref;
    }

	public synchronized Transaction getTransaction() throws SystemException {
    	if (tm == null)
    		init();
		return tm.getTransaction();
	}

	public synchronized void resume(Transaction t) throws InvalidTransactionException, IllegalStateException, SystemException {
    	if (tm == null)
    		init();
    	tm.resume(t);
	}

	public synchronized Transaction suspend() throws SystemException {
    	if (tm == null)
    		init();		
    	return tm.suspend();
	}
}
