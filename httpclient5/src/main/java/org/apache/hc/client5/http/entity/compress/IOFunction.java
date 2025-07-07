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

package org.apache.hc.client5.http.entity.compress;

import java.io.IOException;

import org.apache.hc.core5.annotation.Internal;


/**
 * Minimal equivalent of {@link java.util.function.Function} whose
 * {@link #apply(Object)} method is allowed to throw {@link IOException}.
 * <p>
 * Used internally by the content-coding layer to pass lambdas that wrap /
 * unwrap streams without forcing boiler-plate try/catch blocks.
 * </p>
 *
 * @param <T> input type
 * @param <R> result type
 * @since 5.6
 */
@Internal
@FunctionalInterface
public interface IOFunction<T, R> {

    /**
     * Applies the transformation.
     *
     * @param value source value (never {@code null})
     * @return transformed value
     * @throws IOException if the transformation cannot be performed
     */
    R apply(T value) throws IOException;
}