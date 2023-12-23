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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.github.luben.zstd.ZstdOutputStream;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;

public class TestZstd {


    @Test
    public void testDecompressionWithBrotli() throws Exception {

        final String originalString = "test Zstd\n";
        final byte[] originalBytes = originalString.getBytes(StandardCharsets.UTF_8);

        // Compress using Zstd
        final ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (ZstdOutputStream zstdOut = new ZstdOutputStream(compressedOut)) {
            zstdOut.write(originalBytes);
        }
        final byte[] compressedBytes = compressedOut.toByteArray();


        // Test
        final HttpEntity entity = new ZstdDecompressingEntity(new ByteArrayEntity(compressedBytes, null));
        assertEquals(originalString, EntityUtils.toString(entity));
    }

}
