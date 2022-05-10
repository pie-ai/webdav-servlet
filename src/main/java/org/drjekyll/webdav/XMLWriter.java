/*
 * Copyright 1999,2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drjekyll.webdav;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Map.Entry;

/**
 * XMLWriter helper class.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 */
public class XMLWriter {

    // -------------------------------------------------------------- Constants

    /**
     * Opening tag.
     */
    public static final int OPENING = 0;

    /**
     * Closing tag.
     */
    public static final int CLOSING = 1;

    /**
     * Element with no content.
     */
    public static final int NO_CONTENT = 2;

    // ----------------------------------------------------- Instance Variables

    /**
     * Namespaces to be declared in the root element
     */
    private final Map<String, String> namespaces;

    /**
     * Buffer.
     */
    private StringBuilder buffer = new StringBuilder();

    /**
     * Writer.
     */
    private Writer writer;

    /**
     * Is true until the root element is written
     */
    private boolean isRootElement = true;

    // ----------------------------------------------------------- Constructors

    /**
     * Constructor.
     */
    public XMLWriter(Map<String, String> namespaces) {
        this.namespaces = namespaces;
    }

    /**
     * Constructor.
     */
    public XMLWriter(Writer writer, Map<String, String> namespaces) {
        this.writer = writer;
        this.namespaces = namespaces;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Retrieve generated XML.
     *
     * @return String containing the generated XML
     */
    public String toString() {
        return buffer.toString();
    }

    /**
     * Write property to the XML.
     *
     * @param name  Property name
     * @param value Property value
     */
    public void writeProperty(String name, String value) {
        writeElement(name, OPENING);
        buffer.append(value);
        writeElement(name, CLOSING);
    }

    /**
     * Write an element.
     *
     * @param name Element name
     * @param type Element type
     */
    public void writeElement(String name, int type) {
        StringBuilder nsdecl = new StringBuilder();

        if (isRootElement) {
            for (Entry<String, String> entry : namespaces.entrySet()) {
                String abbrev = entry.getValue();
                nsdecl.append(" xmlns:").append(abbrev).append("=\"").append(entry.getKey()).append(
                    '"');
            }
            isRootElement = false;
        }

        int pos = name.lastIndexOf(':');
        if (pos >= 0) {
            // lookup prefix for namespace
            String fullns = name.substring(0, pos);
            String prefix = namespaces.get(fullns);
            if (prefix == null) {
                // there is no prefix for this namespace
                name = name.substring(pos + 1);
                nsdecl.append(" xmlns=\"").append(fullns).append('"');
            } else {
                // there is a prefix
                name = prefix + ':' + name.substring(pos + 1);
            }
        } else {
            throw new IllegalArgumentException("All XML elements must have a namespace");
        }

        switch (type) {
            case OPENING:
                buffer.append('<').append(name).append(nsdecl).append('>');
                break;
            case CLOSING:
                buffer.append("</").append(name).append(">\n");
                break;
            case NO_CONTENT:
            default:
                buffer.append('<').append(name).append(nsdecl).append("/>");
                break;
        }
    }

    /**
     * Write property to the XML.
     *
     * @param name Property name
     */
    public void writeProperty(String name) {
        writeElement(name, NO_CONTENT);
    }

    /**
     * Write text.
     *
     * @param text Text to append
     */
    public void writeText(String text) {
        buffer.append(text);
    }

    /**
     * Write data.
     *
     * @param data Data to append
     */
    public void writeData(String data) {
        buffer.append("<![CDATA[").append(data).append("]]>");
    }

    /**
     * Write XML Header.
     */
    public void writeXMLHeader() {
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n");
    }

    /**
     * Send data and reinitializes buffer.
     */
    public void sendData() throws IOException {
        if (writer != null) {
            writer.write(buffer.toString());
            writer.flush();
            buffer = new StringBuilder();
        }
    }

}
