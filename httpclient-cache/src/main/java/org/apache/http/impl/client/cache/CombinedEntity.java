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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.cache.Resource;
import org.apache.http.entity.AbstractHttpEntity;

@NotThreadSafe
class CombinedEntity extends AbstractHttpEntity {

    private final Resource resource;
    private final InputStream combinedStream;

    CombinedEntity(final Resource resource, final InputStream instream) throws IOException {
        super();
        this.resource = resource;
        this.combinedStream = new SequenceInputStream(
                new ResourceStream(resource.getInputStream()), instream);
    }

    public long getContentLength() {
        return -1;
    }

    public boolean isRepeatable() {
        return false;
    }

    public boolean isStreaming() {
        return true;
    }

    public InputStream getContent() throws IOException, IllegalStateException {
        return this.combinedStream;
    }

    public void writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        InputStream instream = getContent();
        try {
            int l;
            byte[] tmp = new byte[2048];
            while ((l = instream.read(tmp)) != -1) {
                outstream.write(tmp, 0, l);
            }
        } finally {
            instream.close();
        }
    }

    private void dispose() {
        this.resource.dispose();
    }

    class ResourceStream extends FilterInputStream {

        protected ResourceStream(final InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                dispose();
            }
        }

    }

}
