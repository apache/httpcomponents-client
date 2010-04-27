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

package org.apache.http.auth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Assert;
import org.junit.Test;

public class TestCredentials {

    @Test
    public void testUsernamePasswordCredentialsBasics() {
        UsernamePasswordCredentials creds1 = new UsernamePasswordCredentials(
                "name", "pwd");
        Assert.assertEquals("name", creds1.getUserName());
        Assert.assertEquals(new BasicUserPrincipal("name"),
                creds1.getUserPrincipal());
        Assert.assertEquals("pwd", creds1.getPassword());
        Assert.assertEquals("[principal: name]", creds1.toString());
        UsernamePasswordCredentials creds2 = new UsernamePasswordCredentials(
                "name:pwd");
        Assert.assertEquals("name", creds2.getUserName());
        Assert.assertEquals(new BasicUserPrincipal("name"),
                creds2.getUserPrincipal());
        Assert.assertEquals("pwd", creds2.getPassword());
        Assert.assertEquals("[principal: name]", creds2.toString());
        UsernamePasswordCredentials creds3 = new UsernamePasswordCredentials(
            "name");
        Assert.assertEquals("name", creds3.getUserName());
        Assert.assertEquals(new BasicUserPrincipal("name"),
                creds3.getUserPrincipal());
        Assert.assertEquals(null, creds3.getPassword());
        Assert.assertEquals("[principal: name]", creds3.toString());
    }

    @Test
    public void testNTCredentialsBasics() {
        NTCredentials creds1 = new NTCredentials(
                "name", "pwd", "localhost", "domain");
        Assert.assertEquals("name", creds1.getUserName());
        Assert.assertEquals(new NTUserPrincipal("DOMAIN", "name"),
                creds1.getUserPrincipal());
        Assert.assertEquals("pwd", creds1.getPassword());
        Assert.assertEquals("[principal: DOMAIN/name][workstation: LOCALHOST]",
                creds1.toString());
        NTCredentials creds2 = new NTCredentials(
                "name", null, null, null);
        Assert.assertEquals("name", creds2.getUserName());
        Assert.assertEquals(new NTUserPrincipal(null, "name"),
                creds2.getUserPrincipal());
        Assert.assertEquals(null, creds2.getPassword());
        Assert.assertEquals("[principal: name][workstation: null]",
                creds2.toString());
        NTCredentials creds3 = new NTCredentials(
                "domain/name:pwd");
        Assert.assertEquals("name", creds3.getUserName());
        Assert.assertEquals(new NTUserPrincipal("DOMAIN", "name"),
                creds3.getUserPrincipal());
        Assert.assertEquals("pwd", creds3.getPassword());
        Assert.assertEquals("[principal: DOMAIN/name][workstation: null]",
                creds3.toString());
        NTCredentials creds4 = new NTCredentials(
            "domain/name");
        Assert.assertEquals("name", creds4.getUserName());
        Assert.assertEquals(new NTUserPrincipal("DOMAIN", "name"),
                creds4.getUserPrincipal());
        Assert.assertEquals(null, creds4.getPassword());
        Assert.assertEquals("[principal: DOMAIN/name][workstation: null]",
                creds4.toString());
        NTCredentials creds5 = new NTCredentials(
            "name");
        Assert.assertEquals("name", creds5.getUserName());
        Assert.assertEquals(new NTUserPrincipal(null, "name"),
                creds5.getUserPrincipal());
        Assert.assertEquals(null, creds5.getPassword());
        Assert.assertEquals("[principal: name][workstation: null]",
                creds5.toString());
    }

    @Test
    public void testUsernamePasswordCredentialsHashCode() {
        UsernamePasswordCredentials creds1 = new UsernamePasswordCredentials(
                "name", "pwd");
        UsernamePasswordCredentials creds2 = new UsernamePasswordCredentials(
                "othername", "pwd");
        UsernamePasswordCredentials creds3 = new UsernamePasswordCredentials(
                "name", "otherpwd");

        Assert.assertTrue(creds1.hashCode() == creds1.hashCode());
        Assert.assertTrue(creds1.hashCode() != creds2.hashCode());
        Assert.assertTrue(creds1.hashCode() == creds3.hashCode());
    }

