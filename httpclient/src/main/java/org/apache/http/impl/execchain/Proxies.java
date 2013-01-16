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
package org.apache.http.impl.execchain;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.methods.CloseableHttpResponse;

/**
 * Execution proxies for HTTP message objects.
 *
 * @since 4.3
 */
@NotThreadSafe
class Proxies {

    static void enhanceEntity(final HttpEntityEnclosingRequest request) {
        final HttpEntity entity = request.getEntity();
        if (entity != null && !entity.isRepeatable() && !isEnhanced(entity)) {
            final HttpEntity proxy = (HttpEntity) Proxy.newProxyInstance(
                    HttpEntity.class.getClassLoader(),
                    new Class<?>[] { HttpEntity.class },
                    new RequestEntityExecHandler(entity));
            request.setEntity(proxy);
        }
    }

    static boolean isEnhanced(final HttpEntity entity) {
        if (entity != null && Proxy.isProxyClass(entity.getClass())) {
            final InvocationHandler handler = Proxy.getInvocationHandler(entity);
            return handler instanceof RequestEntityExecHandler;
        } else {
            return false;
        }
    }

    static boolean isRepeatable(final HttpRequest request) {
        if (request instanceof HttpEntityEnclosingRequest) {
            final HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            if (entity != null) {
                if (isEnhanced(entity)) {
                    final RequestEntityExecHandler handler = (RequestEntityExecHandler)
                            Proxy.getInvocationHandler(entity);
                    if (!handler.isConsumed()) {
                        return true;
                    }
                }
                return entity.isRepeatable();
            }
        }
        return true;
    }

    public static CloseableHttpResponse enhanceResponse(
            final HttpResponse original,
            final ConnectionHolder connHolder) {
        return (CloseableHttpResponse) Proxy.newProxyInstance(
                ResponseProxyHandler.class.getClassLoader(),
                new Class<?>[] { CloseableHttpResponse.class },
                new ResponseProxyHandler(original, connHolder));
    }

}
