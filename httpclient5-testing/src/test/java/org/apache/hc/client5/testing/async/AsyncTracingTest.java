package org.apache.hc.client5.testing.async;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.TracingAsyncExec;
import org.apache.hc.client5.http.impl.sync.ChainElements;
import org.apache.hc.core5.http.HttpHost;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.opentracing.contrib.spanmanager.DefaultSpanManager;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

/**
 * @author Pavol Loffay
 */
public class AsyncTracingTest extends IntegrationTestBase {

  private static MockTracer mockTracer = new MockTracer(MockTracer.Propagator.TEXT_MAP);

  @Before
  public void before() {
    mockTracer.reset();
    clientBuilder
        .addExecInterceptorBefore(ChainElements.PROTOCOL.name(), "tracing", new TracingAsyncExec(mockTracer));
  }

  @Test
  public void testStandardTags() throws Exception {
    HttpHost httpHost = start();
    Future<SimpleHttpResponse> future = httpclient.execute(
            SimpleHttpRequest.get(httpHost, "/echo/"), null);

    SimpleHttpResponse simpleHttpResponse = future.get();

    Thread.sleep(1000);
    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    Assert.assertEquals(1, mockSpans.size());
    assertGeneratedErrors(mockSpans);
  }

  @Test
  public void testMultipleRequests() throws Exception {
    final HttpHost httpHost = start();

    final String url = "/echo/";
    int numberOfCalls = 22;
    Map<Long, MockSpan> parentSpans = new LinkedHashMap<>(numberOfCalls);

    ExecutorService executorService = Executors.newFixedThreadPool(10);
    List<Future<?>> futures = new ArrayList<>(numberOfCalls);
    for (int i = 0; i < numberOfCalls; i++) {
      final String requestUrl = url + i;

      final MockSpan parentSpan = mockTracer.buildSpan("foo").start();
      parentSpan.setTag("request-url", httpHost.toURI().toString() + requestUrl);
      parentSpans.put(parentSpan.context().spanId(), parentSpan);

      futures.add(executorService.submit(new Runnable() {
        @Override
        public void run() {
          DefaultSpanManager.getInstance().activate(parentSpan);
          try {
            httpclient.execute(SimpleHttpRequest.get(httpHost, requestUrl), null).get();
          } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
          }
        }
      }));
    }

    // wait to finish all calls
    for (Future<?> future: futures) {
      future.get();
    }

    executorService.awaitTermination(1, TimeUnit.SECONDS);
    executorService.shutdown();

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    Assert.assertEquals(numberOfCalls, mockSpans.size());
    assertGeneratedErrors(mockSpans);

    for (int i = 0; i < numberOfCalls; i++) {
      MockSpan childSpan = mockSpans.get(i);
      MockSpan parentSpan = parentSpans.get(childSpan.parentId());

      Assert.assertEquals(parentSpan.tags().get("request-url"), childSpan.tags().get(Tags.HTTP_URL.getKey()));

      Assert.assertEquals(parentSpan.context().traceId(), childSpan.context().traceId());
      Assert.assertEquals(parentSpan.context().spanId(), childSpan.parentId());
      Assert.assertEquals(0, childSpan.generatedErrors().size());
      Assert.assertEquals(0, parentSpan.generatedErrors().size());
    }
  }

  private void assertGeneratedErrors(List<MockSpan> mockSpans) {
    for (MockSpan mockSpan: mockSpans){
      Assert.assertEquals(mockSpan.generatedErrors().toString(), 0, mockSpan.generatedErrors().size());
    }
  }
}
