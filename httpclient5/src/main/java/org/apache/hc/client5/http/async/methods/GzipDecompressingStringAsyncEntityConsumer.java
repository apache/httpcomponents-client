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
package org.apache.hc.client5.http.async.methods;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * A GZIP-only {@link AsyncEntityConsumer} that decompresses incrementally without buffering the full compressed input.
 * Parses GZIP header and trailer streaming-style using a state machine. Decompressed output is appended to a StringBuilder chunk-by-chunk.
 * Suitable for modest decompressed payloads.
 *
 * @since 5.6
 */
public class GzipDecompressingStringAsyncEntityConsumer implements AsyncEntityConsumer<String> {

    private final StringBuilder resultBuilder = new StringBuilder();
    private FutureCallback<String> callback;
    private List<Header> trailers;
    private String result;
    private Inflater inflater;
    private final byte[] decompressBuffer = new byte[8192];
    private ContentType contentType;

    private enum GzipState {
        HEADER_MAGIC1, HEADER_MAGIC2, HEADER_METHOD, HEADER_FLAGS, HEADER_MTIME1, HEADER_MTIME2, HEADER_MTIME3, HEADER_MTIME4,
        HEADER_XFL, HEADER_OS, HEADER_EXTRA_LEN_LO, HEADER_EXTRA_LEN_HI, HEADER_EXTRA, HEADER_NAME, HEADER_COMMENT,
        HEADER_HCRC_LO, HEADER_HCRC_HI, DATA, TRAILER_CRC1, TRAILER_CRC2, TRAILER_CRC3, TRAILER_CRC4,
        TRAILER_ISIZE1, TRAILER_ISIZE2, TRAILER_ISIZE3, TRAILER_ISIZE4, DONE, ERROR
    }

    private GzipState state = GzipState.HEADER_MAGIC1;
    private int flags = 0;
    private boolean hasHCrc = false;
    private CRC32 headerCrc = new CRC32();
    private CRC32 dataCrc = new CRC32();
    private long uncompressedSize = 0;
    private int varSkip = 0;
    private int extraLen = 0;
    private int hcrc = 0;
    private long crcRead = 0;
    private long isizeRead = 0;

