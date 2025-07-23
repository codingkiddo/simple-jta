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
package org.simplejta.tm.jms.wrapper;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.XAConnection;
import javax.jms.XASession;

import org.simplejta.tm.Resource;
import org.simplejta.tm.jms.JMSXAConnectionPool;

/**
 * <p><code>JMSConnection</code> implements the JMS Connection interface.
 * It uses an underlying XAConnection object. Note that the XAConnection object
 * is permanently asociated with an XASession object - that is, there is
 * one-to-one mapping between the two.</p>
 * 
 * @author Dibyendu Majumdar
 * @since 18-May-2005
 */
public class JMSConnection implements Connection {

    JMSXAConnectionPool pool;

    XAConnection xaConnection;

    XASession xaSession;
    
    Resource resource;

    public JMSConnection(JMSXAConnectionPool pool,
            XAConnection xaConnection, XASession xaSession,
            Resource resource) {
        this.pool = pool;
        this.xaConnection = xaConnection;
        this.xaSession = new JMSXASession(xaSession);
        this.resource = resource;
    }

    public void close() throws JMSException {
        pool.connectionClosed(resource);
    }
    public XASession createXASession() throws JMSException {
        return xaSession;
    }
    public Session createSession(boolean arg0, int arg1) throws JMSException {
        return xaSession.getSession();
    }

    public int hashCode() {
        return xaConnection.hashCode();
    }
    public void start() throws JMSException {
        xaConnection.start();
    }
    public String toString() {
        return xaConnection.toString();
    }
    public String getClientID() throws JMSException {
        return xaConnection.getClientID();
    }
    public ConnectionMetaData getMetaData() throws JMSException {
        return xaConnection.getMetaData();
    }
    public ConnectionConsumer createDurableConnectionConsumer(Topic arg0,
            String arg1, String arg2, ServerSessionPool arg3, int arg4)
            throws JMSException {
        return xaConnection.createDurableConnectionConsumer(arg0, arg1, arg2,
                arg3, arg4);
    }
    public ConnectionConsumer createConnectionConsumer(Destination arg0,
            String arg1, ServerSessionPool arg2, int arg3) throws JMSException {
        return xaConnection.createConnectionConsumer(arg0, arg1, arg2, arg3);
    }
    public ExceptionListener getExceptionListener() throws JMSException {
        return xaConnection.getExceptionListener();
    }
    public void setExceptionListener(ExceptionListener arg0)
            throws JMSException {
        xaConnection.setExceptionListener(arg0);
    }
    public void setClientID(String arg0) throws JMSException {
        xaConnection.setClientID(arg0);
    }
    public boolean equals(Object arg0) {
        return xaConnection.equals(arg0);
    }
    public void stop() throws JMSException {
        xaConnection.stop();
    }
}