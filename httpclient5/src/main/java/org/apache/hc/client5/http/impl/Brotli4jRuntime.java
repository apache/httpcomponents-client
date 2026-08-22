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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;


@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class Brotli4jRuntime {

    private static final String BROTLI4J_LOADER = "com.aayushatharva.brotli4j.Brotli4jLoader";

    private static final String IS_AVAILABLE = "isAvailable";

    private Brotli4jRuntime() {
    }

    /**
     * @return {@code true} if Brotli4j and its native library are available
     * to the HttpClient class loader; {@code false} otherwise
     */
    public static boolean available() {
        return available(Brotli4jRuntime.class.getClassLoader());
    }

    /**
     * Determines whether Brotli4j and its native library are available to the
     * given class loader.
     *
     * @param classLoader the class loader used to locate Brotli4j
     * @return {@code true} if Brotli4j reports that its native runtime is
     * available; {@code false} otherwise
     */
    static boolean available(final ClassLoader classLoader) {
        try {
            final Class<?> loaderClass =
                    Class.forName(BROTLI4J_LOADER, true, classLoader);
            return Boolean.TRUE.equals(
                    loaderClass.getMethod(IS_AVAILABLE).invoke(null));
        } catch (final ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }
}
