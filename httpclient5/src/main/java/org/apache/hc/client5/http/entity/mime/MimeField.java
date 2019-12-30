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

package org.apache.hc.client5.http.entity.mime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;

/**
 * Minimal MIME field.
 *
 * @since 4.0
 */
public class MimeField {

    private final String name;
    private final String value;
    private final List<NameValuePair> parameters;

    public MimeField(final String name, final String value) {
        super();
        this.name = name;
        this.value = value;
        this.parameters = Collections.emptyList();
    }

    /**
     * @since 4.6
     */
    public MimeField(final String name, final String value, final List<NameValuePair> parameters) {
        this.name = name;
        this.value = value;
        this.parameters = parameters != null ?
                Collections.unmodifiableList(new ArrayList<>(parameters)) : Collections.<NameValuePair>emptyList();
    }

    public MimeField(final MimeField from) {
        this(from.name, from.value, from.parameters);
    }

    public String getName() {
        return this.name;
    }

    /**
     * @since 4.6
     */
    public String getValue() {
        return this.value;
    }

    public String getBody() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.value);
        for (int i = 0; i < this.parameters.size(); i++) {
            final NameValuePair parameter = this.parameters.get(i);
            sb.append("; ");
            sb.append(parameter.getName());
            sb.append("=\"");
            sb.append(parameter.getValue());
            sb.append("\"");
        }
        return sb.toString();
    }

    public List<NameValuePair> getParameters() {
        return this.parameters;
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
