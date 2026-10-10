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
package org.apache.hc.client5.http.examples;

import java.util.List;

import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.CookieSpec;
import org.apache.hc.client5.http.cookie.MalformedCookieException;
import org.apache.hc.client5.http.impl.cookie.RFC6265StrictSpec;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;

/**
 * This example demonstrates the {@code __Secure-} and {@code __Host-} cookie name prefixes. A
 * cookie whose name carries one of these prefixes but does not meet the corresponding requirements
 * is rejected by the cookie specification instead of being accepted into the store.
 */
public class ClientCookieNamePrefix {

    public static void main(final String[] args) throws Exception {
        final CookieSpec cookieSpec = new RFC6265StrictSpec();

        // A secure (https) origin.
        final CookieOrigin origin = new CookieOrigin("www.example.com", 443, "/", true);

        // A well-formed __Host- cookie: Secure, host-only (no Domain), Path=/.
        process(cookieSpec, origin, "__Host-SID=sess-1; Secure; Path=/");

        // A __Host- cookie that violates the prefix by carrying a Domain attribute.
        process(cookieSpec, origin, "__Host-SID=sess-1; Secure; Path=/; Domain=example.com");

        // A __Secure- cookie that is missing the required Secure attribute.
        process(cookieSpec, origin, "__Secure-SID=sess-1; Path=/");

        // An ordinary cookie is not subject to the prefix rules.
        process(cookieSpec, origin, "SID=sess-1; Path=/");
    }

    private static void process(
            final CookieSpec cookieSpec, final CookieOrigin origin, final String setCookie) throws Exception {
        final Header header = new BasicHeader("Set-Cookie", setCookie);
        final List<Cookie> cookies = cookieSpec.parse(header, origin);
        for (final Cookie cookie : cookies) {
            try {
                cookieSpec.validate(cookie, origin);
                System.out.println("ACCEPTED: " + setCookie);
            } catch (final MalformedCookieException ex) {
                System.out.println("REJECTED: " + setCookie + "  ->  " + ex.getMessage());
            }
        }
    }

}
