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
package org.apache.hc.client5.http.rest;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestContentConsumerTest {

    ObjectMapper objectMapper;
    AtomicReference<Object> resultRef;
    FutureCallback<RestContent> resultCallback;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        resultRef = new AtomicReference<>();
        resultCallback = new FutureCallback<>() {

            @Override
            public void completed(final RestContent result) {
                resultRef.set(result);
            }

            @Override
            public void failed(final Exception ex) {
                resultRef.set(ex);
            }

            @Override
            public void cancelled() {
                resultRef.set(null);
            }

        };
    }

    static class Stuff {

        private String text;
        private List<String> list;
        private BigDecimal number;

        public String getText() {
            return text;
        }

        public void setText(final String text) {
            this.text = text;
        }

        public List<String> getList() {
            return list;
        }

        public void setList(final List<String> list) {
            this.list = list;
        }

        public BigDecimal getNumber() {
            return number;
        }

        public void setNumber(final BigDecimal number) {
            this.number = number;
        }

    }

    @Test
    void testConsumeJsonContent() throws Exception {
        final Stuff stuff = new Stuff();
        stuff.setText("Some text");
        stuff.setList(Arrays.asList("this", "that"));
        stuff.setNumber(BigDecimal.valueOf(123.5));
        final byte[] bytes = objectMapper.writeValueAsBytes(stuff);

        final RestContentConsumer contentConsumer = new RestContentConsumer(objectMapper);

        contentConsumer.streamStart(
                new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
                resultCallback);
        contentConsumer.consume(ByteBuffer.wrap(bytes));
        contentConsumer.streamEnd(null);
        contentConsumer.releaseResources();

        final Object result = resultRef.get();
        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.type(RestContent.class))
                .satisfies(c -> {
                    Assertions.assertThat(c.getType())
                            .extracting(ContentType::getMimeType)
                            .isEqualTo(ContentType.APPLICATION_JSON.getMimeType());
                    Assertions.assertThat(c.isJsonNode()).isTrue();
                });
    }

    @Test
    void testConsumeBinaryContent() throws Exception {
        final byte[] bytes = new byte[] { 'a', 'b', 'c', '1', '2', '3' };

        final RestContentConsumer contentConsumer = new RestContentConsumer(objectMapper);

        contentConsumer.streamStart(
                new BasicEntityDetails(-1, ContentType.APPLICATION_OCTET_STREAM),
                resultCallback);
        contentConsumer.consume(ByteBuffer.wrap(bytes));
        contentConsumer.streamEnd(null);
        contentConsumer.releaseResources();

        final Object result = resultRef.get();
        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.type(RestContent.class))
                .satisfies(c -> {
                    Assertions.assertThat(c.getType())
                            .extracting(ContentType::getMimeType)
                            .isEqualTo(ContentType.APPLICATION_OCTET_STREAM.getMimeType());
                    Assertions.assertThat(c.isByteArray()).isTrue();
                    Assertions.assertThat(c.asByteArray()).containsSequence(bytes);
                });
    }

    @Test
    void testConsumeTextualContent() throws Exception {
        final String string = "abc123\u00e9";
        final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);

        final RestContentConsumer contentConsumer = new RestContentConsumer(objectMapper);

        contentConsumer.streamStart(
                new BasicEntityDetails(-1, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)),
                resultCallback);
        contentConsumer.consume(ByteBuffer.wrap(bytes));
        contentConsumer.streamEnd(null);
        contentConsumer.releaseResources();

        final Object result = resultRef.get();
        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.type(RestContent.class))
                .satisfies(c -> {
                    Assertions.assertThat(c.getType())
                            .extracting(ContentType::getMimeType)
                            .isEqualTo(ContentType.TEXT_PLAIN.getMimeType());
                    Assertions.assertThat(c.isString()).isTrue();
                    Assertions.assertThat(c.asString()).isEqualTo(string);
                });
    }

    @Test
    void testConsumeException() throws Exception {
        final String string = "abc123";
        final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);

        final RestContentConsumer contentConsumer = new RestContentConsumer(objectMapper);

        contentConsumer.streamStart(
                new BasicEntityDetails(-1, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)),
                resultCallback);
        contentConsumer.consume(ByteBuffer.wrap(bytes));
        contentConsumer.failed(new RestResourceException("boom"));
        contentConsumer.releaseResources();

        final Object result = resultRef.get();
        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.type(RestResourceException.class))
                .extracting("message")
                .isEqualTo("boom");
    }

}
