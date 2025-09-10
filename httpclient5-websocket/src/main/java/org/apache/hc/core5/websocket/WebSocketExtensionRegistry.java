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
package org.apache.hc.core5.websocket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.util.Args;

public final class WebSocketExtensionRegistry {

    private final Map<String, WebSocketExtensionFactory> factories;

    public WebSocketExtensionRegistry() {
        this.factories = new LinkedHashMap<>();
    }

    public WebSocketExtensionRegistry register(final WebSocketExtensionFactory factory) {
        Args.notNull(factory, "Extension factory");
        factories.put(factory.getName(), factory);
        return this;
    }

    public WebSocketExtensionNegotiation negotiate(
            final List<WebSocketExtensionData> requested,
            final boolean server) throws WebSocketException {
        final List<WebSocketExtension> extensions = new ArrayList<>();
        final List<WebSocketExtensionData> responseData = new ArrayList<>();
        if (requested != null) {
            for (final WebSocketExtensionData request : requested) {
                final WebSocketExtensionFactory factory = factories.get(request.getName());
                if (factory != null) {
                    final WebSocketExtension extension = factory.create(request, server);
                    if (extension != null) {
                        extensions.add(extension);
                        responseData.add(extension.getResponseData());
                    }
                }
            }
        }
        return new WebSocketExtensionNegotiation(extensions, responseData);
    }

    public static WebSocketExtensionRegistry createDefault() {
        return new WebSocketExtensionRegistry()
                .register(new PerMessageDeflateExtensionFactory());
    }
}
