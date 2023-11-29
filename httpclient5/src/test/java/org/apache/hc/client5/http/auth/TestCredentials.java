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

package org.apache.hc.client5.http.auth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class TestCredentials {

    @Test
    public void testUsernamePasswordCredentialsBasics() {
        final UsernamePasswordCredentials creds1 = new UsernamePasswordCredentials(
                "name","pwd".toCharArray());
        Assertions.assertEquals("name", creds1.getUserName());
        Assertions.assertEquals(new BasicUserPrincipal("name"),
                creds1.getUserPrincipal());
        Assertions.assertArrayEquals("pwd".toCharArray(), creds1.getUserPassword());
        Assertions.assertEquals("[principal: name]", creds1.toString());
        final UsernamePasswordCredentials creds2 = new UsernamePasswordCredentials(
            "name", null);
        Assertions.assertEquals("name", creds2.getUserName());
        Assertions.assertEquals(new BasicUserPrincipal("name"),
                creds2.getUserPrincipal());
        Assertions.assertNull(creds2.getUserPassword());
        Assertions.assertEquals("[principal: name]", creds2.toString());
    }

    @Test
    public void testNTCredentialsBasics() {
        final NTCredentials creds1 = new NTCredentials(
                "name","pwd".toCharArray(), "localhost", "domain");
        Assertions.assertEquals("name", creds1.getUserName());
        Assertions.assertEquals(new NTUserPrincipal("DOMAIN", "name"),
                creds1.getUserPrincipal());
        Assertions.assertArrayEquals("pwd".toCharArray(), creds1.getPassword());
        Assertions.assertEquals("[principal: DOMAIN\\name][workstation: "+ creds1.getWorkstation() +"][netbiosDomain: DOMAIN]",
                creds1.toString());
        final NTCredentials creds2 = new NTCredentials(
                "name", null, null, null);
        Assertions.assertEquals("name", creds2.getUserName());
        Assertions.assertEquals(new NTUserPrincipal(null, "name"),
                creds2.getUserPrincipal());
        Assertions.assertNull(creds2.getPassword());
        Assertions.assertEquals("[principal: name][workstation: "+creds1.getWorkstation() +"][netbiosDomain: null]",
                creds2.toString());
    }

    @Test
    public void testUsernamePasswordCredentialsHashCode() {
        final UsernamePasswordCredentials creds1 = new UsernamePasswordCredentials(
                "name","pwd".toCharArray());
        final UsernamePasswordCredentials creds2 = new UsernamePasswordCredentials(
                "othername","pwd".toCharArray());
        final UsernamePasswordCredentials creds3 = new UsernamePasswordCredentials(
                "name", "otherpwd".toCharArray());

        Assertions.assertTrue(creds1.hashCode() == creds1.hashCode());
        Assertions.assertTrue(creds1.hashCode() != creds2.hashCode());
        Assertions.assertTrue(creds1.hashCode() == creds3.hashCode());
    }

    @Test
    public void testUsernamePasswordCredentialsEquals() {
        final UsernamePasswordCredentials creds1 = new UsernamePasswordCredentials(
                "name","pwd".toCharArray());
        final UsernamePasswordCredentials creds2 = new UsernamePasswordCredentials(
                "othername","pwd".toCharArray());
        final UsernamePasswordCredentials creds3 = new UsernamePasswordCredentials(
                "name", "otherpwd".toCharArray());

        Assertions.assertEquals(creds1, creds1);
        Assertions.assertNotEquals(creds1, creds2);
        Assertions.assertEquals(creds1, creds3);
    }

    @Test
    public void tesBearerTokenBasics() {
        final BearerToken creds1 = new BearerToken("token of some sort");
        Assertions.assertEquals("token of some sort", creds1.getToken());
    }

    @Test
    public void testBearerTokenHashCode() {
        final BearerToken creds1 = new BearerToken("token of some sort");
        final BearerToken creds2 = new BearerToken("another token of some sort");
        final BearerToken creds3 = new BearerToken("token of some sort");

        Assertions.assertTrue(creds1.hashCode() == creds1.hashCode());
        Assertions.assertTrue(creds1.hashCode() != creds2.hashCode());
        Assertions.assertTrue(creds1.hashCode() == creds3.hashCode());
    }

    @Test
    public void testBearerTokenEquals() {
        final BearerToken creds1 = new BearerToken("token of some sort");
        final BearerToken creds2 = new BearerToken("another token of some sort");
        final BearerToken creds3 = new BearerToken("token of some sort");

        Assertions.assertEquals(creds1, creds1);
        Assertions.assertNotEquals(creds1, creds2);
        Assertions.assertEquals(creds1, creds3);
    }

    @Test
    public void testNTCredentialsHashCode() {
        final NTCredentials creds1 = new NTCredentials(
                "name","pwd".toCharArray(), "somehost", "domain");
        final NTCredentials creds2 = new NTCredentials(
                "othername","pwd".toCharArray(), "somehost", "domain");
        final NTCredentials creds3 = new NTCredentials(
                "name", "otherpwd".toCharArray(), "SomeHost", "Domain");
        final NTCredentials creds4 = new NTCredentials(
                "name","pwd".toCharArray(), "otherhost", "domain");
        final NTCredentials creds5 = new NTCredentials(
                "name","pwd".toCharArray(), null, "domain");
        final NTCredentials creds6 = new NTCredentials(
                "name","pwd".toCharArray(), "somehost", "ms");
        final NTCredentials creds7 = new NTCredentials(
                "name","pwd".toCharArray(), "somehost", null);
        final NTCredentials creds8 = new NTCredentials(
                "name","pwd".toCharArray(), null, "domain");
        final NTCredentials creds9 = new NTCredentials(
                "name","pwd".toCharArray(), "somehost", null);

        Assertions.assertTrue(creds1.hashCode() == creds1.hashCode());
        Assertions.assertTrue(creds1.hashCode() != creds2.hashCode());
        Assertions.assertEquals(creds1.hashCode(), creds3.hashCode());
        Assertions.assertEquals(creds1.hashCode(), creds4.hashCode());
        Assertions.assertEquals(creds1.hashCode(), creds5.hashCode());
        Assertions.assertNotEquals(creds1.hashCode(), creds6.hashCode());
        Assertions.assertNotEquals(creds1.hashCode(), creds7.hashCode());
        Assertions.assertEquals(creds8.hashCode(), creds5.hashCode());
        Assertions.assertEquals(creds9.hashCode(), creds7.hashCode());
    }

    @Test
    public void testNTCredentialsEquals() {
        final NTCredentials creds1 = new NTCredentials(
                "name","pwd".toCharArray(), "somehost", "domain");
        final NTCredentials creds2 = new NTCredentials(
                "othername","pwd".toCharArray(), "somehost", "domain");
        final NTCredentials creds3 = new NTCredentials(
                "name", "otherpwd".toCharArray(), "SomeHost", "Domain");
        final NTCredentials creds4 = new NTCredentials(
                "name","pwd".toCharArray(), "otherhost", "domain");
        final NTCredentials creds5 = new NTCredentials(
                "name","pwd".toCharArray(), null, "domain");
        final NTCredentials creds6 = new NTCredentials(
                "name","pwd".toCharArray(), "somehost", "ms");
        final NTCredentials creds7 = new NTCredentials(
                "name","pwd".toCharArray(), "somehost", null);
        final NTCredentials creds8 = new NTCredentials(
                "name","pwd".toCharArray(), null, "domain");
        final NTCredentials creds9 = new NTCredentials(
                "name","pwd".toCharArray(), "somehost", null);

        Assertions.assertEquals(creds1, creds1);
        Assertions.assertNotEquals(creds1, creds2);
        Assertions.assertEquals(creds1, creds3);
        Assertions.assertEquals(creds1, creds4);
        Assertions.assertEquals(creds1, creds5);
        Assertions.assertNotEquals(creds1, creds6);
        Assertions.assertNotEquals(creds1, creds7);
        Assertions.assertEquals(creds8, creds5);
        Assertions.assertEquals(creds9, creds7);

    }

    @Test
    public void testUsernamePasswordCredentialsSerialization() throws Exception {
        final UsernamePasswordCredentials orig = new UsernamePasswordCredentials("name","pwd".toCharArray());
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final UsernamePasswordCredentials clone = (UsernamePasswordCredentials) inStream.readObject();
        Assertions.assertEquals(orig, clone);
    }

    @Test
    public void testNTCredentialsSerialization() throws Exception {
        final NTCredentials orig = new NTCredentials("name","pwd".toCharArray(), "somehost", "domain");
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final NTCredentials clone = (NTCredentials) inStream.readObject();
        Assertions.assertEquals(orig, clone);
    }
}
