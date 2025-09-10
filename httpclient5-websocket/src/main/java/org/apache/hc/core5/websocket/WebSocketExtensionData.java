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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

public final class WebSocketExtensionData {

    private final String name;
    private final Map<String, String> parameters;

    public WebSocketExtensionData(final String name, final Map<String, String> parameters) {
        this.name = Args.notBlank(name, "Extension name");
        if (parameters != null && !parameters.isEmpty()) {
            this.parameters = new LinkedHashMap<>(parameters);
        } else {
            this.parameters = Collections.emptyMap();
        }
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String format() {
        final StringBuilder buffer = new StringBuilder(name);
        for (final Map.Entry<String, String> entry : parameters.entrySet()) {
            buffer.append("; ").append(entry.getKey());
            if (!TextUtils.isBlank(entry.getValue())) {
                buffer.append('=').append(entry.getValue());
            }
        }
        return buffer.toString();
    }
}
