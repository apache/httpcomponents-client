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

package org.apache.hc.client5.http.async.methods;

import java.util.Arrays;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.support.AbstractResponseBuilder;
import org.apache.hc.core5.util.Args;

/**
 * Builder for {@link SimpleHttpResponse} instances.
 *
 * @since 5.1
 */
public class SimpleResponseBuilder extends AbstractResponseBuilder<SimpleHttpResponse> {

    private SimpleBody body;

    SimpleResponseBuilder(final int status) {
        super(status);
    }

    public static SimpleResponseBuilder create(final int status) {
        Args.checkRange(status, 100, 599, "HTTP status code");
        return new SimpleResponseBuilder(status);
    }

    public static SimpleResponseBuilder copy(final SimpleHttpResponse response) {
        Args.notNull(response, "HTTP response");
        final SimpleResponseBuilder builder = new SimpleResponseBuilder(response.getCode());
        builder.digest(response);
        return builder;
    }

    protected void digest(final SimpleHttpResponse response) {
        super.digest(response);
        setBody(response.getBody());
    }

    @Override
    public SimpleResponseBuilder setVersion(final ProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public SimpleResponseBuilder setHeaders(final Header... headers) {
        super.setHeaders(headers);
        return this;
    }

    @Override
    public SimpleResponseBuilder addHeader(final Header header) {
        super.addHeader(header);
        return this;
    }

    @Override
    public SimpleResponseBuilder addHeader(final String name, final    String value) {
        super.addHeader(name, value);
        return this;
    }

    @Override
    public SimpleResponseBuilder removeHeader(final Header header) {
        super.removeHeader(header);
        return this;
    }

    @Override
    public SimpleResponseBuilder removeHeaders(final String name) {
        super.removeHeaders(name);
        return this;
    }

    @Override
    public SimpleResponseBuilder setHeader(final Header header) {
        super.setHeader(header);
        return this;
    }

    @Override
    public SimpleResponseBuilder setHeader(final String name, final String value) {
        super.setHeader(name, value);
        return this;
    }

    public SimpleBody getBody() {
        return body;
    }

    public SimpleResponseBuilder setBody(final SimpleBody body) {
        this.body = body;
        return this;
    }

    public SimpleResponseBuilder setBody(final String content, final ContentType contentType) {
        this.body = SimpleBody.create(content, contentType);
        return this;
    }

    public SimpleResponseBuilder setBody(final byte[] content, final ContentType contentType) {
        this.body = SimpleBody.create(content, contentType);
        return this;
    }

    public SimpleHttpResponse build() {
        final SimpleHttpResponse result = new SimpleHttpResponse(getStatus());
        result.setVersion(getVersion());
        result.setHeaders(getHeaders());
        result.setBody(body);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("SimpleResponseBuilder [status=");
        builder.append(getStatus());
        builder.append(", headerGroup=");
        builder.append(Arrays.toString(getHeaders()));
        builder.append(", body=");
        builder.append(body);
        builder.append("]");
        return builder.toString();
    }

}
