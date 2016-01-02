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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.Map;
import java.util.TreeMap;

import org.apache.http.HttpHost;
import org.apache.http.osgi.services.ProxyConfiguration;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;


/**
 * @since 4.4.3
 */

public class TestOSGiHttpRoutePlanner  {

    final ProxyConfiguration pc1 = new ProxyConfiguration() {
        @Override
        public boolean isEnabled() {return true; }
        @Override
        public String getHostname() {return "proxy1"; }
        @Override
        public int getPort() { return 8080; }
        @Override
        public String getUsername() { return ""; }
        @Override
        public String getPassword() {return ""; }
        @Override
        public String[] getProxyExceptions() { return new String[]{"localhost", "127.0.0.1", ".apache.org"}; }
    };

    final ProxyConfiguration pc2 = new ProxyConfiguration() {
        @Override
        public boolean isEnabled() {return true; }
        @Override
        public String getHostname() {return "proxy2"; }
        @Override
        public int getPort() { return 9090; }
        @Override
        public String getUsername() { return ""; }
        @Override
        public String getPassword() {return ""; }
        @Override
        public String[] getProxyExceptions() { return new String[]{"localhost", "127.0.0.1", ".oracle.com", "12.34.34.8"}; }
    };

    @Test
    public void testDeterminProxy() throws Exception {
        final ServiceReference sRef1 = mock(ServiceReference.class);
        final ServiceRegistration sReg1 = mock(ServiceRegistration.class);
        when(sReg1.getReference()).thenReturn(sRef1);
        final BundleContext bc = mock(BundleContext.class);
        when(bc.getService(sRef1)).thenReturn(this.pc1);

        final Map<String, ServiceRegistration> registrations = new TreeMap<String, ServiceRegistration>(); // TreeMap for order
        registrations.put("foo1", sReg1);

        OSGiHttpRoutePlanner planner = new OSGiHttpRoutePlanner(bc, registrations);

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
        final ServiceReference sRef2 = mock(ServiceReference.class);
        final ServiceRegistration sReg2 = mock(ServiceRegistration.class);
        when(sReg2.getReference()).thenReturn(sRef2);
        when(bc.getService(sRef2)).thenReturn(this.pc2);
        registrations.put("foo2", sReg2);

        planner = new OSGiHttpRoutePlanner(bc, registrations);
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
        final ServiceReference sRef2 = mock(ServiceReference.class);
        final ServiceRegistration sReg2 = mock(ServiceRegistration.class);
        when(sReg2.getReference()).thenReturn(sRef2);
        final BundleContext bc = mock(BundleContext.class);
        when(bc.getService(sRef2)).thenReturn(this.pc2);
        final Map<String, ServiceRegistration> registrations = new TreeMap<String, ServiceRegistration>();
        registrations.put("foo2", sReg2);

        final OSGiHttpRoutePlanner planner = new OSGiHttpRoutePlanner(bc, registrations);

        HttpHost proxy = planner.determineProxy(new HttpHost("12.34.34.2", 4554), null, null);
        assertNotNull(proxy);
        assertTrue(proxy.getHostName().equals("proxy2"));

        proxy = planner.determineProxy(new HttpHost("12.34.34.8", 4554), null, null);
        assertNotNull(proxy);
    }

}
