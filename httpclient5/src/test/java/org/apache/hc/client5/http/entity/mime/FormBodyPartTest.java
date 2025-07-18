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

package org.apache.hc.client5.http.entity.mime;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormBodyPartTest {

    @TempDir
    Path tempDir;

    @Test
    void testFileConstructorCompat() throws Exception {
        final File tmp = Files.createTempFile(tempDir, "test-", "-file.bin").toFile();
        final FileBody obj = new FileBody(tmp, ContentType.APPLICATION_OCTET_STREAM);
        Assertions.assertEquals(tmp.getName(), obj.getFilename());
    }

    @Test
    void testPathConstructorCompat() throws Exception {
        final Path tmp = Files.createTempFile(tempDir, "test-", "-path.bin");
        final PathBody obj = new PathBody(tmp, ContentType.APPLICATION_OCTET_STREAM);
        Assertions.assertEquals(tmp.getFileName().toString(), obj.getFilename());
    }

}
