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

    public static String extractPathPrefix(final HttpRequest request) {
        final String path = request.getPath();
        try {
            final URIBuilder uriBuilder = new URIBuilder(path);
            uriBuilder.setFragment(null);
            uriBuilder.clearParameters();
            uriBuilder.optimize();
            final List<String> pathSegments = uriBuilder.getPathSegments();

            if (!pathSegments.isEmpty()) {
                pathSegments.remove(pathSegments.size() - 1);
            }
            if (pathSegments.isEmpty()) {
                return "/";
            } else {
                final StringBuilder buf = new StringBuilder();
                buf.append('/');
                for (final String pathSegment : pathSegments) {
                    PercentCodec.encode(buf, pathSegment, StandardCharsets.US_ASCII);
                    buf.append('/');
                }
                return buf.toString();
            }
        } catch (final URISyntaxException ex) {
            return path;
        }
    }

}
