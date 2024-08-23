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

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * A generic {@link HttpClientResponseHandler} that works with the response entity
 * for successful (2xx) responses. If the response code was &gt;= 300, the response
 * body is consumed and an {@link HttpResponseException} is thrown.
 * <p>
 * If this is used with
 * {@link org.apache.hc.client5.http.classic.HttpClient#execute(
 * org.apache.hc.core5.http.ClassicHttpRequest, HttpClientResponseHandler)},
 * HttpClient may handle redirects (3xx responses) internally.
 * </p>
 *
 * @param <T> the type of the value determined by the response.
 * @since 4.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public abstract class AbstractHttpClientResponseHandler<T> implements HttpClientResponseHandler<T> {

    /**
     * Read the entity from the response body and pass it to the entity handler
     * method if the response was successful (a 2xx status code). If no response
     * body exists, this returns null. If the response was unsuccessful (&gt;= 300
     * status code), throws an {@link HttpResponseException}.
     */
    @Override
    public T handleResponse(final ClassicHttpResponse response) throws IOException {
        final HttpEntity entity = response.getEntity();
        if (response.getCode() >= HttpStatus.SC_REDIRECTION) {
            EntityUtils.consume(entity);
            throw new HttpResponseException(response.getCode(), response.getReasonPhrase());
        }
        return entity == null ? null : handleEntity(entity);
    }

    /**
     * Handle the response entity and transform it into the actual response
     * object.
     */
    public abstract T handleEntity(HttpEntity entity) throws IOException;

}
