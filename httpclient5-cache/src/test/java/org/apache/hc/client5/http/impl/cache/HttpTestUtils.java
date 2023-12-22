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
package org.apache.hc.client5.http.impl.cache;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.jupiter.api.Assertions;

public class HttpTestUtils {

    /*
     * Assertions.asserts that two request or response bodies are byte-equivalent.
     */
    public static boolean equivalent(final HttpEntity e1, final HttpEntity e2) throws Exception {
        final InputStream i1 = e1.getContent();
        final InputStream i2 = e2.getContent();
        if (i1 == null && i2 == null) {
            return true;
        }
        if (i1 == null || i2 == null) {
            return false; // avoid possible NPEs below
        }
        int b1 = -1;
        while ((b1 = i1.read()) != -1) {
            if (b1 != i2.read()) {
                return false;
            }
        }
        return (-1 == i2.read());
    }

    /*
     * Retrieves the full header value by combining multiple headers and
     * separating with commas, canonicalizing whitespace along the way.
     */
    public static String getCanonicalHeaderValue(final HttpMessage r, final String name) {
        final int n = r.countHeaders(name);
        r.getFirstHeader(name);
        if (n == 0) {
            return null;
        } else if (n == 1) {
            final Header h = r.getFirstHeader(name);
            return h != null ? h.getValue() : null;
        } else {
            final StringBuilder buf = new StringBuilder();
            for (final Iterator<Header> it = r.headerIterator(name); it.hasNext(); ) {
                if (buf.length() > 0) {
                    buf.append(", ");
                }
                final Header header = it.next();
                if (header != null) {
                    buf.append(header.getValue().trim());
                }
            }
            return buf.toString();
        }
    }

    /*
     * Assertions.asserts that all the headers appearing in r1 also appear in r2
     * with the same canonical header values.
     */
    public static boolean isEndToEndHeaderSubset(final HttpMessage r1, final HttpMessage r2) {
        for (final Header h : r1.getHeaders()) {
            if (!MessageSupport.isHopByHop(h.getName())) {
                final String r1val = getCanonicalHeaderValue(r1, h.getName());
                final String r2val = getCanonicalHeaderValue(r2, h.getName());
                if (!Objects.equals(r1val, r2val)) {
                    return false;
                }
            }
        }
        return true;
    }

    /*
     * Assertions.asserts that message {@code r2} represents exactly the same
     * message as {@code r1}, except for hop-by-hop headers. "When a cache
     * is semantically transparent, the client receives exactly the same
     * response (except for hop-by-hop headers) that it would have received had
     * its request been handled directly by the origin server."
     */
    public static boolean semanticallyTransparent(
            final ClassicHttpResponse r1, final ClassicHttpResponse r2) throws Exception {
        final boolean statusLineEquivalent = Objects.equals(r1.getReasonPhrase(), r2.getReasonPhrase())
                && r1.getCode() == r2.getCode();
        if (!statusLineEquivalent) {
            return false;
        }
        final boolean headerEquivalent = isEndToEndHeaderSubset(r1, r2);
        if (!headerEquivalent) {
            return false;
        }
        final boolean entityEquivalent = equivalent(r1.getEntity(), r2.getEntity());
        if (!entityEquivalent) {
            return false;
        }
        return true;
    }

    /* Assertions.asserts that protocol versions equivalent. */
    public static boolean equivalent(final ProtocolVersion v1, final ProtocolVersion v2) {
        return Objects.equals(v1 != null ? v1 : HttpVersion.DEFAULT, v2 != null ? v2 : HttpVersion.DEFAULT );
    }

    /* Assertions.asserts that two requests are morally equivalent. */
    public static boolean equivalent(final HttpRequest r1, final HttpRequest r2) {
        return equivalent(r1.getVersion(), r2.getVersion()) &&
                Objects.equals(r1.getMethod(), r2.getMethod()) &&
                Objects.equals(r1.getRequestUri(), r2.getRequestUri()) &&
                isEndToEndHeaderSubset(r1, r2);
    }

    /* Assertions.asserts that two requests are morally equivalent. */
    public static boolean equivalent(final HttpResponse r1, final HttpResponse r2) {
        return equivalent(r1.getVersion(), r2.getVersion()) &&
                r1.getCode() == r2.getCode() &&
                Objects.equals(r1.getReasonPhrase(), r2.getReasonPhrase()) &&
                isEndToEndHeaderSubset(r1, r2);
    }

    public static byte[] makeRandomBytes(final int nbytes) {
        final byte[] bytes = new byte[nbytes];
        new Random().nextBytes(bytes);
        return bytes;
    }

    public static Resource makeRandomResource(final int nbytes) {
        final byte[] bytes = new byte[nbytes];
        new Random().nextBytes(bytes);
        return new HeapResource(bytes);
    }

    public static Resource makeNullResource() {
        return null;
    }

    public static ByteArrayBuffer makeRandomBuffer(final int nbytes) {
        final ByteArrayBuffer buf = new ByteArrayBuffer(nbytes);
        buf.setLength(nbytes);
        new Random().nextBytes(buf.array());
        return buf;
    }

    /** Generates a response body with random content.
     *  @param nbytes length of the desired response body
     *  @return an {@link HttpEntity}
     */
    public static HttpEntity makeBody(final int nbytes) {
        return new ByteArrayEntity(makeRandomBytes(nbytes), null);
    }

    public static HeaderGroup headers(final Header... headers) {
        final HeaderGroup headerGroup = new HeaderGroup();
        if (headers != null && headers.length > 0) {
            headerGroup.setHeaders(headers);
        }
        return headerGroup;
    }

