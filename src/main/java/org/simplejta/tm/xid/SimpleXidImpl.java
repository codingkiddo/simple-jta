/*
 * SimpleJTA - A Simple Java Transaction Manager (http://www.simplejta.org/)
 * Copyright 2005 Dibyendu Majumdar (http://www.simplejta.org)
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

import java.nio.ByteBuffer;

import javax.transaction.xa.Xid;

import org.simplejta.tm.SimpleTransactionManager;
import org.simplejta.util.Messages;

import jakarta.transaction.SystemException;

/**
 * <p>
 * SimpleXidImpl implements the Xid interface. To ensure that Xids are unique,
 * we use the transaction manager's id (which should be unique for every instance of the 
 * transaction manager) plus the transaction manager's birth time. We also use our own birthtime,
 * and a unique statically allocated id. Branch ids are simple integers. 
 * </p> 
 *
 * @author Dibyendu Majumdar
 * @since 18.Jan.2005
 */
public class SimpleXidImpl implements Xid {

    public final static int SIMPLEXID_FORMAT = 0x1c131d0a;		// is hex for SJTA
    final static int TXMGRID_LEN = 32;
    final static int GTRID_LEN = TXMGRID_LEN + 8 + 8 + 8;
    final static int BQUAL_LEN = 4;
    final static int GLOBAL_BID = Integer.MAX_VALUE;
    
    /**
     * The birth time of the transaction manager.
     */
    long txmgrBirthTime = 0;
    
    /**
     * A unique id for the transaction manager instance
     */
    String txmgrId;
    
    /**
     * Our birth time.
     */
    long myBirthTime = 0;
    
    /**
     * A counter to generate new ids.
     */
    static long nextId = 0;
    
    /**
     * We use this to synchronize access to nextId
     */
    static Object syncObj = new Object();
    
    /**
     * Our id.
     */
    long id = 0;
    
    /**
     * Our branch qualifier.
     */
    byte[] bqual;
    
    /**
     * Our global transaction id.
     */
    byte[] gtrid;
    
    /**
     * Our format identifier.
     */
    int format;

    /**
     * Branch id.
     */
    int bid;
    
    /**
     * Hash code - computed only once for efficiency
     */
    int hashcode;
    
    /**
     * Tracks whether hashcode has been computed
     */
    boolean hashcodeComputed = false;
    
    /**
     * Construct a new Xid. Branch qualifier will be
     * set to SimpleXidImpl.GLOBAL_BID. This Xid should not be 
     * used in a transaction branch - because the branch qualifier
     * needs to be different for each branch.
     *  
     * @param tm The instance of SimpleTransactionManager that will manage this Xid
     */
    public SimpleXidImpl(SimpleTransactionManager tm) {
        this(tm.getTmid(), tm.getBirthTime());
    }

    /**
     * A private constructor useful for testing and also used internally
     * by other constructors.
     * 
     * @param tmid Identifies the instance of SimpleTransactionManager that will manage this Xid
     * @param birthTime Birth time of the SimpleTransactionManager instance
     */
    private SimpleXidImpl(String tmid, long birthTime) {
        super();
        format = SIMPLEXID_FORMAT;
        txmgrBirthTime = birthTime;
        txmgrId = tmid;
        generateId();
        bid = GLOBAL_BID;
        gtrid = makeGtrid();
        bqual = makeBqual(bid);
    }
    
    /**
     * Clone an Xid and create a new copy.
     * 
     * @param xid Xid to be cloned
     * @throws SystemException Thrown if there is the supplied Xid cannot be cloned.
     */
    public SimpleXidImpl(Xid xid) throws SystemException {
        init(xid.getFormatId(), xid.getGlobalTransactionId(), xid.getBranchQualifier());
    }

    /**
     * Clone an Xid but change the branch id to one specified.
     * This constructor should be used to generate Xids for each transaction
     * branch in a global transaction.
     * 
     * @param xid The Global Transaction id
     * @param bid The branch qualifier
     * @throws SystemException Thrown if there is the supplied Xid cannot be cloned.
     */
    public SimpleXidImpl(Xid xid, int bid) throws SystemException {
        byte[] bqual = makeBqual(bid);
        init(xid.getFormatId(), xid.getGlobalTransactionId(), bqual);
    }

    public SimpleXidImpl(int other_format, byte[] other_gtrid, byte[] other_bqual) throws SystemException {
        init(other_format, other_gtrid, other_bqual);
    }

    /**
     * Create Xid by copying from values supplied
     */
    private void init(int other_format, byte[] other_gtrid, byte[] other_bqual) throws SystemException {
        format = other_format;
        if (format != SIMPLEXID_FORMAT) {
            throw new SystemException(Messages.ECREATEXID + " due to mismatch in format");
        }
        try {
            gtrid = new byte[GTRID_LEN];
            System.arraycopy(other_gtrid, 0, gtrid, 0, gtrid.length);
            bqual = new byte[BQUAL_LEN];
            System.arraycopy(other_bqual, 0, bqual, 0, bqual.length);
            parseGtrid(gtrid);
            bid = parseBqual(bqual);
        } catch (RuntimeException e) {
            throw (SystemException) new SystemException(Messages.ECREATEXID + " due to parse error").initCause(e);
        }
    }
    
    /**
     * Generate a unique id for this instance of the class loader
     */
    private void generateId() {
        synchronized(syncObj) {
            myBirthTime = System.currentTimeMillis();
            if (nextId == Long.MAX_VALUE)
                nextId = 0;
            id = ++nextId;
        }
    }
    
