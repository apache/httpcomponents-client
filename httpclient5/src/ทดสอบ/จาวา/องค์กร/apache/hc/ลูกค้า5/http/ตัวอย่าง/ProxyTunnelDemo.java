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

package org.apache.hc.client5.http.examples;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.classic.ProxyClient;
import org.apache.hc.core5.http.HttpHost;

/**
 * Example code for using {@link ProxyClient} in order to establish a tunnel through an HTTP proxy.
 */
public class ProxyTunnelDemo {

    public final static void main(final String[] args) throws Exception {

        final ProxyClient proxyClient = new ProxyClient();
        final HttpHost target = new HttpHost("www.yahoo.com", 80);
        final HttpHost proxy = new HttpHost("localhost", 8888);
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("user", "pwd".toCharArray());
        try (final Socket socket = proxyClient.tunnel(proxy, target, credentials)) {
            final Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1);
            out.write("GET / HTTP/1.1\r\n");
            out.write("Host: " + target.toHostString() + "\r\n");
            out.write("Agent: whatever\r\n");
            out.write("Connection: close\r\n");
            out.write("\r\n");
            out.flush();
            final BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String line = null;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }
        }
    }

}

