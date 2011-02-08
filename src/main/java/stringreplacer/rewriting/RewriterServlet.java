/* Copyright 2011 Elijah Zupancic

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package stringreplacer.rewriting;

import stringreplacer.utils.ResourceLoader;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.io.InputStream;
import java.net.URLConnection;
import com.google.common.collect.ImmutableList;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.Scanner;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
        
/**
 *
 * @author Elijah Zupancic
 */
public class RewriterServlet extends HttpServlet {
    private static final List<String> targetContentTypes = 
            ImmutableList.of("text/html", "application/x-javascript",
                             "text/css", "application/json", 
                             "application/xhtml+xml", "text/xml",
                             "application/rss+xml", "application/rdf+xml",
                             "application/atom+xml");
    private static final List<String> httpHeadersToCopy =
            ImmutableList.of("accept", "user-agent",
                             "accept-language", "accept-charset",
                             "cookie", "x-forwarded-for",
                             "x-forwarded-host", "x-forwarded-server");   
    
    private Map<String, String> replacements;
    
    public RewriterServlet() {
        super();
    }

    @Override
    public void init() throws ServletException {
        super.init();
                
        try {
            this.replacements = parseReplacementsData("/WEB-INF/replacements.csv");

            //ResourceLoader replacementData = new ResourceLoader("/WEB-INF/replacements.csv");
            //this.replacements = parseReplacementsData(replacementData.getInputStream());

        } catch (Exception e) {
            throw new ServletException("An error occured when loading the " +
                    "replacements configuration file", e);
        }
    }
    
    /**
     * This method in the entry-point for forwarding all HTTP requests made.
     */
    protected void doRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        /* We code the origin server name as the first directory in the URL
         * being requested. This would normally be setup in an Apache
         * ProxyPass setting. Here we slice out the origin server name from
         * the URL.
         */
        final String origin;
        {
            String pathInfo = request.getPathInfo();
            String[] pathSplit = StringUtils.split(pathInfo, "/");

            if (pathSplit.length == 0) {
                throw new IOException("No origin servername specified on url");
            }
            else {
                origin = pathSplit[0];
            }
        }
        
        /* Since the requesting url that was forwarded is placed after the origin
         * server name as a directory, we need to remove the origin server name
         * and get the full URL with query parameters being requested.
         */
        final String path;
        {
            String uri = request.getRequestURI();
            String query = isNotEmpty(request.getQueryString()) ?
                    "?" + request.getQueryString() : "";
            int forwardUriPos = uri.indexOf(origin) + origin.length();
            path  = uri.substring(forwardUriPos) + query;
        }

        final URLConnection connection;
        
        try {
            connection = openUrlConnection(origin, path, request);
            log("Opening: " + connection);
        } catch (FileNotFoundException fnfe) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        
        /* We now search the content type of all forwarded requests for content
         * types that start with our matching strings because it will be only
         * these content types that we will act upon to rewrite string data.
         */
        boolean matching = false;
        {
            final String originContentType = connection.getContentType()
                    .trim().toLowerCase();
            for (String contentType : targetContentTypes) {
                matching = originContentType.startsWith(contentType);
                if (matching) { break; }
            }
        }

        
        /* We need to pass all headers that were sent from the origin server
         * to the client, otherwise the client will get a bunch of garbage
         * like raw gzipped output.
         */
        {
            for (String key : connection.getHeaderFields().keySet()) {
                String value = connection.getHeaderField(key);
                
                /* We have received a HTTP relocation request. We will want to
                 * rewrite this url as well. */
                if (key != null && key.trim().equalsIgnoreCase("Location")) {
                    log("Redirect: " + value + " => ");
                    value = processStringWithRewriters(value);
                    log(value);
                }
                
                response.setHeader(key, value);
            }
            
            response.setContentType(connection.getContentType());
        }
        
        /* Use memory to buffer origin request stream otherwise we might experience
         * some hiccups in performance. */
        InputStream in = null;
        
        try {
            in = new BufferedInputStream(connection.getInputStream());
            
            // Rewrite the input stream
            if (matching) {
                in = attachNestedStreams(in);
                copyFromOrigin(in, response);
            
            // Do nothing and just copy it
            } else {
                copyFromOrigin(in, response);
            }
        }
        finally {
            IOUtils.closeQuietly(in);
        }
    }
    
    protected InputStream attachNestedStreams(InputStream in) throws IOException {
        InputStream attached = in;

        /* Add an input stream filter for every matching pair configured.
         * It still remains to be seen how well this approach performs. */
        for (String match : replacements.keySet()) {
            String replace = replacements.get(match);

            attached = new MatchAndReplaceStream(in, match, replace);
        }

        return attached;
    }
    
    protected String processStringWithRewriters(String source) throws IOException {
        InputStream in = null;
        
        try {
            in = IOUtils.toInputStream(source);
            attachNestedStreams(in);
            return IOUtils.toString(in);
        }
        finally {
            IOUtils.closeQuietly(in);
        }
    }
    
    protected URLConnection openUrlConnection(String origin, String path,
            HttpServletRequest request)
            throws IOException {
        URL originUrl = new URL("http", origin, path);

        /* Since we are forwarding an HTTP request, we need to do a best effort
         * to copy over all of the applicable HTTP headers. */
        HttpURLConnection connection = (HttpURLConnection)originUrl.openConnection();
        connection.setUseCaches(true);

        /* In order to have an accurate copy of the site all origin HTTP headers
         * need to be copied. */
        for (String header : httpHeadersToCopy) {
            String value = request.getHeader(header);
                        
            if (isNotEmpty(value)) {
                connection.setRequestProperty(header, value);
            }
        }
        
        connection.connect();
        
        return connection;
    }
    
    /**
     * This method copies static content from the origin server to the client.
     * @param origin server name of origin server
     * @param path request URI path
     * @param request source HTTP request object
     * @param response source HTTP response object
     */
    protected void copyFromOrigin(InputStream in, HttpServletResponse response)
            throws IOException {
        OutputStream out = null;
        try {
            out = new BufferedOutputStream(response.getOutputStream());
            IOUtils.copy(in, out);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    /**
     * We treat GET and POST the same, so all request to either method get
     * passed to doRequest();
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        doRequest(request, response);
    }

    /**
     * We treat GET and POST the same, so all request to either method get
     * passed to doRequest();
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        doRequest(request, response);
    }
    
    private Map<String, String> parseReplacementsData(String path)
            throws IOException {
        InputStream in = getServletContext().getResourceAsStream(path);
        return parseReplacementsData(in);
    }

    private Map<String, String> parseReplacementsData(InputStream in)
            throws IOException {
        Map<String, String> matches = new HashMap();
        
        Scanner scanner = new Scanner(in, 
                Charset.defaultCharset().displayName());
        
        while(scanner.hasNext()) {
            String line = scanner.nextLine();
            String[] keyVal = line.split(",");
            String match = keyVal[0];
            String replace = keyVal[1];
            matches.put(match, replace);
        }
        
        return matches;
    }
}
