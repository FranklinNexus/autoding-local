/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.xmlpull.v1;
import java.io.IOException;
import java.io.OutputStream;
public interface XmlSerializer {
    void setOutput(OutputStream output, String encoding) throws IOException;
    void startDocument(String encoding, Boolean standalone) throws IOException;
    XmlSerializer startTag(String namespace, String name) throws IOException;
    XmlSerializer attribute(String namespace, String name, String value) throws IOException;
    XmlSerializer endTag(String namespace, String name) throws IOException;
    void endDocument() throws IOException;
    void flush() throws IOException;
}
