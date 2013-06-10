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

import java.net.Socket;

import org.apache.http.conn.scheme.SchemeLayeredSocketFactory;
import org.apache.http.params.HttpParams;

/**
 * {@link org.apache.http.conn.scheme.LayeredSchemeSocketFactory} mockup implementation.
 */
@Deprecated
public class SecureSocketFactoryMockup extends SocketFactoryMockup
    implements SchemeLayeredSocketFactory {

    /* A default instance of this mockup. */
    public final static SchemeLayeredSocketFactory INSTANCE = new SecureSocketFactoryMockup("INSTANCE");

    public SecureSocketFactoryMockup(final String name) {
        super(name);
    }

    // don't implement equals and hashcode, all instances are different!

    @Override
    public String toString() {
        return "SecureSocketFactoryMockup." + mockup_name;
    }


    public Socket createLayeredSocket(final Socket socket, final String host, final int port,
                                      final HttpParams params) {
        throw new UnsupportedOperationException("I'm a mockup!");
    }

}
