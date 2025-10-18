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
package org.apache.hc.client5.http;

import java.io.IOException;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Callback interface for receiving {@code 103 Early Hints}
 * informational responses emitted by the server before the final response.
 *
 * <p>The listener may be invoked multiple times per request, once for each
 * {@code 103} received. It is never invoked for the final (non-1xx) response.</p>
 *
 * <p>Implementations should be fast and non-blocking. If heavy work is needed,
 * offload it to an application executor.</p>
 *
 * @since 5.6
 */
@FunctionalInterface
public interface EarlyHintsListener {

    /**
     * Called for each received {@code 103 Early Hints} informational response.
     *
     * @param hints   the {@code 103} response object as received on the wire
     * @param context the current execution context (never {@code null})
     * @throws HttpException to signal an HTTP-layer error while handling hints
     * @throws IOException   to signal an I/O error while handling hints
     */
    void onEarlyHints(HttpResponse hints, HttpContext context) throws HttpException, IOException;
}
