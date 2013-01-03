package org.apache.http.impl.client.cache;

import org.junit.Assert;
import org.junit.Test;

public class TestDefaultFailureCache {

    private static final String IDENTIFIER = "some-identifier";

    private FailureCache failureCache = new DefaultFailureCache();

    @Test
    public void testResetErrorCount() {
        failureCache.increaseErrorCount(IDENTIFIER);
        failureCache.resetErrorCount(IDENTIFIER);

        int errorCount = failureCache.getErrorCount(IDENTIFIER);
        Assert.assertEquals(0, errorCount);
    }

    @Test
    public void testIncrementErrorCount() {
        failureCache.increaseErrorCount(IDENTIFIER);
        failureCache.increaseErrorCount(IDENTIFIER);
        failureCache.increaseErrorCount(IDENTIFIER);

        int errorCount = failureCache.getErrorCount(IDENTIFIER);
        Assert.assertEquals(3, errorCount);
    }

    @Test
    public void testMaxSize() {
        failureCache = new DefaultFailureCache(3);
        failureCache.increaseErrorCount("a");
        failureCache.increaseErrorCount("b");
        failureCache.increaseErrorCount("c");
        failureCache.increaseErrorCount("d");

        int errorCount = failureCache.getErrorCount("a");
        Assert.assertEquals(0, errorCount);
    }
}
