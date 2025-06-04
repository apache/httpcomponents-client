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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link PathBody}.
 */
class TestPathBody {

    private static final String DATA = "Hello World!";

    private String fileName;

    private Path path;

    @TempDir
    Path tempDir;

    @BeforeEach
    void beforeEach() throws Exception {
        path = Files.createTempFile(tempDir, "test-", "-path.bin");
        fileName = path.getFileName().toString();
        Files.write(path, DATA.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void testGetCharset() throws Exception {
        assertNull(new PathBody(path).getCharset());
    }

    @Test
    void testGetContentLength() throws Exception {
        assertEquals(DATA.length(), new PathBody(path).getContentLength());
    }

    @Test
    void testGetContentType() throws Exception {
        assertEquals(ContentType.APPLICATION_OCTET_STREAM, new PathBody(path).getContentType());
        assertEquals(ContentType.APPLICATION_ATOM_XML, new PathBody(path, ContentType.APPLICATION_ATOM_XML).getContentType());
        assertEquals(ContentType.APPLICATION_ATOM_XML, new PathBody(path, ContentType.APPLICATION_ATOM_XML, "TheBin").getContentType());
    }

    @Test
    void testGetFileName() throws Exception {
        assertEquals(fileName, new PathBody(path).getFilename());
        assertEquals(fileName, new PathBody(path, ContentType.APPLICATION_ATOM_XML).getFilename());
        assertEquals("TheBin", new PathBody(path, ContentType.APPLICATION_ATOM_XML, "TheBin").getFilename());
    }

    @Test
    void testGetInputStream() throws Exception {
        try (InputStream inputStream = new PathBody(path).getInputStream()) {
            assertEquals(DATA, IOUtils.toString(inputStream, StandardCharsets.US_ASCII));
        }
    }

    @Test
    void testGetMediaType() throws Exception {
        assertEquals("application", new PathBody(path).getMediaType());
        assertEquals("multipart", new PathBody(path, ContentType.MULTIPART_FORM_DATA).getMediaType());
        assertEquals("multipart", new PathBody(path, ContentType.MULTIPART_FORM_DATA, "TheBin").getMediaType());
    }

    @Test
    void testGetMimeType() throws Exception {
        assertEquals("application/octet-stream", new PathBody(path).getMimeType());
        assertEquals("multipart/form-data", new PathBody(path, ContentType.MULTIPART_FORM_DATA).getMimeType());
        assertEquals("multipart/form-data", new PathBody(path, ContentType.MULTIPART_FORM_DATA, "TheBin").getMimeType());
    }

    @Test
    void testGetPath() throws Exception {
        assertEquals(path, new PathBody(path).getPath());
        assertEquals(path, new PathBody(path, ContentType.MULTIPART_FORM_DATA).getPath());
        assertEquals(path, new PathBody(path, ContentType.MULTIPART_FORM_DATA, "TheBin").getPath());
    }

    @Test
    void testGetSubType() throws Exception {
        assertEquals("octet-stream", new PathBody(path).getSubType());
        assertEquals("form-data", new PathBody(path, ContentType.MULTIPART_FORM_DATA).getSubType());
        assertEquals("form-data", new PathBody(path, ContentType.MULTIPART_FORM_DATA, "TheBin").getSubType());
    }

    @Test
    void testPathConstructorPath() throws Exception {
        final PathBody obj = new PathBody(path);
        assertEquals(path.getFileName().toString(), obj.getFilename());
    }

    @Test
    void testPathConstructorPathContentType() throws Exception {
        final PathBody obj = new PathBody(path, ContentType.APPLICATION_OCTET_STREAM);
        assertEquals(path.getFileName().toString(), obj.getFilename());
    }

    @Test
    void testPathConstructorPathContentTypeString() throws Exception {
        final PathBody obj = new PathBody(path, ContentType.APPLICATION_OCTET_STREAM, "TheBin");
        assertEquals("TheBin", obj.getFilename());
    }

}
