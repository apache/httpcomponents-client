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

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.message.MessageSupport;

@Internal
public final class CacheHeaderSupport {

    private final static Set<String> HOP_BY_HOP;

    static {
        final TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.add(HttpHeaders.CONNECTION);
        set.add(HttpHeaders.CONTENT_LENGTH);
        set.add(HttpHeaders.TRANSFER_ENCODING);
        set.add(HttpHeaders.HOST);
        set.add(HttpHeaders.KEEP_ALIVE);
        set.add(HttpHeaders.TE);
        set.add(HttpHeaders.UPGRADE);
        set.add(HttpHeaders.PROXY_AUTHORIZATION);
        set.add("Proxy-Authentication-Info");
        set.add(HttpHeaders.PROXY_AUTHENTICATE);
        HOP_BY_HOP = Collections.unmodifiableSet(set);
    }

    public static boolean isHopByHop(final String headerName) {
        if (headerName == null) {
            return false;
        }
        return HOP_BY_HOP.contains(headerName);
    }

    public static boolean isHopByHop(final Header header) {
        if (header == null) {
            return false;
        }
        return isHopByHop(header.getName());
    }

    /**
     * This method should be provided by the core
     */
    public static Set<String> hopByHopConnectionSpecific(final MessageHeaders headers) {
        final Header connectionHeader = headers.getFirstHeader(HttpHeaders.CONNECTION);
        final String connDirective = connectionHeader != null ? connectionHeader.getValue() : null;
        // Disregard most common 'Close' and 'Keep-Alive' tokens
        if (connDirective != null &&
                !connDirective.equalsIgnoreCase(HeaderElements.CLOSE) &&
                !connDirective.equalsIgnoreCase(HeaderElements.KEEP_ALIVE)) {
            final TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            result.addAll(HOP_BY_HOP);
            result.addAll(MessageSupport.parseTokens(connectionHeader));
            return result;
        } else {
            return HOP_BY_HOP;
        }
    }

}
