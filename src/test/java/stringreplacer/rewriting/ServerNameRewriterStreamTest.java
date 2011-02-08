/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package stringreplacer.rewriting;

import stringreplacer.rewriting.MatchAndReplaceStream;
import java.io.InputStream;
import junit.framework.TestCase;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author elijah
 */
public class ServerNameRewriterStreamTest extends TestCase {
    
    public ServerNameRewriterStreamTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of read method, of class ServerNameRewriterStream with no matching text.
     */
    public void testNoMatch() throws Exception {
        System.out.println("NoMatch");
        
        String expectation = "Test String Foo Bar";
        InputStream source = IOUtils.toInputStream(expectation);
        
        MatchAndReplaceStream instance = new MatchAndReplaceStream(source,
                "seattletimes.nwsource.com", "d1.seattletimes.nwsource.com");
        String rewritten = IOUtils.toString(instance);
        
        IOUtils.closeQuietly(source);
        IOUtils.closeQuietly(instance);
        
        assertEquals("Fails to output the non-matching data", expectation, rewritten);
    }

    /**
     * Test of read method, of class ServerNameRewriterStream with matching text.
     */
    public void testMatch() throws Exception {
        System.out.println("Match");
        String matchText = "seattletimes.nwsource.com";
        String replaceText = "d1.seattletimes.nwsource.com";
        
        String start = "Test String Foo Bar " + matchText + " Some more text";
        String expectation = "Test String Foo Bar " + replaceText + " Some more text";
        
        InputStream source = IOUtils.toInputStream(start);
        
        MatchAndReplaceStream instance = new MatchAndReplaceStream(source,
                matchText, replaceText);
        
        String transformed = IOUtils.toString(instance);
        
        IOUtils.closeQuietly(source);
        IOUtils.closeQuietly(instance);
        
        assertEquals("Fails to output the matching data", expectation, transformed);
    }

    /**
     * Test of read method, of class ServerNameRewriterStream with matching text.
     */
    public void testPartialMatch() throws Exception {
        System.out.println("PartialMatch");
        
        String matchText = "seattletimes.nwsource.com";
        String expectation = "Test String Foo Bar " + matchText.subSequence(0, 5) + "[end]";
        InputStream source = IOUtils.toInputStream(expectation);
        
        MatchAndReplaceStream instance = new MatchAndReplaceStream(source,
                matchText, "d1.seattletimes.nwsource.com");
        String rewritten = IOUtils.toString(instance);
        
        IOUtils.closeQuietly(source);
        IOUtils.closeQuietly(instance);
        
        System.out.println(expectation);
        System.out.println(rewritten);
        
        assertEquals("Fails to output the non-matching data", expectation, rewritten);
    }
    
}
