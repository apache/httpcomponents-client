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

package org.apache.hc.client5.http.entity;

import java.io.File;
import java.io.InputStream;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TestEntityBuilder {

    @Test
    public void testBuildEmptyEntity() throws Exception {
        Assertions.assertThrows(IllegalStateException.class, () ->
                EntityBuilder.create().build());
    }

    @Test
    public void testBuildTextEntity() throws Exception {
        final HttpEntity entity = EntityBuilder.create().setText("stuff").build();
        Assertions.assertNotNull(entity);
        Assertions.assertNotNull(entity.getContent());
        Assertions.assertNotNull(entity.getContentType());
        Assertions.assertEquals("text/plain; charset=UTF-8", entity.getContentType());
    }

    @Test
    public void testBuildBinaryEntity() throws Exception {
        final HttpEntity entity = EntityBuilder.create().setBinary(new byte[] {0, 1, 2}).build();
        Assertions.assertNotNull(entity);
        Assertions.assertNotNull(entity.getContent());
        Assertions.assertNotNull(entity.getContentType());
        Assertions.assertEquals("application/octet-stream", entity.getContentType());
    }

    @Test
    public void testBuildStreamEntity() throws Exception {
        final InputStream in = Mockito.mock(InputStream.class);
        final HttpEntity entity = EntityBuilder.create().setStream(in).build();
        Assertions.assertNotNull(entity);
        Assertions.assertNotNull(entity.getContent());
        Assertions.assertNotNull(entity.getContentType());
        Assertions.assertEquals(-1, entity.getContentLength());
        Assertions.assertEquals("application/octet-stream", entity.getContentType());
    }

    @Test
    public void testBuildSerializableEntity() throws Exception {
        final HttpEntity entity = EntityBuilder.create().setSerializable(Boolean.TRUE).build();
        Assertions.assertNotNull(entity);
        Assertions.assertNotNull(entity.getContent());
        Assertions.assertNotNull(entity.getContentType());
        Assertions.assertEquals("application/octet-stream", entity.getContentType());
    }

    @Test
    public void testBuildFileEntity() throws Exception {
        final File file = new File("stuff");
        final HttpEntity entity = EntityBuilder.create().setFile(file).build();
        Assertions.assertNotNull(entity);
        Assertions.assertNotNull(entity.getContentType());
        Assertions.assertEquals("application/octet-stream", entity.getContentType());
    }

    @Test
    public void testExplicitContentProperties() throws Exception {
        final HttpEntity entity = EntityBuilder.create()
            .setContentType(ContentType.APPLICATION_JSON)
            .setContentEncoding("identity")
            .setBinary(new byte[] {0, 1, 2})
            .setText("{\"stuff\"}").build();
        Assertions.assertNotNull(entity);
        Assertions.assertNotNull(entity.getContentType());
        Assertions.assertEquals("application/json; charset=UTF-8", entity.getContentType());
        Assertions.assertNotNull(entity.getContentEncoding());
        Assertions.assertEquals("identity", entity.getContentEncoding());
        Assertions.assertEquals("{\"stuff\"}", EntityUtils.toString(entity));
    }

    @Test
    public void testBuildChunked() throws Exception {
        final HttpEntity entity = EntityBuilder.create().setText("stuff").chunked().build();
        Assertions.assertNotNull(entity);
        Assertions.assertTrue(entity.isChunked());
    }

    @Test
    public void testBuildGZipped() throws Exception {
        final HttpEntity entity = EntityBuilder.create().setText("stuff").gzipCompressed().build();
        Assertions.assertNotNull(entity);
        Assertions.assertNotNull(entity.getContentType());
        Assertions.assertEquals("text/plain; charset=UTF-8", entity.getContentType());
        Assertions.assertNotNull(entity.getContentEncoding());
        Assertions.assertEquals("gzip", entity.getContentEncoding());
    }

}
