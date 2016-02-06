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
package org.apache.http.client.fluent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.http.Consts;
import org.apache.http.entity.ContentType;

/**
 * This class represents arbitrary content of a specific type that can be consumed
 * multiple times and requires no explicit deallocation.
 */
public class Content {

    public static final Content NO_CONTENT = new Content(new byte[] {}, ContentType.DEFAULT_BINARY);

    private final byte[] raw;
    private final ContentType type;

    public Content(final byte[] raw, final ContentType type) {
        super();
        this.raw = raw;
        this.type = type;
    }

    public ContentType getType() {
        return this.type;
    }

    public byte[] asBytes() {
        return this.raw.clone();
    }

    public String asString() {
        Charset charset = this.type.getCharset();
        if (charset == null) {
            charset = Consts.ISO_8859_1;
        }
        return asString(charset);
    }

    /**
     * @since 4.4
     */
    public String asString(final Charset charset) {
        return new String(this.raw, charset);
    }

    public InputStream asStream() {
        return new ByteArrayInputStream(this.raw);
    }

    @Override
    public String toString() {
        return asString();
    }

}
