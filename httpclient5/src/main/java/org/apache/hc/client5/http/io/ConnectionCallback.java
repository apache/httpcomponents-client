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
package org.apache.hc.client5.http.io;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * A callback interface that gets invoked upon different steps of connection initialization.
 * Useful for measuring duration of initialization steps.
 *
 * @since 5.4
 */
public interface ConnectionCallback {

    void onBeforeDnsResolve(HttpContext httpContext);

    void onAfterDnsResolve(HttpContext httpContext);

    void onBeforeSocketConnect(HttpContext httpContext, HttpHost endpointHost);

    void onAfterSocketConnect(HttpContext httpContext, HttpHost endpointHost);

    void onBeforeTlsHandshake(HttpContext httpContext, HttpHost endpointHost);

    void onAfterTlsHandshake(HttpContext httpContext, HttpHost endpointHost);

}
