package org.apache.http.impl.client.cache;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.protocol.HttpContext;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TestExponentialBackingOffSchedulingStrategy {

    private ScheduledExecutorService mockExecutor;
    private ExponentialBackingOffSchedulingStrategy impl;

    @Before
    public void setUp() {
        mockExecutor = EasyMock.createNiceMock(ScheduledExecutorService.class);

        impl = new ExponentialBackingOffSchedulingStrategy(
                mockExecutor,
                ExponentialBackingOffSchedulingStrategy.DEFAULT_BACK_OFF_RATE,
                ExponentialBackingOffSchedulingStrategy.DEFAULT_INITIAL_EXPIRY_IN_MILLIS,
                ExponentialBackingOffSchedulingStrategy.DEFAULT_MAX_EXPIRY_IN_MILLIS
        );
    }

    @Test
    public void testScheduleWithoutPreviousError() {
        HttpCacheEntry cacheEntry = createCacheEntry(withErrorCount(0));
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(cacheEntry);

        expectRequestScheduledWithoutDelay(request);

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithOneFailedAttempt() {
        HttpCacheEntry cacheEntry = createCacheEntry(withErrorCount(1));
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(cacheEntry);

        expectRequestScheduledWithDelay(request, TimeUnit.SECONDS.toMillis(6));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithTwoFailedAttempts() {
        HttpCacheEntry cacheEntry = createCacheEntry(withErrorCount(2));
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(cacheEntry);

        expectRequestScheduledWithDelay(request, TimeUnit.MINUTES.toMillis(1));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithThreeFailedAttempts() {
        HttpCacheEntry cacheEntry = createCacheEntry(withErrorCount(3));
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(cacheEntry);

        expectRequestScheduledWithDelay(request, TimeUnit.MINUTES.toMillis(10));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithFourFailedAttempts() {
        HttpCacheEntry cacheEntry = createCacheEntry(withErrorCount(4));
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(cacheEntry);

        expectRequestScheduledWithDelay(request, TimeUnit.MINUTES.toMillis(100));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithFiveFailedAttempts() {
        HttpCacheEntry cacheEntry = createCacheEntry(withErrorCount(5));
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(cacheEntry);

        expectRequestScheduledWithDelay(request, TimeUnit.MINUTES.toMillis(1000));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithSixFailedAttempts() {
        HttpCacheEntry cacheEntry = createCacheEntry(withErrorCount(6));
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(cacheEntry);

        expectRequestScheduledWithDelay(request, TimeUnit.DAYS.toMillis(1));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithMaxNumberOfFailedAttempts() {
        HttpCacheEntry cacheEntry = createCacheEntry(withErrorCount(Integer.MAX_VALUE));
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(cacheEntry);

        expectRequestScheduledWithDelay(request, TimeUnit.DAYS.toMillis(1));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    private void expectRequestScheduledWithoutDelay(AsynchronousValidationRequest request) {
        expectRequestScheduledWithDelay(request, 0);
    }

    private void expectRequestScheduledWithDelay(AsynchronousValidationRequest request, long delayInMillis) {
        EasyMock.expect(mockExecutor.schedule(request, delayInMillis, TimeUnit.MILLISECONDS)).andReturn(null);
    }

    private void replayMocks() {
        EasyMock.replay(mockExecutor);
    }

    private void verifyMocks() {
        EasyMock.verify(mockExecutor);
    }

    private AsynchronousValidationRequest createAsynchronousValidationRequest(HttpCacheEntry cacheEntry) {
        CachingHttpClient cachingHttpClient = new CachingHttpClient();
        AsynchronousValidator mockValidator = new AsynchronousValidator(cachingHttpClient, impl);
        HttpHost target = new HttpHost("foo.example.com");
        HttpGet request = new HttpGet("/");
        HttpContext mockHttpContext = EasyMock.createNiceMock(HttpContext.class);
        return new AsynchronousValidationRequest(mockValidator, cachingHttpClient, target, request, mockHttpContext, cacheEntry, "identifier");
    }

    private HttpCacheEntry createCacheEntry(int errorCount) {
        return new HttpCacheEntryBuilder()
                .setRequestDate(new Date())
                .setResponseDate(new Date())
                .setStatusLine(new OKStatus())
                .setAllHeaders(new Header[0])
                .setErrorCount(errorCount)
                .build();
    }

    private static int withErrorCount(int errorCount) {
        return errorCount;
    }
}
