package org.apache.hc.client5.http.impl.sync;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.spanmanager.DefaultSpanManager;
import io.opentracing.contrib.spanmanager.SpanManager;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;
import org.apache.hc.client5.http.sync.ExecChain;
import org.apache.hc.client5.http.sync.ExecChain.Scope;
import org.apache.hc.client5.http.sync.ExecChainHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;

/**
 * @author Pavol Loffay
 */
public class TracingExec implements ExecChainHandler {
  /**
   * SpanContext which will be used as a parent for created client span.
   */
  public static final String PARENT_CONTEXT = TracingExec.class.getName() + ".parentSpanContext";

  private final Tracer tracer;
  private final SpanManager spanManager = DefaultSpanManager.getInstance();

  public static HttpClientBuilder addTracing(HttpClientBuilder clientBuilder, Tracer tracer) {
    clientBuilder.addExecInterceptorAfter(ChainElements.MAIN_TRANSPORT.name(), "tracing", new TracingExec(tracer));

    return clientBuilder;
  }

  public TracingExec(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public ClassicHttpResponse execute(ClassicHttpRequest request, Scope scope, ExecChain chain)
      throws IOException, HttpException {
    Span span = null;
    try {
      Tracer.SpanBuilder spanBuilder = tracer.buildSpan(request.getMethod())
          .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

      if (scope.clientContext.getAttribute(PARENT_CONTEXT, SpanContext.class) != null) {
        spanBuilder.asChildOf(scope.clientContext.getAttribute(PARENT_CONTEXT, SpanContext.class));
      } else if (spanManager.current().getSpan() != null) {
        spanBuilder.asChildOf(spanManager.current().getSpan());
      }

      span = spanBuilder.start();
      tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS,
          new HttpHeadersInjectAdapter(request));
      // TODO add request tags

      ClassicHttpResponse response = chain.proceed(request, scope);
      // TODO add response tags
      return response;
    } catch (IOException | HttpException | RuntimeException e) {
      // TODO add error tags
      throw e;
    } finally {
      if (span != null) {
      }
    }

  }

  class HttpHeadersInjectAdapter implements TextMap {
      private HttpRequest httpRequest;

      public HttpHeadersInjectAdapter(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
      }

      @Override
      public void put(String key, String value) {
        httpRequest.addHeader(key, value);
      }

      @Override
      public Iterator<Entry<String, String>> iterator() {
        throw new UnsupportedOperationException(
            "This class should be used only with tracer#inject()");
      }
    }
}
