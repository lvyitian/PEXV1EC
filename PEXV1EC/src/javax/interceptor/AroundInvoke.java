/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package javax.interceptor;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

import java.lang.annotation.Target;
import java.lang.annotation.Retention;

/**
 * <p>Defines an interceptor method that interposes on business methods.
 * The method must take a single parameter of type 
 * {@link javax.interceptor.InvocationContext} and have a return type
 * {@link java.lang.Object}.  The method must not be declared as abstract,
 * final, or static.</p>
 * 
 * <pre>
 * &#064;AroundInvoke
 * public Object intercept(InvocationContext ctx) throws Exception { ... }
 * </pre>
 * 
 * <p><tt>AroundInvoke</tt> methods may be declared in interceptor
 * classes, in the superclasses of interceptor classes, in the target
 * class, and/or in superclasses of the target class.</p>
 *
 * <p>A given class must not declare more than one <tt>AroundInvoke</tt> 
 * method.</p>
 * 
 * <p>An <tt>AroundInvoke</tt> method can invoke any component or 
 * resource that the method it is intercepting can invoke.</p>
 * 
 * <p>In general, <tt>AroundInvoke</tt> method invocations occur within the 
 * same transaction and security context as the method on which they are 
 * interposing.</p>
 * 
 * <p><tt>AroundInvoke</tt> methods may throw any exceptions that are 
 * allowed by the throws clause of the method on which they are 
 * interposing. They may catch and suppress exceptions and recover 
 * by calling {@link javax.interceptor.InvocationContext#proceed()}.</p>
 *
 * @since Interceptors 1.0 
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AroundInvoke {
}