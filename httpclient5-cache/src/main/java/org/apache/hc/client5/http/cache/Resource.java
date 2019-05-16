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
package org.apache.hc.client5.http.cache;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Represents a disposable system resource used for handling
 * cached response bodies.
 * <p>
 * Implementations of this interface are expected to be threading-safe.
 * </p>
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.SAFE)
public abstract class Resource implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Returns resource content as a {@link InputStream}.
     *
     * @throws ResourceIOException
     */
    public InputStream getInputStream() throws ResourceIOException {
        return new ByteArrayInputStream(get());
    }

    /**
     * Returns resource content as a byte array.
     * <p>
     * Please note for memory efficiency some resource implementations
     * may return a reference to the underlying byte array. The returned
     * value should be treated as immutable.
     *
     * @throws ResourceIOException
     *
     * @since 5.0
     */
    public abstract byte[] get() throws ResourceIOException;

    /**
     * Returns the length in bytes of the response body.
     */
    public abstract long length();

    /**
     * Indicates the system no longer needs to keep this
     * response body and any system resources associated with
     * it may be reclaimed.
     */
    public abstract void dispose();

}
