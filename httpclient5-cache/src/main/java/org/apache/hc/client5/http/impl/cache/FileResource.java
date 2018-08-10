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
package org.apache.hc.client5.http.impl.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * Cache resource backed by a file.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class FileResource extends Resource {

    private static final long serialVersionUID = 4132244415919043397L;

    private final AtomicReference<File> fileRef;
    private final long len;

    public FileResource(final File file) {
        super();
        Args.notNull(file, "File");
        this.fileRef = new AtomicReference<>(file);
        this.len = file.length();
    }

    File getFile() {
        return this.fileRef.get();
    }

    @Override
    public byte[] get() throws ResourceIOException {
        final File file = this.fileRef.get();
        if (file == null) {
            throw new ResourceIOException("Resouce already dispoased");
        }
        try (final InputStream in = new FileInputStream(file)) {
            final ByteArrayBuffer buf = new ByteArrayBuffer(1024);
            final byte[] tmp = new byte[2048];
            int len;
            while ((len = in.read(tmp)) != -1) {
                buf.append(tmp, 0, len);
            }
            return buf.toByteArray();
        } catch (final IOException ex) {
            throw new ResourceIOException(ex.getMessage(), ex);
        }
    }

    @Override
    public InputStream getInputStream() throws ResourceIOException {
        final File file = this.fileRef.get();
        if (file != null) {
            try {
                return new FileInputStream(file);
            } catch (final FileNotFoundException ex) {
                throw new ResourceIOException(ex.getMessage(), ex);
            }
        }
        throw new ResourceIOException("Resouce already dispoased");
    }

    @Override
    public long length() {
        return len;
    }

    @Override
    public void dispose() {
        final File file = this.fileRef.getAndSet(null);
        if (file != null) {
            file.delete();
        }
    }

}
