package org.apache.http.impl.client.cache;

import org.easymock.classextension.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;

public class TestImmediateSchedulingStrategy
{
    private ExecutorService mockExecutor;
    private AsynchronousValidationRequest revalidationRequest;
    private SchedulingStrategy schedulingStrategy;

    @Before
    public void setUp()
    {
        mockExecutor = EasyMock.createNiceMock(ExecutorService.class);
        revalidationRequest = EasyMock.createNiceMock(AsynchronousValidationRequest.class);
        schedulingStrategy = new ImmediateSchedulingStrategy(mockExecutor);
    }

    @Test
    public void testRequestScheduledImmediately()
    {
        mockExecutor.execute(revalidationRequest);

        EasyMock.replay(mockExecutor);
        schedulingStrategy.schedule(revalidationRequest);
        EasyMock.verify(mockExecutor);
    }
}
