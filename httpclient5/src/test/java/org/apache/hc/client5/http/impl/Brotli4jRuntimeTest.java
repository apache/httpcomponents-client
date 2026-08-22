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
package org.apache.hc.client5.http.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.service.BrotliNativeProvider;
import org.junit.jupiter.api.Test;

class Brotli4jRuntimeTest {

    private static final String LIBRARY_PATH_PROPERTY =
            "brotli4j.library.path";

    @Test
    void unavailableWhenNativeLibraryCannotBeLoaded() throws Exception {
        final URL brotli4jLocation = Brotli4jLoader.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation();
        final URL serviceLocation = BrotliNativeProvider.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation();

        final File missingLibrary = new File(
                "target",
                "missing-brotli-" + UUID.randomUUID());
        final String previousLibraryPath = System.setProperty(
                LIBRARY_PATH_PROPERTY,
                missingLibrary.getAbsolutePath());

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[] {brotli4jLocation, serviceLocation},
                null)) {

            assertNotNull(Class.forName(
                    "com.aayushatharva.brotli4j.Brotli4jLoader",
                    false,
                    classLoader));

            assertFalse(Brotli4jRuntime.available(classLoader));
        } finally {
            if (previousLibraryPath != null) {
                System.setProperty(
                        LIBRARY_PATH_PROPERTY,
                        previousLibraryPath);
            } else {
                System.clearProperty(LIBRARY_PATH_PROPERTY);
            }
        }
    }
}