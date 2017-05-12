package org.apache.hc.client5.http.impl.async;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChain.Scope;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.spanmanager.DefaultSpanManager;
import io.opentracing.contrib.spanmanager.SpanManager.ManagedSpan;
import io.opentracing.tag.Tags;

/**
 * @author Pavol Loffay
 */
public class TracingAsyncExec implements AsyncExecChainHandler {

  private Tracer tracer;

  public TracingAsyncExec(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public void execute(HttpRequest request, AsyncEntityProducer entityProducer, final Scope scope,
      AsyncExecChain chain, final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {

    SpanBuilder spanBuilder = tracer.buildSpan(request.getMethod())
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

    ManagedSpan current = DefaultSpanManager.getInstance().current();
    if (current.getSpan() != null) {
      spanBuilder.asChildOf(current.getSpan());
    }

    final Span span = spanBuilder.start();
    // TODO add request tags
    try {
      Tags.HTTP_URL.set(span, request.getUri().toString());
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }

    chain.proceed(request, entityProducer, scope, new AsyncExecCallback() {
      @Override
      public AsyncDataConsumer handleResponse(HttpResponse response, EntityDetails entityDetails)
          throws HttpException, IOException {
        //activate for usage in user defined callbacks
        DefaultSpanManager.getInstance().activate(span);
        AsyncDataConsumer asyncDataConsumer = asyncExecCallback.handleResponse(response, entityDetails);
        // TODO add response tags
//        span.finish();
        return asyncDataConsumer;
      }

      @Override
      public void completed() {
        asyncExecCallback.completed();
        span.finish();
      }

      @Override
      public void failed(Exception cause) {
        asyncExecCallback.failed(cause);
        span.finish();
      }
    });
  }
}
