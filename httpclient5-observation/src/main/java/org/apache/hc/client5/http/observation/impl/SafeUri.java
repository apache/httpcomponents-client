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
package org.apache.hc.client5.http.observation.impl;

import org.apache.hc.core5.util.Args;

/**
 * Reduces a request URI to a form that is safe to use as a span name or a metric tag.
 * User info, query and fragment are removed, as they may carry credentials and are
 * unbounded in cardinality.
 *
 * @since 5.7
 */
public final class SafeUri {

    private SafeUri() {
    }

    /**
     * Returns the given request URI without user info, query and fragment.
     *
     * @param uri the absolute URI or request target.
     * @return the redacted URI.
     */
    public static String of(final String uri) {
        Args.notNull(uri, "URI");
        int end = uri.length();
        final int query = uri.indexOf('?');
        if (query >= 0) {
            end = query;
        }
        final int fragment = uri.indexOf('#');
        if (fragment >= 0 && fragment < end) {
            end = fragment;
        }
        final int authorityStart = authorityStart(uri, end);
        if (authorityStart >= 0) {
            int authorityEnd = uri.indexOf('/', authorityStart);
            if (authorityEnd < 0 || authorityEnd > end) {
                authorityEnd = end;
            }
            final int at = uri.lastIndexOf('@', authorityEnd - 1);
            if (at >= authorityStart) {
                return uri.substring(0, authorityStart) + uri.substring(at + 1, end);
            }
        }
        return uri.substring(0, end);
    }

    private static int authorityStart(final String uri, final int end) {
        if (end == 0 || !isAlpha(uri.charAt(0))) {
            return -1;
        }
        int i = 1;
        while (i < end && (isAlpha(uri.charAt(i)) || isDigit(uri.charAt(i)) || "+-.".indexOf(uri.charAt(i)) >= 0)) {
            i++;
        }
        return uri.startsWith("://", i) && i + 3 <= end ? i + 3 : -1;
    }

    private static boolean isAlpha(final char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z';
    }

    private static boolean isDigit(final char ch) {
        return ch >= '0' && ch <= '9';
    }

}
