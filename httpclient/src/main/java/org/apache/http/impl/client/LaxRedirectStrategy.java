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

package org.apache.http.impl.client;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.protocol.HttpContext;

/**
 * Lax {@link RedirectStrategy} implementation that automatically redirects all HEAD, GET and POST
 * requests. This strategy relaxes restrictions on automatic redirection of POST methods imposed
 * by the HTTP specification.
 *
 * @since 4.2
 */
@Immutable
public class LaxRedirectStrategy extends DefaultRedirectStrategy {

    /**
     * Redirectable methods.
     */
    private static final String[] REDIRECT_METHODS = new String[] {
        HttpGet.METHOD_NAME,
        HttpPost.METHOD_NAME,
        HttpHead.METHOD_NAME
    };

    @Override
    public boolean isRedirected(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }

        String method = request.getRequestLine().getMethod();

        int status = response.getStatusLine().getStatusCode();
        switch (status) {
        case HttpStatus.SC_MOVED_TEMPORARILY:
            Header location = response.getFirstHeader("location");
            return isRedirectable(method) && location != null;
        case HttpStatus.SC_MOVED_PERMANENTLY:
        case HttpStatus.SC_TEMPORARY_REDIRECT:
            return isRedirectable(method);
        case HttpStatus.SC_SEE_OTHER:
            return true;
        default:
            return false;
        }
    }

    private boolean isRedirectable(String method) {
        for (String m: REDIRECT_METHODS) {
            if (m.equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }

}
