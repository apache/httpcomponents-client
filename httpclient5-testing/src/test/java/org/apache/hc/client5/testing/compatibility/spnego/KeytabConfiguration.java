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
package org.apache.hc.client5.testing.compatibility.spnego;

import java.nio.file.Path;
import java.util.HashMap;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

public class KeytabConfiguration extends Configuration {
    private static final String IBM_KRB5_LOGIN_MODULE =
            "com.ibm.security.auth.module.Krb5LoginModule";
    private static final String SUN_KRB5_LOGIN_MODULE =
            "com.sun.security.auth.module.Krb5LoginModule";

    private static final String JAVA_VENDOR_NAME = System.getProperty("java.vendor");
    private static final boolean IS_IBM_JAVA = JAVA_VENDOR_NAME.contains("IBM");

    private final String principal;
    private final Path keytabFilePath;

    public KeytabConfiguration(final String principal, final Path keyTabFilePath) {
        this.principal = principal;
        this.keytabFilePath = keyTabFilePath;
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(final String name) {
        final HashMap<String, Object> options = new HashMap<>();

        if (IS_IBM_JAVA) {
            options.put("principal", principal);
            options.put("useKeyTab", "true");
            options.put("useKeytab", "file://" + keytabFilePath.normalize().toString());
            return new AppConfigurationEntry[] { new AppConfigurationEntry(
                IBM_KRB5_LOGIN_MODULE, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                options) };
        } else {
            options.put("principal", principal);
            options.put("doNotPrompt", "true");
            options.put("useKeyTab", "true");
            options.put("keyTab", keytabFilePath.normalize().toString());
            return new AppConfigurationEntry[] { new AppConfigurationEntry(
                SUN_KRB5_LOGIN_MODULE, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                options) };
        }
    }
}