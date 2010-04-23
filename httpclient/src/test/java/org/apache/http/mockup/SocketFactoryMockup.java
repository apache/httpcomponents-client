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

package org.apache.http.mockup;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.params.HttpParams;

/**
 * {@link SchemeSocketFactory} mockup implementation.
 */
public class SocketFactoryMockup implements SchemeSocketFactory {

    /* A default instance of this mockup. */
    public final static SchemeSocketFactory INSTANCE = new SocketFactoryMockup("INSTANCE");

    /** The name of this mockup socket factory. */
    protected final String mockup_name;

    public SocketFactoryMockup(String name) {
        mockup_name = (name != null) ? name : String.valueOf(hashCode());
    }

    // don't implement equals and hashcode, all instances are different!

    @Override
    public String toString() {
        return "SocketFactoryMockup." + mockup_name;
    }

    public Socket createSocket(final HttpParams params) {
        throw new UnsupportedOperationException("I'm a mockup!");
    }

    public Socket connectSocket(
            Socket sock,
            InetSocketAddress remoteAddress,
            InetSocketAddress localAddress,
            HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
        throw new UnsupportedOperationException("I'm a mockup!");
    }

    public boolean isSecure(Socket sock) {
        // no way that the argument is from *this* factory...
        throw new IllegalArgumentException("I'm a mockup!");
    }

}
