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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.TextUtils;

public final class WebSocketExtensions {

    private WebSocketExtensions() {
    }

    public static List<WebSocketExtensionData> parse(final Header header) {
        final List<WebSocketExtensionData> extensions = new ArrayList<>();
        addOffers(header, extensions);
        return extensions;
    }

    /**
     * Parses and combines every {@code Sec-WebSocket-Extensions} header field. RFC 6455 permits the
     * header to be split across multiple fields and requires them to be treated as a single combined
     * value, so a client's fallback offers carried in a later field are examined as well.
     *
     * @since 5.7
     */
    public static List<WebSocketExtensionData> parse(final Iterator<Header> headers) {
        final List<WebSocketExtensionData> extensions = new ArrayList<>();
        if (headers != null) {
            while (headers.hasNext()) {
                addOffers(headers.next(), extensions);
            }
        }
        return extensions;
    }

    private static void addOffers(final Header header, final List<WebSocketExtensionData> extensions) {
        if (header == null) {
            return;
        }
        for (final HeaderElement element : MessageSupport.parseElements(header)) {
            final String name = element.getName();
            if (TextUtils.isBlank(name)) {
                continue;
            }
            final Map<String, String> params = new LinkedHashMap<>();
            boolean duplicateParameter = false;
            for (final NameValuePair param : element.getParameters()) {
                // RFC 7692 section 7.1: an extension parameter MUST NOT appear more than once in a
                // negotiation offer. A repeated parameter makes the whole offer invalid, so it is
                // dropped here and the extension is left un-negotiated rather than silently collapsed
                // into a single map entry.
                if (params.containsKey(param.getName())) {
                    duplicateParameter = true;
                    break;
                }
                params.put(param.getName(), param.getValue());
            }
            if (duplicateParameter) {
                continue;
            }
            extensions.add(new WebSocketExtensionData(name, params));
        }
    }
}
