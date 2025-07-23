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

import jakarta.jms.BytesMessage;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.StreamMessage;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.TopicSubscriber;


/**
 * <p>
 * <code>JMSSession</code> is a wrapper around the standard JMS Session 
 * object. 
 * </p> 
 *
 * @author Dibyendu Majumdar
 * @since May 19, 2005
 */
public class JMSSession implements Session {
    
    Session realSession;
    
    public JMSSession(Session session) {
        this.realSession = session;
    }

    public void close() throws JMSException {
        // System.err.println("Session.close() called");
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

	@Override
	public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector)
			throws JMSException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal)
			throws JMSException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector)
			throws JMSException {
		// TODO Auto-generated method stub
		return null;
	}
}
