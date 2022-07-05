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

package org.apache.hc.client5.http.protocol;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.CookieSpec;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.cookie.MalformedCookieException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Response interceptor that populates the current {@link CookieStore} with data
 * contained in response cookies received in the given the HTTP response.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class ResponseProcessCookies implements HttpResponseInterceptor {

    /**
     * Singleton instance.
     *
     * @since 5.2
     */
    public static final ResponseProcessCookies INSTANCE = new ResponseProcessCookies();

    private static final Logger LOG = LoggerFactory.getLogger(ResponseProcessCookies.class);

    public ResponseProcessCookies() {
        super();
    }

    @Override
    public void process(final HttpResponse response, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(response, "HTTP request");
        Args.notNull(context, "HTTP context");

        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final String exchangeId = clientContext.getExchangeId();

        // Obtain actual CookieSpec instance
        final CookieSpec cookieSpec = clientContext.getCookieSpec();
        if (cookieSpec == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Cookie spec not specified in HTTP context", exchangeId);
            }
            return;
        }
        // Obtain cookie store
        final CookieStore cookieStore = clientContext.getCookieStore();
        if (cookieStore == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Cookie store not specified in HTTP context", exchangeId);
            }
            return;
        }
        // Obtain actual CookieOrigin instance
        final CookieOrigin cookieOrigin = clientContext.getCookieOrigin();
        if (cookieOrigin == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Cookie origin not specified in HTTP context", exchangeId);
            }
            return;
        }
        final Iterator<Header> it = response.headerIterator(HttpHeaders.SET_COOKIE);
        processCookies(exchangeId, it, cookieSpec, cookieOrigin, cookieStore);
    }

    private void processCookies(
            final String exchangeId,
            final Iterator<Header> iterator,
            final CookieSpec cookieSpec,
            final CookieOrigin cookieOrigin,
            final CookieStore cookieStore) {
        while (iterator.hasNext()) {
            final Header header = iterator.next();
            try {
                final List<Cookie> cookies = cookieSpec.parse(header, cookieOrigin);
                for (final Cookie cookie : cookies) {
                    try {
                        cookieSpec.validate(cookie, cookieOrigin);
                        cookieStore.addCookie(cookie);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} Cookie accepted [{}]", exchangeId, formatCookie(cookie));
                        }
                    } catch (final MalformedCookieException ex) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("{} Cookie rejected [{}] {}", exchangeId, formatCookie(cookie), ex.getMessage());
                        }
                    }
                }
            } catch (final MalformedCookieException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("{} Invalid cookie header: \"{}\". {}", exchangeId, header, ex.getMessage());
                }
            }
        }
    }

    private static String formatCookie(final Cookie cookie) {
        final StringBuilder buf = new StringBuilder();
        buf.append(cookie.getName());
        buf.append("=\"");
        String v = cookie.getValue();
        if (v != null) {
            if (v.length() > 100) {
                v = v.substring(0, 100) + "...";
            }
            buf.append(v);
        }
        buf.append("\"");
        buf.append(", domain:");
        buf.append(cookie.getDomain());
        buf.append(", path:");
        buf.append(cookie.getPath());
        buf.append(", expiry:");
        buf.append(cookie.getExpiryInstant());
        return buf.toString();
    }

}
