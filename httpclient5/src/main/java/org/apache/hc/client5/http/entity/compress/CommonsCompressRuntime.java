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

package org.apache.hc.client5.http.entity.compress;


import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Lightweight guard that checks whether the Commons Compress factory
 * class is loadable with the current class loader.
 * <p>
 * Used by the codec registry to decide if reflective wiring of optional
 * codecs should even be attempted.
 * </p>
 *
 * @since 5.6
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
final class CommonsCompressRuntime {

    private static final String CCSF =
            "org.apache.commons.compress.compressors.CompressorStreamFactory";

    /**
     * Non-instantiable.
     */
    private CommonsCompressRuntime() {
    }

    /**
     * Returns {@code true} if the core Commons Compress class can be loaded
     * with the current class-loader, {@code false} otherwise.
     */
    static boolean available() {
        try {
            Class.forName(CCSF, false,
                    CommonsCompressRuntime.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }
}

