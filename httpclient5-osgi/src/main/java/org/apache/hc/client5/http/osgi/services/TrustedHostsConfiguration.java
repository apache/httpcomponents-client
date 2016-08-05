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
package org.apache.hc.client5.http.osgi.services;

/**
 * @since 5.0-alpha2
 */
public interface TrustedHostsConfiguration {

    /**
     * Flag to mark if current configuration has to be processed when creating SSL sessions..
     *
     * @return true if current configuration has to be processed when creating an SSL session, false otherwise.
     */
    boolean isEnabled();

    /**
     * Flag to mark all SSL certificates are blindly trusted by the client.
     *
     * Pay attention on no enabling this feature in production environment as it is totally insecure.
     *
     * @return true if all SSL certificates are blindly trusted by the client, false otherwise.
     */
    boolean trustAll();

    /**
     * The list of trusted hosts for which self-signed certificate is acceptable.
     *
     * @return an array representing the list of trusted hosts for which self-signed certificate is acceptable.
     */
    String[] getTrustedHosts();

}