    /**
     * Generate a global transaction identifier.
     * 
     * gtrid contains 
     * txmgrId(32) + 
     * txmgrBirthTime(8) + 
     * myBirthTime(8) + 
     * id(8)   
     */
    private byte[] makeGtrid() {
        byte[] gtrid = new byte[GTRID_LEN];
        ByteBuffer bb = ByteBuffer.wrap(gtrid);
        String tmid = (txmgrId + "                               ").substring(0, TXMGRID_LEN);
        byte[] chars = tmid.getBytes();
        bb.put(chars, 0, TXMGRID_LEN);
        bb.putLong(txmgrBirthTime);
        bb.putLong(myBirthTime);
        bb.putLong(id);
        return gtrid;
    }

    /**
     * Parse a global transaction identifier
     */
    private void parseGtrid(byte[] gtrid) {
        ByteBuffer bb = ByteBuffer.wrap(gtrid);
        byte[] chars = new byte[TXMGRID_LEN];
        bb.get(chars, 0, TXMGRID_LEN);
        txmgrId = new String(chars).trim();
        txmgrBirthTime = bb.getLong();
        myBirthTime = bb.getLong();
        id = bb.getLong();
    }
    
    /**
     * Generate a branch qualifier
     * bqual contains:
     * bid(4) - branch id.
     */
    private byte[] makeBqual(int bid) {
        byte[] bqual = new byte[BQUAL_LEN];
        ByteBuffer bb = ByteBuffer.wrap(bqual);
        bb.putInt(bid);
        return bqual;
    }
    
    /**
     * Parse a branch qualifier.
     */
    private int parseBqual(byte[] bqual) {
        ByteBuffer bb = ByteBuffer.wrap(bqual);
        int bid = bb.getInt();
        return bid;
    }

    /**
     * Test if this Xid is owned by specified instance of transaction manager.
     */
    public boolean belongsTo(String tmid) {
    	return format == SIMPLEXID_FORMAT && txmgrId != null && txmgrId.equals(tmid);
    }
    
    /**
     * Test if this Xid is owned by specified instance of transaction manager.
     */
    public boolean belongsTo(SimpleTransactionManager tm) {
    	return format == SIMPLEXID_FORMAT && txmgrId != null && txmgrId.equals(tm.getTmid());
    }
    
    /**
     * Return the format id
     */
    public int getFormatId() {
        return format;
    }

    /**
     * Return the branch qualifier
     */
    public byte[] getBranchQualifier() {
        return (byte[]) bqual.clone();
    }

    /**
     * Return the global transaction identifier
     */
    public byte[] getGlobalTransactionId() {
        return (byte[]) gtrid.clone();
    }
    
    /**
     * Return a String representation of Xid
     */
    public String toString() {
        return "SimpleXidImpl[format=" + Integer.toHexString(format) + 
		       ",gtrid={txmgrId=" + txmgrId + ",txmgrBirthTime=" + txmgrBirthTime +
        	   ",myBirthTime=" + myBirthTime + ",id=" + id +"}, bqual={" + bid + "}]";
    }
    
    /**
     * Compare this Xid with another one.
     */
    public boolean equals(Object o) {
        if (!(o instanceof SimpleXidImpl)) 
            return false;
        SimpleXidImpl xid = (SimpleXidImpl) o;
        if (format == xid.format &&
            txmgrId.equals(xid.txmgrId) &&
            txmgrBirthTime == xid.txmgrBirthTime &&
            myBirthTime == xid.myBirthTime &&
            id == xid.id &&
            bid == xid.bid)
            return true;
        return false;
    }
    
    /**
     * Compute hashcode. For efficiency, do it only once.
     */
    public int hashCode() {
		synchronized (this) {
			if (!hashcodeComputed) {
				StringBuffer sb = new StringBuffer(128);
				sb.append(format);
				sb.append(txmgrId);
				sb.append(txmgrBirthTime);
				sb.append(myBirthTime);
				sb.append(id);
				sb.append(bid);
				hashcode = sb.toString().hashCode();
				hashcodeComputed = true;
			}
			return hashcode;
		}
	}
    
    public static void main(String args[]) {
        
        try {
            Xid xid1 = new SimpleXidImpl("TMGR.1", System.currentTimeMillis());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            System.out.println(Integer.toHexString(Character.getNumericValue('S')));
            System.out.println(Integer.toHexString(Character.getNumericValue('J')));
            System.out.println(Integer.toHexString(Character.getNumericValue('T')));
            System.out.println(Integer.toHexString(Character.getNumericValue('A')));
            Xid xid2 = new SimpleXidImpl("TMGR.2", System.currentTimeMillis());
            Xid xid3 = new SimpleXidImpl(xid1);
            Xid xid4 = new SimpleXidImpl(xid1, 4);
            Xid xid5 = new SimpleXidImpl(xid4.getFormatId(), xid4.getGlobalTransactionId(), xid4.getBranchQualifier());
            System.out.println("xid1 = " + xid1);
            System.out.println("xid2 = " + xid2);
            System.out.println("xid3 = " + xid3);
            System.out.println("xid4 = " + xid4);
            System.out.println("xid5 = " + xid5);
            System.out.println("xid1 == xid2 [expect false] " + xid1.equals(xid2));
            System.out.println("xid1 == xid3 [expect true] " + xid1.equals(xid3));
            System.out.println("xid1 == xid4 [expect false] " + xid1.equals(xid4));
            System.out.println("xid4 == xid5 [expect true] " + xid4.equals(xid5));
            Xid xid6 = XidFactory.createGlobalXidFromBranchXid(xid4);
            System.out.println("xid6 = " + xid6);
            System.out.println("xid1 == xid6 [expect true] " + xid6.equals(xid1));
        } catch (SystemException e) {
            e.printStackTrace();
        }
    }
    
}
