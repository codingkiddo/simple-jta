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

import java.sql.SQLException;
import java.util.Map;

import javax.transaction.SystemException;
import javax.transaction.xa.Xid;

/**
 * ResourceFactory implementations are responsible for creating new {@link Resource}
 * objects, and for implementing connection pooling.
 * 
 * @author Dibyendu
 */
public interface ResourceFactory  {

    /**
	 * Obtains a new Resource instance. The resource instance will not be
	 * associated with any transaction. It is assumed that the caller will
	 * enlist the resource with appropriate transaction.
	 */
    public Resource getResource() throws IllegalStateException, SQLException,
            SystemException;

    /**
	 * Obtains a new Resource instance. The resource instance will not be
	 * associated with any transaction. It is assumed that the caller will
	 * enlist the resource with appropriate transaction.
	 * <p>A GlobalTransaction xid is passed as a hint - this will be
     * used to optimize connection pooling, a resource that has previously been
     * enlisted for the given xid will be returned if available.
     * 
     * @param xid -
     *            The GlobalTransaction xid for which the resource is being
     *            requested. This information will be used to optimize
     *            connection pooling, a resource that has previously been
     *            enlisted will be returned if available.
     */
    public Resource getResource(Xid xid) throws IllegalStateException,
            SQLException, SystemException;

    /**
     * Sets properties to be used for connecting to the resource
     * manager. Interpretation of these properties is upto the relevant
     * resource adaptor implementation.
     * @param connectionProperties Map of properties
     */
    void setConnectionProperties(Map connectionProperties);
    
    /**
     * Destroys the instance and closes all connections associated with it.
     */
    public void destroy() throws Exception;
}
