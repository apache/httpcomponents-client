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
package org.apache.hc.client5.http.cache;

/**
 * Records static constants for caching directives.
 *
 * @since 4.1
 */
public class HeaderConstants {

    /**
     * @deprecated Use {@link org.apache.hc.core5.http.Method}
     */
    @Deprecated
    public static final String GET_METHOD = "GET";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.Method}
     */
    @Deprecated
    public static final String HEAD_METHOD = "HEAD";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.Method}
     */
    @Deprecated
    public static final String OPTIONS_METHOD = "OPTIONS";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.Method}
     */
    @Deprecated
    public static final String PUT_METHOD = "PUT";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.Method}
     */
    @Deprecated
    public static final String DELETE_METHOD = "DELETE";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.Method}
     */
    @Deprecated
    public static final String TRACE_METHOD = "TRACE";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.Method}
     */
    @Deprecated
    public static final String POST_METHOD = "POST";

    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String LAST_MODIFIED = "Last-Modified";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String IF_MATCH = "If-Match";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String IF_RANGE = "If-Range";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String IF_NONE_MATCH = "If-None-Match";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String PRAGMA = "Pragma";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String MAX_FORWARDS = "Max-Forwards";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String ETAG = "ETag";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String EXPIRES = "Expires";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String AGE = "Age";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String VARY = "Vary";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String ALLOW = "Allow";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String VIA = "Via";
    /**
     * @deprecated Use {@link #CACHE_CONTROL_PUBLIC}
     */
    @Deprecated
    public static final String PUBLIC = "public";
    /**
     * @deprecated Use {@link #CACHE_CONTROL_PRIVATE}
     */
    @Deprecated
    public static final String PRIVATE = "private";

    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String CACHE_CONTROL_PUBLIC = "public";
    public static final String CACHE_CONTROL_PRIVATE = "private";
    public static final String CACHE_CONTROL_NO_STORE = "no-store";
    public static final String CACHE_CONTROL_NO_CACHE = "no-cache";
    public static final String CACHE_CONTROL_MAX_AGE = "max-age";
    public static final String CACHE_CONTROL_S_MAX_AGE = "s-maxage";
    public static final String CACHE_CONTROL_MAX_STALE = "max-stale";
    public static final String CACHE_CONTROL_MIN_FRESH = "min-fresh";
    public static final String CACHE_CONTROL_MUST_REVALIDATE = "must-revalidate";
    public static final String CACHE_CONTROL_PROXY_REVALIDATE = "proxy-revalidate";
    public static final String CACHE_CONTROL_STALE_IF_ERROR = "stale-if-error";
    public static final String CACHE_CONTROL_STALE_WHILE_REVALIDATE = "stale-while-revalidate";
    public static final String CACHE_CONTROL_ONLY_IF_CACHED = "only-if-cached";
    public static final String CACHE_CONTROL_MUST_UNDERSTAND = "must-understand";
    public static final String CACHE_CONTROL_IMMUTABLE= "immutable";
    /**
     * @deprecated Use {@link #CACHE_CONTROL_STALE_IF_ERROR}
     */
    @Deprecated
    public static final String STALE_IF_ERROR = "stale-if-error";
    /**
     * @deprecated Use {@link #CACHE_CONTROL_STALE_WHILE_REVALIDATE}
     */
    @Deprecated
    public static final String STALE_WHILE_REVALIDATE = "stale-while-revalidate";

    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String WARNING = "Warning";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String RANGE = "Range";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String CONTENT_RANGE = "Content-Range";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";
    /**
     * @deprecated Use {@link org.apache.hc.core5.http.HttpHeaders}
     */
    @Deprecated
    public static final String AUTHORIZATION = "Authorization";

}
