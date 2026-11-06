/* SPDX-License-Identifier: GPL-3.0-or-later */
package android.util;
import java.io.IOException;
import java.io.OutputStream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.xmlpull.v1.XmlSerializer;
public final class Xml {
    public static XmlSerializer newSerializer() { return new Serializer(); }
    private static final class Serializer implements XmlSerializer {
        private XMLStreamWriter writer;
        private interface Action { void run() throws XMLStreamException; }
        private void write(Action action) throws IOException {
            if (fixture.FixtureState.failSerialization) throw new IOException("Synthetic serializer failure");
            try { action.run(); } catch (XMLStreamException e) { throw new IOException(e); }
        }
        public void setOutput(OutputStream output, String encoding) throws IOException {
            write(() -> writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output, encoding));
        }
        public void startDocument(String encoding, Boolean standalone) throws IOException { write(() -> writer.writeStartDocument(encoding, "1.0")); }
        public XmlSerializer startTag(String ns, String name) throws IOException { write(() -> writer.writeStartElement(name)); return this; }
        public XmlSerializer attribute(String ns, String name, String value) throws IOException { write(() -> writer.writeAttribute(name, value)); return this; }
        public XmlSerializer endTag(String ns, String name) throws IOException { write(() -> writer.writeEndElement()); return this; }
        public void endDocument() throws IOException { write(() -> writer.writeEndDocument()); }
        public void flush() throws IOException { write(() -> writer.flush()); }
    }
}
