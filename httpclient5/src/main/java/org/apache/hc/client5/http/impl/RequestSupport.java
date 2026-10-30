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

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.net.PathSegmentCodec;
import org.apache.hc.core5.net.PercentCodec;

/**
 * Protocol support methods. For internal use only.
 *
 * @since 5.2
 */
@Internal
public final class RequestSupport {

    static final BitSet UNRESERVED = new BitSet(256);

    static {
        for (int i = 'a'; i <= 'z'; i++) {
            UNRESERVED.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            UNRESERVED.set(i);
        }
        // numeric characters
        for (int i = '0'; i <= '9'; i++) {
            UNRESERVED.set(i);
        }
        UNRESERVED.set('-');
        UNRESERVED.set('.');
        UNRESERVED.set('_');
        UNRESERVED.set('~');
    }

    /**
     * TODO To be replaced by method from core
     */
    static List<String> optimizePathSegments(final List<String> inputSegments) {
        if (!inputSegments.isEmpty()) {
            final LinkedList<String> outputSegments = new LinkedList<>();
            for (final String inputSegment : inputSegments) {
                if (!inputSegment.isEmpty() && !".".equals(inputSegment)) {
                    if ("..".equals(inputSegment)) {
                        if (!outputSegments.isEmpty()) {
                            outputSegments.removeLast();
                        }
                    } else {
                        outputSegments.addLast(inputSegment);
                    }
                }
            }
            if (!inputSegments.isEmpty()) {
                final String lastSegment = inputSegments.get(inputSegments.size() - 1);
                if (lastSegment.isEmpty() || lastSegment.equals("..") || lastSegment.equals(".")) {
                    outputSegments.addLast("");
                }
            }
            return outputSegments;
        } else {
            return Collections.singletonList("");
        }
    }

    public static String extractPathPrefix(final HttpRequest request) {
        String path = request.getPath();
        boolean hasSegments = false;
        boolean requiresOptimization = false;
        char previous = 0;
        for (int i = 0; i < path.length(); i++) {
            final char ch = path.charAt(i);
            if (ch == '?' || ch == '#') {
                path = path.substring(0, i);
                break;
            }
            if (ch == '/') {
                hasSegments = true;
                if (previous == '/') {
                    requiresOptimization = true;
                }
            }
            if (ch == '.' || ch != '/' && !UNRESERVED.get(ch)) {
                requiresOptimization = true;
            }
            previous = ch;
        }
        if (!requiresOptimization) {
            return hasSegments ? path.substring(0, path.lastIndexOf('/') + 1) : path;
        } else {
            final List<String> inputSegments = PathSegmentCodec.parsePathSegments(path, StandardCharsets.UTF_8);
            final List<String> outputSegments = optimizePathSegments(inputSegments);
            if (!outputSegments.isEmpty()) {
                outputSegments.remove(outputSegments.size() - 1);
            }
            if (outputSegments.isEmpty()) {
                return "/";
            }
            final StringBuilder buf = new StringBuilder();
            buf.append('/');
            for (final String pathSegment : outputSegments) {
                PercentCodec.encode(buf, pathSegment, StandardCharsets.UTF_8);
                buf.append('/');
            }
            return buf.toString();
        }
    }

}
