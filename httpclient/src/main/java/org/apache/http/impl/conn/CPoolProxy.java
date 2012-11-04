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

package org.apache.http.impl.conn;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.http.HttpClientConnection;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.conn.HttpSSLConnection;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.3
 */
@NotThreadSafe
class CPoolProxy implements InvocationHandler {

    private volatile CPoolEntry poolEntry;

    CPoolProxy(final CPoolEntry entry) {
        super();
        this.poolEntry = entry;
    }

    CPoolEntry getPoolEntry() {
        return this.poolEntry;
    }

    CPoolEntry detach() {
        CPoolEntry local = this.poolEntry;
        this.poolEntry = null;
        return local;
    }

    HttpClientConnection getConnection() {
        CPoolEntry local = this.poolEntry;
        if (local == null) {
            return null;
        }
        return local.getConnection();
    }

    public void close() throws IOException {
        CPoolEntry local = this.poolEntry;
        if (local != null) {
            HttpClientConnection conn = local.getConnection();
            conn.close();
        }
    }

    public void shutdown() throws IOException {
        CPoolEntry local = this.poolEntry;
        if (local != null) {
            HttpClientConnection conn = local.getConnection();
            conn.shutdown();
        }
    }

    public boolean isOpen() {
        HttpClientConnection conn = getConnection();
        if (conn != null) {
            return conn.isOpen();
        } else {
            return false;
        }
    }

    public boolean isStale() {
        HttpClientConnection conn = getConnection();
        if (conn != null) {
            return conn.isStale();
        } else {
            return true;
        }
    }

    public Object invoke(
            final Object proxy, final Method method, final Object[] args) throws Throwable {
        String mname = method.getName();
        if (mname.equals("close")) {
            close();
            return null;
        } else if (mname.equals("shutdown")) {
            shutdown();
            return null;
        } else if (mname.equals("isOpen")) {
            return isOpen();
        } else if (mname.equals("isStale")) {
            return isStale();
        } else {
            HttpClientConnection conn = getConnection();
            if (conn == null) {
                throw new ConnectionShutdownException();
            }
            try {
                return method.invoke(conn, args);
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

    public static HttpClientConnection newProxy(
            final CPoolEntry poolEntry) {
        return (HttpClientConnection) Proxy.newProxyInstance(
                CPoolProxy.class.getClassLoader(),
                new Class<?>[] { HttpClientConnection.class, HttpSSLConnection.class, HttpContext.class },
                new CPoolProxy(poolEntry));
    }

    private static CPoolProxy getHandler(
            final HttpClientConnection proxy) {
        InvocationHandler handler = Proxy.getInvocationHandler(proxy);
        if (!CPoolProxy.class.isInstance(handler)) {
            throw new IllegalStateException("Unexpected proxy handler class: " + handler);
        }
        return CPoolProxy.class.cast(handler);
    }

    public static CPoolEntry getPoolEntry(final HttpClientConnection proxy) {
        CPoolEntry entry = getHandler(proxy).getPoolEntry();
        if (entry == null) {
            throw new ConnectionShutdownException();
        }
        return entry;
    }

    public static CPoolEntry detach(final HttpClientConnection proxy) {
        return getHandler(proxy).detach();
    }

}
