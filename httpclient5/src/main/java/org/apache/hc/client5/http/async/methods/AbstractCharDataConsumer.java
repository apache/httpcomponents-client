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
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Asserts;

public abstract class AbstractCharDataConsumer implements AsyncDataConsumer {

    private static final ByteBuffer EMPTY_BIN = ByteBuffer.wrap(new byte[0]);

    private final CharBuffer charbuf = CharBuffer.allocate(8192);

    private volatile CharsetDecoder charsetDecoder;

    protected abstract int capacity();

    protected abstract void data(CharBuffer data, boolean endOfStream) throws IOException;

    protected abstract void completed() throws IOException;

    protected final void setCharsetDecoder(final CharsetDecoder charsetDecoder) {
        this.charsetDecoder = charsetDecoder;
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(capacity());
    }

    private void checkResult(final CoderResult result) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
    }

    private void doDecode(final boolean endOfStream) throws IOException {
        charbuf.flip();
        final int chunk = charbuf.remaining();
        if (chunk > 0) {
            data(charbuf, endOfStream);
        }
        charbuf.clear();
    }

    @Override
    public final int consume(final ByteBuffer src) throws IOException {
        Asserts.notNull(charsetDecoder, "Charset decoder");
        while (src.hasRemaining()) {
            checkResult(charsetDecoder.decode(src, charbuf, false));
            doDecode(false);
        }
        return capacity();
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        Asserts.notNull(charsetDecoder, "Charset decoder");
        checkResult(charsetDecoder.decode(EMPTY_BIN, charbuf, true));
        doDecode(false);
        checkResult(charsetDecoder.flush(charbuf));
        doDecode(true);
        completed();
    }

}