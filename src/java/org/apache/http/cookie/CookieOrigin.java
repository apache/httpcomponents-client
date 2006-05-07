/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.cookie;

public final class CookieOrigin {

	private final String host;
	private final int port;
	private final String path;
	private final boolean secure;
	
	public CookieOrigin(final String host, int port, final String path, boolean secure) {
		super();
        if (host == null) {
            throw new IllegalArgumentException(
                    "Host of origin may not be null");
        }
        if (host.trim().equals("")) {
            throw new IllegalArgumentException(
                    "Host of origin may not be blank");
        }
        if (port < 0) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        if (path == null) {
            throw new IllegalArgumentException(
                    "Path of origin may not be null.");
        }
		this.host = host;
		this.port = port;
		this.path = path;
		this.secure = secure;
	}

	public String getHost() {
		return this.host;
	}

	public String getPath() {
		return this.path;
	}

	public int getPort() {
		return this.port;
	}

	public boolean isSecure() {
		return this.secure;
	}
	
}
