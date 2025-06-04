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

package org.apache.hc.client5.http.entity.mime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Args;

/**
 * Binary body part backed by an NIO {@link Path}.
 *
 * @see org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
 *
 * @since 5.6
 */
public class PathBody extends AbstractContentBody {

    private static String getFileName(final Path path) {
        return path != null ? Objects.toString(path.getFileName(), null) : null;
    }

    private final String fileName;

    private final Path path;

    /**
     * Constructs a new instance for a given Path.
     *
     * @param path the source Path.
     */
    public PathBody(final Path path) {
        this(path, ContentType.DEFAULT_BINARY, getFileName(path));
    }

    /**
     * Constructs a new instance for a given Path.
     *
     * @param path        the source Path.
     * @param contentType the content type.
     */
    public PathBody(final Path path, final ContentType contentType) {
        this(path, contentType, getFileName(path));
    }

    /**
     * Constructs a new instance for a given Path.
     *
     * @param path        the source Path.
     * @param contentType the content type.
     * @param fileName    The file name to override the Path's file name.
     */
    public PathBody(final Path path, final ContentType contentType, final String fileName) {
        super(contentType);
        this.path = Args.notNull(path, "path");
        this.fileName = fileName == null ? getFileName(path) : fileName;
    }

    @Override
    public long getContentLength() {
        try {
            return Files.size(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String getFilename() {
        return fileName;
    }

    /**
     * Gets a new input stream.
     *
     * @return a new input stream.
     * @throws IOException if an I/O error occurs
     */
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    /**
     * Gets the source Path.
     *
     * @return the source Path.
     */
    public Path getPath() {
        return path;
    }

    @Override
    public void writeTo(final OutputStream out) throws IOException {
        Files.copy(path, Args.notNull(out, "Output stream"));
    }

}
