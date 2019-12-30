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
package org.apache.hc.client5.http.impl.win;

import com.sun.jna.platform.win32.Sspi;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinError;

public final class WindowsNegotiateSchemeGetTokenFail extends WindowsNegotiateScheme {

    public WindowsNegotiateSchemeGetTokenFail(final String schemeName, final String servicePrincipalName) {
        super(schemeName, servicePrincipalName);
    }

    @Override
    String getToken(final Sspi.CtxtHandle continueCtx, final Sspi.SecBufferDesc continueToken, final String targetName) {
        dispose();
        /* We will rather throw SEC_E_TARGET_UNKNOWN because SEC_E_DOWNGRADE_DETECTED is not
         * available on Windows XP and this unit test always fails.
         */
        throw new Win32Exception(WinError.SEC_E_TARGET_UNKNOWN);
    }

}
