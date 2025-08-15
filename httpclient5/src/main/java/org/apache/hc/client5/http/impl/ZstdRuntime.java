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

/**
 * Utility to detect availability of the Zstandard JNI runtime on the classpath.
 * <p>
 * Used by the async client implementation to <em>conditionally</em> register
 * zstd encoders/decoders without creating a hard dependency on {@code zstd-jni}.
 * </p>
 *
 * <p>This class performs a lightweight reflective check and intentionally avoids
 * linking to JNI classes at compile time to prevent {@link LinkageError}s when
 * the optional dependency is absent.</p>
 *
 * @since 5.6
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class ZstdRuntime {

    private static final String ZSTD = "com.github.luben.zstd.Zstd";

    private ZstdRuntime() {
    }

    /**
     * @return {@code true} if {@code com.github.luben.zstd.Zstd} can be loaded
     * by the current class loader; {@code false} otherwise
     */
    public static boolean available() {
        try {
            Class.forName(ZSTD, false, ZstdRuntime.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }
}

