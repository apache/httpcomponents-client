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
import java.util.Locale;

import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.CookieRestrictionViolationException;
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
     * Default instance of {@link ResponseProcessCookies}.
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

        final HttpClientContext clientContext = HttpClientContext.cast(context);
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
                        if (!cookieOrigin.isSecure() && overlaysSecureCookie(cookie, cookieStore)) {
                            throw new CookieRestrictionViolationException(
                                    "Insecure origin may not overwrite secure cookie \"" + cookie.getName() + "\"");
                        }
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

    /**
     * RFC 6265bis, 5.7: a cookie received from an insecure origin is ignored if the store
     * holds a secure cookie with the same name whose domain domain-matches the new cookie's
     * domain (or vice versa) and whose path is path-matched by the new cookie's path.
     */
    private static boolean overlaysSecureCookie(final Cookie cookie, final CookieStore cookieStore) {
        for (final Cookie existing : cookieStore.getCookies()) {
            if (existing.isSecure()
                    && existing.getName().equals(cookie.getName())
                    && (domainMatch(existing.getDomain(), cookie.getDomain())
                        || domainMatch(cookie.getDomain(), existing.getDomain()))
                    && pathMatch(cookie.getPath(), existing.getPath())) {
                return true;
            }
        }
        return false;
    }

    private static boolean domainMatch(final String host, final String domain) {
        if (host == null || domain == null) {
            return false;
        }
        final String h = host.toLowerCase(Locale.ROOT);
        final String d = domain.toLowerCase(Locale.ROOT);
        return h.equals(d) || h.endsWith("." + d);
    }

    private static boolean pathMatch(final String path, final String cookiePath) {
        final String p = path != null ? path : "/";
        final String c = cookiePath != null ? cookiePath : "/";
        if (p.equals(c)) {
            return true;
        }
        return p.startsWith(c) && (c.endsWith("/") || p.charAt(c.length()) == '/');
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
