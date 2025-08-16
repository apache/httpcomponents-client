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
 * Callback interface for receiving {@code 103 Early Hints} (RFC&nbsp;8297)
 * informational responses emitted by the origin server before the final response.
 *
 * <p>The listener is invoked zero or more times per request, once for each
 * {@code 103} received. It is <strong>never</strong> invoked for the final
 * (non-1xx) response.</p>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>Invoked on the I/O thread (async clients) or the request thread
 *   (classic clients). Implementations should be fast and non-blocking.</li>
 *   <li>Headers observed in Early Hints are advisory. Clients typically act only
 *   on safe headers such as {@code Link: ...; rel=preload} or
 *   {@code rel=preconnect}.</li>
 *   <li>Multiple {@code 103} responses may be received; ordering follows the
 *   network order prior to the final response.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * HttpClients.custom()
 *     .setEarlyHintsListener((hints, context) -> {
 *         for (Header h : hints.getHeaders("Link")) {
 *             // inspect preload links, metrics, etc.
 *         }
 *     })
 *     .build();
 * }</pre>
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
