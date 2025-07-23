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
package org.simplejta.util;

/**
 * ExceptionUtils implements utilities that help with managing Exceptions.
 *
 * @author Dibyendu Majumdar
 * @since 20.Dec.2004
 */
public class ExceptionUtil {
    
    /**
     * A fix for JDK 1.4 problem. JDK 1.4 ignores printStackTrace()
     * methods for exceptions that are set as causes. If you implement
     * a custom chained exception that overrides printStackTrace(), and if
     * such an exception is set as the cause of another Exception, then
     * JDK 1.4 will never execute the over-ridden printStackTrace() when
     * printStackTrace() is invoked on the enclosing exception.  
     * 
     * 
     * @param oldException 
     * @param e
     * @return
     */
	public static Exception chainException(Exception oldException, Exception e) {
		if (oldException == null) {
			oldException = e;
		}
		else {
			Throwable t1 = e;
			Throwable t = t1.getCause();
			while (t != null) {
				t1 = t;
				t = t1.getCause();
			}
			t1.initCause(oldException);
			oldException = e;
		}
		return oldException;
	}

}
