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

/**
 * Client specific HTTP entity implementations for compression and decompression.
 *
 * <p>This package contains classes for handling compression and decompression of HTTP entities
 * using various algorithms, such as GZIP and Deflate. It includes classes for compressing and
 * decompressing HTTP entity content, as well as utilities to handle lazy decompression of input streams.
 *
 * <ul>
 * <li>{@link org.apache.hc.client5.http.entity.compress.CompressingEntity} - A wrapper for {@link org.apache.hc.core5.http.HttpEntity} that applies compression to content.</li>
 * <li>{@link org.apache.hc.client5.http.entity.compress.CompressingFactory} - A factory class to manage compression and decompression formats.</li>
 * <li>{@link org.apache.hc.client5.http.entity.compress.DecompressingEntity} - A wrapper for decompressing HTTP entity content on-the-fly.</li>
 * <li>{@link org.apache.hc.client5.http.entity.compress.LazyDecompressingInputStream} - Input stream that decompresses content only when it's accessed.</li>
 * </ul>
 *
 * <p>These classes use the Apache Commons Compress library to support multiple compression formats.</p>
 *
 * @since 5.5
 */
package org.apache.hc.client5.http.entity.compress;