    @Override
    public void streamStart(final EntityDetails entityDetails, final FutureCallback<String> resultCallback) throws HttpException, IOException {
        this.callback = resultCallback;
        this.trailers = new ArrayList<>();
        final String encoding = entityDetails.getContentEncoding();
        if (!"gzip".equalsIgnoreCase(encoding)) {
            throw new HttpException("Unsupported content coding: " + encoding + ". Only GZIP is supported.");
        }
        try {
            this.contentType = ContentType.parse(entityDetails.getContentType());
        } catch (final Exception ex) {
            this.contentType = ContentType.APPLICATION_OCTET_STREAM;
        }
        this.inflater = new Inflater(true); // raw deflate, no wrapper
        this.headerCrc = new CRC32();
        this.dataCrc = new CRC32();
        this.uncompressedSize = 0;
        this.state = GzipState.HEADER_MAGIC1;
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(Integer.MAX_VALUE); // Always ready for more
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        while (src.hasRemaining() && state != GzipState.DONE && state != GzipState.ERROR) {
            if (state.ordinal() < GzipState.DATA.ordinal()) {
                // Parse header incrementally
                final byte b = src.get();
                headerCrc.update(b);
                switch (state) {
                    case HEADER_MAGIC1:
                        if (b != (byte) 0x1f) throw new IOException("Not GZIP format");
                        state = GzipState.HEADER_MAGIC2;
                        break;
                    case HEADER_MAGIC2:
                        if (b != (byte) 0x8b) throw new IOException("Not GZIP format");
                        state = GzipState.HEADER_METHOD;
                        break;
                    case HEADER_METHOD:
                        if (b != (byte) 8) throw new IOException("Compression not deflate");
                        state = GzipState.HEADER_FLAGS;
                        break;
                    case HEADER_FLAGS:
                        flags = b & 0xff;
                        hasHCrc = (flags & 0x02) != 0;
                        state = GzipState.HEADER_MTIME1;
                        break;
                    case HEADER_MTIME1:
                        state = GzipState.HEADER_MTIME2;
                        break;
                    case HEADER_MTIME2:
                        state = GzipState.HEADER_MTIME3;
                        break;
                    case HEADER_MTIME3:
                        state = GzipState.HEADER_MTIME4;
                        break;
                    case HEADER_MTIME4:
                        state = GzipState.HEADER_XFL;
                        break;
                    case HEADER_XFL:
                        state = GzipState.HEADER_OS;
                        break;
                    case HEADER_OS:
                        if ((flags & 0x04) != 0) {
                            state = GzipState.HEADER_EXTRA_LEN_LO;
                        } else if ((flags & 0x08) != 0) {
                            state = GzipState.HEADER_NAME;
                        } else if ((flags & 0x10) != 0) {
                            state = GzipState.HEADER_COMMENT;
                        } else if (hasHCrc) {
                            state = GzipState.HEADER_HCRC_LO;
                        } else {
                            state = GzipState.DATA;
                        }
                        break;
                    case HEADER_EXTRA_LEN_LO:
                        extraLen = b & 0xff;
                        state = GzipState.HEADER_EXTRA_LEN_HI;
                        break;
                    case HEADER_EXTRA_LEN_HI:
                        extraLen |= (b & 0xff) << 8;
                        varSkip = extraLen;
                        state = (varSkip > 0) ? GzipState.HEADER_EXTRA : ((flags & 0x08) != 0 ? GzipState.HEADER_NAME :
                                ((flags & 0x10) != 0 ? GzipState.HEADER_COMMENT : (hasHCrc ? GzipState.HEADER_HCRC_LO : GzipState.DATA)));
                        break;
                    case HEADER_EXTRA:
                        varSkip--;
                        if (varSkip == 0) {
                            state = (flags & 0x08) != 0 ? GzipState.HEADER_NAME :
                                    ((flags & 0x10) != 0 ? GzipState.HEADER_COMMENT : hasHCrc ? GzipState.HEADER_HCRC_LO : GzipState.DATA);
                        }
                        break;
                    case HEADER_NAME:
                        if (b == 0) {
                            state = (flags & 0x10) != 0 ? GzipState.HEADER_COMMENT : hasHCrc ? GzipState.HEADER_HCRC_LO : GzipState.DATA;
                        }
                        break;
                    case HEADER_COMMENT:
                        if (b == 0) {
                            state = hasHCrc ? GzipState.HEADER_HCRC_LO : GzipState.DATA;
                        }
                        break;
                    case HEADER_HCRC_LO:
                        hcrc = b & 0xff;
                        state = GzipState.HEADER_HCRC_HI;
                        break;
                    case HEADER_HCRC_HI:
                        hcrc |= (b & 0xff) << 8;
                        if (hcrc != (int) (headerCrc.getValue() & 0xffff)) throw new IOException("Header CRC mismatch");
                        state = GzipState.DATA;
                        break;
                    default:
                        break;
                }
            } else if (state == GzipState.DATA) {
                // Decompress streaming
                final int len = src.remaining();
                final byte[] input = new byte[len];
                src.get(input);
                inflater.setInput(input);

                boolean doneInflating = false;
                while (!inflater.needsInput() && !doneInflating) {
                    final int bytesInflated;
                    try {
                        bytesInflated = inflater.inflate(decompressBuffer);
                    } catch (final DataFormatException e) {
                        throw new IOException("Decompression error", e);
                    }
                    if (bytesInflated > 0) {
                        resultBuilder.append(new String(decompressBuffer, 0, bytesInflated, StandardCharsets.UTF_8));
                        dataCrc.update(decompressBuffer, 0, bytesInflated);
                        uncompressedSize += bytesInflated;
                    } else if (bytesInflated == 0) {
                        if (inflater.finished()) {
                            state = GzipState.TRAILER_CRC1;
                            doneInflating = true;
                        } else if (inflater.needsDictionary()) {
                            throw new ZipException("GZIP dictionary needed");
                        } else {
                            // No progress, need more input
                            break;
                        }
                    }
                }
                final int used = len - inflater.getRemaining();
                src.position(src.position() - (len - used)); // Rewind to leave remaining bytes for trailer processing
            } else {
                // Parse trailer incrementally
                final byte b = src.get();
                switch (state) {
                    case TRAILER_CRC1:
                        crcRead = b & 0xffL;
                        state = GzipState.TRAILER_CRC2;
                        break;
                    case TRAILER_CRC2:
                        crcRead |= (b & 0xffL) << 8;
                        state = GzipState.TRAILER_CRC3;
                        break;
                    case TRAILER_CRC3:
                        crcRead |= (b & 0xffL) << 16;
                        state = GzipState.TRAILER_CRC4;
                        break;
                    case TRAILER_CRC4:
                        crcRead |= (b & 0xffL) << 24;
                        if (crcRead != dataCrc.getValue()) throw new IOException("Data CRC mismatch");
                        state = GzipState.TRAILER_ISIZE1;
                        break;
                    case TRAILER_ISIZE1:
                        isizeRead = b & 0xffL;
                        state = GzipState.TRAILER_ISIZE2;
                        break;
                    case TRAILER_ISIZE2:
                        isizeRead |= (b & 0xffL) << 8;
                        state = GzipState.TRAILER_ISIZE3;
                        break;
                    case TRAILER_ISIZE3:
                        isizeRead |= (b & 0xffL) << 16;
                        state = GzipState.TRAILER_ISIZE4;
                        break;
                    case TRAILER_ISIZE4:
                        isizeRead |= (b & 0xffL) << 24;
                        if (isizeRead != (uncompressedSize & 0xffffffffL)) throw new IOException("ISIZE mismatch");
                        state = GzipState.DONE;
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (trailers != null) {
            this.trailers.addAll(trailers);
        }
        // Finish any pending decompression (if no trailer or partial)
        boolean doneInflating = false;
        while (state == GzipState.DATA && !doneInflating) {
            final int bytesInflated;
            try {
                bytesInflated = inflater.inflate(decompressBuffer);
            } catch (final DataFormatException e) {
                throw new IOException("Decompression error", e);
            }
            if (bytesInflated > 0) {
                resultBuilder.append(new String(decompressBuffer, 0, bytesInflated, StandardCharsets.UTF_8));
                dataCrc.update(decompressBuffer, 0, bytesInflated);
                uncompressedSize += bytesInflated;
            } else if (bytesInflated == 0) {
                if (inflater.finished()) {
                    state = GzipState.TRAILER_CRC1;
                    doneInflating = true;
                } else {
                    break;
                }
            }
        }
        if (state != GzipState.DONE) {
            throw new IOException("Incomplete GZIP stream: state=" + state);
        }
        result = resultBuilder.toString();
        if (callback != null) {
            callback.completed(result);
        }
    }

    @Override
    public void failed(final Exception cause) {
        if (callback != null) {
            callback.failed(cause);
        }
    }

    @Override
    public void releaseResources() {
        if (inflater != null) {
            inflater.end();
            inflater = null;
        }
        resultBuilder.setLength(0);
    }

    @Override
    public String getContent() {
        return result;
    }
}