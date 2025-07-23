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
package org.simplejta.tm;

import org.springframework.beans.factory.access.BeanFactoryLocator;
import org.springframework.beans.factory.access.BeanFactoryReference;
import org.springframework.beans.factory.access.SingletonBeanFactoryLocator;

public class SimpleTransactionManagerReference {

	static final String STM_TMGR_FACTORY_KEY = "classpath*:SimpleJTA.xml";

	BeanFactoryReference beanFactoryReference;
	
	String key;
	
	String tmId;
	
	public SimpleTransactionManagerReference(String key, String tmId) {
		this.key = key;
		this.tmId = tmId;
		BeanFactoryLocator bfl = SingletonBeanFactoryLocator.getInstance(STM_TMGR_FACTORY_KEY);
		beanFactoryReference = bfl.useBeanFactory(key);
	}

	public SimpleTransactionManager getTransactionManager() {
		return (SimpleTransactionManager) beanFactoryReference.getFactory().getBean(tmId);
	}
	
	public void release() {
		beanFactoryReference.release();
	}
	
}
