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
package org.simplejta.tm.jms.adaptors;

import java.util.Map;

import javax.jms.JMSException;
import javax.jms.XAConnectionFactory;

import org.simplejta.tm.jms.JMSConnectionFactoryAdaptor;
import org.simplejta.util.ClassUtils;
import org.simplejta.util.Messages;

public class ActiveMQJMSConnectionFactoryAdaptor implements
		JMSConnectionFactoryAdaptor {

	String url;
	String user;
	String password;
	
	public XAConnectionFactory createXAConnectionFactory() throws JMSException {
		XAConnectionFactory qcf;
	    String classname = "org.apache.activemq.ActiveMQXAConnectionFactory";
		try {
			Class clazz = ClassUtils.forName(classname);
			qcf = (XAConnectionFactory) clazz.newInstance();
			ClassUtils.invokeMethod(clazz, qcf, "setBrokerURL", url);
			ClassUtils.invokeMethod(clazz, qcf, "setUserName", user);
			ClassUtils.invokeMethod(clazz, qcf, "setPassword", password);
		} catch (Throwable e) {
			throw (JMSException) new JMSException(Messages.ECREATEJMS + " " + classname).initCause(e);
		}
		return qcf;
	}

	public XAConnectionFactory createXAConnectionFactory(Map connectionProperties) throws JMSException {
		XAConnectionFactory qcf;
	    String classname = "org.apache.activemq.ActiveMQXAConnectionFactory";
		try {
			Class clazz = ClassUtils.forName(classname);
			qcf = (XAConnectionFactory) clazz.newInstance();
			ClassUtils.invokeMethod(clazz, qcf, "setBrokerURL", connectionProperties.get("brokerURL"));
			ClassUtils.invokeMethod(clazz, qcf, "setUserName", connectionProperties.get("userName"));
			ClassUtils.invokeMethod(clazz, qcf, "setPassword", connectionProperties.get("password"));
		} catch (Throwable e) {
			throw (JMSException) new JMSException(Messages.ECREATEJMS + " " + classname).initCause(e);
		}
		return qcf;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public boolean joinSupported() {
		return false;
	}

	public boolean reuseAfterEnd() {
		return false;
	}
	
	
}
