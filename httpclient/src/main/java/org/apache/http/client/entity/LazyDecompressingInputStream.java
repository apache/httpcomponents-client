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

import org.apache.http.annotation.NotThreadSafe;

import java.io.IOException;
import java.io.InputStream;

/**
 * Lazy init InputStream wrapper.
 */
@NotThreadSafe
class LazyDecompressingInputStream extends InputStream {

    private final InputStream wrappedStream;

    private final DecompressingEntity decompressingEntity;

    private InputStream wrapperStream;

    public LazyDecompressingInputStream(
            final InputStream wrappedStream,
            final DecompressingEntity decompressingEntity) {
        this.wrappedStream = wrappedStream;
        this.decompressingEntity = decompressingEntity;
    }

    @Override
    public int read() throws IOException {
        initWrapper();
        return wrapperStream.read();
    }

    @Override
    public int available() throws IOException {
        initWrapper();
        return wrapperStream.available();
    }

    private void initWrapper() throws IOException {
        if (wrapperStream == null) {
            wrapperStream = decompressingEntity.decorate(wrappedStream);
        }
    }

    @Override
    public void close() throws IOException {
        if (wrapperStream != null) {
            wrapperStream.close();
        }
        wrappedStream.close();
    }

}
