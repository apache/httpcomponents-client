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

package org.apache.hc.client5.http.classic.methods;

import java.net.URI;


/**
 * Common HTTP methods using {@link HttpUriRequest} as a HTTP request message representation.
 * <p>
 * Each static method creates a request object of the exact subclass of {@link HttpUriRequest}
 * with a non-null URI.
 *
 * @since 5.0
 */
public final class ClassicHttpRequests {

    public static HttpUriRequest delete(final String uri) {
        return delete(URI.create(uri));
    }

    public static HttpUriRequest delete(final URI uri) {
        return new HttpDelete(uri);
    }

    public static HttpUriRequest get(final String uri) {
        return get(URI.create(uri));
    }

    public static HttpUriRequest get(final URI uri) {
        return new HttpGet(uri);
    }

    public static HttpUriRequest head(final String uri) {
        return head(URI.create(uri));
    }

    public static HttpUriRequest head(final URI uri) {
        return new HttpHead(uri);
    }

    public static HttpUriRequest options(final String uri) {
        return options(URI.create(uri));
    }

    public static HttpUriRequest options(final URI uri) {
        return new HttpOptions(uri);
    }

    public static HttpUriRequest patch(final String uri) {
        return patch(URI.create(uri));
    }

    public static HttpUriRequest patch(final URI uri) {
        return new HttpPatch(uri);
    }

    public static HttpUriRequest post(final String uri) {
        return post(URI.create(uri));
    }

    public static HttpUriRequest post(final URI uri) {
        return new HttpPost(uri);
    }

    public static HttpUriRequest put(final String uri) {
        return put(URI.create(uri));
    }

    public static HttpUriRequest put(final URI uri) {
        return new HttpPut(uri);
    }

    public static HttpUriRequest trace(final String uri) {
        return trace(URI.create(uri));
    }

    public static HttpUriRequest trace(final URI uri) {
        return new HttpTrace(uri);
    }

}
