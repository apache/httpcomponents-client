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
package org.apache.hc.client5.http.rest;

/**
 * Signals a non-2xx HTTP response from a REST proxy method call.
 *
 * @since 5.7
 */
public final class RestClientResponseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final byte[] responseBody;

    RestClientResponseException(final int statusCode, final String reasonPhrase,
                                final byte[] responseBody) {
        super("HTTP " + statusCode + (reasonPhrase != null ? " " + reasonPhrase : ""));
        this.statusCode = statusCode;
        this.responseBody = responseBody != null ? responseBody.clone() : null;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return the status code.
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the response body bytes, or {@code null} if the response had no body.
     *
     * @return the response body.
     */
    public byte[] getResponseBody() {
        return responseBody != null ? responseBody.clone() : null;
    }

}
