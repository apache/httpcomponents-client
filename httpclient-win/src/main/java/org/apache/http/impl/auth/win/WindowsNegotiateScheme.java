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

import java.util.Locale;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.impl.auth.AuthSchemeBase;
import org.apache.http.message.BufferedHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.CharArrayBuffer;

import com.sun.jna.platform.win32.Secur32;
import com.sun.jna.platform.win32.Sspi;
import com.sun.jna.platform.win32.Sspi.CredHandle;
import com.sun.jna.platform.win32.Sspi.CtxtHandle;
import com.sun.jna.platform.win32.Sspi.SecBufferDesc;
import com.sun.jna.platform.win32.Sspi.TimeStamp;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.ptr.IntByReference;

/**
 * Auth scheme that makes use of JNA to implement Negotiate & NTLM on Windows Platforms.
 * <p/>
 * This will delegate negotiation to the windows machine.
 * <p/>
 * EXPERIMENTAL
 *
 * @since 4.3
 */
@NotThreadSafe
public class WindowsNegotiateScheme extends AuthSchemeBase {

    public static boolean isAvaliable() {
        String os = System.getProperty("os.name");
        os = os != null ? os.toLowerCase(Locale.US) : null;
        if (os != null && os.contains("windows")) {
            try {
                return Sspi.MAX_TOKEN_SIZE > 0;
            } catch (Exception ignore) { // Likely ClassNotFound
                return false;
            }
        }
        return false;
    }

    // NTLM or Negotiate
    private final String scheme;

    private CredHandle clientCred;
    private CtxtHandle sppicontext;
    private boolean continueNeeded;
    private String challenge;

    public WindowsNegotiateScheme(final String scheme) {
        super();

        this.scheme = (scheme == null) ? AuthSchemes.SPNEGO : scheme;
        this.challenge = null;
        this.continueNeeded = true;
    }

    public void dispose() {
        if (clientCred != null && !clientCred.isNull()) {
            final int rc = Secur32.INSTANCE.FreeCredentialsHandle(clientCred);
            if (WinError.SEC_E_OK != rc) {
                throw new Win32Exception(rc);
            }
        }
        if (sppicontext != null && !sppicontext.isNull()) {
            final int rc = Secur32.INSTANCE.DeleteSecurityContext(sppicontext);
            if (WinError.SEC_E_OK != rc) {
                throw new Win32Exception(rc);
            }
        }
        continueNeeded = true; // waiting
        clientCred = null;
        sppicontext = null;
    }

    @Override
    public void finalize() throws Throwable {
        dispose();
        super.finalize();
    }

    public String getSchemeName() {
        return scheme;
    }

    // String parameters not supported
    public String getParameter(final String name) {
        return null;
    }

    // NTLM/Negotiate do not support authentication realms
    public String getRealm() {
        return null;
    }

    public boolean isConnectionBased() {
        return true;
    }


    @Override
    protected void parseChallenge(
            final CharArrayBuffer buffer,
            final int beginIndex,
            final int endIndex) throws MalformedChallengeException {
        this.challenge = buffer.substringTrimmed(beginIndex, endIndex);

        if (this.challenge.length() == 0) {
            if (clientCred != null) {
                if (continueNeeded) {
                    throw new RuntimeException("Unexpected token");
                }
                dispose();
            }
        }
    }

    @Override
    public Header authenticate(
            final Credentials credentials,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {

        final String response;
        if (clientCred == null) {
            // ?? We don't use the credentials, should we allow anything?
            if (!(credentials instanceof CurrentWindowsCredentials)) {
                throw new InvalidCredentialsException(
                        "Credentials cannot be used for " + getSchemeName() + " authentication: "
                                + credentials.getClass().getName());
            }

            // client credentials handle
            try {
                final String username = CurrentWindowsCredentials.getCurrentUsername();
                final TimeStamp lifetime = new TimeStamp();

                clientCred = new CredHandle();
                final int rc = Secur32.INSTANCE.AcquireCredentialsHandle(username,
                        scheme, Sspi.SECPKG_CRED_OUTBOUND, null, null, null, null,
                        clientCred, lifetime);

                if (WinError.SEC_E_OK != rc) {
                    throw new Win32Exception(rc);
                }

                response = getToken(null, null, username);
            } catch (Throwable t) {
                dispose();
                throw new AuthenticationException("Authentication Failed", t);
            }
        } else if (this.challenge == null || this.challenge.length() == 0) {
            dispose();
            throw new AuthenticationException("Authentication Failed");
        } else {
            try {
                final byte[] continueTokenBytes = Base64.decodeBase64(this.challenge);
                final SecBufferDesc continueTokenBuffer = new SecBufferDesc(
                        Sspi.SECBUFFER_TOKEN, continueTokenBytes);
                response = getToken(this.sppicontext, continueTokenBuffer, "localhost");
            } catch (Throwable t) {
                dispose();
                throw new AuthenticationException("Authentication Failed", t);
            }
        }

        final CharArrayBuffer buffer = new CharArrayBuffer(scheme.length() + 30);
        if (isProxy()) {
            buffer.append(AUTH.PROXY_AUTH_RESP);
        } else {
            buffer.append(AUTH.WWW_AUTH_RESP);
        }
        buffer.append(": ");
        buffer.append(scheme); // NTLM or Negotiate
        buffer.append(" ");
        buffer.append(response);
        return new BufferedHeader(buffer);
    }

    /**
     * @see http://msdn.microsoft.com/en-us/library/windows/desktop/aa375506(v=vs.85).aspx
     */
    private String getToken(
            final CtxtHandle continueCtx,
            final SecBufferDesc continueToken,
            final String targetName) {
        final IntByReference attr = new IntByReference();
        final SecBufferDesc token = new SecBufferDesc(
                Sspi.SECBUFFER_TOKEN, Sspi.MAX_TOKEN_SIZE);

        sppicontext = new CtxtHandle();
        final int rc = Secur32.INSTANCE.InitializeSecurityContext(clientCred,
                continueCtx, targetName, Sspi.ISC_REQ_CONNECTION, 0,
                Sspi.SECURITY_NATIVE_DREP, continueToken, 0, sppicontext, token,
                attr, null);
        switch (rc) {
            case WinError.SEC_I_CONTINUE_NEEDED:
                continueNeeded = true;
                break;
            case WinError.SEC_E_OK:
                dispose(); // Don't keep the context
                continueNeeded = false;
                break;
            default:
                dispose();
                throw new Win32Exception(rc);
        }
        return Base64.encodeBase64String(token.getBytes());
    }

    public boolean isComplete() {
        return !continueNeeded;
    }

    @Deprecated
    public Header authenticate(
            final Credentials credentials,
            final HttpRequest request) throws AuthenticationException {
        return authenticate(credentials, request, null);
    }

}


