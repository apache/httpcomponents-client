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
package org.apache.hc.client5.testing.compatibility;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.apache.hc.client5.http.utils.ByteArrayBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;

public final class ContainerImages {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerImages.class);

    public final static String WEB_SERVER = "test-httpd";
    public final static int HTTP_PORT = 8080;
    public final static int HTTPS_PORT = 8443;
    public final static String PROXY = "test-proxy";
    public final static int PROXY_PORT = 8888;
    public final static int PROXY_PW_PROTECTED_PORT = 8889;

    static final byte[] BYTES = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);

    static byte[] randomData(final int max) {
        final Random random = new Random();
        random.setSeed(System.currentTimeMillis());
        final int n = random.nextInt(max);
        final ByteArrayBuilder builder = new ByteArrayBuilder();
        for (int i = 0; i < n; i++) {
            builder.append(BYTES);
        }
        return builder.toByteArray();
    }

    public static GenericContainer<?> apacheHttpD(final Network network) {
        return new GenericContainer<>(new ImageFromDockerfile()
                .withFileFromClasspath("server-cert.pem", "docker/server-cert.pem")
                .withFileFromClasspath("server-key.pem", "docker/server-key.pem")
                .withFileFromClasspath("httpd.conf", "docker/httpd/httpd.conf")
                .withFileFromClasspath("httpd-ssl.conf", "docker/httpd/httpd-ssl.conf")
                .withFileFromTransferable("111", Transferable.of(randomData(10240)))
                .withFileFromTransferable("222", Transferable.of(randomData(10240)))
                .withFileFromTransferable("333", Transferable.of(randomData(10240)))
                .withDockerfileFromBuilder(builder ->
                        builder
                                .from("httpd:2.4")
                                .env("httpd_home", "/usr/local/apache2")
                                .env("var_dir", "/var/httpd")
                                .env("www_dir", "${var_dir}/www")
                                .env("private_dir", "${www_dir}/private")
                                .run("mkdir ${httpd_home}/ssl")
                                .copy("server-cert.pem", "${httpd_home}/ssl/")
                                .copy("server-key.pem", "${httpd_home}/ssl/")
                                .copy("httpd.conf", "${httpd_home}/conf/")
                                .copy("httpd-ssl.conf", "${httpd_home}/conf/extra/")
                                .copy("111", "${www_dir}/")
                                .copy("222", "${www_dir}/")
                                .copy("333", "${www_dir}/")
                                .run("mkdir -p ${private_dir}")
                                //# user: testuser; pwd: nopassword
                                .run("echo \"testuser:{SHA}0Ybo2sSKJNARW1aNCrLJ6Lguats=\" > ${private_dir}/.htpasswd")
                                .run("echo \"testuser:Restricted Files:73deccd22e07066db8c405e5364335f5\" > ${private_dir}/.htpasswd_digest")
                                .run("echo \"Big Secret\" > ${private_dir}/big-secret.txt")
                                .build()))
                .withNetwork(network)
                .withNetworkAliases(WEB_SERVER)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .withExposedPorts(HTTP_PORT, HTTPS_PORT);
    }

    public static GenericContainer<?> squid(final Network network) {
        return new GenericContainer<>(new ImageFromDockerfile()
                .withFileFromClasspath("squid.conf", "docker/squid/squid.conf")
                .withDockerfileFromBuilder(builder ->
                        builder
                                .from("ubuntu/squid:5.2-22.04_beta")
                                .env("conf_dir", "/etc/squid")
                                .copy("squid.conf", "${conf_dir}/")
                                //# user: squid; pwd: nopassword
                                .run("echo \"squid:\\$apr1\\$.5saX63T\\$cMSoCJPqEfUw9br6zBdSO0\" > ${conf_dir}/htpasswd")
                                .build()))
                .withNetwork(network)
                .withNetworkAliases(PROXY)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .withExposedPorts(PROXY_PORT, PROXY_PW_PROTECTED_PORT);

    }

}
