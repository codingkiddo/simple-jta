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

import java.util.EventObject;

import javax.transaction.xa.Xid;

/**
 * @author Dibyendu Majumdar
 * @since 01.Jan.2005
 */
public class ResourceEvent extends EventObject {

	private static final long serialVersionUID = 1651984469568434106L;

	Xid xid;
	
	public ResourceEvent(Resource resource, Xid xid) {
		super(resource);
		this.xid = xid;
	}

	public final Xid getXid() {
		return xid;
	}
	
	public final Resource getResource() {
		return (Resource) getSource();
	}
	
}
