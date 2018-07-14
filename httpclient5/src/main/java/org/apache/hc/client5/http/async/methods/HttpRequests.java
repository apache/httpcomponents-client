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

import org.apache.hc.client5.http.StandardMethods;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHttpRequest;

/**
 * HTTP methods defined in RFC2616.
 *
 * @since 5.0-beta2
 */
public enum HttpRequests {

    DELETE {
        @Override
        public BasicHttpRequest create(final URI uri) {
            return new BasicHttpRequest(StandardMethods.DELETE.name(), uri);
        }

        @Override
        public BasicHttpRequest create(final HttpHost host, final String path) {
            return new BasicHttpRequest(StandardMethods.DELETE.name(), host, path);
        }
    },

    GET {
        @Override
        public BasicHttpRequest create(final URI uri) {
            return new BasicHttpRequest(StandardMethods.GET.name(), uri);
        }

        @Override
        public BasicHttpRequest create(final HttpHost host, final String path) {
            return new BasicHttpRequest(StandardMethods.GET.name(), host, path);
        }
    },

    HEAD {
        @Override
        public BasicHttpRequest create(final URI uri) {
            return new BasicHttpRequest(StandardMethods.HEAD.name(), uri);
        }

        @Override
        public BasicHttpRequest create(final HttpHost host, final String path) {
            return new BasicHttpRequest(StandardMethods.HEAD.name(), host, path);
        }
    },

    OPTIONS {
        @Override
        public BasicHttpRequest create(final URI uri) {
            return new BasicHttpRequest(StandardMethods.OPTIONS.name(), uri);
        }

        @Override
        public BasicHttpRequest create(final HttpHost host, final String path) {
            return new BasicHttpRequest(StandardMethods.OPTIONS.name(), host, path);
        }
    },

    PATCH {
        @Override
        public BasicHttpRequest create(final URI uri) {
            return new BasicHttpRequest(StandardMethods.PATCH.name(), uri);
        }

        @Override
        public BasicHttpRequest create(final HttpHost host, final String path) {
            return new BasicHttpRequest(StandardMethods.PATCH.name(), host, path);
        }
    },

    POST {
        @Override
        public BasicHttpRequest create(final URI uri) {
            return new BasicHttpRequest(StandardMethods.POST.name(), uri);
        }

        @Override
        public BasicHttpRequest create(final HttpHost host, final String path) {
            return new BasicHttpRequest(StandardMethods.POST.name(), host, path);
        }
    },

    PUT {
        @Override
        public BasicHttpRequest create(final URI uri) {
            return new BasicHttpRequest(StandardMethods.PUT.name(), uri);
        }

        @Override
        public BasicHttpRequest create(final HttpHost host, final String path) {
            return new BasicHttpRequest(StandardMethods.PUT.name(), host, path);
        }
    },

    TRACE {
        @Override
        public BasicHttpRequest create(final URI uri) {
            return new BasicHttpRequest(StandardMethods.TRACE.name(), uri);
        }

        @Override
        public BasicHttpRequest create(final HttpHost host, final String path) {
            return new BasicHttpRequest(StandardMethods.TRACE.name(), host, path);
        }
    };

    /**
     * Creates a request object of the exact subclass of {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI String.
     * @return a new subclass of BasicHttpRequest
     */
    public BasicHttpRequest create(final String uri) {
        return create(URI.create(uri));
    }

    /**
     * Creates a request object of the exact subclass of {@link BasicHttpRequest}.
     *
     * @param uri a non-null URI.
     * @return a new subclass of BasicHttpRequest
     */
    public abstract BasicHttpRequest create(URI uri);

    /**
     * Creates a request object of the exact subclass of {@link BasicHttpRequest}.
     *
     * @param host HTTP host.
     * @param path request path.
     * @return a new subclass of BasicHttpRequest
     */
    public abstract BasicHttpRequest create(final HttpHost host, final String path);

}
