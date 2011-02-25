/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.protocol;

import java.io.IOException;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.entity.DeflateDecompressingEntity;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.protocol.HttpContext;

/**
 * {@link HttpResponseInterceptor} responsible for processing Content-Encoding
 * responses.
 * <p>
 * Instances of this class are stateless and immutable, therefore threadsafe.
 *
 * @since 4.1
 *
 */
@Immutable
public class ResponseContentEncoding implements HttpResponseInterceptor {

    /**
     * Handles the following {@code Content-Encoding}s by
     * using the appropriate decompressor to wrap the response Entity:
     * <ul>
     * <li>gzip - see {@link GzipDecompressingEntity}</li>
     * <li>deflate - see {@link DeflateDecompressingEntity}</li>
     * <li>identity - no action needed</li>
     * </ul>
     *
     * @param response the response which contains the entity
     * @param  context not currently used
     *
     * @throws HttpException if the {@code Content-Encoding} is none of the above
     */
    public void process(
            final HttpResponse response,
            final HttpContext context) throws HttpException, IOException {
        HttpEntity entity = response.getEntity();

        // It wasn't a 304 Not Modified response, 204 No Content or similar
        if (entity != null) {
            Header ceheader = entity.getContentEncoding();
            if (ceheader != null) {
                HeaderElement[] codecs = ceheader.getElements();
                for (HeaderElement codec : codecs) {
                    String codecname = codec.getName().toLowerCase(Locale.US);
                    if ("gzip".equals(codecname) || "x-gzip".equals(codecname)) {
                        response.setEntity(new GzipDecompressingEntity(response.getEntity()));
                        return;
                    } else if ("deflate".equals(codecname)) {
                        response.setEntity(new DeflateDecompressingEntity(response.getEntity()));
                        return;
                    } else if ("identity".equals(codecname)) {

                        /* Don't need to transform the content - no-op */
                        return;
                    } else {
                        throw new HttpException("Unsupported Content-Coding: " + codec.getName());
                    }
                }
            }
        }
    }

}
