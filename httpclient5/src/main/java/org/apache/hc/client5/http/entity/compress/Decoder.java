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

package org.apache.hc.client5.http.entity.compress;

import java.io.IOException;
import java.io.InputStream;

/**
 * Pull-side transformer that takes a compressed {@link InputStream} and
 * returns a lazily-decoded view of the same byte sequence.
 *
 * <p>Implementations <strong>must</strong> return a stream that honours the
 * usual {@code InputStream} contract and propagates {@link IOException}s
 * raised by the underlying transport.</p>
 *
 * @since 5.6
 */
@FunctionalInterface
public interface Decoder {

    /**
     * Wraps the supplied source stream in a decoding stream that transparently
     * produces the uncompressed data.
     *
     * @param src the source {@code InputStream} positioned at the start of the
     *            encoded payload (never {@code null})
     * @return a new, undecoded {@code InputStream}; the caller is responsible
     * for closing it
     * @throws IOException if the decoding stream cannot be created or an
     *                     underlying I/O error occurs
     */
    InputStream wrap(InputStream src) throws IOException;
}


