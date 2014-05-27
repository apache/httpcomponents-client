package org.apache.http.client.utils;


import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.Charset;

public class TestRequestBuilder {

  @Test
  public void testBuildGETwithUTF8() throws Exception {
    assertBuild(Consts.UTF_8);
  }

  @Test
  public void testBuildGETwithISO88591() throws Exception {
    assertBuild(Consts.ISO_8859_1);
  }

  private void assertBuild(Charset charset) throws Exception {
    RequestBuilder requestBuilder = RequestBuilder.create("GET", charset);
    requestBuilder.setUri("https://somehost.com/stuff");
    requestBuilder.addParameters(createParameters());

    String encodedData1 = URLEncoder.encode("\"1ª position\"", charset.displayName());
    String encodedData2 = URLEncoder.encode("José Abraão", charset.displayName());

    String uriExpected = String.format("https://somehost.com/stuff?parameter1=value1&parameter2=%s&parameter3=%s", encodedData1, encodedData2);

    HttpUriRequest request = requestBuilder.build();
    Assert.assertEquals(uriExpected, request.getURI().toString());
  }

  private NameValuePair[] createParameters() {
    NameValuePair parameters[] = new NameValuePair[3];
    parameters[0] = new BasicNameValuePair("parameter1", "value1");
    parameters[1] = new BasicNameValuePair("parameter2", "\"1ª position\"");
    parameters[2] = new BasicNameValuePair("parameter3", "José Abraão");
    return parameters;
  }
}
