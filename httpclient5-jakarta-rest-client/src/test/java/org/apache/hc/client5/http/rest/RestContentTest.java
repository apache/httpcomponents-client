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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.apache.hc.core5.http.ContentType;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestContentTest {

    ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
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

    static Stuff generateStuff() {
        final Stuff stuff = new Stuff();
        stuff.setText("Some text");
        stuff.setList(Arrays.asList("this", "that"));
        stuff.setNumber(BigDecimal.valueOf(123.5));
        return stuff;
    }

    static Stuff generateMoreStuff() {
        final Stuff stuff = new Stuff();
        stuff.setText("Some other text");
        stuff.setList(Arrays.asList("this", "that", "and what not"));
        stuff.setNumber(BigDecimal.valueOf(55.25));
        return stuff;
    }

    @Test
    void testJsonNodeContent() throws Exception {
        final Stuff stuff = generateStuff();

        final JsonNode jsonNode = objectMapper.valueToTree(stuff);

        final RestContent content = RestContent.create(objectMapper, jsonNode, ContentType.APPLICATION_JSON);

        Assertions.assertThat(content).isNotNull().satisfies(c -> {
            Assertions.assertThat(c.asJsonNode()).isSameAs(jsonNode);
            Assertions.assertThat(c.asByteArray()).containsSequence(objectMapper.writeValueAsBytes(stuff));
            Assertions.assertThat(c.asString()).isEqualTo(objectMapper.writeValueAsString(stuff));
        });
    }

    @Test
    void testJsonByteArrayContent() throws Exception {
        final byte[] bytes = "some stuff with funny characters \u00e9".getBytes(StandardCharsets.ISO_8859_1);

        final RestContent content = RestContent.create(objectMapper, bytes, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.ISO_8859_1));

        Assertions.assertThat(content).isNotNull().satisfies(c -> {
            Assertions.assertThat(c.asByteArray()).isSameAs(bytes);
            Assertions.assertThat(c.asString()).isEqualTo(new String(bytes, StandardCharsets.ISO_8859_1));
            Assertions.assertThatThrownBy(c::asJsonNode).isInstanceOf(JsonParseException.class);
        });
    }

    @Test
    void testJsonStringContent() throws Exception {
        final String string = "some stuff with funny characters \u00e9";

        final RestContent content = RestContent.create(objectMapper, string, ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8));

        Assertions.assertThat(content).isNotNull().satisfies(c -> {
            Assertions.assertThat(c.asByteArray()).containsSequence(string.getBytes(StandardCharsets.UTF_8));
            Assertions.assertThat(c.asString()).isEqualTo(string);
            Assertions.assertThatThrownBy(c::asJsonNode).isInstanceOf(JsonParseException.class);
        });
    }

    @Test
    void testJsonBinaryNodeContent() throws Exception {
        final byte[] bytes = "some stuff with funny characters \u00e9".getBytes(StandardCharsets.ISO_8859_1);
        final JsonNode jsonNode = new BinaryNode(bytes);

        final RestContent content = RestContent.create(objectMapper, jsonNode, ContentType.APPLICATION_JSON);

        Assertions.assertThat(content).isNotNull().satisfies(c -> {
            Assertions.assertThat(c.asJsonNode()).isSameAs(jsonNode);
            Assertions.assertThat(c.asByteArray()).containsSequence(bytes);
            Assertions.assertThat(c.asString()).isEqualTo(objectMapper.writeValueAsString(jsonNode));
        });
    }

    @Test
    void testJsonTextNodeContent() throws Exception {
        final String string = "some stuff with funny characters \u00e9";
        final JsonNode jsonNode = new TextNode(string);

        final RestContent content = RestContent.create(objectMapper, jsonNode, ContentType.APPLICATION_JSON);

        Assertions.assertThat(content).isNotNull().satisfies(c -> {
            Assertions.assertThat(c.asJsonNode()).isSameAs(jsonNode);
            Assertions.assertThat(c.asByteArray()).containsSequence(string.getBytes(StandardCharsets.UTF_8));
            Assertions.assertThat(c.asString()).isEqualTo(string);
        });
    }

    @Test
    void testJsonContentDecodeFromBytes() throws Exception {
        final Stuff stuff = generateStuff();

        final byte[] bytes = objectMapper.writeValueAsBytes(stuff);

        final RestContent content = RestContent.create(objectMapper, bytes, ContentType.APPLICATION_JSON);

        final Object result = content.decodeBody(Stuff.class, null);

        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.type(Stuff.class))
                .usingRecursiveComparison()
                .isEqualTo(stuff);
    }

    @Test
    void testJsonContentDecodeFromString() throws Exception {
        final Stuff stuff = generateStuff();

        final String string = objectMapper.writeValueAsString(stuff);

        final RestContent content = RestContent.create(objectMapper, string, ContentType.APPLICATION_JSON);

        final Object result = content.decodeBody(Stuff.class, null);

        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.type(Stuff.class))
                .usingRecursiveComparison()
                .isEqualTo(stuff);
    }

    @Test
    void testJsonContentDecodeFromJsonNode() throws Exception {
        final Stuff stuff = generateStuff();

        final JsonNode jsonNode = objectMapper.valueToTree(stuff);

        final RestContent content = RestContent.create(objectMapper, jsonNode, ContentType.APPLICATION_JSON);

        final Object result = content.decodeBody(Stuff.class, null);

        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.type(Stuff.class))
                .usingRecursiveComparison()
                .isEqualTo(stuff);
    }

    @Test
    void testPlainTextContentDecodeToStringFromString() throws Exception {
        final String string = "some stuff with funny characters \u00e9";

        final RestContent content = RestContent.create(objectMapper, string, ContentType.TEXT_PLAIN);

        final Object result = content.decodeBody(String.class, null);

        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.type(String.class))
                .isEqualTo(string);
    }

    @Test
    void testPlainTextContentDecodeToStringFromBytes() throws Exception {
        final byte[] bytes = "some stuff with funny characters \u00e9".getBytes(StandardCharsets.UTF_8);

        final RestContent content = RestContent.create(objectMapper, bytes, ContentType.TEXT_PLAIN);

        final Object result = content.decodeBody(String.class, null);

        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.type(String.class))
                .isEqualTo(new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void testJsonContentDecodeToStringFromJsonNode() throws Exception {
        final Stuff stuff = generateStuff();

        final JsonNode jsonNode = objectMapper.valueToTree(stuff);

        final RestContent content = RestContent.create(objectMapper, jsonNode, ContentType.APPLICATION_JSON);

        final Object result = content.decodeBody(String.class, null);

        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.type(String.class))
                .isEqualTo(objectMapper.writeValueAsString(stuff));
    }

    @Test
    void testGenericJsonContentDecodeFromBytes() throws Exception {
        final List<Stuff> stuff = Arrays.asList(generateStuff(), generateMoreStuff());

        final byte[] bytes = objectMapper.writeValueAsBytes(stuff);

        final RestContent content = RestContent.create(objectMapper, bytes, ContentType.APPLICATION_JSON);

        final Object result = content.decodeBody(List.class, objectMapper.getTypeFactory().constructType(new TypeReference<List<Stuff>>() {
        }));

        Assertions.assertThat(result)
                .isNotNull()
                .asInstanceOf(InstanceOfAssertFactories.list(Stuff.class))
                .satisfiesExactly(s1 -> {
                            Assertions.assertThat(s1)
                                    .isNotNull()
                                    .usingRecursiveComparison()
                                    .isEqualTo(stuff.get(0));
                        },
                        s2 -> {
                            Assertions.assertThat(s2)
                                    .isNotNull()
                                    .usingRecursiveComparison()
                                    .isEqualTo(stuff.get(1));
                        });
    }

}
