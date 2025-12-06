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
 * Internal implementation classes for the SSE client support in
 * {@link org.apache.hc.client5.http.sse}.
 *
 * <p>Types in this package implement the plumbing for
 * {@code text/event-stream} consumption: parsers, entity consumers,
 * event readers, and default {@code EventSource} wiring. They are
 * primarily intended for framework, integration, and testing
 * purposes.</p>
 *
 * <p>Most of these classes are annotated with
 * {@link org.apache.hc.core5.annotation.Internal
 * Internal} and are <strong>not</strong> considered part of the
 * public API. They may change incompatibly between minor releases.</p>
 *
 * <p>Applications should normally depend on the abstractions in
 * {@link org.apache.hc.client5.http.sse}. The following types in this
 * package are provided as reusable convenience implementations and
 * are expected to be used directly by clients:</p>
 *
 * <ul>
 *   <li>{@link org.apache.hc.client5.http.sse.impl.ExponentialJitterBackoff}</li>
 *   <li>{@link org.apache.hc.client5.http.sse.impl.FixedBackoffStrategy}</li>
 *   <li>{@link org.apache.hc.client5.http.sse.impl.NoBackoffStrategy}</li>
 *   <li>{@link org.apache.hc.client5.http.sse.impl.SseParser}</li>
 * </ul>
 *
 * @since 5.7
 */
package org.apache.hc.client5.http.sse.impl;
