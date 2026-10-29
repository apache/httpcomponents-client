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
package org.apache.hc.client5.http.impl;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.net.PercentCodec;
import org.apache.hc.core5.net.URIBuilder;

/**
 * Protocol support methods. For internal use only.
 *
 * @since 5.2
 */
@Internal
public final class RequestSupport {

    /**
     * Extracts the path prefix (the path component up to and including the last segment
     * separator) of the request, with dot segments removed and percent encoding normalized.
     * The query and the fragment are ignored.
     */
    public static String extractPathPrefix(final HttpRequest request) {
        final String path = request.getPath();
        final int pathEnd = findPathEnd(path);
        final String prefix = extractNormalizedPathPrefix(path, pathEnd);
        if (prefix != null) {
            return prefix;
        }
        if (pathEnd < path.length()) {
            try {
                // Avoid parsing and decoding the query
                return normalizePathPrefix(new URIBuilder(path.substring(0, pathEnd)));
            } catch (final URISyntaxException ignore) {
                // The path alone may not be a valid URI reference (such as "//" from "//?query")
            }
        }
        try {
            return normalizePathPrefix(new URIBuilder(path));
        } catch (final URISyntaxException ex) {
            return path;
        }
    }

    private static String normalizePathPrefix(final URIBuilder uriBuilder) {
        uriBuilder.setFragment(null);
        uriBuilder.clearParameters();
        uriBuilder.optimize();
        final List<String> pathSegments = uriBuilder.getPathSegments();

        if (!pathSegments.isEmpty()) {
            pathSegments.remove(pathSegments.size() - 1);
        }
        if (pathSegments.isEmpty()) {
            return "/";
        }
        final StringBuilder buf = new StringBuilder();
        buf.append('/');
        for (final String pathSegment : pathSegments) {
            PercentCodec.encode(buf, pathSegment, StandardCharsets.US_ASCII);
            buf.append('/');
        }
        return buf.toString();
    }

    /**
     * Returns the index of the first query or fragment delimiter, or the length of the path if there is none.
     */
    private static int findPathEnd(final String path) {
        for (int i = 0; i < path.length(); i++) {
            final char ch = path.charAt(i);
            if (ch == '?' || ch == '#') {
                return i;
            }
        }
        return path.length();
    }

    /**
     * Returns the path prefix without decoding and re-encoding the path if the path is already
     * in normalized form: it starts with a separator, has no empty or dot segments other than
     * a trailing empty segment, and consists of unreserved characters only. Returns {@code null}
     * otherwise.
     */
    private static String extractNormalizedPathPrefix(final String path, final int pathEnd) {
        if (pathEnd == 0 || path.charAt(0) != '/') {
            return null;
        }
        int lastSeparator = 0;
        int segmentStart = 1;
        for (int i = 1; i <= pathEnd; i++) {
            final char ch = i < pathEnd ? path.charAt(i) : '/';
            if (ch == '/') {
                final int segmentLength = i - segmentStart;
                if (segmentLength == 0 && i < pathEnd) {
                    return null;
                }
                if (segmentLength == 1 && path.charAt(segmentStart) == '.') {
                    return null;
                }
                if (segmentLength == 2 && path.charAt(segmentStart) == '.' && path.charAt(segmentStart + 1) == '.') {
                    return null;
                }
                if (i < pathEnd) {
                    lastSeparator = i;
                }
                segmentStart = i + 1;
            } else if (!isUnreserved(ch)) {
                return null;
            }
        }
        return path.substring(0, lastSeparator + 1);
    }

    private static boolean isUnreserved(final char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9'
                || ch == '-' || ch == '.' || ch == '_' || ch == '~';
    }

}
