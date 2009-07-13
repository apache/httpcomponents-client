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


import java.net.URI;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;



/**
 * Mockup of a {@link ProxySelector}.
 * Always returns a fixed list.
 */
public class ProxySelectorMockup extends ProxySelector {

    protected List<Proxy> proxyList;


    /**
     * Creates a mock proxy selector.
     *
     * @param proxies   the list of proxies, or
     *                  <code>null</code> for direct connections
     */
    public ProxySelectorMockup(List<Proxy> proxies) {

        if (proxies == null) {
            proxies = new ArrayList<Proxy>(1);
            proxies.add(Proxy.NO_PROXY);
        } else if (proxies.isEmpty()) {
            throw new IllegalArgumentException
                ("Proxy list must not be empty.");
        }

        proxyList = proxies;
    }


    /**
     * Obtains the constructor argument.
     *
     * @param ignored   not used by this mockup
     *
     * @return  the list passed to the constructor,
     *          or a default list with "DIRECT" as the only element
     */
    @Override
    public List<Proxy> select(URI ignored) {
        return proxyList;
    }


    /**
     * Does nothing.
     */
    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        // no body
    }
}

