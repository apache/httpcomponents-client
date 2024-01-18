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
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Generates {@link Resource} instances whose body is stored in a temporary file.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class FileResourceFactory implements ResourceFactory {

    private final File cacheDir;
    private final BasicIdGenerator idgen;

    public FileResourceFactory(final File cacheDir) {
        super();
        this.cacheDir = cacheDir;
        this.idgen = new BasicIdGenerator();
    }

    private File generateUniqueCacheFile(final String requestId) {
        final StringBuilder buffer = new StringBuilder();
        this.idgen.generate(buffer);
        buffer.append('.');
        final int len = Math.min(requestId.length(), 100);
        for (int i = 0; i < len; i++) {
            final char ch = requestId.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '.') {
                buffer.append(ch);
            } else {
                buffer.append('-');
            }
        }
        return new File(this.cacheDir, buffer.toString());
    }

    @Override
    public Resource generate(
            final String requestId,
            final byte[] content, final int off, final int len) throws ResourceIOException {
        Args.notNull(requestId, "Request id");
        final File file = generateUniqueCacheFile(requestId);
        try (FileOutputStream outStream = new FileOutputStream(file)) {
            if (content != null) {
                outStream.write(content, off, len);
            }
        } catch (final IOException ex) {
            throw new ResourceIOException(ex.getMessage(), ex);
        }
        return new FileResource(file);
    }

    @Override
    public Resource generate(final String requestId, final byte[] content) throws ResourceIOException {
        Args.notNull(content, "Content");
        return generate(requestId, content, 0, content.length);
    }

    /**
     * @deprecated Do not use.
     */
    @Deprecated
    @Override
    public Resource copy(
            final String requestId,
            final Resource resource) throws ResourceIOException {
        return resource;
    }

}
