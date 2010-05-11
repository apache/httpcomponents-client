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
package org.apache.http.client.cache.impl;

import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;

public class HttpTestUtils {

    /*
     * "The following HTTP/1.1 headers are hop-by-hop headers..."
     *
     * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.5.1
     */
    private static final String[] HOP_BY_HOP_HEADERS = { "Connection", "Keep-Alive", "Proxy-Authenticate",
            "Proxy-Authorization", "TE", "Trailers", "Transfer-Encoding", "Upgrade" };

    /*
     * "Multiple message-header fields with the same field-name MAY be present
     * in a message if and only if the entire field-value for that header field
     * is defined as a comma-separated list [i.e., #(values)]."
     *
     * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2
     */
    private static final String[] MULTI_HEADERS = { "Accept", "Accept-Charset", "Accept-Encoding",
            "Accept-Language", "Allow", "Cache-Control", "Connection", "Content-Encoding",
            "Content-Language", "Expect", "Pragma", "Proxy-Authenticate", "TE", "Trailer",
            "Transfer-Encoding", "Upgrade", "Via", "Warning", "WWW-Authenticate" };
    private static final String[] SINGLE_HEADERS = { "Accept-Ranges", "Age", "Authorization",
            "Content-Length", "Content-Location", "Content-MD5", "Content-Range", "Content-Type",
            "Date", "ETag", "Expires", "From", "Host", "If-Match", "If-Modified-Since",
            "If-None-Match", "If-Range", "If-Unmodified-Since", "Last-Modified", "Location",
            "Max-Forwards", "Proxy-Authorization", "Range", "Referer", "Retry-After", "Server",
            "User-Agent", "Vary" };

    /*
     * Determines whether the given header name is considered a hop-by-hop
     * header.
     *
     * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.5.1
     */
    public static boolean isHopByHopHeader(String name) {
        for (String s : HOP_BY_HOP_HEADERS) {
            if (s.equalsIgnoreCase(name))
                return true;
        }
        return false;
    }

    /*
     * Determines whether a given header name may only appear once in a message.
     */
    public static boolean isSingleHeader(String name) {
        for (String s : SINGLE_HEADERS) {
            if (s.equalsIgnoreCase(name))
                return true;
        }
        return false;
    }

    /*
     * Assert.asserts that two request or response bodies are byte-equivalent.
     */
    public static boolean equivalent(HttpEntity e1, HttpEntity e2) throws Exception {
        InputStream i1 = e1.getContent();
        InputStream i2 = e2.getContent();
        if (i1 == null && i2 == null)
            return true;
        if (i1 == null || i2 == null)
            return false; // avoid possible NPEs below
        int b1 = -1;
        while ((b1 = i1.read()) != -1) {
            if (b1 != i2.read())
                return false;
        }
        return (-1 == i2.read());
    }

    /*
     * Assert.asserts that the components of two status lines match in a way
     * that differs only by hop-by-hop information. "2.1 Proxy Behavior ...We
     * remind the reader that HTTP version numbers are hop-by-hop components of
     * HTTP meesages, and are not end-to-end."
     *
     * @see http://www.ietf.org/rfc/rfc2145.txt
     */
    public static boolean semanticallyTransparent(StatusLine l1, StatusLine l2) {
        return (l1.getReasonPhrase().equals(l2.getReasonPhrase()) && l1.getStatusCode() == l2
                .getStatusCode());
    }

    /* Assert.asserts that the components of two status lines match. */
    public static boolean equivalent(StatusLine l1, StatusLine l2) {
        return (l1.getProtocolVersion().equals(l2.getProtocolVersion()) && semanticallyTransparent(
                l1, l2));
    }

    /* Assert.asserts that the components of two request lines match. */
    public static boolean equivalent(RequestLine l1, RequestLine l2) {
        return (l1.getMethod().equals(l2.getMethod())
                && l1.getProtocolVersion().equals(l2.getProtocolVersion()) && l1.getUri().equals(
                l2.getUri()));
    }

    /*
     * Retrieves the full header value by combining multiple headers and
     * separating with commas, canonicalizing whitespace along the way.
     *
     * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2
     */
    public static String getCanonicalHeaderValue(HttpMessage r, String name) {
        if (isSingleHeader(name)) {
            Header h = r.getFirstHeader(name);
            return (h != null) ? h.getValue() : null;
        }
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (Header h : r.getHeaders(name)) {
            if (!first) {
                buf.append(", ");
            }
            buf.append(h.getValue().trim());
            first = false;
        }
        return buf.toString();
    }

    /*
     * Assert.asserts that all the headers appearing in r1 also appear in r2
     * with the same canonical header values.
     */
    public static boolean isEndToEndHeaderSubset(HttpMessage r1, HttpMessage r2) {
        for (Header h : r1.getAllHeaders()) {
            if (!isHopByHopHeader(h.getName())) {
                String r1val = getCanonicalHeaderValue(r1, h.getName());
                String r2val = getCanonicalHeaderValue(r2, h.getName());
                if (!r1val.equals(r2val))
                    return false;
            }
        }
        return true;
    }

    /*
     * Assert.asserts that message <code>r2</code> represents exactly the same
     * message as <code>r1</code>, except for hop-by-hop headers. "When a cache
     * is semantically transparent, the client receives exactly the same
     * response (except for hop-by-hop headers) that it would have received had
     * its request been handled directly by the origin server."
     *
     * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec1.html#sec1.3
     */
    public static boolean semanticallyTransparent(HttpResponse r1, HttpResponse r2)
            throws Exception {
        return (equivalent(r1.getEntity(), r2.getEntity())
                && semanticallyTransparent(r1.getStatusLine(), r2.getStatusLine()) && isEndToEndHeaderSubset(
                r1, r2));
    }

    /* Assert.asserts that two requests are morally equivalent. */
    public static boolean equivalent(HttpRequest r1, HttpRequest r2) {
        return (equivalent(r1.getRequestLine(), r2.getRequestLine()) && isEndToEndHeaderSubset(r1,
                r2));
    }
}