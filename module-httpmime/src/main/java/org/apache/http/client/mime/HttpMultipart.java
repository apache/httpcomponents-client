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

package org.apache.http.client.mime;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.http.protocol.HTTP;
import org.apache.james.mime4j.field.ContentTypeField;
import org.apache.james.mime4j.field.Field;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.Entity;
import org.apache.james.mime4j.message.Multipart;
import org.apache.james.mime4j.util.CharsetUtil;

/**
 * Extension of the mime4j standard class needed to work around 
 * some formatting issues in the {@link Multipart#writeTo(OutputStream)}
 * method until they have been addressed downstream.
 */
public class HttpMultipart extends Multipart {

    private HttpMultipartMode mode;
    
    public HttpMultipart() {
        super();
        this.mode = HttpMultipartMode.STRICT;
    }
    
    public HttpMultipartMode getMode() {
        return this.mode;
    }

    public void setMode(final HttpMultipartMode mode) {
        this.mode = mode;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        Entity e = getParent();
        
        ContentTypeField cField = (ContentTypeField) e.getHeader().getField(
                Field.CONTENT_TYPE);
        String boundary = cField.getBoundary();
        Charset charset = null;
        
        switch (this.mode) {
        case STRICT:
            charset = MIME.DEFAULT_CHARSET;
            break;
        case BROWSER_COMPATIBLE:
            if (cField.getCharset() != null) {
                charset = CharsetUtil.getCharset(cField.getCharset());
            } else {
                charset = CharsetUtil.getCharset(HTTP.DEFAULT_CONTENT_CHARSET);
            }
            break;
        }
        
        List<?> bodyParts = getBodyParts();

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(out, charset),
                8192);
        
        switch (this.mode) {
        case STRICT:
            writer.write(getPreamble());
            writer.write("\r\n");

            for (int i = 0; i < bodyParts.size(); i++) {
                writer.write("--");
                writer.write(boundary);
                writer.write("\r\n");
                writer.flush();
                BodyPart part = (BodyPart) bodyParts.get(i);
                part.writeTo(out);
                writer.write("\r\n");
            }

            writer.write("--");
            writer.write(boundary);
            writer.write("--\r\n");
            writer.write(getEpilogue());
            writer.write("\r\n");
            writer.flush();
            break;
        case BROWSER_COMPATIBLE:

            // (1) Do not write preamble and epilogue
            // (2) Only write Content-Disposition 
            // (3) Use content charset
            
            writer.write("\r\n");

            for (int i = 0; i < bodyParts.size(); i++) {
                writer.write("--");
                writer.write(boundary);
                writer.write("\r\n");
                writer.flush();
                BodyPart part = (BodyPart) bodyParts.get(i);
                
                Field cd = part.getHeader().getField(MIME.CONTENT_DISPOSITION);
                writer.write(cd.toString());
                writer.write("\r\n");
                writer.write("\r\n");
                writer.flush();
                part.getBody().writeTo(out);
                
                writer.write("\r\n");
            }

            writer.write("--");
            writer.write(boundary);
            writer.write("--\r\n");
            writer.write("\r\n");
            writer.flush();
            break;
        }
    }
    
}
