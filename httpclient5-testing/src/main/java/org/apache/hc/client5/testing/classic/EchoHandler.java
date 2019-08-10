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

package org.apache.hc.client5.testing.classic;

import java.io.IOException;
import java.util.Locale;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * A handler that echos the incoming request entity.
 */
public class EchoHandler implements HttpRequestHandler {

    /**
     * Handles a request by echoing the incoming request entity.
     * If there is no request entity, an empty document is returned.
     *
     * @param request   the request
     * @param response  the response
     * @param context   the context
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     */
    @Override
    public void handle(final ClassicHttpRequest request,
                       final ClassicHttpResponse response,
                       final HttpContext context)
        throws HttpException, IOException {

        final String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (!"GET".equals(method) &&
                !"HEAD".equals(method) &&
                !"POST".equals(method) &&
                !"PUT".equals(method)) {
            throw new MethodNotSupportedException(method + " not supported by " + getClass().getName());
        }

        HttpEntity entity = request.getEntity();

        // For some reason, just putting the incoming entity into
        // the response will not work. We have to buffer the message.
        final byte[] data;
        final ContentType contentType;
        if (entity == null) {
            data = new byte [0];
            contentType = null;
        } else {
            data = EntityUtils.toByteArray(entity);
            final String contentTypeStr = entity.getContentType();
            contentType = contentTypeStr == null ? null : ContentType.parse(contentTypeStr);
        }

        final ByteArrayEntity bae = new ByteArrayEntity(data, contentType);
        entity = bae;

        response.setCode(HttpStatus.SC_OK);
        response.setEntity(entity);
    }

}
