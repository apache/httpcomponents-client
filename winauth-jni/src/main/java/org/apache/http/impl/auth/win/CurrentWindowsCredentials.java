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

package org.apache.http.impl.auth.win;

import java.io.Serializable;
import java.security.Principal;

import org.apache.http.annotation.Immutable;
import org.apache.http.auth.Credentials;

import com.sun.jna.platform.win32.Secur32Util;
import com.sun.jna.platform.win32.Secur32.EXTENDED_NAME_FORMAT;

/**
 * Returns the current Windows user credentials
 * <p/>
 * EXPERIMENTAL
 *
 * @since 4.3
 */
@Immutable
public class CurrentWindowsCredentials implements Credentials, Serializable, Principal {

    private static final long serialVersionUID = 4361166468529298169L;

    /**
     * Get the SAM-compatible username of the currently logged-on user.
     *
     * @return String.
     */
    public static String getCurrentUsername() {
        return Secur32Util.getUserNameEx(EXTENDED_NAME_FORMAT.NameSamCompatible);
    }

    private CurrentWindowsCredentials() {
    }

    private static class LazyHolder {
        private static final CurrentWindowsCredentials INSTANCE = new CurrentWindowsCredentials();
    }

    public static CurrentWindowsCredentials get() {
        return LazyHolder.INSTANCE;
    }

    public Principal getUserPrincipal() {
        return this;
    }

    @Override
    public int hashCode() {
        return 245678; // always the same?
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o instanceof CurrentWindowsCredentials) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return getCurrentUsername();
    }

    /**
     * Returns an empty password
     */
    public String getPassword() {
        return "";
    }

    public String getName() {
        return getCurrentUsername();
    }

}



