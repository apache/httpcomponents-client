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
package org.apache.hc.client5.http.impl;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.core5.annotation.Internal;

/**
 * Utilities for working with JDK 21 virtual threads without introducing a hard runtime dependency.
 *
 * <p>
 * <p>
 * All methods use reflection to detect and construct virtual-thread components so that the client
 * <p>
 * remains source- and binary-compatible with earlier JDKs. On runtimes where virtual threads are
 * <p>
 * unavailable, the helpers either return {@code false} (for detection) or throw
 * <p>
 * {@link UnsupportedOperationException} (for construction).
 * </p>
 *
 **/
@Internal
public final class VirtualThreadSupport {

    private VirtualThreadSupport() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName("java.lang.Thread$Builder$OfVirtual", false,
                    VirtualThreadSupport.class.getClassLoader());
            Class.forName("java.lang.Thread").getMethod("ofVirtual");
            return true;
        } catch (final Throwable t) {
            return false;
        }
    }

    /**
     * Prefer JDK’s per-task executors when present; otherwise fail.
     */
    public static ExecutorService newVirtualThreadPerTaskExecutor(final String namePrefix) {
        if (!isAvailable()) {
            throw new UnsupportedOperationException("Virtual threads are not available on this runtime");
        }
        try {
            final Class<?> executors = Class.forName("java.util.concurrent.Executors");
            try {
                final Method m = executors.getMethod("newThreadPerTaskExecutor", ThreadFactory.class);
                final ThreadFactory vtFactory = newVirtualThreadFactory(namePrefix);
                return (ExecutorService) m.invoke(null, vtFactory);
            } catch (final NoSuchMethodException ignore) {
                final Method m = executors.getMethod("newVirtualThreadPerTaskExecutor");
                return (ExecutorService) m.invoke(null);
            }
        } catch (final Throwable t) {
            throw new UnsupportedOperationException("Failed to initialize virtual thread per-task executor", t);
        }
    }

    public static ThreadFactory newVirtualThreadFactory(final String ignored) {
        if (!isAvailable()) {
            throw new UnsupportedOperationException("Virtual threads are not available on this runtime");
        }
        try {
            final Class<?> threadClass = Class.forName("java.lang.Thread");
            final Object builder = threadClass.getMethod("ofVirtual").invoke(null);
            final Class<?> ofVirtualClass = Class.forName("java.lang.Thread$Builder$OfVirtual");
            return (ThreadFactory) ofVirtualClass.getMethod("factory").invoke(builder);
        } catch (final Throwable t) {
            throw new UnsupportedOperationException("Failed to initialize virtual thread factory", t);
        }
    }
}
