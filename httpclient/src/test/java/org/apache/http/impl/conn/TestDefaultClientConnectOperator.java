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
package org.apache.http.impl.conn;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.http.conn.DnsResolver;

import org.junit.Assert;
import org.junit.Test;

public class TestDefaultClientConnectOperator {

    @Test
    public void testCustomDnsResolver() throws Exception {
        DnsResolver dnsResolver = mock(DnsResolver.class);
        InetAddress[] firstAddress = translateIp("192.168.1.1");
        when(dnsResolver.resolve("somehost.example.com")).thenReturn(firstAddress);

        InetAddress[] secondAddress = translateIp("192.168.12.16");
        when(dnsResolver.resolve("otherhost.example.com")).thenReturn(secondAddress);

        DefaultClientConnectionOperator operator = new DefaultClientConnectionOperator(
                SchemeRegistryFactory.createDefault(), dnsResolver);

        Assert.assertArrayEquals(firstAddress, operator.resolveHostname("somehost.example.com"));
        Assert.assertArrayEquals(secondAddress, operator.resolveHostname("otherhost.example.com"));
    }

    @Test(expected=UnknownHostException.class)
    public void testDnsResolverUnknownHost() throws Exception {
        DnsResolver dnsResolver = mock(DnsResolver.class);
        when(dnsResolver.resolve("unknown.example.com")).thenThrow(new UnknownHostException());

        DefaultClientConnectionOperator operator = new DefaultClientConnectionOperator(
                SchemeRegistryFactory.createDefault(), dnsResolver);
        operator.resolveHostname("unknown.example.com");
    }

    @Test
    public void testDefaultLocalHost() throws Exception {
        DefaultClientConnectionOperator operator = new DefaultClientConnectionOperator(
                SchemeRegistryFactory.createDefault());
        operator.resolveHostname("localhost");
    }

    private InetAddress[] translateIp(String ip) throws UnknownHostException {
        String[] ipParts = ip.split("\\.");

        byte[] byteIpAddress = new byte[4];
        for (int i = 0; i < 4; i++) {
            byteIpAddress[i] = Integer.decode(ipParts[i]).byteValue();
        }
        return new InetAddress[] { InetAddress.getByAddress(byteIpAddress) };
    }

}
