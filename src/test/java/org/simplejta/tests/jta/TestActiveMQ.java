package org.simplejta.tests.jta;

import java.util.Properties;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.transaction.UserTransaction;

import junit.framework.TestCase;

import org.simplejta.tm.jms.SimpleJTAJMSConnectionFactory;
import org.simplejta.tm.ut.SimpleUserTransaction;

public class TestActiveMQ extends TestCase {

	public TestActiveMQ(String arg0) {
		super(arg0);
	}

	public void testCase1() throws Exception {
		Properties props = new Properties();
		props.setProperty("beanFactory", "myapp");
		props.setProperty("transactionManager", "myTM");
		UserTransaction ut = new SimpleUserTransaction(props);
		
        SimpleJTAJMSConnectionFactory qcf = new SimpleJTAJMSConnectionFactory();
        qcf.setBeanFactory("myapp");
        qcf.setTransactionManager("myTM");
        qcf.setConnectionPool("JMSConnectionPool1");

        Connection qc = null;
        Session session = null;
        Queue q = null;
        MessageProducer queueSender = null;
        MessageConsumer queueReceiver = null;
        try {
        	System.out.println("Starting global transaction");
            ut.begin();
        	System.out.println("Opening connection");
            qc = qcf.createConnection();
        	System.out.println("Opening session");
            session = qc.createSession(true, 0);
        	System.out.println("Opening queue");
            q = session.createQueue("jms");
        	System.out.println("Creating MessageProducer");
            queueSender = session.createProducer(q);

            System.out.println("Creating a TextMessage");
            TextMessage outMessage = session.createTextMessage();
            System.out.println("Adding Text");
            String s = "hello world @ " + System.currentTimeMillis();
            outMessage.setText(s);

            // Ask the QueueSender to send the message we have created
            System.out.println("Sending the message to " + q.getQueueName());
            queueSender.send(outMessage);

            System.out.println("Closing MessageProducer");
            queueSender.close();
            queueSender = null;
            System.out.println("Closing Session");
            session.close();
            session = null;
            System.out.println("Closing Connection");
            qc.close();
            qc = null;
            System.out.println("Commiting global transaction");
            ut.commit();

        	System.out.println("Starting global transaction");
            ut.begin();
        	System.out.println("Opening connection");
            qc = qcf.createConnection();
        	System.out.println("Opening session");
            session = qc.createSession(true, 0);
        	System.out.println("Starting connection");
            qc.start();
        	System.out.println("Opening queue");
            q = session.createQueue("jms");
        	System.out.println("Creating MessageConsumer");
            queueReceiver = session.createConsumer(q);

            // Ask the QueueSender to send the message we have created
            System.out.println("Receiving a message from " + q.getQueueName());
            Message msg = queueReceiver.receive(30*1000);

            if (msg != null) {
            	TextMessage tmsg = (TextMessage) msg;
            	assertEquals(tmsg.getText(), s);
            }
            else 
            	throw new Exception("Failed to receive expected message");

            System.out.println("Closing MessageConsumer");
            queueReceiver.close();
            queueReceiver = null;
            System.out.println("Closing Session");
            session.close();
            session = null;
            System.out.println("Closing Connection");
            qc.close();
            qc = null;
            System.out.println("Commiting global transaction");
            ut.commit();
            ut = null;

            System.out.println("Completed OK");
            
        } finally {
            if (queueSender != null) {
                try {
                    queueSender.close();
                } catch (Exception e) {
                }
            }
            if (queueReceiver != null) {
                try {
                    queueReceiver.close();
                } catch (Exception e) {
                }
            }
            if (session != null) {
                try {
                    session.close();
                } catch (Exception e) {
                }
            }
            if (qc != null) {
                try {
                    qc.close();
                } catch (Exception e) {
                }
            }
            if (ut != null) {
                try {
                    ut.rollback();
                } catch (Exception e) {
                }
                ((SimpleUserTransaction)ut).destroy();
            }
        }
		
	}
	
}
