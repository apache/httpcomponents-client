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

package org.apache.http.entity.mime;

import org.apache.http.annotation.NotThreadSafe;

import org.apache.http.entity.mime.content.ContentBody;
import org.apache.james.mime4j.descriptor.ContentDescriptor;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.Header;

/**
 * An extension of the mime4j standard {@link BodyPart} class that 
 * automatically populates the header with standard fields based 
 * on the content description of the enclosed body.
 * 
 *
 * @since 4.0
 */
@NotThreadSafe // Entity is @NotThreadSafe
public class FormBodyPart extends BodyPart {

    private final String name;
    
    public FormBodyPart(final String name, final ContentBody body) {
        super();
        if (name == null) {
            throw new IllegalArgumentException("Name may not be null");
        }
        if (body == null) {
            throw new IllegalArgumentException("Body may not be null");
        }
        this.name = name;
        
        Header header = new Header();
        setHeader(header);
        setBody(body);

        generateContentDisp(body);
        generateContentType(body);
        generateTransferEncoding(body);
    }
    
    public String getName() {
        return this.name;
    }
    
    protected void generateContentDisp(final ContentBody body) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("form-data; name=\"");
        buffer.append(getName());
        buffer.append("\"");
        if (body.getFilename() != null) {
            buffer.append("; filename=\"");
            buffer.append(body.getFilename());
            buffer.append("\"");
        }
        addField(MIME.CONTENT_DISPOSITION, buffer.toString());
    }
    
    protected void generateContentType(final ContentDescriptor desc) {
        if (desc.getMimeType() != null) {
            StringBuilder buffer = new StringBuilder();
            buffer.append(desc.getMimeType());
            if (desc.getCharset() != null) {
                buffer.append("; charset=");
                buffer.append(desc.getCharset());
            }
            addField(MIME.CONTENT_TYPE, buffer.toString());
        }
    }
    
    protected void generateTransferEncoding(final ContentDescriptor desc) {
        if (desc.getTransferEncoding() != null) {
            addField(MIME.CONTENT_TRANSFER_ENC, desc.getTransferEncoding());
        }
    }

    private void addField(final String name, final String value) {
        getHeader().addField(new MinimalField(name, value));
    }
    
}
