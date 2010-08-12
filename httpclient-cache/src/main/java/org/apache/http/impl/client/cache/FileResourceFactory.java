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
package org.apache.http.impl.client.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.InputLimit;
import org.apache.http.client.cache.Resource;
import org.apache.http.client.cache.ResourceFactory;

/**
 * Generates {@link Resource} instances whose body is stored in a temporary file.
 *
 * @since 4.1
 */
@Immutable
public class FileResourceFactory implements ResourceFactory {

    private final File cacheDir;
    private final BasicIdGenerator idgen;

    public FileResourceFactory(final File cacheDir) {
        super();
        this.cacheDir = cacheDir;
        this.idgen = new BasicIdGenerator();
    }

    private File generateUniqueCacheFile(final String requestId) {
        StringBuilder buffer = new StringBuilder();
        this.idgen.generate(buffer);
        buffer.append('.');
        int len = Math.min(requestId.length(), 100);
        for (int i = 0; i < len; i++) {
            char ch = requestId.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '.') {
                buffer.append(ch);
            } else {
                buffer.append('-');
            }
        }
        return new File(this.cacheDir, buffer.toString());
    }

    public Resource generate(
            final String requestId,
            final InputStream instream,
            final InputLimit limit) throws IOException {
        File file = generateUniqueCacheFile(requestId);
        FileOutputStream outstream = new FileOutputStream(file);
        try {
            byte[] buf = new byte[2048];
            long total = 0;
            int l;
            while ((l = instream.read(buf)) != -1) {
                outstream.write(buf, 0, l);
                total += l;
                if (limit != null && total > limit.getValue()) {
                    limit.reached();
                    break;
                }
            }
        } finally {
            outstream.close();
        }
        return new FileResource(file);
    }

    public Resource copy(
            final String requestId,
            final Resource resource) throws IOException {
        File file = generateUniqueCacheFile(requestId);

        if (resource instanceof FileResource) {
            File src = ((FileResource) resource).getFile();
            IOUtils.copyFile(src, file);
        } else {
            FileOutputStream out = new FileOutputStream(file);
            IOUtils.copyAndClose(resource.getInputStream(), out);
        }
        return new FileResource(file);
    }

}
