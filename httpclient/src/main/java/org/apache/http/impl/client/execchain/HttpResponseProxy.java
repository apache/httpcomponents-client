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

package org.apache.http.impl.client.execchain;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.methods.CloseableHttpResponse;

/**
 * A proxy class for {@link HttpResponse} that can be used to release client connection
 * associated with the original response.
 *
 * @since 4.3
 */
@NotThreadSafe
class HttpResponseProxy implements InvocationHandler {

    private final HttpResponse original;
    private final ConnectionReleaseTriggerImpl connReleaseTrigger;

    private HttpResponseProxy(
            final HttpResponse original,
            final ConnectionReleaseTriggerImpl connReleaseTrigger) {
        super();
        this.original = original;
        this.connReleaseTrigger = connReleaseTrigger;
        HttpEntity entity = original.getEntity();
        if (entity != null && entity.isStreaming() && connReleaseTrigger != null) {
            this.original.setEntity(new ResponseEntityWrapper(entity, connReleaseTrigger));
        }
    }

    public void close() throws IOException {
        if (this.connReleaseTrigger != null) {
            this.connReleaseTrigger.abortConnection();
        }
    }

    public Object invoke(
            final Object proxy, final Method method, final Object[] args) throws Throwable {
        String mname = method.getName();
        if (mname.equals("close")) {
            close();
            return null;
        } else {
            try {
                return method.invoke(original, args);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause != null) {
                    throw cause;
                } else {
                    throw ex;
                }
            }
        }
    }

    public static CloseableHttpResponse newProxy(
            final HttpResponse original,
            final ConnectionReleaseTriggerImpl connReleaseTrigger) {
        return (CloseableHttpResponse) Proxy.newProxyInstance(
                HttpResponseProxy.class.getClassLoader(),
                new Class<?>[] { CloseableHttpResponse.class },
                new HttpResponseProxy(original, connReleaseTrigger));
    }

}
