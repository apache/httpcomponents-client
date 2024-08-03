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

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * A lax implementation of the {@link RedirectStrategy} that automatically handles HTTP redirects
 * for HEAD, GET, POST, and DELETE requests based on the response status code.
 *
 * <p>This strategy relaxes the HTTP specification restrictions on automatic redirection of POST
 * requests, allowing redirects for these methods where the specification might otherwise prohibit
 * them. This can be useful in scenarios where a server indicates a redirect for a POST request
 * that the client should follow.</p>
 *
 * <p>This class is thread-safe and stateless, suitable for use across multiple threads
 * without additional synchronization.</p>
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class LaxRedirectStrategy extends DefaultRedirectStrategy {

    public static final LaxRedirectStrategy INSTANCE = new LaxRedirectStrategy();

    private static final String[] REDIRECT_METHODS = new String[]{HttpGet.METHOD_NAME, HttpPost.METHOD_NAME, HttpHead.METHOD_NAME, HttpDelete.METHOD_NAME};

    @Override
    public boolean isRedirected(final HttpRequest request, final HttpResponse response, final HttpContext context) {
        if (!response.containsHeader(HttpHeaders.LOCATION)) {
            return false;
        }

        final int statusCode = response.getCode();
        final String method = request.getMethod();
        final Header locationHeader = response.getFirstHeader("location");
        switch (statusCode) {
            case HttpStatus.SC_MOVED_TEMPORARILY:
                return isRedirectable(method) && locationHeader != null;
            case HttpStatus.SC_MOVED_PERMANENTLY:
            case HttpStatus.SC_TEMPORARY_REDIRECT:
                return isRedirectable(method);
            case HttpStatus.SC_SEE_OTHER:
                return true;
            default:
                return false;
        }
    }

    protected boolean isRedirectable(final String method) {
        for (final String m : REDIRECT_METHODS) {
            if (m.equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }
}