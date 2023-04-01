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
package org.apache.hc.client5.http.impl.routing;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A DistributedProxySelector is a custom {@link ProxySelector} implementation that
 * delegates proxy selection to a list of underlying ProxySelectors in a
 * distributed manner. It ensures that proxy selection is load-balanced
 * across the available ProxySelectors, and provides thread safety by
 * maintaining separate states for each thread.
 *
 * <p>The DistributedProxySelector class maintains a list of ProxySelectors,
 * a {@link ThreadLocal} variable for the current {@link ProxySelector}, and an {@link AtomicInteger}
 * to keep track of the shared index across all threads. When the select()
 * method is called, it delegates the proxy selection to the current
 * ProxySelector or the next available one in the list if the current one
 * returns an empty proxy list. Any exceptions that occur during proxy
 * selection are caught and ignored, and the next ProxySelector is tried.
 *
 * <p>The connectFailed() method notifies the active {@link ProxySelector} of a
 * connection failure, allowing the underlying ProxySelector to handle
 * connection failures according to its own logic.
 *
 * @since 5.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class DistributedProxySelector extends ProxySelector {

    private static final Logger LOG = LoggerFactory.getLogger(DistributedProxySelector.class);

    /**
     * A list of {@link ProxySelector} instances to be used by the DistributedProxySelector
     * for selecting proxies.
     */
    private final List<ProxySelector> selectors;

    /**
     * A {@link ThreadLocal} variable holding the current {@link ProxySelector} for each thread,
     * ensuring thread safety when accessing the current {@link ProxySelector}.
     */
    private final ThreadLocal<ProxySelector> currentSelector;

    /**
     * An {@link AtomicInteger} representing the shared index across all threads for
     * maintaining the current position in the list of ProxySelectors, ensuring
     * proper distribution of {@link ProxySelector} usage.
     */
    private final AtomicInteger sharedIndex;


    /**
     * Constructs a DistributedProxySelector with the given list of {@link ProxySelector}.
     * The constructor initializes the currentSelector as a {@link ThreadLocal}, and
     * the sharedIndex as an {@link AtomicInteger}.
     *
     * @param selectors the list of ProxySelectors to use.
     * @throws IllegalArgumentException if the list is null or empty.
     */
    public DistributedProxySelector(final List<ProxySelector> selectors) {
        if (selectors == null || selectors.isEmpty()) {
            throw new IllegalArgumentException("At least one ProxySelector is required");
        }
        this.selectors = new ArrayList<>(selectors);
        this.currentSelector = new ThreadLocal<>();
        this.sharedIndex = new AtomicInteger();
    }

    /**
     * Selects a list of proxies for the given {@link URI} by delegating to the current
     * {@link ProxySelector} or the next available {@link ProxySelector} in the list if the current
     * one returns an empty proxy list. If an {@link Exception} occurs, it will be caught
     * and ignored, and the next {@link ProxySelector} will be tried.
     *
     * @param uri the {@link URI} to select a proxy for.
     * @return a list of proxies for the given {@link URI}.
     */
    @Override
    public List<Proxy> select(final URI uri) {
        List<Proxy> result = Collections.emptyList();
        ProxySelector selector;

        for (int i = 0; i < selectors.size(); i++) {
            selector = nextSelector();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Selecting next proxy selector for URI {}: {}", uri, selector);
            }

            try {
                currentSelector.set(selector);
                result = currentSelector.get().select(uri);
                if (!result.isEmpty()) {
                    break;
                }
            } catch (final Exception e) {
                // ignore and try the next selector
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Exception caught while selecting proxy for URI {}: {}", uri, e.getMessage());
                }
            } finally {
                currentSelector.remove();
            }
        }
        return result;
    }

    /**
     * Notifies the active {@link ProxySelector} of a connection failure. This method
     * retrieves the current {@link ProxySelector} from the {@link ThreadLocal} variable and
     * delegates the handling of the connection failure to the underlying
     * ProxySelector's connectFailed() method. After handling the connection
     * failure, the current ProxySelector is removed from the {@link ThreadLocal} variable.
     *
     * @param uri the {@link URI} that failed to connect.
     * @param sa  the {@link SocketAddress} of the proxy that failed to connect.
     * @param ioe the {@link IOException} that resulted from the failed connection.
     */
    @Override
    public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
        final ProxySelector selector = currentSelector.get();
        if (selector != null) {
            selector.connectFailed(uri, sa, ioe);
            currentSelector.remove();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Removed the current ProxySelector for URI {}: {}", uri, selector);
            }
        }
    }

    /**
     * Retrieves the next available {@link ProxySelector} in the list of selectors,
     * incrementing the shared index atomically to ensure proper distribution
     * across different threads.
     *
     * @return the next {@link ProxySelector} in the list.
     */
    private ProxySelector nextSelector() {
        final int nextIndex = sharedIndex.getAndUpdate(i -> (i + 1) % selectors.size());
        return selectors.get(nextIndex);
    }
}
