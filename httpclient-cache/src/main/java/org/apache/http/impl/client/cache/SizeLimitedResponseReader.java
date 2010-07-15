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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpResponse;

/**
 * @since 4.1
 */
class SizeLimitedResponseReader {

    private final int maxResponseSizeBytes;
    private final HttpResponse response;

    private ByteArrayOutputStream outputStream;
    private InputStream contentInputStream;
    private boolean isTooLarge;
    private boolean responseIsConsumed;
    private byte[] sizeLimitedContent;
    private boolean outputStreamConsumed;

    /**
     * Create an {@link HttpResponse} that is limited in size, this allows for checking
     * the size of objects that will be stored in the cache.
     *
     * @param maxResponseSizeBytes
     *      Maximum size that a response can be to be eligible for cache inclusion
     *
     * @param response
     *      The {@link HttpResponse}
     */
    public SizeLimitedResponseReader(int maxResponseSizeBytes, HttpResponse response) {
        this.maxResponseSizeBytes = maxResponseSizeBytes;
        this.response = response;
    }

    protected boolean isResponseTooLarge() throws IOException {
        if (!responseIsConsumed)
            isTooLarge = consumeResponse();

        return isTooLarge;
    }

    private synchronized boolean consumeResponse() throws IOException {

        if (responseIsConsumed)
            throw new IllegalStateException(
                    "You cannot call this method more than once, because it consumes an underlying stream");

        responseIsConsumed = true;

        HttpEntity entity = response.getEntity();
        if (entity == null)
            return false;

        contentInputStream = entity.getContent();
        int bytes = 0;

        outputStream = new ByteArrayOutputStream();

        int current;

        while (bytes < maxResponseSizeBytes && (current = contentInputStream.read()) != -1) {
            outputStream.write(current);
            bytes++;
        }

        if ((current = contentInputStream.read()) != -1) {
            outputStream.write(current);
            return true;
        }

        return false;
    }

    private synchronized void consumeOutputStream() {
        if (outputStreamConsumed)
            throw new IllegalStateException(
                    "underlying output stream has already been written to byte[]");

        if (!responseIsConsumed)
            throw new IllegalStateException("Must call consumeResponse first.");

        sizeLimitedContent = outputStream.toByteArray();
        outputStreamConsumed = true;
    }

    protected byte[] getResponseBytes() {
        if (!outputStreamConsumed)
            consumeOutputStream();

        return sizeLimitedContent;
    }

    protected HttpResponse getReconstructedResponse() {

        InputStream combinedStream = getCombinedInputStream();

        return constructResponse(response, combinedStream);
    }

    protected InputStream getCombinedInputStream() {
        InputStream input1 = new ByteArrayInputStream(getResponseBytes());
        InputStream input2 = getContentInputStream();
        return new CombinedInputStream(input1, input2);
    }

    protected InputStream getContentInputStream() {
        return contentInputStream;
    }

    protected HttpResponse constructResponse(HttpResponse originalResponse,
            InputStream combinedStream) {
        HttpResponse response = new BasicHttpResponse(originalResponse.getProtocolVersion(),
                HttpStatus.SC_OK, "Success");

        HttpEntity entity = new InputStreamEntity(combinedStream, -1);
        response.setEntity(entity);
        response.setHeaders(originalResponse.getAllHeaders());

        return response;
    }

}
