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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * HttpMultipart represents a collection of MIME multipart encoded content bodies.
 *
 * @since 4.3
 */
abstract class AbstractMultipartFormat {

    /**
     * The preamble to be included before the multipart content.
     */
    private String preamble;

    /**
     * The epilogue to be included after the multipart content.
     */
    private String epilogue;

    static ByteArrayBuffer encode(
            final Charset charset, final CharSequence string) {
        final ByteBuffer encoded = charset.encode(CharBuffer.wrap(string));
        final ByteArrayBuffer bab = new ByteArrayBuffer(encoded.remaining());
        bab.append(encoded.array(), encoded.arrayOffset() + encoded.position(), encoded.remaining());
        return bab;
    }

    static void writeBytes(
            final ByteArrayBuffer b, final OutputStream out) throws IOException {
        out.write(b.array(), 0, b.length());
    }

    static void writeBytes(
            final CharSequence s, final Charset charset, final OutputStream out) throws IOException {
        final ByteArrayBuffer b = encode(charset, s);
        writeBytes(b, out);
    }

    static void writeBytes(
            final CharSequence s, final OutputStream out) throws IOException {
        final ByteArrayBuffer b = encode(StandardCharsets.ISO_8859_1, s);
        writeBytes(b, out);
    }

    static boolean isLineBreak(final char ch) {
        return ch == '\r' || ch == '\n' || ch == '\f' || ch == 11;
    }

    static CharSequence stripLineBreaks(final CharSequence s) {
        if (s == null) {
            return null;
        }
        boolean requiresRewrite = false;
        int n = 0;
        for (; n < s.length(); n++) {
            final char ch = s.charAt(n);
            if (isLineBreak(ch)) {
                requiresRewrite = true;
                break;
            }
        }
        if (!requiresRewrite) {
            return s;
        }
        final StringBuilder buf = new StringBuilder();
        buf.append(s, 0, n);
        for (; n < s.length(); n++) {
            final char ch = s.charAt(n);
            if (isLineBreak(ch)) {
                buf.append(' ');
            } else {
                buf.append(ch);
            }
        }
        return buf.toString();
    }

    static void writeField(
            final MimeField field, final OutputStream out) throws IOException {
        writeBytes(stripLineBreaks(field.getName()), out);
        writeBytes(FIELD_SEP, out);
        writeBytes(stripLineBreaks(field.getBody()), out);
        writeBytes(CR_LF, out);
    }

    static void writeField(
            final MimeField field, final Charset charset, final OutputStream out) throws IOException {
        writeBytes(stripLineBreaks(field.getName()), charset, out);
        writeBytes(FIELD_SEP, out);
        writeBytes(stripLineBreaks(field.getBody()), charset, out);
        writeBytes(CR_LF, out);
    }

    static final ByteArrayBuffer FIELD_SEP = encode(StandardCharsets.ISO_8859_1, ": ");
    static final ByteArrayBuffer CR_LF = encode(StandardCharsets.ISO_8859_1, "\r\n");
    static final ByteArrayBuffer TWO_HYPHENS = encode(StandardCharsets.ISO_8859_1, "--");

    final Charset charset;
    final String boundary;

    /**
     * Creates an instance with the specified settings.
     *
     * @param charset  the character set to use. May be {@code null}, in which case {@link StandardCharsets#ISO_8859_1} is used.
     * @param boundary to use  - must not be {@code null}
     * @throws IllegalArgumentException if charset is null or boundary is null
     */
    public AbstractMultipartFormat(final Charset charset, final String boundary) {
        super();
        Args.notNull(boundary, "Multipart boundary");
        this.charset = charset != null ? charset : StandardCharsets.ISO_8859_1;
        this.boundary = boundary;
    }

    /*  */

    /**
     * Constructs a new instance of {@code AbstractMultipartFormat} with the given charset, boundary, preamble, and epilogue.
     *
     * @param charset  the charset to use.
     * @param boundary the boundary string to use.
     * @param preamble the preamble string to use. Can be {@code null}.
     * @param epilogue the epilogue string to use. Can be {@code null}.
     * @throws IllegalArgumentException if the boundary string is {@code null}.
     */
    public AbstractMultipartFormat(final Charset charset, final String boundary, final String preamble, final String epilogue) {
        super();
        Args.notNull(boundary, "Multipart boundary");
        this.charset = charset != null ? charset : StandardCharsets.ISO_8859_1;
        this.boundary = boundary;
        this.preamble = preamble;
        this.epilogue = epilogue;
    }

    public AbstractMultipartFormat(final String boundary) {
        this(null, boundary);
    }

    public abstract List<MultipartPart> getParts();

    /**
     * Writes the multipart message to the specified output stream.
     * <p>
     * If {@code writeContent} is {@code true}, the content of each part will also be written.
     *
     * <p>If {@code preamble} is not {@code null}, it will be written before the first boundary.
     * If {@code epilogue} is not {@code null}, it will be written after the last boundary.
     *
     * @param out          the output stream to write the message to.
     * @param writeContent whether to write the content of each part.
     * @throws IOException if an I/O error occurs.
     */
    void doWriteTo(
            final OutputStream out,
            final boolean writeContent) throws IOException {

        final ByteArrayBuffer boundaryEncoded = encode(this.charset, this.boundary);
        if (this.preamble != null) {
            writeBytes(this.preamble, out);
            writeBytes(CR_LF, out);
        }
        for (final MultipartPart part : getParts()) {
            writeBytes(TWO_HYPHENS, out);
            writeBytes(boundaryEncoded, out);
            writeBytes(CR_LF, out);

            formatMultipartHeader(part, out);

            writeBytes(CR_LF, out);

            if (writeContent) {
                part.getBody().writeTo(out);
            }
            writeBytes(CR_LF, out);
        }
        writeBytes(TWO_HYPHENS, out);
        writeBytes(boundaryEncoded, out);
        writeBytes(TWO_HYPHENS, out);
        writeBytes(CR_LF, out);
        if (this.epilogue != null) {
            writeBytes(this.epilogue, out);
            writeBytes(CR_LF, out);
        }
    }

    /**
     * Write the multipart header fields; depends on the style.
     */
    protected abstract void formatMultipartHeader(
            final MultipartPart part,
            final OutputStream out) throws IOException;

    /**
     * Writes out the content in the multipart/form encoding. This method
     * produces slightly different formatting depending on its compatibility
     * mode.
     */
    public void writeTo(final OutputStream out) throws IOException {
        doWriteTo(out, true);
    }

    /**
     * Determines the total length of the multipart content (content length of
     * individual parts plus that of extra elements required to delimit the parts
     * from one another). If any of the @{link BodyPart}s contained in this object
     * is of a streaming entity of unknown length the total length is also unknown.
     * <p>
     * This method buffers only a small amount of data in order to determine the
     * total length of the entire entity. The content of individual parts is not
     * buffered.
     * </p>
     *
     * @return total length of the multipart entity if known, {@code -1}
     * otherwise.
     */
    public long getTotalLength() {
        long contentLen = 0;
        for (final MultipartPart part : getParts()) {
            final ContentBody body = part.getBody();
            final long len = body.getContentLength();
            if (len >= 0) {
                contentLen += len;
            } else {
                return -1;
            }
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            doWriteTo(out, false);
            final byte[] extra = out.toByteArray();
            return contentLen + extra.length;
        } catch (final IOException ex) {
            // Should never happen
            return -1;
        }
    }

}
