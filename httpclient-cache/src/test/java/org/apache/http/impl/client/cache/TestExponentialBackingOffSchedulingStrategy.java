package org.apache.http.impl.client.cache;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.protocol.HttpContext;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TestExponentialBackingOffSchedulingStrategy {

    private ScheduledExecutorService mockExecutor;
    private ExponentialBackOffSchedulingStrategy impl;

    @Before
    public void setUp() {
        mockExecutor = EasyMock.createNiceMock(ScheduledExecutorService.class);

        impl = new ExponentialBackOffSchedulingStrategy(
                mockExecutor,
                ExponentialBackOffSchedulingStrategy.DEFAULT_BACK_OFF_RATE,
                ExponentialBackOffSchedulingStrategy.DEFAULT_INITIAL_EXPIRY_IN_MILLIS,
                ExponentialBackOffSchedulingStrategy.DEFAULT_MAX_EXPIRY_IN_MILLIS
        );
    }

    @Test
    public void testScheduleWithoutPreviousError() {
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(0));

        expectRequestScheduledWithoutDelay(request);

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithOneFailedAttempt() {
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(1));

        expectRequestScheduledWithDelay(request, TimeUnit.SECONDS.toMillis(6));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithTwoFailedAttempts() {
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(2));

        expectRequestScheduledWithDelay(request, TimeUnit.MINUTES.toMillis(1));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithThreeFailedAttempts() {
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(3));

        expectRequestScheduledWithDelay(request, TimeUnit.MINUTES.toMillis(10));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithFourFailedAttempts() {
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(4));

        expectRequestScheduledWithDelay(request, TimeUnit.MINUTES.toMillis(100));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithFiveFailedAttempts() {
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(5));

        expectRequestScheduledWithDelay(request, TimeUnit.MINUTES.toMillis(1000));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithSixFailedAttempts() {
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(6));

        expectRequestScheduledWithDelay(request, TimeUnit.DAYS.toMillis(1));

        replayMocks();
        impl.schedule(request);
        verifyMocks();
    }

    @Test
    public void testScheduleWithMaxNumberOfFailedAttempts() {
        AsynchronousValidationRequest request = createAsynchronousValidationRequest(withErrorCount(Integer.MAX_VALUE));

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

    private AsynchronousValidationRequest createAsynchronousValidationRequest(int errorCount) {
        CachingHttpClient cachingHttpClient = new CachingHttpClient();
        AsynchronousValidator mockValidator = new AsynchronousValidator(cachingHttpClient, impl);
        HttpHost target = new HttpHost("foo.example.com");
        HttpGet request = new HttpGet("/");
        HttpContext mockHttpContext = EasyMock.createNiceMock(HttpContext.class);
        return new AsynchronousValidationRequest(mockValidator, cachingHttpClient, target, request, mockHttpContext, null, "identifier", errorCount);
    }

    private static int withErrorCount(int errorCount) {
        return errorCount;
    }
}
