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

import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 *
 * @author Elijah Zupancic
 */
class MatchAndReplaceStream extends InputRewriterStream {
    private int replacePos = -1;
    
    private final Queue<Byte> buffer;
    
    private final byte[] match;
    private final byte[] replace;
    
    private byte[] prevBuffer = new byte[] {};
    
    public final String matchText;
    public final String replaceText;

    private final StringBuffer nestedInputStreamRecord = new StringBuffer();

    
    public MatchAndReplaceStream(InputStream source, String matchText,
            String replaceText) {
        super(source);
        
        this.matchText = matchText;
        this.replaceText = replaceText;
        
        this.match = matchText.getBytes();
        this.replace = replaceText.getBytes();
        
        buffer = new ArrayBlockingQueue<Byte>(match.length + 1);

        if (source instanceof MatchAndReplaceStream) {
            MatchAndReplaceStream s = ((MatchAndReplaceStream)source);
            nestedInputStreamRecord.append(s.nestedInputStreamRecord);
        }

        if (nestedInputStreamRecord.length() > 0) {
            nestedInputStreamRecord.append(" / ");
        }

        nestedInputStreamRecord.append("[" + matchText + "/" + replaceText + "]");
    }

    @Override
    public int read() throws IOException {
        // if there is no match text, just return the source stream
        if (match.length == 0) {
            return source.read();
        }
        
        if (replacePos >= 0) {
            byte r = replace[replacePos];

            replacePos++;

            if (replacePos == replace.length) {
                replacePos = -1;
                buffer.clear();
            }

            return r;
        }
        else if (!buffer.isEmpty()) {
            return buffer.poll();
        }

        int sourceRead;
        
        for (byte b : match) {
            sourceRead = source.read();
            
            if (b != sourceRead && buffer.isEmpty()) {
                return sourceRead;
            }
            // 1+n matching, if true (failure) add to the buffer and recurse
            else if (b != sourceRead) {
                buffer.add((byte)sourceRead);
                return this.read();
            }
            else {
                buffer.add(b);
            }
        }
        
        replacePos = 0;
        
        return this.read();
    }

    @Override
    public String toString() {
        return super.toString() + " " + nestedInputStreamRecord.toString();
    }
}