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

package org.apache.hc.client5.http.utils;

import java.io.IOException;

/**
 * Signals that an error has occurred during encoding/decoding process.
 * <p>
 * This exception is thrown to indicate that a problem occurred while
 * encoding (such as URL encoding) or decoding data. It is a specific
 * type of {@link IOException} that is used within the Apache HttpComponents
 * to handle errors related to data transformation processes.
 * </p>
 *
 * @since 5.4
 */
public class CodingException extends IOException {


    private static final long serialVersionUID = 1668301205622354315L;

    /**
     * Constructs a new CodingException with the specified detail message.
     *
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method).
     */
    public CodingException(final String message) {
        super(message);
    }

    /**
     * Constructs a new CodingException with the specified detail message and cause.
     * <p>
     * Note that the detail message associated with {@code cause} is not automatically
     * incorporated into this exception's detail message.
     * </p>
     *
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method). (A {@code null} value is
     *                permitted, and indicates that the cause is nonexistent or
     *                unknown.)
     */
    public CodingException(final String message, final Throwable cause) {
        super(message, cause);
        initCause(cause);
    }

}
