package org.apache.http.impl.cookie;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Parses the list from <a href="http://publicsuffix.org/">publicsuffix.org</a>
 * and configures a PublicSuffixFilter.
 */
public class PublicSuffixListParser {
    private static final int MAX_LINE_LEN = 256;
    private PublicSuffixFilter filter;
    
    PublicSuffixListParser(PublicSuffixFilter filter) {
        this.filter = filter;
    }

    /**
     * Parses the public suffix list format.
     * When creating the reader from the file, make sure to
     * use the correct encoding (the original list is in UTF-8).
     * 
     * @param list the suffix list. The caller is responsible for closing the reader.
     * @throws IOException on error while reading from list
     */
    public void parse(Reader list) throws IOException {
        Collection<String> rules = new ArrayList<String>();
        Collection<String> exceptions = new ArrayList<String>();
        BufferedReader r = new BufferedReader(list);
        StringBuilder sb = new StringBuilder(256);
        boolean more = true;
        while (more) {
            more = readLine(r, sb);
            String line = sb.toString();
            if (line.length() == 0) continue;
            if (line.startsWith("//")) continue; //entire lines can also be commented using //
            if (line.startsWith(".")) line = line.substring(1); // A leading dot is optional
            // An exclamation mark (!) at the start of a rule marks an exception to a previous wildcard rule
            boolean isException = line.startsWith("!"); 
            if (isException) line = line.substring(1);
            
            if (isException) {
                exceptions.add(line);
            } else {
                rules.add(line);
            }
        }
        
        filter.setPublicSuffixes(rules);
        filter.setExceptions(exceptions);
    }
    
    /**
     * 
     * @param r
     * @param sb
     * @return false when the end of the stream is reached
     * @throws IOException
     */
    private boolean readLine(Reader r, StringBuilder sb) throws IOException {
        sb.setLength(0);
        int b;
        boolean hitWhitespace = false;
        while ((b = r.read()) != -1) {
            char c = (char) b;
            if (c == '\n') break;
            // Each line is only read up to the first whitespace
            if (Character.isWhitespace(c)) hitWhitespace = true;
            if (!hitWhitespace) sb.append(c);
            if (sb.length() > MAX_LINE_LEN) throw new IOException("Line too long"); // prevent excess memory usage
        }
        return (b != -1);
    }
}
