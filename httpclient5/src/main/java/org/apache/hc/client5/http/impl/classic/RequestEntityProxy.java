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
package org.apache.hc.client5.http.impl.classic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;

class RequestEntityProxy implements HttpEntity  {

    static void enhance(final ClassicHttpRequest request) {
        final HttpEntity entity = request.getEntity();
        if (entity != null && !entity.isRepeatable() && !isEnhanced(entity)) {
            request.setEntity(new RequestEntityProxy(entity));
        }
    }

    static boolean isEnhanced(final HttpEntity entity) {
        return entity instanceof RequestEntityProxy;
    }

    private final HttpEntity original;
    private boolean consumed = false;

    RequestEntityProxy(final HttpEntity original) {
        super();
        this.original = original;
    }

    public HttpEntity getOriginal() {
        return original;
    }

    public boolean isConsumed() {
        return consumed;
    }

    @Override
    public boolean isRepeatable() {
        if (!consumed) {
            return true;
        } else {
            return original.isRepeatable();
        }
    }

    @Override
    public boolean isChunked() {
        return original.isChunked();
    }

    @Override
    public long getContentLength() {
        return original.getContentLength();
    }

    @Override
    public String getContentType() {
        return original.getContentType();
    }

    @Override
    public String getContentEncoding() {
        return original.getContentEncoding();
    }

    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
        return original.getContent();
    }

    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        consumed = true;
        original.writeTo(outStream);
    }

    @Override
    public boolean isStreaming() {
        return original.isStreaming();
    }

    @Override
    public Supplier<List<? extends Header>> getTrailers() {
        return original.getTrailers();
    }

    @Override
    public Set<String> getTrailerNames() {
        return original.getTrailerNames();
    }

    @Override
    public void close() throws IOException {
        original.close();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RequestEntityProxy{");
        sb.append(original);
        sb.append('}');
        return sb.toString();
    }

}
