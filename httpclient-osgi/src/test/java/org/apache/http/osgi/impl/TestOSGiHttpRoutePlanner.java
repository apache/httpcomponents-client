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
package org.apache.http.osgi.impl;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.util.Hashtable;

import org.apache.http.HttpHost;
import org.apache.http.osgi.services.ProxyConfiguration;
import org.junit.Test;


/**
 * @since 4.4.3
 */

public class TestOSGiHttpRoutePlanner  {

    private final ProxyConfiguration pc1 = proxy("proxy1", 8080, "localhost", "127.0.0.1", ".apache.org");
    private final ProxyConfiguration pc2 = proxy("proxy2", 9090, "localhost", "127.0.0.1", ".oracle.com", "12.34.34.8");

    @Test
    public void testDeterminProxy() throws Exception {
        OSGiHttpRoutePlanner planner = new OSGiHttpRoutePlanner(singletonList(pc1));

        HttpHost proxy = planner.determineProxy(new HttpHost("localhost", 8090), null, null);
        assertNull(proxy);

        proxy = planner.determineProxy(new HttpHost("there", 9090), null, null);
        assertNotNull(proxy);
        assertTrue(proxy.getHostName().equals("proxy1"));

        proxy = planner.determineProxy(new HttpHost("10.2.144.23", 4554), null, null);
        assertNotNull(proxy);
        assertTrue(proxy.getHostName().equals("proxy1"));

        final InetAddress addr = InetAddress.getByName("localhost");
        proxy = planner.determineProxy(new HttpHost(addr, 4554), null, null);
        assertNull(proxy);

        proxy = planner.determineProxy(new HttpHost("hc.apache.org", 4554), null, null);
        assertNull(proxy);


        // test with more than one registration of proxyConfiguration
        planner = new OSGiHttpRoutePlanner(asList(pc1, pc2));
        proxy = planner.determineProxy(new HttpHost("localhost", 8090), null, null);
        assertNull(proxy);

        proxy = planner.determineProxy(new HttpHost("there", 9090), null, null);
        assertNotNull(proxy);
        assertTrue(proxy.getHostName().equals("proxy1")); // the first one

        proxy = planner.determineProxy(new HttpHost(addr, 4554), null, null);
        assertNull(proxy);

        proxy = planner.determineProxy(new HttpHost("hc.apache.org", 4554), null, null);
        assertNull(proxy);

        proxy = planner.determineProxy(new HttpHost("docs.oracle.com", 4554), null, null);
        assertNull(proxy);
    }

    @Test
    public void testMasking() throws Exception {
        final OSGiHttpRoutePlanner planner = new OSGiHttpRoutePlanner(singletonList(pc2));

        HttpHost proxy = planner.determineProxy(new HttpHost("12.34.34.2", 4554), null, null);
        assertNotNull(proxy);
        assertTrue(proxy.getHostName().equals("proxy2"));

        proxy = planner.determineProxy(new HttpHost("12.34.34.8", 4554), null, null);
        assertNotNull(proxy);
    }

    private ProxyConfiguration proxy(final String host, final int port, final String... exceptions) {
        final OSGiProxyConfiguration proxyConfiguration = new OSGiProxyConfiguration();
        final Hashtable<String, Object> config = new Hashtable<String, Object>();
        config.put("proxy.enabled", true);
        config.put("proxy.host", host);
        config.put("proxy.port", port);
        config.put("proxy.user", "");
        config.put("proxy.password", "");
        config.put("proxy.exceptions", exceptions);
        proxyConfiguration.update(config);
        return proxyConfiguration;
    }

}
