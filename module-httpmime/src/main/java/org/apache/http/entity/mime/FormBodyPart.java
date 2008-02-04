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

package org.apache.http.entity.mime;

import org.apache.http.entity.mime.content.ContentBody;
import org.apache.james.mime4j.field.Field;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.Header;

/**
 * An extension of the mime4j standard {@link BodyPart} class that 
 * automatically populates the header with standard fields based 
 * on the content description of the enclosed body.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
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
        
        Header header = new RFC822Header();
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
        buffer.append(MIME.CONTENT_DISPOSITION);
        buffer.append(": form-data; name=\"");
        buffer.append(getName());
        buffer.append("\"");
        if (body.getFilename() != null) {
            buffer.append("; filename=\"");
            buffer.append(body.getFilename());
            buffer.append("\"");
        }
        getHeader().addField(Field.parse(buffer.toString()));
    }
    
    protected void generateContentType(final ContentDescriptor desc) {
        if (desc.getMimeType() != null) {
            StringBuilder buffer = new StringBuilder();
            buffer.append(MIME.CONTENT_TYPE);
            buffer.append(": ");
            buffer.append(desc.getMimeType());
            if (desc.getCharset() != null) {
                buffer.append("; charset=");
                buffer.append(desc.getCharset().name());
            }
            getHeader().addField(Field.parse(buffer.toString()));
        }
    }
    
    protected void generateTransferEncoding(final ContentDescriptor desc) {
        if (desc.getTransferEncoding() != null) {
            StringBuilder buffer = new StringBuilder();
            buffer.append(MIME.CONTENT_TRANSFER_ENC);
            buffer.append(": ");
            buffer.append(desc.getTransferEncoding());
            getHeader().addField(Field.parse(buffer.toString()));
        }
    }

}
