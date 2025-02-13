/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.testing.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import javax.security.auth.Subject;

import org.apache.hc.core5.annotation.Internal;

/**
 * This class is based on SecurityUtils in Apache Avatica which is loosely based on SecurityUtils in
 * Jetty 12.0
 * <p>
 * Collections of utility methods to deal with the scheduled removal of the security classes defined
 * by <a href="https://openjdk.org/jeps/411">JEP 411</a>.
 * </p>
 */
@Internal
public class SecurityUtils {
    private static final MethodHandle CALL_AS = lookupCallAs();
    private static final MethodHandle CURRENT = lookupCurrent();
    private static final MethodHandle DO_PRIVILEGED = lookupDoPrivileged();

    private SecurityUtils() {
    }

    private static MethodHandle lookupCallAs() {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            try {
                // Subject.doAs() is deprecated for removal and replaced by Subject.callAs().
                // Lookup first the new API, since for Java versions where both exist, the
                // new API delegates to the old API (for example Java 18, 19 and 20).
                // Otherwise (Java 17), lookup the old API.
                return lookup.findStatic(Subject.class, "callAs",
                    MethodType.methodType(Object.class, Subject.class, Callable.class));
            } catch (final NoSuchMethodException x) {
                try {
                    // Lookup the old API.
                    final MethodType oldSignature =
                            MethodType.methodType(Object.class, Subject.class,
                                PrivilegedExceptionAction.class);
                    final MethodHandle doAs =
                            lookup.findStatic(Subject.class, "doAs", oldSignature);
                    // Convert the Callable used in the new API to the PrivilegedAction used in the
                    // old
                    // API.
                    final MethodType convertSignature =
                            MethodType.methodType(PrivilegedExceptionAction.class, Callable.class);
                    final MethodHandle converter =
                            lookup.findStatic(SecurityUtils.class,
                                "callableToPrivilegedExceptionAction", convertSignature);
                    return MethodHandles.filterArguments(doAs, 1, converter);
                } catch (final NoSuchMethodException e) {
                    throw new AssertionError(e);
                }
            }
        } catch (final IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static MethodHandle lookupDoPrivileged() {
        try {
            // Use reflection to work with Java versions that have and don't have AccessController.
            final Class<?> klass =
                    ClassLoader.getSystemClassLoader().loadClass("java.security.AccessController");
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.findStatic(klass, "doPrivileged",
                MethodType.methodType(Object.class, PrivilegedAction.class));
        } catch (final NoSuchMethodException | IllegalAccessException x) {
            // Assume that single methods won't be removed from AcessController
            throw new AssertionError(x);
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }

    private static MethodHandle lookupCurrent() {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            // Subject.getSubject(AccessControlContext) is deprecated for removal and replaced by
            // Subject.current().
            // Lookup first the new API, since for Java versions where both exists, the
            // new API delegates to the old API (for example Java 18, 19 and 20).
            // Otherwise (Java 17), lookup the old API.
            return lookup.findStatic(Subject.class, "current",
                MethodType.methodType(Subject.class));
        } catch (final NoSuchMethodException e) {
            final MethodHandle getContext = lookupGetContext();
            final MethodHandle getSubject = lookupGetSubject();
            return MethodHandles.filterReturnValue(getContext, getSubject);
        } catch (final IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static MethodHandle lookupGetSubject() {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            final Class<?> contextklass =
                    ClassLoader.getSystemClassLoader()
                            .loadClass("java.security.AccessControlContext");
            return lookup.findStatic(Subject.class, "getSubject",
                MethodType.methodType(Subject.class, contextklass));
        } catch (final ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static MethodHandle lookupGetContext() {
        try {
            // Use reflection to work with Java versions that have and don't have AccessController.
            final Class<?> controllerKlass =
                    ClassLoader.getSystemClassLoader().loadClass("java.security.AccessController");
            final Class<?> contextklass =
                    ClassLoader.getSystemClassLoader()
                            .loadClass("java.security.AccessControlContext");

            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.findStatic(controllerKlass, "getContext",
                MethodType.methodType(contextklass));
        } catch (final ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Maps to AccessController#doPrivileged if available, otherwise returns action.run().
     * @param action the action to run
     * @return the result of running the action
     * @param <T> the type of the result
     */
    public static <T> T doPrivileged(final PrivilegedAction<T> action) {
        // Keep this method short and inlineable.
        if (DO_PRIVILEGED == null) {
            return action.run();
        }
        return doPrivileged(DO_PRIVILEGED, action);
    }

    private static <T> T doPrivileged(final MethodHandle doPrivileged, final PrivilegedAction<T> action) {
        try {
            return (T) doPrivileged.invoke(action);
        } catch (final Throwable t) {
            throw sneakyThrow(t);
        }
    }

    /**
     * Maps to Subject.callAs() if available, otherwise maps to Subject.doAs()
     * @param subject the subject this action runs as
     * @param action the action to run
     * @return the result of the action
     * @param <T> the type of the result
     * @throws CompletionException
     */
    public static <T> T callAs(final Subject subject, final Callable<T> action) throws CompletionException {
        try {
            return (T) CALL_AS.invoke(subject, action);
        } catch (final PrivilegedActionException e) {
            throw new CompletionException(e.getCause());
        } catch (final Throwable t) {
            throw sneakyThrow(t);
        }
    }

    /**
     * Maps to Subject.currect() is available, otherwise maps to Subject.getSubject()
     * @return the current subject
     */
    public static Subject currentSubject() {
        try {
            return (Subject) CURRENT.invoke();
        } catch (final Throwable t) {
            throw sneakyThrow(t);
        }
    }

    @SuppressWarnings("unused")
    private static <T> PrivilegedExceptionAction<T>
            callableToPrivilegedExceptionAction(final Callable<T> callable) {
        return callable::call;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(final Throwable e) throws E {
        throw (E) e;
    }
}
