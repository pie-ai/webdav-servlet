package org.drjekyll.webdav.prop;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class XMLHelper {

    private XMLHelper() {
        // utility
    }

    @Nullable
    public static Node findSubElement(Node parent, String localName) {
        if (parent == null) {
            return null;
        }
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && child
                .getLocalName()
                .equals(localName)) {
                return child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    public static List<String> getPropertiesFromXML(Node propNode) {
        List<String> properties = new ArrayList<>();
        NodeList childList = propNode.getChildNodes();
        for (int i = 0; i < childList.getLength(); i++) {
            Node currentNode = childList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = currentNode.getLocalName();
                String namespace = currentNode.getNamespaceURI();
                // href is a live property which is handled differently
                properties.add(namespace + ':' + nodeName);
            }
        }
        return properties;
    }

}
