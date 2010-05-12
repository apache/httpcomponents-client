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
package org.apache.http.impl.client.cache;

import java.io.IOException;
import java.io.InputStream;

/**
 * A class that presents two inputstreams as a single stream
 *
 * @since 4.1
 */
class CombinedInputStream extends InputStream {

    private final InputStream inputStream1;
    private final InputStream inputStream2;

    /**
     * Take two inputstreams and produce an object that makes them appear as if they
     * are actually a 'single' input stream.
     *
     * @param inputStream1
     *            First stream to read
     * @param inputStream2
     *            Second stream to read
     */
    public CombinedInputStream(InputStream inputStream1, InputStream inputStream2) {
        if (inputStream1 == null)
            throw new IllegalArgumentException("inputStream1 cannot be null");
        if (inputStream2 == null)
            throw new IllegalArgumentException("inputStream2 cannot be null");

        this.inputStream1 = inputStream1;
        this.inputStream2 = inputStream2;
    }

    @Override
    public int available() throws IOException {
        return inputStream1.available() + inputStream2.available();
    }

    @Override
    public int read() throws IOException {
        int result = inputStream1.read();

        if (result == -1)
            result = inputStream2.read();

        return result;
    }
}
