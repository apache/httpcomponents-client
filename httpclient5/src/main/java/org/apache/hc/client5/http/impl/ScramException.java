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

package org.apache.hc.client5.http.impl;

import org.apache.hc.client5.http.auth.AuthenticationException;

/**
 * Represents an exception that occurs during SCRAM (Salted Challenge Response Authentication Mechanism) authentication.
 * <p>
 * SCRAM is a family of SASL mechanisms used for secure authentication. This exception is thrown when
 * an error or issue is encountered during the SCRAM authentication process.
 * </p>
 *
 * @since 5.5
 */
public class ScramException extends AuthenticationException {

    private static final long serialVersionUID = 2491660491058647342L;

    /**
     * Constructs a new {@code ScramException} with {@code null} as its detail message.
     * The cause is not initialized and may be subsequently initialized by a call to {@link #initCause}.
     */
    public ScramException() {
        super();
    }

    /**
     * Constructs a new {@code ScramException} with the specified detail message.
     * The cause is not initialized and may be subsequently initialized by a call to {@link #initCause}.
     *
     * @param message the detail message, saved for later retrieval by the {@link #getMessage()} method.
     */
    public ScramException(final String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ScramException} with the specified detail message and cause.
     *
     * @param message the detail message, saved for later retrieval by the {@link #getMessage()} method.
     * @param cause   the cause, saved for later retrieval by the {@link #getCause()} method.
     *                A {@code null} value indicates that the cause is nonexistent or unknown.
     */
    public ScramException(final String message, final Throwable cause) {
        super(message, cause);
    }
}