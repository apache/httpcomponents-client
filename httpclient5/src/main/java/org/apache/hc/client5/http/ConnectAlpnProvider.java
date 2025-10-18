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

import java.util.List;

import org.apache.hc.core5.http.HttpHost;

/**
 * Supplies the Application-Layer Protocol Negotiation (ALPN) protocol IDs
 * to advertise in the HTTP {@code ALPN} header on a {@code CONNECT} request
 * (RFC 7639).
 *
 * <p>If this method returns {@code null} or an empty list, the client will
 * not add the {@code ALPN} header.</p>
 *
 * <p>Implementations should be fast and side-effect free; it may be invoked
 * for each CONNECT attempt.</p>
 *
 * @since 5.6
 */
@FunctionalInterface
public interface ConnectAlpnProvider {

    /**
     * Returns the ALPN protocol IDs to advertise for a tunnel to {@code target}
     * over the given {@code route}.
     *
     * @param target the origin server the tunnel will connect to (non-null)
     * @param route  the planned connection route, including proxy info (non-null)
     * @return list of protocol IDs (e.g., {@code "h2"}, {@code "http/1.1"});
     * {@code null} or empty to omit the header
     */
    List<String> getAlpnForTunnel(HttpHost target, HttpRoute route);
}
