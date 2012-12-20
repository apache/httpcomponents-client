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

package org.apache.http.client.entity;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.apache.http.HttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.junit.Test;

public class TestGZip {

    @Test
    public void testGzipDecompressingEntityDoesNotCrashInConstructorAndLeaveInputStreamOpen()
            throws Exception {
        final AtomicBoolean inputStreamIsClosed = new AtomicBoolean(false);
        HttpEntity in = new InputStreamEntity(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("An exception occurred");
            }

            @Override
            public void close() throws IOException {
                inputStreamIsClosed.set(true);
            }

        }, 123);
        GzipDecompressingEntity gunzipe = new GzipDecompressingEntity(in);
        try {
            gunzipe.getContent();
        } catch (IOException e) {
            // As I cannot get the content, GzipDecompressingEntity is supposed
            // to have released everything
            Assert.assertTrue(inputStreamIsClosed.get());
        }
    }

}
