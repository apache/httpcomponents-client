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

package org.apache.hc.client5.http.async.methods;

import java.net.URI;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;

/**
 * Common HTTP methods using {@link SimpleHttpRequest} as a HTTP request message representation.
 *
 * @since 5.0
 */
public enum SimpleHttpRequests {

    DELETE {
        @Override
        public SimpleHttpRequest create(final URI uri) {
            return new SimpleHttpRequest(Method.DELETE, uri);
        }

        @Override
        public SimpleHttpRequest create(final HttpHost host, final String path) {
            return new SimpleHttpRequest(Method.DELETE, host, path);
        }
    },

    GET {
        @Override
        public SimpleHttpRequest create(final URI uri) {
            return new SimpleHttpRequest(Method.GET, uri);
        }

        @Override
        public SimpleHttpRequest create(final HttpHost host, final String path) {
            return new SimpleHttpRequest(Method.GET, host, path);
        }
    },

    HEAD {
        @Override
        public SimpleHttpRequest create(final URI uri) {
            return new SimpleHttpRequest(Method.HEAD, uri);
        }

        @Override
        public SimpleHttpRequest create(final HttpHost host, final String path) {
            return new SimpleHttpRequest(Method.HEAD, host, path);
        }
    },

    OPTIONS {
        @Override
        public SimpleHttpRequest create(final URI uri) {
            return new SimpleHttpRequest(Method.OPTIONS, uri);
        }

        @Override
        public SimpleHttpRequest create(final HttpHost host, final String path) {
            return new SimpleHttpRequest(Method.OPTIONS, host, path);
        }
    },

    PATCH {
        @Override
        public SimpleHttpRequest create(final URI uri) {
            return new SimpleHttpRequest(Method.PATCH, uri);
        }

        @Override
        public SimpleHttpRequest create(final HttpHost host, final String path) {
            return new SimpleHttpRequest(Method.PATCH, host, path);
        }
    },

    POST {
        @Override
        public SimpleHttpRequest create(final URI uri) {
            return new SimpleHttpRequest(Method.POST, uri);
        }

        @Override
        public SimpleHttpRequest create(final HttpHost host, final String path) {
            return new SimpleHttpRequest(Method.POST, host, path);
        }
    },

    PUT {
        @Override
        public SimpleHttpRequest create(final URI uri) {
            return new SimpleHttpRequest(Method.PUT, uri);
        }

        @Override
        public SimpleHttpRequest create(final HttpHost host, final String path) {
            return new SimpleHttpRequest(Method.PUT, host, path);
        }
    },

    TRACE {
        @Override
        public SimpleHttpRequest create(final URI uri) {
            return new SimpleHttpRequest(Method.TRACE, uri);
        }

        @Override
        public SimpleHttpRequest create(final HttpHost host, final String path) {
            return new SimpleHttpRequest(Method.TRACE, host, path);
        }
    };

    /**
     * Creates a request object of the exact subclass of {@link SimpleHttpRequest}.
     *
     * @param uri a non-null URI String.
     * @return a new subclass of SimpleHttpRequest
     */
    public SimpleHttpRequest create(final String uri) {
        return create(URI.create(uri));
    }

    /**
     * Creates a request object of the exact subclass of {@link SimpleHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new subclass of SimpleHttpRequest
     */
    public abstract SimpleHttpRequest create(URI uri);

    /**
     * Creates a request object of the exact subclass of {@link SimpleHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new subclass of SimpleHttpRequest
     */
    public abstract SimpleHttpRequest create(final HttpHost host, final String path);

}
