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

package org.apache.hc.client5.http.impl.async;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.protocol.UriPatternMatcher;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;

class AsyncPushConsumerRegistry {

    private final UriPatternMatcher<Supplier<AsyncPushConsumer>> primary;
    private final ConcurrentMap<String, UriPatternMatcher<Supplier<AsyncPushConsumer>>> hostMap;

    public AsyncPushConsumerRegistry() {
        this.primary = new UriPatternMatcher<>();
        this.hostMap = new ConcurrentHashMap<>();
    }

    private UriPatternMatcher<Supplier<AsyncPushConsumer>> getPatternMatcher(final String hostname) {
        if (hostname == null) {
            return primary;
        }
        final UriPatternMatcher<Supplier<AsyncPushConsumer>> hostMatcher = hostMap.get(hostname);
        if (hostMatcher != null) {
            return hostMatcher;
        }
        return primary;
    }

    public AsyncPushConsumer get(final HttpRequest request) {
        Args.notNull(request, "Request");
        final URIAuthority authority = request.getAuthority();
        final String key = authority != null ? authority.getHostName().toLowerCase(Locale.ROOT) : null;
        final UriPatternMatcher<Supplier<AsyncPushConsumer>> patternMatcher = getPatternMatcher(key);
        if (patternMatcher == null) {
            return null;
        }
        String path = request.getPath();
        final int i = path.indexOf('?');
        if (i != -1) {
            path = path.substring(0, i);
        }
        final Supplier<AsyncPushConsumer> supplier = patternMatcher.lookup(path);
        return supplier != null ? supplier.get() : null;
    }

    public void register(final String hostname, final String uriPattern, final Supplier<AsyncPushConsumer> supplier) {
        Args.notBlank(uriPattern, "URI pattern");
        Args.notNull(supplier, "Supplier");
        if (hostname == null) {
            primary.register(uriPattern, supplier);
        } else {
            final String key = hostname.toLowerCase(Locale.ROOT);
            UriPatternMatcher<Supplier<AsyncPushConsumer>> matcher = hostMap.get(key);
            if (matcher == null) {
                final UriPatternMatcher<Supplier<AsyncPushConsumer>> newMatcher = new UriPatternMatcher<>();
                matcher = hostMap.putIfAbsent(key, newMatcher);
                if (matcher == null) {
                    matcher = newMatcher;
                }
            }
            matcher.register(uriPattern, supplier);
        }
    }

}
