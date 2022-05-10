package org.drjekyll.webdav.prop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import lombok.extern.slf4j.Slf4j;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.WebdavStatus;
import org.drjekyll.webdav.XMLWriter;
import org.drjekyll.webdav.exceptions.AccessDeniedException;
import org.drjekyll.webdav.exceptions.WebdavException;
import org.drjekyll.webdav.locking.LockedObject;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.methods.DeterminableMethod;
import org.drjekyll.webdav.methods.Method;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

@Slf4j
public class DoProppatch extends Method {

    private final boolean readOnly;

    private final WebdavStore store;

    private final ResourceLocks resourceLocks;

    public DoProppatch(
        WebdavStore store, ResourceLocks resLocks, boolean readOnly
    ) {
        this.readOnly = readOnly;
        this.store = store;
        resourceLocks = resLocks;
    }

    @Override
    public void execute(
        Transaction transaction, HttpServletRequest req, HttpServletResponse resp
    ) throws IOException {
        log.trace("-- {}", getClass().getName());

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);
        String parentPath = getParentPath(getCleanPath(path));

        if (!checkLocks(transaction, req, resourceLocks, parentPath)) {
            resp.setStatus(WebdavStatus.SC_LOCKED);
            return; // parent is locked
        }

        if (!checkLocks(transaction, req, resourceLocks, path)) {
            resp.setStatus(WebdavStatus.SC_LOCKED);
            return; // resource is locked
        }

        // TODO for now, PROPPATCH just sends a valid response, stating that
        // everything is fine, but doesn't do anything.

        // Retrieve the resources
        String tempLockOwner = "doProppatch" + System.currentTimeMillis() + req;

        if (resourceLocks.lock(transaction,
            path,
            tempLockOwner,
            false,
            0,
            TEMP_TIMEOUT,
            TEMPORARY
        )) {
            try {
                StoredObject so = store.getStoredObject(transaction, path);
                LockedObject lo = resourceLocks.getLockedObjectByPath(transaction,
                    getCleanPath(path)
                );

                if (so == null) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                    // we do not to continue since there is no root
                    // resource
                }

                if (so.isNullResource()) {
                    String methodsAllowed = DeterminableMethod.determineMethodsAllowed(so);
                    resp.addHeader("Allow", methodsAllowed);
                    resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    return;
                }

                String[] lockTokens = getLockIdFromIfHeader(req);
                boolean lockTokenMatchesIfHeader =
                    lockTokens != null && lockTokens[0].equals(lo.getID());
                if (lo != null && lo.isExclusive() && !lockTokenMatchesIfHeader) {
                    // Object on specified path is LOCKED
                    Map<String, Integer> errorList = new HashMap<>();
                    errorList.put(path, WebdavStatus.SC_LOCKED);
                    sendReport(req, resp, errorList);
                    return;
                }

                // contains all properties from
                // toset and toremove

                path = getCleanPath(getRelativePath(req));

                if (req.getContentLength() == 0) {
                    // no content: error
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return;
                }
                DocumentBuilder documentBuilder = getDocumentBuilder();
                Node toremoveNode = null;
                Node tosetNode = null;
                try {
                    Document document =
                        documentBuilder.parse(new InputSource(req.getInputStream()));
                    // Get the root element of the document
                    Element rootElement = document.getDocumentElement();

                    tosetNode = XMLHelper.findSubElement(XMLHelper.findSubElement(rootElement,
                        "set"
                    ), "prop");
                    toremoveNode = XMLHelper.findSubElement(XMLHelper.findSubElement(rootElement,
                        "remove"
                    ), "prop");
                } catch (Exception e) {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return;
                }

                HashMap<String, String> namespaces = new HashMap<>();
                namespaces.put("DAV:", "D");

                Collection<String> tochange = new ArrayList<>();
                if (tosetNode != null) {
                    List<String> toset = XMLHelper.getPropertiesFromXML(tosetNode);
                    tochange.addAll(toset);
                }

                if (toremoveNode != null) {
                    List<String> toremove = XMLHelper.getPropertiesFromXML(toremoveNode);
                    tochange.addAll(toremove);
                }

                resp.setStatus(WebdavStatus.SC_MULTI_STATUS);
                resp.setContentType("text/xml; charset=UTF-8");

                // Create multistatus object
                XMLWriter generatedXML = new XMLWriter(resp.getWriter(), namespaces);
                generatedXML.writeXMLHeader();
                generatedXML.writeElement("DAV::multistatus", XMLWriter.OPENING);

                generatedXML.writeElement("DAV::response", XMLWriter.OPENING);
                String status =
                    "HTTP/1.1 " + HttpServletResponse.SC_OK + ' ' + WebdavStatus.getStatusText(
                        HttpServletResponse.SC_OK);

                // Generating href element
                generatedXML.writeElement("DAV::href", XMLWriter.OPENING);

                String href = req.getContextPath();
                if (href.endsWith("/") && path.startsWith("/")) {
                    href += path.substring(1);
                } else {
                    href += path;
                }
                if (so.isFolder() && !href.endsWith("/")) {
                    href += "/";
                }

                generatedXML.writeText(rewriteUrl(href));

                generatedXML.writeElement("DAV::href", XMLWriter.CLOSING);

                for (String property : tochange) {
                    generatedXML.writeElement("DAV::propstat", XMLWriter.OPENING);

                    generatedXML.writeElement("DAV::prop", XMLWriter.OPENING);
                    generatedXML.writeElement(property, XMLWriter.NO_CONTENT);
                    generatedXML.writeElement("DAV::prop", XMLWriter.CLOSING);

                    generatedXML.writeElement("DAV::status", XMLWriter.OPENING);
                    generatedXML.writeText(status);
                    generatedXML.writeElement("DAV::status", XMLWriter.CLOSING);

                    generatedXML.writeElement("DAV::propstat", XMLWriter.CLOSING);
                }

                generatedXML.writeElement("DAV::response", XMLWriter.CLOSING);

                generatedXML.writeElement("DAV::multistatus", XMLWriter.CLOSING);

                generatedXML.sendData();
            } catch (AccessDeniedException e) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            } catch (WebdavException e) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (ServletException e) {
                log.error("Could not patch properties", e);
            } finally {
                resourceLocks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
