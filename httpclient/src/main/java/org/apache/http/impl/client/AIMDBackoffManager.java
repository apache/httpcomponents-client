package org.apache.http.impl.client;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.BackoffManager;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.routing.HttpRoute;

/**
 * The <code>AIMDBackoffManager</code> applies an additive increase,
 * multiplicative decrease (AIMD) to managing a dynamic limit to
 * the number of connections allowed to a given host.
 * 
 * @since 4.2
 */
public class AIMDBackoffManager implements BackoffManager {

    private ConnPerRouteBean connPerRoute;
    private Clock clock;
    private long coolDown = 5 * 1000L;
    private double backoffFactor = 0.5;
    private int cap = ConnPerRouteBean.DEFAULT_MAX_CONNECTIONS_PER_ROUTE;
    private Map<HttpRoute,Long> lastRouteProbes =
        new HashMap<HttpRoute,Long>();
    private Map<HttpRoute,Long> lastRouteBackoffs =
        new HashMap<HttpRoute,Long>();

    
    /**
     * Creates an <code>AIMDBackoffManager</code> to manage
     * per-host connection pool sizes represented by the
     * given {@link ConnPerRouteBean}.
     * @param connPerRoute per-host routing maximums to
     *   be managed
     */
    public AIMDBackoffManager(ConnPerRouteBean connPerRoute) {
        this(connPerRoute, new SystemClock());
    }
    
    AIMDBackoffManager(ConnPerRouteBean connPerRoute, Clock clock) {
        this.clock = clock;
        this.connPerRoute = connPerRoute;
    }

    public void backOff(HttpRoute route) {
        synchronized(connPerRoute) {
            int curr = connPerRoute.getMaxForRoute(route);
            Long lastUpdate = getLastUpdate(lastRouteBackoffs, route);
            long now = clock.getCurrentTime();
            if (now - lastUpdate < coolDown) return;
            connPerRoute.setMaxForRoute(route, getBackedOffPoolSize(curr));
            lastRouteBackoffs.put(route, now);
        }
    }

    private int getBackedOffPoolSize(int curr) {
        if (curr <= 1) return 1;
        return (int)(Math.floor(backoffFactor * curr));
    }

    public void probe(HttpRoute route) {
        synchronized(connPerRoute) {
            int curr = connPerRoute.getMaxForRoute(route);
            int max = (curr >= cap) ? cap : curr + 1; 
            Long lastProbe = getLastUpdate(lastRouteProbes, route);
            Long lastBackoff = getLastUpdate(lastRouteBackoffs, route);
            long now = clock.getCurrentTime();
            if (now - lastProbe < coolDown || now - lastBackoff < coolDown)
                return; 
            connPerRoute.setMaxForRoute(route, max);
            lastRouteProbes.put(route, now);
        }
    }

    private Long getLastUpdate(Map<HttpRoute,Long> updates, HttpRoute route) {
        Long lastUpdate = updates.get(route);
        if (lastUpdate == null) lastUpdate = 0L;
        return lastUpdate;
    }

    /**
     * Sets the factor to use when backing off; the new
     * per-host limit will be roughly, the current max times
     * this factor. <code>Math.floor</code> is applied in the
     * case of non-integer outcomes to ensure we actually
     * decrease the pool size. Pool sizes are never decreased
     * below 1, however. Defaults to 0.5.
     * @param d must be between 0.0 and 1.0, exclusive.
     */
    public void setBackoffFactor(double d) {
        if (d <= 0.0 || d >= 1.0) {
            throw new IllegalArgumentException("backoffFactor must be 0.0 < f < 1.0");
        }
        backoffFactor = d;
    }
    
    /**
     * Sets the amount of time, in milliseconds, to wait between
     * adjustments in pool sizes for a given host, to allow
     * enough time for the adjustments to take effect. Defaults
     * to 5000L (5 seconds). 
     * @param l must be positive
     */
    public void setCooldownMillis(long l) {
        if (coolDown <= 0) {
            throw new IllegalArgumentException("cooldownMillis must be positive");
        }
        coolDown = l;
    }
    
    /**
     * Sets the absolute maximum per-host connection pool size to
     * probe up to; defaults to 2 (the default per-host max).
     * @param cap must be >= 1
     */
    public void setPerHostConnectionCap(int cap) {
        if (cap < 1) {
            throw new IllegalArgumentException("perHostConnectionCap must be >= 1");
        }
        this.cap = cap;
    }

}
