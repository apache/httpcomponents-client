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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.gss.GssCredentials;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.BearerSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.gss.SpnegoSchemeFactory;
import org.apache.hc.client5.testing.compatibility.ContainerImages;
import org.apache.hc.client5.testing.util.SecurityUtils;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;


public class SpnegoTestUtil {

    public static GssCredentials createCredentials(final Subject subject) {
        return SecurityUtils.callAs(subject, new Callable<GssCredentials>() {
            @Override
            public GssCredentials call() throws Exception {
                return new GssCredentials(
                    GSSManager.getInstance().createCredential(GSSCredential.INITIATE_ONLY));
            }
        });
    }

    public static Path createKeytabDir() {
        try {
            return Files.createTempDirectory("keytabs",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("r-xr-xr-x")));
        } catch (final IOException e) {
            return Paths.get("/tmp/keytabs");
        }
    }

    public static Registry<AuthSchemeFactory> getDefaultSpnegoSchemeRegistry() {
        return RegistryBuilder.<AuthSchemeFactory>create()
                .register(StandardAuthScheme.BEARER, BearerSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.BASIC, BasicSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.DIGEST, DigestSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.SPNEGO, SpnegoSchemeFactory.DEFAULT)
                // register other schemes as needed
                .build();
    }

    //Squid does not support mutual auth
    public static Registry<AuthSchemeFactory> getLegacySpnegoSchemeRegistry() {
        return RegistryBuilder.<AuthSchemeFactory>create()
                .register(StandardAuthScheme.BEARER, BearerSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.BASIC, BasicSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.DIGEST, DigestSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.SPNEGO, SpnegoSchemeFactory.LEGACY)
                // register other schemes as needed
                .build();
    }

    public static Subject loginFromKeytab(final String principal, final Path keytabFilePath) {
        final Configuration kerberosConfig = new KeytabConfiguration(principal, keytabFilePath);
        final Subject subject = new Subject();

        final LoginContext lc;
        try {
            lc = new LoginContext("SPNEGOTest", subject, new CallbackHandler() {
                @Override
                public void handle(final Callback[] callbacks)
                        throws IOException, UnsupportedCallbackException {
                    throw new UnsupportedCallbackException(callbacks[0],
                            "Only keytab supported");
                }
            }, kerberosConfig);
            lc.login();
            return subject;
        } catch (final LoginException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates the krb5.conf file with the specified host and port,
     * writes it to a tmp file,
     * and sets the java.security.krb5.conf system property to point to it.
     *
     * @param KdcHostPort
     * @return Path to the updated krb5.conf file
     * @throws IOException
     */
    public static Path prepareKrb5Conf(final String KdcHostPort) throws IOException {
        // Copy krb5.conf to filesystem
        final InputStream krb5 = SpnegoTestUtil.class.getResourceAsStream(
            "/docker/kdc/krb5.conf");
        // replace KDC address
        final String krb5In;
        try (final BufferedReader reader = new BufferedReader(
            new InputStreamReader(krb5, StandardCharsets.UTF_8))) {
            krb5In = reader.lines()
                    .collect(Collectors.joining("\n"));
        }
        final String krb5Out = krb5In.replaceAll(ContainerImages.KDC_SERVER, KdcHostPort);
        final Path tmpKrb5 = Files.createTempDirectory("test_krb_config_dir")
                .resolve("krb5.conf");
        Files.write(tmpKrb5, krb5Out.getBytes(StandardCharsets.UTF_8));
        // Set the copied krb5.conf for java
        System.setProperty("java.security.krb5.conf", tmpKrb5.toString());
        return tmpKrb5;
    }

}
