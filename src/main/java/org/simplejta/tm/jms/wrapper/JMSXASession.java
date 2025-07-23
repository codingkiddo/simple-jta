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

import java.io.Serializable;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.jms.XASession;
import javax.transaction.xa.XAResource;


/**
 * <p>
 * <code>JMSXASession</code> is a wrapper around the real XASession. Its primary objective
 * is to suppress close() and to wrap associated Session object. 
 * </p> 
 *
 * @author Dibyendu Majumdar
 * @since May 19, 2005
 */
public class JMSXASession implements XASession {

    XASession realSession;
    
    public JMSXASession(XASession session) {
        realSession = session;
    }
    
    public void close() throws JMSException {
        // System.err.println("XASession.close called");
    }
    public Session getSession() throws JMSException {
        return new JMSSession(realSession.getSession());
    }
    
    public void commit() throws JMSException {
        realSession.commit();
    }
    public QueueBrowser createBrowser(Queue arg0) throws JMSException {
        return realSession.createBrowser(arg0);
    }
    public QueueBrowser createBrowser(Queue arg0, String arg1)
            throws JMSException {
        return realSession.createBrowser(arg0, arg1);
    }
    public BytesMessage createBytesMessage() throws JMSException {
        return realSession.createBytesMessage();
    }
    public MessageConsumer createConsumer(Destination arg0) throws JMSException {
        return realSession.createConsumer(arg0);
    }
    public MessageConsumer createConsumer(Destination arg0, String arg1)
            throws JMSException {
        return realSession.createConsumer(arg0, arg1);
    }
    public MessageConsumer createConsumer(Destination arg0, String arg1,
            boolean arg2) throws JMSException {
        return realSession.createConsumer(arg0, arg1, arg2);
    }
    public TopicSubscriber createDurableSubscriber(Topic arg0, String arg1)
            throws JMSException {
        return realSession.createDurableSubscriber(arg0, arg1);
    }
    public TopicSubscriber createDurableSubscriber(Topic arg0, String arg1,
            String arg2, boolean arg3) throws JMSException {
        return realSession.createDurableSubscriber(arg0, arg1, arg2, arg3);
    }
    public MapMessage createMapMessage() throws JMSException {
        return realSession.createMapMessage();
    }
    public Message createMessage() throws JMSException {
        return realSession.createMessage();
    }
    public ObjectMessage createObjectMessage() throws JMSException {
        return realSession.createObjectMessage();
    }
    public ObjectMessage createObjectMessage(Serializable arg0)
            throws JMSException {
        return realSession.createObjectMessage(arg0);
    }
    public MessageProducer createProducer(Destination arg0) throws JMSException {
        return realSession.createProducer(arg0);
    }
    public Queue createQueue(String arg0) throws JMSException {
        return realSession.createQueue(arg0);
    }
    public StreamMessage createStreamMessage() throws JMSException {
        return realSession.createStreamMessage();
    }
    public TemporaryQueue createTemporaryQueue() throws JMSException {
        return realSession.createTemporaryQueue();
    }
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        return realSession.createTemporaryTopic();
    }
    public TextMessage createTextMessage() throws JMSException {
        return realSession.createTextMessage();
    }
    public TextMessage createTextMessage(String arg0) throws JMSException {
        return realSession.createTextMessage(arg0);
    }
    public Topic createTopic(String arg0) throws JMSException {
        return realSession.createTopic(arg0);
    }
    public boolean equals(Object arg0) {
        return realSession.equals(arg0);
    }
    public int getAcknowledgeMode() throws JMSException {
        return realSession.getAcknowledgeMode();
    }
    public MessageListener getMessageListener() throws JMSException {
        return realSession.getMessageListener();
    }
    public boolean getTransacted() throws JMSException {
        return realSession.getTransacted();
    }
    public XAResource getXAResource() {
        return realSession.getXAResource();
    }
    public int hashCode() {
        return realSession.hashCode();
    }
    public void recover() throws JMSException {
        realSession.recover();
    }
    public void rollback() throws JMSException {
        realSession.rollback();
    }
    public void run() {
        realSession.run();
    }
    public void setMessageListener(MessageListener arg0) throws JMSException {
        realSession.setMessageListener(arg0);
    }
    public String toString() {
        return realSession.toString();
    }
    public void unsubscribe(String arg0) throws JMSException {
        realSession.unsubscribe(arg0);
    }
}
