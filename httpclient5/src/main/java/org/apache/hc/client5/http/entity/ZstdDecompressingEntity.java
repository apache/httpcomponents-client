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

package org.apache.hc.client5.http.entity;

import org.apache.hc.core5.http.HttpEntity;

/**
 * <p>
 * Implements an {@link HttpEntity} that performs Zstd-based decompression on the fly.
 * </p>
 *
 * <p>
 * This class is typically used to wrap an HTTP response entity, transparently
 * decompressing the Zstd-encoded data contained within.
 * </p>
 *
 * <p>
 * This implementation is dependent on the availability of the
 * <code>com.github.luben.zstd.ZstdInputStream</code> class. If this class is not
 * available, the {@link #isAvailable()} method will return <code>false</code>.
 * </p>
 *
 * @see HttpEntity
 * @since 5.4
 */
public class ZstdDecompressingEntity extends DecompressingEntity {

    /**
     * Creates a new {@link ZstdDecompressingEntity} that wraps the specified
     * HTTP entity.
     *
     * @param entity the non-null HTTP entity to be wrapped
     */
    public ZstdDecompressingEntity(final HttpEntity entity) {
        super(entity, ZstdInputStreamFactory.getInstance());
    }

    /**
     * <p>
     * Checks if Zstd decompression is available. This method tries to load
     * the <code>com.github.luben.zstd.ZstdInputStream</code> class.
     * </p>
     *
     * @return <code>true</code> if Zstd decompression is available, <code>false</code>
     * otherwise
     */
    public static boolean isAvailable() {
        try {
            Class.forName("com.github.luben.zstd.ZstdInputStream");
            return true;
        } catch (final ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }
}
