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
package org.apache.hc.client5.http.fluent;

import java.io.IOException;

import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * {@link org.apache.hc.core5.http.io.HttpClientResponseHandler} implementation
 * that converts {@link org.apache.hc.core5.http.HttpResponse} messages
 * to {@link Content} instances.
 *
 * @see Content
 *
 * @since 4.4
 */
public class ContentResponseHandler extends AbstractHttpClientResponseHandler<Content> {

    @Override
    public Content handleEntity(final HttpEntity entity) throws IOException {
        return entity != null ?
                new Content(EntityUtils.toByteArray(entity), ContentType.parse(entity.getContentType())) :
                Content.NO_CONTENT;
    }

}
