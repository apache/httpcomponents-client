/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.fluent.header;

public class CacheControl {

    public static final String NAME = "Cache-Control";

    /*
     * cache-request-directive
     */
    public static final String NO_CACHE = "no-cache";
    public static final String NO_STORE = "no-store";
    public static final String MAX_STALE = "max-stale";
    public static final String NO_TRANSFORM = "no-transform";
    public static final String ONLY_IF_CACHED = "only-if-cached";

    public static String MAX_AGE(final long deltaSeconds) {
        return "max-age=" + deltaSeconds;
    }

    public static String MAX_STALE(final long deltaSeconds) {
        return MAX_STALE + "=" + deltaSeconds;
    }

    public static String MIN_FRESH(final long deltaSeconds) {
        return "min-fresh=" + deltaSeconds;
    }

    /*
     * cache-response-directive
     */
    public static final String PUBLIC = "public";
    public static final String PRIVATE = "private";
    public static final String MUST_REVALIDATE = "must-revalidate";
    public static final String PROXY_REVALIDATE = "proxy-revalidate";

    public static String S_MAXAGE(final long deltaSeconds) {
        return "s-maxage=" + deltaSeconds;
    }

}
