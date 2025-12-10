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
package org.apache.hc.client5.http.sse.impl;

/**
 * Parser strategy for SSE entity consumption.
 *
 * <ul>
 *   <li>{@link #CHAR}: Uses a {@code CharBuffer} with UTF-8 decoding and a spec-compliant
 *       line parser. Safer and simpler; good default.</li>
 *   <li>{@link #BYTE}: Uses a {@code ByteBuffer} with byte-level line framing and minimal
 *       string allocation. Can be slightly faster at very high rates.</li>
 * </ul>
 *
 * @since 5.7
 */
public enum SseParser {
    /**
     * CharBuffer → spec reader.
     */
    CHAR,
    /**
     * ByteBuffer → byte-level framing &amp; minimal decode.
     */
    BYTE
}
