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

import java.io.IOException;
import java.io.InputStream;

import com.github.luben.zstd.ZstdInputStream;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * <p>
 * Singleton factory for creating Zstd decompression {@link InputStream} instances.
 * </p>
 *
 * <p>
 * This factory is stateless, making it safe for use across multiple threads.
 * </p>
 *
 * <p>
 * The factory relies on the availability of the <code>com.github.luben.zstd.ZstdInputStream</code> class
 * for creating the decompression streams. Make sure that the required library is available on the classpath.
 * </p>
 *
 * @see InputStreamFactory
 * @see ZstdInputStream
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class ZstdInputStreamFactory implements InputStreamFactory {

    /**
     * Singleton instance of the factory.
     */
    private static final ZstdInputStreamFactory INSTANCE = new ZstdInputStreamFactory();

    /**
     * Retrieves the singleton instance of the {@link ZstdInputStreamFactory}.
     *
     * @return the singleton instance
     */
    public static ZstdInputStreamFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new Zstd decompression {@link InputStream} that wraps the given input stream.
     *
     * @param inputStream the non-null input stream to be wrapped
     * @return a new Zstd decompression {@link InputStream}
     * @throws IOException if an I/O error occurs
     */
    @Override
    public InputStream create(final InputStream inputStream) throws IOException {
        return new ZstdInputStream(inputStream);
    }
}
