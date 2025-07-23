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

import java.util.Hashtable;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;

/**
 * @author Dibyendu Majumdar
 * @since 6 March 2005
 */
public class SimpleUserTransactionFactory implements ObjectFactory {

	public Object getObjectInstance(Object obj, Name name, Context nameCtx,
			Hashtable environment) throws Exception {
		if (!(obj instanceof Reference)) {
			return null;
		}
		Reference ref = (Reference) obj;
		Properties props = new Properties();
		for (int i = 0; i < ref.size(); i++) {
			StringRefAddr addr = (StringRefAddr) ref.get(i);
			props.setProperty((String)addr.getType(), (String)addr.getContent());
		}
		return new SimpleUserTransaction(props);
	}
}
