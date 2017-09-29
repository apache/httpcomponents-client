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

package org.apache.http.entity.mime;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal MIME field.
 *
 * @since 4.0
 */
public class MinimalField {

    private final String name;
    private final String value;
    private Map<MIME.HeaderFieldParam, String> parameters;

    public MinimalField(final String name, final String value) {
        super();
        this.name = name;
        this.value = value;
        this.parameters = new TreeMap<MIME.HeaderFieldParam, String>();
    }

    public MinimalField(final String name, final String value, final Map<MIME.HeaderFieldParam, String> parameters) {
        this.name = name;
        this.value = value;
        this.parameters = new TreeMap<MIME.HeaderFieldParam, String>(parameters);
    }

    public MinimalField(final MinimalField from) {
        this(from.name, from.value, from.parameters);
    }

    public String getName() {
        return this.name;
    }

    public String getBody() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.value);
        for (final Iterator<Map.Entry<MIME.HeaderFieldParam, String>> it = this.parameters.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<MIME.HeaderFieldParam, String> next = it.next();
            sb.append("; ");
            sb.append(next.getKey().getName());
            sb.append("=\"");
            sb.append(next.getValue());
            sb.append("\"");
        }
        return sb.toString();
    }

    public Map<MIME.HeaderFieldParam, String> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append(this.name);
        buffer.append(": ");
        buffer.append(this.getBody());
        return buffer.toString();
    }

}
