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
package org.apache.hc.client5.http.impl.async;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;

@Internal
final class AsyncClientExchangeHandlerProxy implements InvocationHandler {

    private final AsyncClientExchangeHandler handler;
    private final Runnable onRelease;
    private final AtomicBoolean released;

    private AsyncClientExchangeHandlerProxy(
            final AsyncClientExchangeHandler handler,
            final Runnable onRelease) {
        this.handler = handler;
        this.onRelease = onRelease;
        this.released = new AtomicBoolean(false);
    }

    static AsyncClientExchangeHandler newProxy(
            final AsyncClientExchangeHandler handler,
            final Runnable onRelease) {
        return (AsyncClientExchangeHandler) Proxy.newProxyInstance(
                AsyncClientExchangeHandler.class.getClassLoader(),
                new Class<?>[]{AsyncClientExchangeHandler.class},
                new AsyncClientExchangeHandlerProxy(handler, onRelease));
    }

    @Override
    public Object invoke(
            final Object proxy,
            final Method method,
            final Object[] args) throws Throwable {
        if ("releaseResources".equals(method.getName())
                && method.getParameterCount() == 0) {
            try {
                return method.invoke(handler, args);
            } catch (final InvocationTargetException ex) {
                throw ex.getCause();
            } finally {
                if (released.compareAndSet(false, true)) {
                    onRelease.run();
                }
            }
        }
        try {
            return method.invoke(handler, args);
        } catch (final InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

}
