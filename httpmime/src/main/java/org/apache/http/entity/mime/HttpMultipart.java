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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.http.annotation.NotThreadSafe;

import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.protocol.HTTP;
import org.apache.james.mime4j.field.ContentTypeField;
import org.apache.james.mime4j.field.FieldName;
import org.apache.james.mime4j.message.Body;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.Entity;
import org.apache.james.mime4j.message.Header;
import org.apache.james.mime4j.message.MessageWriter;
import org.apache.james.mime4j.message.Multipart;
import org.apache.james.mime4j.parser.Field;
import org.apache.james.mime4j.util.ByteArrayBuffer;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.CharsetUtil;

/**
 * An extension of the mime4j standard {@link Multipart} class, which is
 * capable of operating either in the strict (fully RFC 822, RFC 2045, 
 * RFC 2046 compliant) or the browser compatible modes.
 * 
 *
 * @since 4.0
 */
@NotThreadSafe // parent is @NotThreadSafe
public class HttpMultipart extends Multipart {

    private static ByteArrayBuffer encode(Charset charset, String string) {
        ByteBuffer encoded = charset.encode(CharBuffer.wrap(string));
        ByteArrayBuffer bab = new ByteArrayBuffer(encoded.remaining());
        bab.append(encoded.array(), encoded.position(), encoded.remaining());
        return bab;
    }
    
    private static void writeBytes(ByteArrayBuffer b, OutputStream out) throws IOException {
        out.write(b.buffer(), 0, b.length());
    }
    
    private static void writeBytes(ByteSequence b, OutputStream out) throws IOException {
        if (b instanceof ByteArrayBuffer) {
            writeBytes((ByteArrayBuffer) b, out);
        } else {
            out.write(b.toByteArray());
        }
    }
    
    private static final ByteArrayBuffer CR_LF = encode(MIME.DEFAULT_CHARSET, "\r\n");
    private static final ByteArrayBuffer TWO_DASHES = encode(MIME.DEFAULT_CHARSET, "--");
    
    private HttpMultipartMode mode;
    
    public HttpMultipart(final String subType) {
        super(subType);
        this.mode = HttpMultipartMode.STRICT;
    }
    
    public HttpMultipartMode getMode() {
        return this.mode;
    }

    public void setMode(final HttpMultipartMode mode) {
        this.mode = mode;
    }

    protected Charset getCharset() {
        Entity e = getParent();
        ContentTypeField cField = (ContentTypeField) e.getHeader().getField(
                FieldName.CONTENT_TYPE);
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
        return charset;
    }
    
    protected String getBoundary() {
        Entity e = getParent();
        ContentTypeField cField = (ContentTypeField) e.getHeader().getField(
                FieldName.CONTENT_TYPE);
        return cField.getBoundary();
    }

    private void doWriteTo(
        final HttpMultipartMode mode, 
        final OutputStream out, 
        boolean writeContent) throws IOException {
        
        List<BodyPart> bodyParts = getBodyParts();
        Charset charset = getCharset();

        ByteArrayBuffer boundary = encode(charset, getBoundary());
        
        switch (mode) {
        case STRICT:
            String preamble = getPreamble();
            if (preamble != null && preamble.length() != 0) {
                ByteArrayBuffer b = encode(charset, preamble);
                writeBytes(b, out);
                writeBytes(CR_LF, out);
            }

            for (int i = 0; i < bodyParts.size(); i++) {
                writeBytes(TWO_DASHES, out);
                writeBytes(boundary, out);
                writeBytes(CR_LF, out);

                BodyPart part = bodyParts.get(i);
                Header header = part.getHeader();
                
                List<Field> fields = header.getFields();
                for (Field field: fields) {
                    writeBytes(field.getRaw(), out);
                    writeBytes(CR_LF, out);
                }
                writeBytes(CR_LF, out);
                if (writeContent) {
                    MessageWriter.DEFAULT.writeBody(part.getBody(), out);
                }
                writeBytes(CR_LF, out);
            }
            writeBytes(TWO_DASHES, out);
            writeBytes(boundary, out);
            writeBytes(TWO_DASHES, out);
            writeBytes(CR_LF, out);
            String epilogue = getEpilogue();
            if (epilogue != null && epilogue.length() != 0) {
                ByteArrayBuffer b = encode(charset, epilogue);
                writeBytes(b, out);
                writeBytes(CR_LF, out);
            }
            break;
        case BROWSER_COMPATIBLE:

            // (1) Do not write preamble and epilogue
            // (2) Only write Content-Disposition 
            // (3) Use content charset
            
            for (int i = 0; i < bodyParts.size(); i++) {
                writeBytes(TWO_DASHES, out);
                writeBytes(boundary, out);
                writeBytes(CR_LF, out);
                BodyPart part = bodyParts.get(i);
                
                Field cd = part.getHeader().getField(MIME.CONTENT_DISPOSITION);
                
                StringBuilder s = new StringBuilder();
                s.append(cd.getName());
                s.append(": ");
                s.append(cd.getBody());
                writeBytes(encode(charset, s.toString()), out);
                writeBytes(CR_LF, out);
                writeBytes(CR_LF, out);
                if (writeContent) {
                    MessageWriter.DEFAULT.writeBody(part.getBody(), out);
                }
                writeBytes(CR_LF, out);
            }

            writeBytes(TWO_DASHES, out);
            writeBytes(boundary, out);
            writeBytes(TWO_DASHES, out);
            writeBytes(CR_LF, out);
            break;
        }
    }

    /**
     * Writes out the content in the multipart/form encoding. This method 
     * produces slightly different formatting depending on its compatibility 
     * mode.
     * 
     * @see #getMode()
     */
    public void writeTo(final OutputStream out) throws IOException {
        doWriteTo(this.mode, out, true);
    }

    /**
     * Determines the total length of the multipart content (content length of 
     * individual parts plus that of extra elements required to delimit the parts 
     * from one another). If any of the @{link BodyPart}s contained in this object 
     * is of a streaming entity of unknown length the total length is also unknown.
     * <p/>
     * This method buffers only a small amount of data in order to determine the
     * total length of the entire entity. The content of individual parts is not 
     * buffered.  
     * 
     * @return total length of the multipart entity if known, <code>-1</code> 
     *   otherwise.
     */
    public long getTotalLength() {
        List<?> bodyParts = getBodyParts();

        long contentLen = 0;
        for (int i = 0; i < bodyParts.size(); i++) {
            BodyPart part = (BodyPart) bodyParts.get(i);
            Body body = part.getBody();
            if (body instanceof ContentBody) {
                long len = ((ContentBody) body).getContentLength();
                if (len >= 0) {
                    contentLen += len;
                } else {
                    return -1;
                }
            } else {
                return -1;
            }
        }
            
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            doWriteTo(this.mode, out, false);
            byte[] extra = out.toByteArray();
            return contentLen + extra.length;
        } catch (IOException ex) {
            // Should never happen
            return -1;
        }
    }
    
}