    @Test
    public void testUsernamePasswordCredentialsEquals() {
        UsernamePasswordCredentials creds1 = new UsernamePasswordCredentials(
                "name", "pwd");
        UsernamePasswordCredentials creds2 = new UsernamePasswordCredentials(
                "othername", "pwd");
        UsernamePasswordCredentials creds3 = new UsernamePasswordCredentials(
                "name", "otherpwd");

        Assert.assertTrue(creds1.equals(creds1));
        Assert.assertFalse(creds1.equals(creds2));
        Assert.assertTrue(creds1.equals(creds3));
    }

    @Test
    public void testNTCredentialsHashCode() {
        NTCredentials creds1 = new NTCredentials(
                "name", "pwd", "somehost", "domain");
        NTCredentials creds2 = new NTCredentials(
                "othername", "pwd", "somehost", "domain");
        NTCredentials creds3 = new NTCredentials(
                "name", "otherpwd", "SomeHost", "Domain");
        NTCredentials creds4 = new NTCredentials(
                "name", "pwd", "otherhost", "domain");
        NTCredentials creds5 = new NTCredentials(
                "name", "pwd", null, "domain");
        NTCredentials creds6 = new NTCredentials(
                "name", "pwd", "somehost", "ms");
        NTCredentials creds7 = new NTCredentials(
                "name", "pwd", "somehost", null);
        NTCredentials creds8 = new NTCredentials(
                "name", "pwd", null, "domain");
        NTCredentials creds9 = new NTCredentials(
                "name", "pwd", "somehost", null);

        Assert.assertTrue(creds1.hashCode() == creds1.hashCode());
        Assert.assertTrue(creds1.hashCode() != creds2.hashCode());
        Assert.assertTrue(creds1.hashCode() == creds3.hashCode());
        Assert.assertFalse(creds1.hashCode() == creds4.hashCode());
        Assert.assertFalse(creds1.hashCode() == creds5.hashCode());
        Assert.assertFalse(creds1.hashCode() == creds6.hashCode());
        Assert.assertFalse(creds1.hashCode() == creds7.hashCode());
        Assert.assertTrue(creds8.hashCode() == creds5.hashCode());
        Assert.assertTrue(creds9.hashCode() == creds7.hashCode());
    }

    @Test
    public void testNTCredentialsEquals() {
        NTCredentials creds1 = new NTCredentials(
                "name", "pwd", "somehost", "domain");
        NTCredentials creds2 = new NTCredentials(
                "othername", "pwd", "somehost", "domain");
        NTCredentials creds3 = new NTCredentials(
                "name", "otherpwd", "SomeHost", "Domain");
        NTCredentials creds4 = new NTCredentials(
                "name", "pwd", "otherhost", "domain");
        NTCredentials creds5 = new NTCredentials(
                "name", "pwd", null, "domain");
        NTCredentials creds6 = new NTCredentials(
                "name", "pwd", "somehost", "ms");
        NTCredentials creds7 = new NTCredentials(
                "name", "pwd", "somehost", null);
        NTCredentials creds8 = new NTCredentials(
                "name", "pwd", null, "domain");
        NTCredentials creds9 = new NTCredentials(
                "name", "pwd", "somehost", null);

        Assert.assertTrue(creds1.equals(creds1));
        Assert.assertFalse(creds1.equals(creds2));
        Assert.assertTrue(creds1.equals(creds3));
        Assert.assertFalse(creds1.equals(creds4));
        Assert.assertFalse(creds1.equals(creds5));
        Assert.assertFalse(creds1.equals(creds6));
        Assert.assertFalse(creds1.equals(creds7));
        Assert.assertTrue(creds8.equals(creds5));
        Assert.assertTrue(creds9.equals(creds7));

    }

    @Test
    public void testUsernamePasswordCredentialsSerialization() throws Exception {
        UsernamePasswordCredentials orig = new UsernamePasswordCredentials("name", "pwd");
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        UsernamePasswordCredentials clone = (UsernamePasswordCredentials) instream.readObject();
        Assert.assertEquals(orig, clone);
    }

    @Test
    public void testNTCredentialsSerialization() throws Exception {
        NTCredentials orig = new NTCredentials("name", "pwd", "somehost", "domain");
        ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        byte[] raw = outbuffer.toByteArray();
        ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        ObjectInputStream instream = new ObjectInputStream(inbuffer);
        NTCredentials clone = (NTCredentials) instream.readObject();
        Assert.assertEquals(orig, clone);
    }

}
