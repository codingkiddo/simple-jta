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
package org.simplejta.tm.jms;

import java.util.Map;

import javax.jms.JMSException;
import javax.jms.XAConnectionFactory;

import org.simplejta.tm.ResourceFactoryAdaptor;

/**
 * 
 * @author Dibyendu
 * @see org.simplejta.tm.jms.adaptors.ActiveMQJMSConnectionFactoryAdaptor
 */
public interface JMSConnectionFactoryAdaptor extends ResourceFactoryAdaptor {
	
	XAConnectionFactory createXAConnectionFactory(Map connectionProperties) throws JMSException;
}
