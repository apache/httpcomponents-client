package org.apache.http.impl.client.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.client.cache.Resource;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;

public class TestHttpCacheEntrySerializers extends TestCase {

	private static final Charset UTF8 = Charset.forName("UTF-8");
	
	public void testDefaultSerializer() throws Exception {
		readWriteVerify(new DefaultHttpCacheEntrySerializer());
	}

	public void readWriteVerify(HttpCacheEntrySerializer serializer) throws IOException {
		// write the entry
		HttpCacheEntry writeEntry = newCacheEntry();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		serializer.writeTo(writeEntry, out);

		// read the entry
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		HttpCacheEntry readEntry = serializer.readFrom(in);

		// compare
		assertTrue(areEqual(readEntry, writeEntry));
	}

	private HttpCacheEntry newCacheEntry() throws UnsupportedEncodingException {
		Header[] headers = new Header[5];
		for (int i = 0; i < headers.length; i++) {
			headers[i] = new BasicHeader("header" + i, "value" + i);
		}
		String body = "Lorem ipsum dolor sit amet";
		
		ProtocolVersion pvObj = new ProtocolVersion("HTTP", 1, 1);
		StatusLine slObj = new BasicStatusLine(pvObj, 200, "ok");
		Set<String> variants = new HashSet<String>();
		variants.add("test variant 1");
		variants.add("test variant 2");

		HttpCacheEntry cacheEntry = new HttpCacheEntry(new Date(), new Date(),
				slObj, headers, new HeapResource(Base64.decodeBase64(body
						.getBytes(UTF8.name()))), variants);

		return cacheEntry;
	}

	private boolean areEqual(HttpCacheEntry one, HttpCacheEntry two) throws IOException {
		// dates are only stored with second precision, so scrub milliseconds
		if (!((one.getRequestDate().getTime() / 1000) == (two.getRequestDate()
				.getTime() / 1000)))
			return false;
		if (!((one.getResponseDate().getTime() / 1000) == (two
				.getResponseDate().getTime() / 1000)))
			return false;
		if (!one.getProtocolVersion().equals(two.getProtocolVersion()))
			return false;
		
		byte[] onesByteArray = resourceToBytes(one.getResource());
		byte[] twosByteArray = resourceToBytes(two.getResource());
		
		if (!Arrays.equals(onesByteArray,twosByteArray))
			return false;

		Header[] oneHeaders = one.getAllHeaders();
		Header[] twoHeaders = one.getAllHeaders();
		if (!(oneHeaders.length == twoHeaders.length))
			return false;
		for (int i = 0; i < oneHeaders.length; i++) {
			if (!oneHeaders[i].getName().equals(twoHeaders[i].getName()))
				return false;
			if (!oneHeaders[i].getValue().equals(twoHeaders[i].getValue()))
				return false;
		}

		return true;
	}
	
	private byte[] resourceToBytes(Resource res) throws IOException {
		InputStream inputStream = res.getInputStream();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		
		int readBytes;
		byte[] bytes = new byte[8096];
		while ((readBytes = inputStream.read(bytes)) > 0) {
			outputStream.write(bytes, 0, readBytes);
		}
		
		byte[] byteData = outputStream.toByteArray();
		
		inputStream.close();
		outputStream.close();

		return byteData;
	}
}
