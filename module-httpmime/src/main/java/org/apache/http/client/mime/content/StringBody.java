/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.client.mime.content;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.mime.MIME;
import org.apache.james.mime4j.message.AbstractBody;

public class StringBody extends AbstractBody implements ContentBody {

    private final byte[] content;
    private final Charset charset;
    
    public StringBody(final String text, Charset charset) throws UnsupportedEncodingException {
        super();
        if (text == null) {
            throw new IllegalArgumentException("Text may not be null");
        }
        if (charset == null) {
            charset = Charset.defaultCharset();
        }
        this.content = text.getBytes(charset.name());
        this.charset = charset;
    }
    
    public StringBody(final String text) throws UnsupportedEncodingException {
        this(text, null);
    }
    
    public Reader getReader() throws IOException {
        return new InputStreamReader(
                new ByteArrayInputStream(this.content),
                this.charset);
    }

    public void writeTo(final OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        IOUtils.copy(new ByteArrayInputStream(this.content), out);
    }

    public String getTransferEncoding() {
        return MIME.ENC_8BIT;
    }

    public Charset getCharset() {
        return this.charset;
    }

    public String getMimeType() {
        return "text/plain";
    }
    
    public long getContentLength() {
        return this.content.length;
    }
    
    public String getFilename() {
        return null;
    }
    
}
