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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.net.PercentCodec;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

/**
 * Generates {@link Resource} instances whose body is stored in a temporary file.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class FileResourceFactory implements ResourceFactory {

    private final File cacheDir;

    public FileResourceFactory(final File cacheDir) {
        super();
        this.cacheDir = cacheDir;
    }

    static String generateUniqueCacheFileName(final String requestId, final String eTag, final byte[] content, final int off, final int len) {
        final StringBuilder buf = new StringBuilder();
        if (eTag != null) {
            PercentCodec.RFC3986.encode(buf, eTag);
            buf.append('@');
        } else if (content != null) {
            final MessageDigest sha256;
            try {
                sha256 = MessageDigest.getInstance("SHA-256");
            } catch (final NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
            sha256.update(content, off, len);
            buf.append(TextUtils.toHexString(sha256.digest()));
            buf.append('@');
        }
        PercentCodec.RFC3986.encode(buf, requestId);
        return buf.toString();
    }

    /**
     * @since 5.4
     */
    @Override
    public Resource generate(
            final String requestId,
            final String eTag,
            final byte[] content, final int off, final int len) throws ResourceIOException {
        Args.notNull(requestId, "Request id");
        final String filename = generateUniqueCacheFileName(requestId, eTag, content, off, len);
        final File file = new File(cacheDir, filename);
        try (FileOutputStream outStream = new FileOutputStream(file, false)) {
            if (content != null) {
                outStream.write(content, off, len);
            }
        } catch (final IOException ex) {
            throw new ResourceIOException(ex.getMessage(), ex);
        }
        return new FileResource(file);
    }

    @Override
    public Resource generate(final String requestId, final byte[] content, final int off, final int len) throws ResourceIOException {
        if (content != null) {
            return generate(requestId, null, content, off, len);
        }
        return generate(requestId, null, null, 0, 0);
    }

    @Override
    public Resource generate(final String requestId, final byte[] content) throws ResourceIOException {
        if (content != null) {
            return generate(requestId, null, content, 0, content.length);
        }
        return generate(requestId, null, null, 0, 0);
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
