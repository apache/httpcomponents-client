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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;

/**
 * {@link File} backed {@link HttpCacheEntry} that requires explicit deallocation.
 */
@Immutable
class FileCacheEntry extends HttpCacheEntry {

    private static final long serialVersionUID = -8396589100351931966L;

    private final File file;
    private final FileResource resource;

    public FileCacheEntry(
            final Date requestDate,
            final Date responseDate,
            final StatusLine statusLine,
            final Header[] responseHeaders,
            final File file,
            final Set<String> variants) {
        super(requestDate, responseDate, statusLine, responseHeaders, variants);
        this.file = file;
        this.resource = new FileResource(file);
    }

    @Override
    public long getBodyLength() {
        return this.file.length();
    }

    @Override
    public InputStream getBody() throws IOException {
        return new FileInputStream(this.file);
    }

    @Override
    public Resource getResource() {
        return this.resource;
    }

    class FileResource implements Resource {

        private File file;

        FileResource(final File file) {
            super();
            this.file = file;
        }

        public synchronized void dispose() {
            if (this.file != null) {
                this.file.delete();
                this.file = null;
            }
        }

    }

}
