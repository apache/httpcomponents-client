/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * ====================================================================
 */

package org.apache.http.client.fluent;

import java.io.IOException;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;

public class FluentExecutor {

    public static FluentExecutor newInstance() {
        FluentExecutor fexe = new FluentExecutor();
        return fexe;
    }

    private ThreadSafeClientConnManager localConnManager;
    private SchemeRegistry localSchemeRegistry;
    private SchemeSocketFactory localSocketFactory;

    private FluentExecutor() {
        localSchemeRegistry = SchemeRegistryFactory.createDefault();
        localConnManager = new ThreadSafeClientConnManager(localSchemeRegistry);
        localSocketFactory = PlainSocketFactory.getSocketFactory();

    }

    public FluentResponse exec(FluentRequest req)
            throws ClientProtocolException, IOException {
        DefaultHttpClient client = getClient();
        client.setCredentialsProvider(req.credentialsProvider);
        client.setParams(req.localParams);
        HttpResponse resp = client.execute(req, req.localContext);
        FluentResponse fresp = new FluentResponse(resp);
        return fresp;
    }

    public FluentResponse[] exec(final FluentRequest[] reqs)
            throws InterruptedException {
        if (reqs == null)
            throw new NullPointerException("The request array may not be null.");
        int length = reqs.length;
        if (length == 0)
            return new FluentResponse[0];
        FluentResponse[] resps = new FluentResponse[length];
        MultiRequestThread[] threads = new MultiRequestThread[length];
        for (int id = 0; id < length; id++) {
            threads[id] = new MultiRequestThread(this, reqs, resps, id);
        }
        for (int id = 0; id < length; id++) {
            threads[id].start();
        }
        for (int id = 0; id < length; id++) {
            threads[id].join();
        }
        return resps;
    }

    public DefaultHttpClient getClient() {
        DefaultHttpClient client;
        client = new DefaultHttpClient(localConnManager);
        return client;
    }

    public int getMaxConnectionsPerRoute() {
        return localConnManager.getDefaultMaxPerRoute();
    }

    public int getMaxTotalConnections() {
        return localConnManager.getMaxTotal();
    }

    public Scheme getScheme(final String name) {
        return localSchemeRegistry.getScheme(name);
    }

    public List<String> getSchemeNames() {
        List<String> schemeNames = localSchemeRegistry.getSchemeNames();
        return schemeNames;
    }

    public FluentExecutor registerScheme(final String name, final int port) {
        Scheme sch = new Scheme(name, port, localSocketFactory);
        localSchemeRegistry.register(sch);
        return this;
    }

    public FluentExecutor setMaxConnectionsPerRoute(final int maxPerRoute) {
        localConnManager.setDefaultMaxPerRoute(maxPerRoute);
        return this;
    }

    public FluentExecutor setMaxTotalConnections(final int maxTotal) {
        localConnManager.setMaxTotal(maxTotal);
        return this;
    }

    public FluentExecutor unregisterAllSchemes() {
        for (String name : getSchemeNames())
            localSchemeRegistry.unregister(name);
        return this;
    }

    public FluentExecutor unregisterScheme(final String name) {
        localSchemeRegistry.unregister(name);
        return this;
    }
}

class MultiRequestThread extends Thread {
    private FluentExecutor executor;
    private FluentRequest[] reqs;
    private FluentResponse[] resps;
    private int id;

    MultiRequestThread(final FluentExecutor executor,
            final FluentRequest[] reqs, final FluentResponse[] resps,
            final int id) {
        this.executor = executor;
        this.reqs = reqs;
        this.resps = resps;
        this.id = id;
    }

    @Override
    public void run() {
        FluentRequest req = reqs[id];
        try {
            FluentResponse resp = executor.exec(req);
            resp.loadContent();
            resps[id] = resp;
        } catch (Exception e) {
            req.abort();
        }
    }
}
