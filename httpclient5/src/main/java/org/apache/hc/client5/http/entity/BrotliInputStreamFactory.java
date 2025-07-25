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
package org.apache.hc.client5.http.entity;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.brotli.dec.BrotliInputStream;

/**
 * {@link InputStreamFactory} for handling Brotli Content Coded responses.
 * @deprecated See {@link org.apache.hc.client5.http.entity.compress.ContentCodecRegistry#decoder(org.apache.hc.client5.http.entity.compress.ContentCoding)}
 * @since 5.2
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Deprecated
public class BrotliInputStreamFactory implements InputStreamFactory {

    /**
     * Canonical token for the deflate content-coding.
     * @since 5.6
     */
    public static final String ENCODING = "br";

    /**
     * Default instance of {@link BrotliInputStreamFactory}.
     */
    private static final BrotliInputStreamFactory INSTANCE = new BrotliInputStreamFactory();

    public static BrotliInputStreamFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public InputStream create(final InputStream inputStream) throws IOException {
        return new BrotliInputStream(inputStream);
    }
}
