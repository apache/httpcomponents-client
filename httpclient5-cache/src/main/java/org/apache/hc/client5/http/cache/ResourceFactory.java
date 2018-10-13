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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Generates {@link Resource} instances for handling cached
 * HTTP response bodies.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface ResourceFactory {

    /**
     * Creates a {@link Resource} from a given response body.
     * @param requestId a unique identifier for this particular response body.
     * @param content byte array that represents the origin HTTP response body.
     * @return a {@code Resource} containing however much of
     *   the response body was successfully read.
     * @throws ResourceIOException
     */
    Resource generate(String requestId, byte[] content) throws ResourceIOException;

    /**
     * Creates a {@link Resource} from a given response body.
     * @param requestId a unique identifier for this particular response body.
     * @param content byte array that represents the origin HTTP response body.
     * @param off   the start offset in the array.
     * @param len   the number of bytes to read from the array.
     * @return a {@code Resource} containing however much of
     *   the response body was successfully read.
     * @throws ResourceIOException
     */
    Resource generate(String requestId, byte[] content, int off, int len) throws ResourceIOException;

    /**
     * Clones an existing {@link Resource}.
     * @param requestId unique identifier provided to associate
     *   with the cloned response body.
     * @param resource the original response body to clone.
     * @return the {@code Resource} copy
     * @throws ResourceIOException
     */
    Resource copy(String requestId, Resource resource) throws ResourceIOException;

}
