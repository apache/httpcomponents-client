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

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Args;

/**
 * Content of response message that can be represented either
 * as a {@link JsonNode}, a string or a byte array.
 *
 * @since 5.7
 */
final class RestContent {

    static RestContent create(final ObjectMapper objectMapper, final JsonNode jsonNode, final ContentType contentType) {
        return new RestContent(objectMapper, jsonNode, null, null, contentType);
    }

    static RestContent create(final ObjectMapper objectMapper, final String string, final ContentType contentType) {
        return new RestContent(objectMapper, null, string, null, contentType);
    }

    static RestContent create(final ObjectMapper objectMapper, final byte[] bytes, final ContentType contentType) {
        return new RestContent(objectMapper, null, null, bytes, contentType);
    }

    private final ObjectMapper objectMapper;
    private final JsonNode jsonNode;
    private final String string;
    private final byte[] bytes;
    private final ContentType type;

    private RestContent(final ObjectMapper objectMapper,
                        final JsonNode jsonNode,
                        final String string,
                        final byte[] bytes,
                        final ContentType type) {
        this.objectMapper = Args.notNull(objectMapper, "Object mapper");
        this.jsonNode = jsonNode;
        this.string = string;
        this.bytes = bytes;
        this.type = type;
    }

    public ContentType getType() {
        return type;
    }

    public byte[] asByteArray() throws IOException {
        if (bytes != null) {
            return bytes;
        } else if (string != null) {
            final Charset charset = ContentType.getCharset(type, StandardCharsets.UTF_8);
            return string.getBytes(charset);
        } else if (jsonNode != null) {
            if (jsonNode.isBinary()) {
                return jsonNode.binaryValue();
            } else {
                return objectMapper.writeValueAsBytes(jsonNode);
            }
        } else {
            return null;
        }
    }

    public String asString() throws IOException {
        if (string != null) {
            return string;
        } else if (bytes != null) {
            final Charset charset = ContentType.getCharset(type, StandardCharsets.UTF_8);
            return new String(bytes, charset);
        } else if (jsonNode != null) {
            if (jsonNode.isTextual()) {
                return jsonNode.textValue();
            } else {
                return objectMapper.writeValueAsString(jsonNode);
            }
        } else {
            return null;
        }
    }

    public JsonNode asJsonNode() throws IOException {
        if (jsonNode != null) {
            return jsonNode;
        } else if (bytes != null) {
            return objectMapper.readTree(bytes);
        } else if (string != null) {
            return objectMapper.readTree(string);
        } else {
            return null;
        }
    }

    public boolean isJsonNode() {
        return jsonNode != null;
    }

    public boolean isString() {
        return string != null;
    }

    public boolean isByteArray() {
        return bytes != null;
    }

    public boolean hasBody() {
        if (jsonNode != null) {
            return !jsonNode.isMissingNode();
        } else if (string != null) {
            return true;
        } if (bytes != null) {
            return true;
        }
        return false;
    }

    public Object getBody() {
        if (jsonNode != null) {
            return jsonNode;
        } else if (string != null) {
            return string;
        } if (bytes != null) {
            return bytes;
        } else {
            return null;
        }
    }

    public Object decodeBody(final Class<?> rawType, final JavaType javaType) throws IOException {
        if (rawType == void.class || rawType == Void.class) {
            return null;
        }
        if (rawType == JsonNode.class) {
            return asJsonNode();
        }
        if (rawType == String.class) {
            return asString();
        }
        if (rawType == byte[].class) {
            return asByteArray();
        }
        if (!hasBody()) {
            return null;
        }
        if (javaType != null) {
            final ObjectReader objectReader = objectMapper.readerFor(javaType);
            if (jsonNode != null) {
                return objectReader.readValue(jsonNode);
            } else if (string != null) {
                return objectReader.readValue(string);
            } else if (bytes != null) {
                return objectReader.readValue(bytes);
            }
            return null;
        }
        if (jsonNode != null) {
            return objectMapper.treeToValue(jsonNode, rawType);
        } else if (string != null) {
            return objectMapper.readValue(string, rawType);
        } else if (bytes != null) {
            return objectMapper.readValue(bytes, rawType);
        }
        return null;
    }

    public Object decodeBody(final Class<?> rawType, final Type genericType) throws IOException {
        return decodeBody(rawType, genericType != null ? objectMapper.getTypeFactory().constructType(genericType) : null);
    }

    @Override
    public String toString() {
        return "RestContent{" +
                "jsonNode=" + isJsonNode() +
                ", string=" + isString() +
                ", bytes=" + isByteArray() +
                ", type=" + type +
                '}';
    }

}