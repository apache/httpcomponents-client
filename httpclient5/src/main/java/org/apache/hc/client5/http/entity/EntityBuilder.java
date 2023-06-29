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

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.SerializableEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Builder for {@link HttpEntity} instances.
 * <p>
 * Several setter methods of this builder are mutually exclusive. In case of multiple invocations
 * of the following methods only the last one will have effect:
 * </p>
 * <ul>
 *   <li>{@link #setText(String)}</li>
 *   <li>{@link #setBinary(byte[])}</li>
 *   <li>{@link #setStream(java.io.InputStream)}</li>
 *   <li>{@link #setSerializable(java.io.Serializable)}</li>
 *   <li>{@link #setParameters(java.util.List)}</li>
 *   <li>{@link #setParameters(NameValuePair...)}</li>
 *   <li>{@link #setFile(java.io.File)}</li>
 * </ul>
 *
 * @since 4.3
 */
public class EntityBuilder {

    private String text;
    private byte[] binary;
    private InputStream stream;
    private List<NameValuePair> parameters;
    private Serializable serializable;
    private File file;
    private ContentType contentType;
    private String contentEncoding;
    private boolean chunked;
    private boolean gzipCompressed;

    EntityBuilder() {
        super();
    }

    public static EntityBuilder create() {
        return new EntityBuilder();
    }

    private void clearContent() {
        this.text = null;
        this.binary = null;
        this.stream = null;
        this.parameters = null;
        this.serializable = null;
        this.file = null;
    }

    /**
     * Gets the entity content as a string if set using {@link #setText(String)}.
     *
     * @return the entity content as a string, may be null.
     */
    public String getText() {
        return text;
    }

    /**
     * Sets entity content as a string. This method is mutually exclusive with
     * {@link #setBinary(byte[])},
     * {@link #setStream(java.io.InputStream)},
     * {@link #setSerializable(java.io.Serializable)},
     * {@link #setParameters(java.util.List)},
     * {@link #setParameters(NameValuePair...)}
     * {@link #setFile(java.io.File)} methods.
     *
     * @param text entity content as a string.
     * @return this
     */
    public EntityBuilder setText(final String text) {
        clearContent();
        this.text = text;
        return this;
    }

    /**
     * Gets entity content as a byte array if set using
     * {@link #setBinary(byte[])}.
     *
     * @return entity content as a byte array.
     */
    public byte[] getBinary() {
        return binary;
    }

    /**
     * Sets entity content as a byte array. This method is mutually exclusive with
     * {@link #setText(String)},
     * {@link #setStream(java.io.InputStream)},
     * {@link #setSerializable(java.io.Serializable)},
     * {@link #setParameters(java.util.List)},
     * {@link #setParameters(NameValuePair...)}
     * {@link #setFile(java.io.File)}.
     *
     * @param binary The new entity content as a byte array.
     * @return this
     */
    public EntityBuilder setBinary(final byte[] binary) {
        clearContent();
        this.binary = binary;
        return this;
    }

    /**
     * Gets entity content as an {@link InputStream} if set using
     * {@link #setStream(java.io.InputStream)} method.
     *
     * @return entity content as an {@link InputStream}
     */
    public InputStream getStream() {
        return stream;
    }

    /**
     * Sets entity content as an {@link InputStream}. This method is mutually exclusive with
     * {@link #setText(String)},
     * {@link #setBinary(byte[])},
     * {@link #setSerializable(java.io.Serializable)},
     * {@link #setParameters(java.util.List)},
     * {@link #setParameters(NameValuePair...)}
     * {@link #setFile(java.io.File)}.
     *
     * @param stream The new entity content as an InputStream.
     * @return this
     */
    public EntityBuilder setStream(final InputStream stream) {
        clearContent();
        this.stream = stream;
        return this;
    }

    /**
     * Gets entity content as a parameter list if set using
     * {@link #setParameters(java.util.List)} or
     * {@link #setParameters(NameValuePair...)}.
     *
     * @return entity content as a parameter list.
     */
    public List<NameValuePair> getParameters() {
        return parameters;
    }

    /**
     * Sets entity content as a parameter list. This method is mutually exclusive with
     * {@link #setText(String)},
     * {@link #setBinary(byte[])},
     * {@link #setStream(java.io.InputStream)},
     * {@link #setSerializable(java.io.Serializable)},
     * {@link #setFile(java.io.File)}.
     *
     * @param parameters entity content as a parameter list.
     * @return this
     */
    public EntityBuilder setParameters(final List<NameValuePair> parameters) {
        clearContent();
        this.parameters = parameters;
        return this;
    }

    /**
     * Sets entity content as a parameter list. This method is mutually exclusive with
     * {@link #setText(String)},
     * {@link #setBinary(byte[])},
     * {@link #setStream(java.io.InputStream)},
     * {@link #setSerializable(java.io.Serializable)},
     * {@link #setFile(java.io.File)}.
     *
     * @param parameters entity content as a parameter list.
     * @return this
     */
    public EntityBuilder setParameters(final NameValuePair... parameters) {
        return setParameters(Arrays.asList(parameters));
    }

    /**
     * Gets entity content as a {@link Serializable} if set using
     * {@link #setSerializable(java.io.Serializable)} method.
     *
     * @return entity content as a {@link Serializable}.
     */
    public Serializable getSerializable() {
        return serializable;
    }

    /**
     * Sets entity content as a {@link Serializable}. This method is mutually exclusive with
     * {@link #setText(String)},
     * {@link #setBinary(byte[])},
     * {@link #setStream(java.io.InputStream)},
     * {@link #setParameters(java.util.List)},
     * {@link #setParameters(NameValuePair...)}
     * {@link #setFile(java.io.File)}.
     *
     * @param serializable entity content as a {@link Serializable}.
     * @return this
     */
    public EntityBuilder setSerializable(final Serializable serializable) {
        clearContent();
        this.serializable = serializable;
        return this;
    }

    /**
     * Gets the entity content as a {@link File} if set using
     * {@link #setFile(java.io.File)}.
     *
     * @return Gets the entity content as a {@link File}.
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets entity content as a {@link File}. This method is mutually exclusive with
     * {@link #setText(String)},
     * {@link #setBinary(byte[])},
     * {@link #setStream(java.io.InputStream)},
     * {@link #setParameters(java.util.List)},
     * {@link #setParameters(NameValuePair...)}
     * {@link #setSerializable(java.io.Serializable)}.
     *
     * @param file entity content as a {@link File}.
     * @return this
     */
    public EntityBuilder setFile(final File file) {
        clearContent();
        this.file = file;
        return this;
    }

    /**
     * Gets the {@link ContentType} of the entity, may be null.
     *
     * @return the {@link ContentType} of the entity, may be null.
     */
    public ContentType getContentType() {
        return contentType;
    }

    /**
     * Sets the {@link ContentType} of the entity.
     *
     * @param contentType the {@link ContentType} of the entity, may be null.
     * @return this
     */
    public EntityBuilder setContentType(final ContentType contentType) {
        this.contentType = contentType;
        return this;
    }

    /**
     * Gets the content encoding of the entity, may be null.
     *
     * @return the content encoding of the entity, may be null.
     */
    public String getContentEncoding() {
        return contentEncoding;
    }

    /**
     * Sets the content encoding of the entity.
     *
     * @param contentEncoding the content encoding of the entity, may be null.
     * @return this
     */
    public EntityBuilder setContentEncoding(final String contentEncoding) {
        this.contentEncoding = contentEncoding;
        return this;
    }

    /**
     * Tests if the entity is to be chunk coded ({@code true}), or not ({@code false}).
     *
     * @return {@code true} if entity is to be chunk coded, {@code false} otherwise.
     */
    public boolean isChunked() {
        return chunked;
    }

    /**
     * Sets entities to be chunked.
     * @return this
     */
    public EntityBuilder chunked() {
        this.chunked = true;
        return this;
    }

    /**
     * Tests if entities are to be GZIP compressed ({@code true}), or not ({@code false}).
     *
     * @return {@code true} if entity is to be GZIP compressed, {@code false} otherwise.
     */
    public boolean isGzipCompressed() {
        return gzipCompressed;
    }

    /**
     * Sets entities to be GZIP compressed.
     *
     * @return this
     */
    public EntityBuilder gzipCompressed() {
        this.gzipCompressed = true;
        return this;
    }

    private ContentType getContentOrDefault(final ContentType def) {
        return this.contentType != null ? this.contentType : def;
    }

    /**
     * Builds a new instance of {@link HttpEntity} based on the current state.
     *
     * @return a new instance.
     */
    public HttpEntity build() {
        final AbstractHttpEntity e;
        if (this.text != null) {
            e = new StringEntity(this.text, getContentOrDefault(ContentType.DEFAULT_TEXT), this.contentEncoding,
                this.chunked);
        } else if (this.binary != null) {
            e = new ByteArrayEntity(this.binary, getContentOrDefault(ContentType.DEFAULT_BINARY),
                this.contentEncoding, this.chunked);
        } else if (this.stream != null) {
            e = new InputStreamEntity(this.stream, -1, getContentOrDefault(ContentType.DEFAULT_BINARY),
                this.contentEncoding);
        } else if (this.parameters != null) {
            e = new UrlEncodedFormEntity(this.parameters,
                    this.contentType != null ? this.contentType.getCharset() : null);
        } else if (this.serializable != null) {
            e = new SerializableEntity(this.serializable, ContentType.DEFAULT_BINARY, this.contentEncoding);
        } else if (this.file != null) {
            e = new FileEntity(this.file, getContentOrDefault(ContentType.DEFAULT_BINARY), this.contentEncoding);
        } else {
            throw new IllegalStateException("No entity set");
        }
        if (this.gzipCompressed) {
            return new GzipCompressingEntity(e);
        }
        return e;
    }

}
