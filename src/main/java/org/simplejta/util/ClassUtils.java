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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * @author Dibyendu Majumdar
 * @since 14.Jan.2005
 */
public class ClassUtils {

	private static Logger log = Logger.getLogger(ClassUtils.class);

	/**
	 * Get the ClassLoader to use. We always use the current Thread's
	 * Context ClassLoader. Assumption is that all threads within the
	 * application share the same ClassLoader.
	 */
	public static ClassLoader getClassLoader() {
	    ClassLoader cl = Thread.currentThread().getContextClassLoader();
	    if (cl == null) {
	        throw new NullPointerException(Messages.EMISSINGCLASSLOADER);
	    }
	    return cl;
	}
	
	
	/**
	 * A wrapper for Class.forName() so that we can change the behaviour
	 * globally without changing the rest of the code.
	 * 
	 * @param name Name of the class to be loaded
	 * @throws ClassNotFoundException
	 */
	public static Class forName(String name) throws ClassNotFoundException {
		ClassLoader cl = getClassLoader();
		Class clazz = null;
		if (log.isDebugEnabled()) {
		    log.debug("SIMPLEJTA-ClassUtils: Loading class " + name + " using ClassLoader " + cl.toString());
		}
		clazz = Class.forName(name, true, cl);
		return clazz;
	}

	/**
	 * Load a properties file from the classpath.
	 * 
	 * @param name Name of the properties file
	 * @throws IOException If the properties file could not be loaded
	 */
	public static Properties getResourceAsProperties(String name) throws IOException {
	    ClassLoader cl = getClassLoader();
	    InputStream is = null;
        is = cl.getResourceAsStream(name);
	    if (is == null) {
	        throw new IOException(Messages.ELOADRESOURCE + " " + name);
	    }
	    Properties props = new Properties();
	    props.load(is);
	    if (log.isDebugEnabled()) {
		    log.debug("SIMPLEJTA-ClassUtils: Loaded properties = " + props + " from resource " + name);
	    }
	    return props;
	}

	/**
	 * Helper for invoking an instance method that takes a single parameter.
	 * This method also handles parameters of primitive type.
	 * 
	 * @param cl
	 *            The class that the instance belongs to
	 * @param instance
	 *            The object on which we will invoke the method
	 * @param methodName
	 *            The method name
	 * @param param
	 *            The parameter
	 * @return
	 * @throws Throwable
	 */
	public static Object invokeMethod(Class cl, Object instance,
			String methodName, Object param) throws Throwable {
	    Class paramClass;
	    if (param instanceof Integer)
	        paramClass = Integer.TYPE;
	    else if (param instanceof Long) 
	        paramClass = Long.TYPE;
	    else if (param instanceof Short)
	        paramClass = Short.TYPE;
	    else if (param instanceof Boolean)
	        paramClass = Boolean.TYPE;
	    else if (param instanceof Double)
	        paramClass = Double.TYPE;
	    else if (param instanceof Float)
	        paramClass = Float.TYPE;
	    else if (param instanceof Character)
	        paramClass = Character.TYPE;
	    else if (param instanceof Byte)
	        paramClass = Byte.TYPE;
	    else
	        paramClass = param.getClass();
		Method method = cl.getMethod(methodName,
				new Class[] { paramClass });
		try {
			return method.invoke(instance, new Object[] { param });
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}
	
	/**
	 * Helper for invoking a static method that takes one parameter.
	 * 
	 * @param cl
	 *            The class that implements the static method
	 * @param methodName
	 *            The method name
	 * @param param
	 *            A parameter
	 * @param paramClass
	 *            Class of the parameter
	 * @return
	 * @throws Throwable
	 */
	public static Object invokeStaticMethod(Class cl, String methodName,
			Object param, Class paramClass) throws Throwable {
		Method method = cl.getMethod(methodName, new Class[] { paramClass });
		try {
			return method.invoke(null, new Object[] { param });
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	/**
	 * Helper for invoking a constructor with one parameter.
	 * 
	 * @param className Class of which an instance is to be allocated
	 * @param param Parameter
	 * @param paramClass Type of the parameter
	 * @return
	 * @throws ClassNotFoundException
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws IllegalArgumentException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	public static Object createObject(String className, Object param, Class paramClass)
			throws ClassNotFoundException, SecurityException,
			NoSuchMethodException, IllegalArgumentException,
			InstantiationException, IllegalAccessException,
			InvocationTargetException {
		Class clazzImpl = ClassUtils.forName(className);
		Constructor ctor = clazzImpl
				.getConstructor(new Class[] { paramClass });
		Object instance = ctor.newInstance(new Object[] { param });
		return instance;
	}
}