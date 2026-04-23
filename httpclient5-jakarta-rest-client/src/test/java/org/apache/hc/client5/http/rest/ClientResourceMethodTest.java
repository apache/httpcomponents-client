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
package org.apache.hc.client5.http.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ClientResourceMethodTest {

    // --- combinePaths ---

    @Test
    void testCombineBaseAndSub() {
        assertEquals("/api/users",
                ClientResourceMethod.combinePaths("/api", "/users"));
    }

    @Test
    void testCombineTrailingSlash() {
        assertEquals("/api/users",
                ClientResourceMethod.combinePaths("/api/", "/users"));
    }

    @Test
    void testCombineSubWithoutLeadingSlash() {
        assertEquals("/api/users",
                ClientResourceMethod.combinePaths("/api", "users"));
    }

    @Test
    void testCombineBaseWithoutLeadingSlash() {
        assertEquals("/widgets",
                ClientResourceMethod.combinePaths("widgets", null));
    }

    @Test
    void testCombineBothWithoutLeadingSlash() {
        assertEquals("/widgets/items",
                ClientResourceMethod.combinePaths("widgets", "items"));
    }

    @Test
    void testCombineBaseWithoutSlashSubWithSlash() {
        assertEquals("/widgets/items",
                ClientResourceMethod.combinePaths("widgets", "/items"));
    }

    @Test
    void testCombineEmptyBase() {
        assertEquals("/users",
                ClientResourceMethod.combinePaths("", "/users"));
    }

    @Test
    void testCombineNullSub() {
        assertEquals("/api",
                ClientResourceMethod.combinePaths("/api", null));
    }

    @Test
    void testCombineBothEmpty() {
        assertEquals("/",
                ClientResourceMethod.combinePaths("", null));
    }

    // --- stripRegex ---

    @Test
    void testStripRegexSimple() {
        assertEquals("{id}",
                ClientResourceMethod.stripRegex("{id:\\d+}"));
    }

    @Test
    void testStripRegexNoRegex() {
        assertEquals("{id}",
                ClientResourceMethod.stripRegex("{id}"));
    }

    @Test
    void testStripRegexMultipleVars() {
        assertEquals("/{group}/{id}",
                ClientResourceMethod.stripRegex("/{group:\\w+}/{id:\\d+}"));
    }

    // --- extractTemplateVariables ---

    @Test
    void testExtractSingleVar() {
        final Set<String> vars =
                ClientResourceMethod.extractTemplateVariables("/items/{id}");
        assertEquals(Set.of("id"), vars);
    }

    @Test
    void testExtractMultipleVars() {
        final Set<String> vars =
                ClientResourceMethod.extractTemplateVariables("/{group}/{id}");
        assertEquals(Set.of("group", "id"), vars);
    }

    @Test
    void testExtractVarWithRegex() {
        final Set<String> vars =
                ClientResourceMethod.extractTemplateVariables("/items/{id:\\d+}");
        assertEquals(Set.of("id"), vars);
    }

    @Test
    void testExtractNoVars() {
        assertTrue(ClientResourceMethod
                .extractTemplateVariables("/plain/path").isEmpty());
    }

}
