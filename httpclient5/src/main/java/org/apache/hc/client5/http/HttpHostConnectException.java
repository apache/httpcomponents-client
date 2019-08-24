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

import java.net.ConnectException;

import org.apache.hc.core5.net.NamedEndpoint;

/**
 * A {@link ConnectException} that specifies the {@link NamedEndpoint} that was being connected to.
 *
 * @since 4.0
 */
public class HttpHostConnectException extends ConnectException {

    private static final long serialVersionUID = -3194482710275220224L;

    private final NamedEndpoint namedEndpoint;

    /**
     * Creates a HttpHostConnectException with the specified detail message.
     */
    public HttpHostConnectException(final String message) {
        super(message);
        this.namedEndpoint = null;
    }

    public HttpHostConnectException(final String message, final NamedEndpoint namedEndpoint) {
        super(message);
        this.namedEndpoint = namedEndpoint;
    }

    /**
     * @since 5.0
     */
    public NamedEndpoint getHost() {
        return this.namedEndpoint;
    }

}
