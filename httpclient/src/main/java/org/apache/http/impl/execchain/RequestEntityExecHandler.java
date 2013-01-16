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

import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.http.HttpEntity;
import org.apache.http.annotation.NotThreadSafe;

/**
 * A wrapper class for {@link HttpEntity} enclosed in a request message.
 *
 * @since 4.3
 */
@NotThreadSafe
class RequestEntityExecHandler implements InvocationHandler  {

    private static final Method WRITE_TO_METHOD;

    static {
        try {
            WRITE_TO_METHOD = HttpEntity.class.getMethod("writeTo", OutputStream.class);
        } catch (final NoSuchMethodException ex) {
            throw new Error(ex);
        }
    }

    private final HttpEntity original;
    private boolean consumed = false;

    RequestEntityExecHandler(final HttpEntity original) {
        super();
        this.original = original;
    }

    public HttpEntity getOriginal() {
        return original;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public Object invoke(
            final Object proxy, final Method method, final Object[] args) throws Throwable {
        try {
            if (method.equals(WRITE_TO_METHOD)) {
                this.consumed = true;
            }
            return method.invoke(original, args);
        } catch (final InvocationTargetException ex) {
            final Throwable cause = ex.getCause();
            if (cause != null) {
                throw cause;
            } else {
                throw ex;
            }
        }
    }

}
