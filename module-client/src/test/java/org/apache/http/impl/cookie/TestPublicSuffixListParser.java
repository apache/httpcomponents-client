package org.apache.http.impl.cookie;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.cookie.CookieOrigin;

public class TestPublicSuffixListParser extends TestCase {
    private static final String LIST_FILE = "suffixlist.txt";
    private PublicSuffixFilter filter;
    
    public TestPublicSuffixListParser(String testName) {
        super(testName);
        try {
            Reader r = new InputStreamReader(getClass().getResourceAsStream(LIST_FILE), "UTF-8");
            filter = new PublicSuffixFilter(new RFC2109DomainHandler());
            PublicSuffixListParser parser = new PublicSuffixListParser(filter);
            parser.parse(r);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }        
    }

    public static Test suite() {
        return new TestSuite(TestPublicSuffixListParser.class);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestPublicSuffixListParser.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }    
    
    public void testParse() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        
        cookie.setDomain(".jp");
        assertFalse(filter.match(cookie, new CookieOrigin("apache.jp", 80, "/stuff", false)));
        
        cookie.setDomain(".ac.jp");
        assertFalse(filter.match(cookie, new CookieOrigin("apache.ac.jp", 80, "/stuff", false)));
        
        cookie.setDomain(".any.tokyo.jp");
        assertFalse(filter.match(cookie, new CookieOrigin("apache.any.tokyo.jp", 80, "/stuff", false)));
        
        // exception
        cookie.setDomain(".metro.tokyo.jp");
        assertTrue(filter.match(cookie, new CookieOrigin("apache.metro.tokyo.jp", 80, "/stuff", false)));
    }
    
    public void testUnicode() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        
        cookie.setDomain(".h\u00E5.no"); // \u00E5 is <aring>
        assertFalse(filter.match(cookie, new CookieOrigin("apache.h\u00E5.no", 80, "/stuff", false)));
        
        cookie.setDomain(".xn--h-2fa.no");
        assertFalse(filter.match(cookie, new CookieOrigin("apache.xn--h-2fa.no", 80, "/stuff", false)));
        
        cookie.setDomain(".h\u00E5.no");
        assertFalse(filter.match(cookie, new CookieOrigin("apache.xn--h-2fa.no", 80, "/stuff", false)));
        
        cookie.setDomain(".xn--h-2fa.no");
        assertFalse(filter.match(cookie, new CookieOrigin("apache.h\u00E5.no", 80, "/stuff", false)));
    }
    
    public void testWhitespace() throws Exception {
        BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(".xx");
        assertFalse(filter.match(cookie, new CookieOrigin("apache.xx", 80, "/stuff", false)));
        
        // yy appears after whitespace
        cookie.setDomain(".yy");
        assertTrue(filter.match(cookie, new CookieOrigin("apache.yy", 80, "/stuff", false)));
        
        // zz is commented
        cookie.setDomain(".zz");
        assertTrue(filter.match(cookie, new CookieOrigin("apache.zz", 80, "/stuff", false)));
    }
}
