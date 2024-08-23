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

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.StatusLine;
import org.conscrypt.Internal;

/**
 * Signals that the tunnel request was rejected by the proxy host.
 *
 * @since 4.0
 *
 * @deprecated Do not use/
 */
@Deprecated
public class TunnelRefusedException extends HttpException {

    private static final long serialVersionUID = -8646722842745617323L;

    private final HttpResponse response;

    /**
     * @deprecated Do not use.
     */
    @Deprecated
    public TunnelRefusedException(final String message, final String responseMessage) {
        super(message);
        this.response = new BasicHttpResponse(500);
    }

    @Internal
    public TunnelRefusedException(final HttpResponse response) {
        super("CONNECT refused by proxy: " + new StatusLine(response));
        this.response = response;
    }

    /**
     * @deprecated Use {@link #getResponse()}.
     */
    @Deprecated
    public String getResponseMessage() {
        return "CONNECT refused by proxy: " + new StatusLine(response);
    }

    /**
     * @since 5.4
     */
    public HttpResponse getResponse() {
        return response;
    }

}
