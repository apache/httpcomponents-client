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
        if (header == null) {
            return extensions;
        }
        for (final HeaderElement element : MessageSupport.parseElements(header)) {
            final String name = element.getName();
            if (TextUtils.isBlank(name)) {
                continue;
            }
            final Map<String, String> params = new LinkedHashMap<>();
            for (final NameValuePair param : element.getParameters()) {
                params.put(param.getName(), param.getValue());
            }
            extensions.add(new WebSocketExtensionData(name, params));
        }
        return extensions;
    }
}