    public static HttpCacheEntry makeCacheEntry(final Instant requestDate,
                                                final Instant responseDate,
                                                final Method method,
                                                final String requestUri,
                                                final Header[] requestHeaders,
                                                final int status,
                                                final Header[] responseHeaders,
                                                final Collection<String> variants) {
        return new HttpCacheEntry(
                requestDate,
                responseDate,
                method.name(), requestUri, headers(requestHeaders),
                status, headers(responseHeaders),
                null,
                variants);
    }

    public static HttpCacheEntry makeCacheEntry(final Instant requestDate,
                                                final Instant responseDate,
                                                final Method method,
                                                final String requestUri,
                                                final Header[] requestHeaders,
                                                final int status,
                                                final Header[] responseHeaders,
                                                final Resource resource) {
        return new HttpCacheEntry(
                requestDate,
                responseDate,
                method.name(), requestUri, headers(requestHeaders),
                status, headers(responseHeaders),
                resource,
                null);
    }

    public static HttpCacheEntry makeCacheEntry(final Instant requestDate,
                                                final Instant responseDate,
                                                final int status,
                                                final Header[] responseHeaders,
                                                final Collection<String> variants) {
        return makeCacheEntry(
                requestDate,
                responseDate,
                Method.GET, "/", null,
                status, responseHeaders,
                variants);
    }

    public static HttpCacheEntry makeCacheEntry(final Instant requestDate,
                                                final Instant responseDate,
                                                final int status,
                                                final Header[] responseHeaders,
                                                final Resource resource) {
        return makeCacheEntry(
                requestDate,
                responseDate,
                Method.GET, "/", null,
                status, responseHeaders,
                resource);
    }

    public static Header[] getStockHeaders(final Instant when) {
        return new Header[] {
                new BasicHeader("Date", DateUtils.formatStandardDate(when)),
                new BasicHeader("Server", "MockServer/1.0")
        };
    }

    public static HttpCacheEntry makeCacheEntry(final Instant requestDate, final Instant responseDate) {
        final Duration diff = Duration.between(requestDate, responseDate);
        final Instant when = requestDate.plusMillis(diff.toMillis() / 2);
        return makeCacheEntry(requestDate, responseDate, getStockHeaders(when));
    }

    public static HttpCacheEntry makeCacheEntry(final Instant requestDate,
                                                final Instant responseDate,
                                                final Header[] headers,
                                                final byte[] bytes) {
        return makeCacheEntry(requestDate, responseDate, HttpStatus.SC_OK, headers, new HeapResource(bytes));
    }

    public static HttpCacheEntry makeCacheEntry(final Instant requestDate,
                                                final Instant responseDate,
                                                final Header... headers) {
        return makeCacheEntry(requestDate, responseDate, headers, makeRandomBytes(128));
    }

    public static HttpCacheEntry makeCacheEntry(final Instant requestDate,
                                                final Instant responseDate,
                                                final Header[] headers,
                                                final Collection<String> variants) {
        return makeCacheEntry(requestDate, responseDate, Method.GET, "/", null, HttpStatus.SC_OK, headers, variants);
    }

    public static HttpCacheEntry makeCacheEntry(final Collection<String> variants) {
        final Instant now = Instant.now();
        return makeCacheEntry(now, now, new Header[] {}, variants);
    }

    public static HttpCacheEntry makeCacheEntry(final Header[] headers, final byte[] bytes) {
        final Instant now = Instant.now();
        return makeCacheEntry(now, now, headers, bytes);
    }

    public static HttpCacheEntry makeCacheEntry(final byte[] bytes) {
        final Instant now = Instant.now();
        return makeCacheEntry(getStockHeaders(now), bytes);
    }

    public static HttpCacheEntry makeCacheEntry(final Header... headers) {
        return makeCacheEntry(headers, makeRandomBytes(128));
    }

    public static HttpCacheEntry makeCacheEntry() {
        final Instant now = Instant.now();
        return makeCacheEntry(now, now);
    }

    public static ClassicHttpResponse make200Response() {
        final ClassicHttpResponse out = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        out.setHeader("Date", DateUtils.formatStandardDate(Instant.now()));
        out.setHeader("Server", "MockOrigin/1.0");
        out.setHeader("Content-Length", "128");
        out.setEntity(makeBody(128));
        return out;
    }

    public static final ClassicHttpResponse make200Response(final Instant date, final String cacheControl) {
        final ClassicHttpResponse response = HttpTestUtils.make200Response();
        response.setHeader("Date", DateUtils.formatStandardDate(date));
        response.setHeader("Cache-Control",cacheControl);
        response.setHeader("Etag","\"etag\"");
        return response;
    }

    public static ClassicHttpResponse make304Response() {
        return new BasicClassicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not modified");
    }

    public static ClassicHttpRequest makeDefaultRequest() {
        return new BasicClassicHttpRequest(Method.GET.toString(), "/");
    }

    public static ClassicHttpRequest makeDefaultHEADRequest() {
        return new BasicClassicHttpRequest(Method.HEAD.toString(), "/");
    }

    public static ClassicHttpResponse make500Response() {
        return new BasicClassicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
    }

    public static <T> FutureCallback<T> countDown(final CountDownLatch latch, final Consumer<T> consumer) {
        return new FutureCallback<T>() {

            @Override
            public void completed(final T result) {
                if (consumer != null) {
                    consumer.accept(result);
                }
                latch.countDown();
            }

            @Override
            public void failed(final Exception ex) {
                latch.countDown();
                Assertions.fail(ex);
            }

            @Override
            public void cancelled() {
                latch.countDown();
                Assertions.fail("Unexpected cancellation");
            }

        };

    }

    public static <T> FutureCallback<T> countDown(final CountDownLatch latch) {
        return countDown(latch, null);
    }

}
