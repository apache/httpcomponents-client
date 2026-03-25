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

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test that validates Windows Named Pipe (npipe://) connectivity
 * to Docker Desktop via {@code \\.\pipe\docker_engine}.
 * <p>
 * This test requires:
 * <ul>
 *     <li>Windows OS</li>
 *     <li>Docker Desktop running (which exposes {@code \\.\pipe\docker_engine})</li>
 * </ul>
 * <p>
 * The test uses Testcontainers to verify Docker is available, then exercises the
 * HttpClient Named Pipe transport by querying Docker's REST API directly through
 * the named pipe — the same way Docker CLI communicates with the daemon on Windows.
 * </p>
 * <p>
 * This is the Windows equivalent of connecting to {@code /var/run/docker.sock}
 * on Linux/macOS.
 * </p>
 *
 * @since 5.7
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledOnOs(OS.WINDOWS)
class WindowsNamedPipeDockerIT {

    private static final String DOCKER_PIPE = "\\\\.\\pipe\\docker_engine";

    @Test
    @DisplayName("GET /version via named pipe to Docker Desktop")
    void testDockerVersionViaNpipe() throws Exception {
        // Verify Docker is actually available (Testcontainers check)
        Assertions.assertTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker must be available for this test");

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            final HttpGet httpGet = new HttpGet("http://localhost/version");
            httpGet.setConfig(RequestConfig.custom()
                    .setNamedPipe(DOCKER_PIPE)
                    .build());

            client.execute(httpGet, response -> {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode(),
                        "Docker /version should return 200 OK");
                final String body = EntityUtils.toString(response.getEntity());
                Assertions.assertNotNull(body, "Response body should not be null");
                Assertions.assertTrue(body.contains("ApiVersion"),
                        "Docker /version response should contain ApiVersion");
                return null;
            });
        }
    }

    @Test
    @DisplayName("GET /info via named pipe to Docker Desktop")
    void testDockerInfoViaNpipe() throws Exception {
        Assertions.assertTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker must be available for this test");

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            final HttpGet httpGet = new HttpGet("http://localhost/info");
            httpGet.setConfig(RequestConfig.custom()
                    .setNamedPipe(DOCKER_PIPE)
                    .build());

            client.execute(httpGet, response -> {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode(),
                        "Docker /info should return 200 OK");
                final String body = EntityUtils.toString(response.getEntity());
                Assertions.assertNotNull(body, "Response body should not be null");
                Assertions.assertTrue(body.contains("Containers"),
                        "Docker /info response should contain Containers field");
                return null;
            });
        }
    }

    @Test
    @DisplayName("GET /containers/json via named pipe to Docker Desktop")
    void testDockerContainersViaNpipe() throws Exception {
        Assertions.assertTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker must be available for this test");

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            final HttpGet httpGet = new HttpGet("http://localhost/containers/json");
            httpGet.setConfig(RequestConfig.custom()
                    .setNamedPipe(DOCKER_PIPE)
                    .build());

            client.execute(httpGet, response -> {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode(),
                        "Docker /containers/json should return 200 OK");
                final String body = EntityUtils.toString(response.getEntity());
                Assertions.assertNotNull(body, "Response body should not be null");
                // Should be a JSON array (possibly empty)
                Assertions.assertTrue(body.startsWith("["),
                        "Docker /containers/json should return a JSON array");
                return null;
            });
        }
    }

    @Test
    @DisplayName("Start Testcontainer and verify it appears via named pipe")
    void testTestcontainersViaNpipe() throws Exception {
        Assertions.assertTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker must be available for this test");

        // Start a simple container via Testcontainers
        try (final org.testcontainers.containers.GenericContainer<?> container =
                     new org.testcontainers.containers.GenericContainer<>("alpine:3.19")
                             .withCommand("sleep", "30")) {
            container.start();
            final String containerId = container.getContainerId();

            // Now query Docker API through our named pipe transport
            try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                final HttpGet httpGet = new HttpGet(
                        "http://localhost/containers/" + containerId + "/json");
                httpGet.setConfig(RequestConfig.custom()
                        .setNamedPipe(DOCKER_PIPE)
                        .build());

                client.execute(httpGet, response -> {
                    Assertions.assertEquals(HttpStatus.SC_OK, response.getCode(),
                            "Container inspect should return 200 OK");
                    final String body = EntityUtils.toString(response.getEntity());
                    Assertions.assertTrue(body.contains(containerId),
                            "Container inspect should reference the container ID");
                    Assertions.assertTrue(body.contains("Running"),
                            "Container should be in Running state");
                    return null;
                });
            }
        }
    }
}
