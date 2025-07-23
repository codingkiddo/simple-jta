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
package org.simplejta.tm.jdbc;

import java.util.Hashtable;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;

import org.simplejta.util.ClassUtils;

/**
 * @author Dibyendu Majumdar
 * @since 6 March 2005
 */
public class SimpleJTADataSourceFactory implements ObjectFactory {

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.naming.spi.ObjectFactory#getObjectInstance(java.lang.Object,
	 *      javax.naming.Name, javax.naming.Context, java.util.Hashtable)
	 */
	public Object getObjectInstance(Object obj, Name name, Context nameCtx,
			Hashtable environment) throws Exception {
		if (!(obj instanceof Reference))
			return null;
		Reference ref = (Reference) obj;
		Properties props = new Properties();
		String className = null;
		for (int i = 0; i < ref.size(); i++) {
			StringRefAddr addr = (StringRefAddr) ref.get(i);
			String type = addr.getType();
			String address = (String) addr.getContent();
			if (address != null) {
				if (type.equalsIgnoreCase("className")) {
					className = address;
				} else {
					props.setProperty(type, address);
				}
			}
		}
		if (className == null)
			return null;
		return ClassUtils.createObject(className, props, Properties.class);
	}

}