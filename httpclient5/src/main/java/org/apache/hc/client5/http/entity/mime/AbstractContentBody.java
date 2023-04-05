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

package org.apache.hc.client5.http.entity.mime;

import java.nio.charset.Charset;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Args;

/**
 *
 * @since 4.0
 */
public abstract class AbstractContentBody implements ContentBody {

    private final ContentType contentType;

    /**
     * @since 4.3
     */
    public AbstractContentBody(final ContentType contentType) {
        super();
        Args.notNull(contentType, "Content type");
        this.contentType = contentType;
    }

    /**
     * @since 4.3
     */
    public ContentType getContentType() {
        return this.contentType;
    }

    @Override
    public String getMimeType() {
        return this.contentType.getMimeType();
    }

    @Override
    public String getMediaType() {
        final String mimeType = this.contentType.getMimeType();
        final int i = mimeType.indexOf('/');
        if (i != -1) {
            return mimeType.substring(0, i);
        }
        return mimeType;
    }

    @Override
    public String getSubType() {
        final String mimeType = this.contentType.getMimeType();
        final int separatorIndex  = mimeType.indexOf('/');
        if (separatorIndex != -1) {
            return mimeType.substring(separatorIndex + 1);
        }
        return null;
    }

    @Override
    public String getCharset() {
        final Charset charset = this.contentType.getCharset();
        return charset != null ? charset.name() : null;
    }

}
