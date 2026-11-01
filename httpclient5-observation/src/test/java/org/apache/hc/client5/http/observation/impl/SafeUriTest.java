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
package org.apache.hc.client5.http.observation.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SafeUriTest {

    @Test
    void removesUserInfoQueryAndFragment() {
        assertEquals("http://host:8080/path", SafeUri.of("http://user:secret@host:8080/path?token=SECRET#frag"));
    }

    @Test
    void removesUserInfoWithoutPath() {
        assertEquals("http://host", SafeUri.of("http://user:secret@host?token=SECRET"));
    }

    @Test
    void removesQueryFromRequestTarget() {
        assertEquals("/path", SafeUri.of("/path?token=SECRET"));
        assertEquals("/path", SafeUri.of("/path#frag?x"));
    }

    @Test
    void keepsAtSignInPath() {
        assertEquals("http://host/a@b", SafeUri.of("http://host/a@b?x=1"));
    }

    @Test
    void keepsUriLikeTextInRelativePath() {
        assertEquals("/redirect/http://user@host/x", SafeUri.of("/redirect/http://user@host/x?y=1"));
        assertEquals("path/to://user@host", SafeUri.of("path/to://user@host#frag"));
        assertEquals("/a://b@c", SafeUri.of("/a://b@c"));
    }

    @Test
    void recognizesSchemeByItsSyntax() {
        assertEquals("git+ssh://host/repo", SafeUri.of("git+ssh://user:secret@host/repo?x=1"));
        assertEquals("HTTP://host", SafeUri.of("HTTP://user@host"));
        assertEquals("1http://user@host", SafeUri.of("1http://user@host"));
        assertEquals("ht tp://user@host", SafeUri.of("ht tp://user@host"));
    }

    @Test
    void leavesPlainUriUntouched() {
        assertEquals("http://host:8080/path", SafeUri.of("http://host:8080/path"));
        assertEquals("/path", SafeUri.of("/path"));
        assertEquals("*", SafeUri.of("*"));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> SafeUri.of(null));
    }

}
