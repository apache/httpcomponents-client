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
package org.apache.hc.client5.http.osgi.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.hc.client5.http.osgi.impl.HostMatcher.DomainNameMatcher;
import org.apache.hc.client5.http.osgi.impl.HostMatcher.HostMatcherFactory;
import org.apache.hc.client5.http.osgi.impl.HostMatcher.HostNameMatcher;
import org.apache.hc.client5.http.osgi.impl.HostMatcher.IPAddressMatcher;
import org.apache.hc.client5.http.osgi.impl.HostMatcher.NetworkAddress;
import org.junit.Test;

public final class HostMatcherTest {

    @Test
    public void testNetworkAddress() {
        final NetworkAddress nullNetworkAddress = NetworkAddress.parse("www.apache.org");
        assertNull(nullNetworkAddress);

        final NetworkAddress na = NetworkAddress.parse("127.0.0.1");
        assertEquals(2130706433, na.address);
        assertEquals(-2147483648, na.mask);
    }

    @Test
    public void testIPAddressMatcher() {
        final NetworkAddress na = NetworkAddress.parse("127.0.0.1");
        final IPAddressMatcher ipam = new IPAddressMatcher(na);
        assertFalse(ipam.matches("127.0.0.255"));
    }

    @Test
    public void testDomainNameMatcher() {
        final DomainNameMatcher dnm = new DomainNameMatcher(".apache.org");
        assertTrue(dnm.matches("www.apache.org"));
        assertTrue(dnm.matches("hc.apache.org"));
        assertTrue(dnm.matches("commons.apache.org"));
        assertTrue(dnm.matches("cocoon.apache.org"));
        assertFalse(dnm.matches("www.gnu.org"));
    }

    @Test
    public void testHostNameMatcher() {
        final HostNameMatcher hnm = new HostNameMatcher("www.apache.org");
        assertTrue(hnm.matches("www.apache.org"));
        assertTrue(hnm.matches("WwW.APACHE.org"));
        assertTrue(hnm.matches("wWw.apache.ORG"));
        assertTrue(hnm.matches("WWW.APACHE.ORG"));
        assertFalse(hnm.matches("www.gnu.org"));
    }

    @Test
    public void testHostMatcherFactory() {
        assertTrue(HostMatcherFactory.createMatcher("127.0.0.1") instanceof IPAddressMatcher);
        assertTrue(HostMatcherFactory.createMatcher(".apache.org") instanceof DomainNameMatcher);
        assertTrue(HostMatcherFactory.createMatcher("www.apache.org") instanceof HostNameMatcher);
    }

}
