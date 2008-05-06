/*
 * $HeadURL$
 * $Revision$
 * $Date$
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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestCredentials extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestCredentials(final String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestCredentials.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestCredentials.class);
    }

    public void testUsernamePasswordCredentialsBasics() {
        UsernamePasswordCredentials creds1 = new UsernamePasswordCredentials(
                "name", "pwd"); 
        assertEquals("name", creds1.getUserName());
        assertEquals("name", creds1.getPrincipalName());
        assertEquals("pwd", creds1.getPassword());
        assertEquals("[username: name]", creds1.toString());
        UsernamePasswordCredentials creds2 = new UsernamePasswordCredentials(
                "name:pwd"); 
        assertEquals("name", creds2.getUserName());
        assertEquals("name", creds2.getPrincipalName());
        assertEquals("pwd", creds2.getPassword());
        assertEquals("[username: name]", creds2.toString());
        UsernamePasswordCredentials creds3 = new UsernamePasswordCredentials(
            "name"); 
        assertEquals("name", creds3.getUserName());
        assertEquals("name", creds3.getPrincipalName());
        assertEquals(null, creds3.getPassword());
        assertEquals("[username: name]", creds3.toString());
    }
    
    public void testNTCredentialsBasics() {
        NTCredentials creds1 = new NTCredentials(
                "name", "pwd", "localhost", "domain"); 
        assertEquals("name", creds1.getUserName());
        assertEquals("DOMAIN/name", creds1.getPrincipalName());
        assertEquals("pwd", creds1.getPassword());
        assertEquals("[username: name][workstation: LOCALHOST]" +
                "[domain: DOMAIN]", creds1.toString());
        NTCredentials creds2 = new NTCredentials(
                "name", null, null, null); 
        assertEquals("name", creds2.getUserName());
        assertEquals("name", creds2.getPrincipalName());
        assertEquals(null, creds2.getPassword());
        assertEquals("[username: name][workstation: null]" +
                "[domain: null]", creds2.toString());
        NTCredentials creds3 = new NTCredentials(
                "domain/name:pwd"); 
        assertEquals("name", creds3.getUserName());
        assertEquals("DOMAIN/name", creds3.getPrincipalName());
        assertEquals("pwd", creds3.getPassword());
        assertEquals("[username: name][workstation: null]" +
                "[domain: DOMAIN]", creds3.toString());
        NTCredentials creds4 = new NTCredentials(
            "domain/name"); 
        assertEquals("name", creds4.getUserName());
        assertEquals("DOMAIN/name", creds4.getPrincipalName());
        assertEquals(null, creds4.getPassword());
        assertEquals("[username: name][workstation: null]" +
                "[domain: DOMAIN]", creds4.toString());
        NTCredentials creds5 = new NTCredentials(
            "name"); 
        assertEquals("name", creds5.getUserName());
        assertEquals("name", creds5.getPrincipalName());
        assertEquals(null, creds5.getPassword());
        assertEquals("[username: name][workstation: null]" +
                "[domain: null]", creds5.toString());
    }
 
    public void testUsernamePasswordCredentialsHashCode() {
        UsernamePasswordCredentials creds1 = new UsernamePasswordCredentials(
                "name", "pwd"); 
        UsernamePasswordCredentials creds2 = new UsernamePasswordCredentials(
                "othername", "pwd"); 
        UsernamePasswordCredentials creds3 = new UsernamePasswordCredentials(
                "name", "otherpwd"); 

        assertTrue(creds1.hashCode() == creds1.hashCode());
        assertTrue(creds1.hashCode() != creds2.hashCode());
        assertTrue(creds1.hashCode() == creds3.hashCode());
    }
    
    public void testUsernamePasswordCredentialsEquals() {
        UsernamePasswordCredentials creds1 = new UsernamePasswordCredentials(
                "name", "pwd"); 
        UsernamePasswordCredentials creds2 = new UsernamePasswordCredentials(
                "othername", "pwd"); 
        UsernamePasswordCredentials creds3 = new UsernamePasswordCredentials(
                "name", "otherpwd"); 

        assertTrue(creds1.equals(creds1));
        assertFalse(creds1.equals(creds2));
        assertTrue(creds1.equals(creds3));
    }
    
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

        assertTrue(creds1.hashCode() == creds1.hashCode());
        assertTrue(creds1.hashCode() != creds2.hashCode());
        assertTrue(creds1.hashCode() == creds3.hashCode());
        assertFalse(creds1.hashCode() == creds4.hashCode());
        assertFalse(creds1.hashCode() == creds5.hashCode());
        assertFalse(creds1.hashCode() == creds6.hashCode());
        assertFalse(creds1.hashCode() == creds7.hashCode());
        assertTrue(creds8.hashCode() == creds5.hashCode());
        assertTrue(creds9.hashCode() == creds7.hashCode());
    }
    
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

        assertTrue(creds1.equals(creds1));
        assertFalse(creds1.equals(creds2));
        assertTrue(creds1.equals(creds3));
        assertFalse(creds1.equals(creds4));
        assertFalse(creds1.equals(creds5));
        assertFalse(creds1.equals(creds6));
        assertFalse(creds1.equals(creds7));
        assertTrue(creds8.equals(creds5));
        assertTrue(creds9.equals(creds7));
        
    }
}
