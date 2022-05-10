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
import org.drjekyll.webdav.MimeTyper;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.WebdavStatus;
import org.drjekyll.webdav.XMLWriter;
import org.drjekyll.webdav.exceptions.AccessDeniedException;
import org.drjekyll.webdav.exceptions.WebdavException;
import org.drjekyll.webdav.locking.LockedObject;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.methods.Method;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

@Slf4j
public class DoPropfind extends Method {

    /**
     * PROPFIND - Specify a property mask.
     */
    private static final int FIND_BY_PROPERTY = 0;

    /**
     * PROPFIND - Display all properties.
     */
    private static final int FIND_ALL_PROP = 1;

    /**
     * PROPFIND - Return property names.
     */
    private static final int FIND_PROPERTY_NAMES = 2;

    private final WebdavStore store;

    private final ResourceLocks resourceLocks;

    private final MimeTyper mimeTyper;

    private int depth;

    public DoPropfind(
        WebdavStore store, ResourceLocks resLocks, MimeTyper mimeTyper
    ) {
        this.store = store;
        resourceLocks = resLocks;
        this.mimeTyper = mimeTyper;
    }

    @Override
    public void execute(
        Transaction transaction, HttpServletRequest req, HttpServletResponse resp
    ) throws IOException {
        log.trace("-- {}", getClass().getName());

        // Retrieve the resources
        String path = getCleanPath(getRelativePath(req));
        String tempLockOwner = "doPropfind" + System.currentTimeMillis() + req;
        depth = getDepth(req);

        if (resourceLocks.lock(transaction,
            path,
            tempLockOwner,
            false,
            depth,
            TEMP_TIMEOUT,
            TEMPORARY
        )) {

            try {
                StoredObject so = store.getStoredObject(transaction, path);
                if (so == null) {
                    resp.setContentType("text/xml; charset=UTF-8");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
                    return;
                }

                path = getCleanPath(getRelativePath(req));

                int propertyFindType = FIND_ALL_PROP;
                Node propNode = null;

                if (req.getContentLength() != 0) {
                    DocumentBuilder documentBuilder = getDocumentBuilder();
                    try {
                        Document document =
                            documentBuilder.parse(new InputSource(req.getInputStream()));
                        // Get the root element of the document
                        Element rootElement = document.getDocumentElement();

                        propNode = XMLHelper.findSubElement(rootElement, "prop");
                        if (propNode != null) {
                            propertyFindType = FIND_BY_PROPERTY;
                        } else if (XMLHelper.findSubElement(rootElement, "propname") != null) {
                            propertyFindType = FIND_PROPERTY_NAMES;
                        } else if (XMLHelper.findSubElement(rootElement, "allprop") != null) {
                            propertyFindType = FIND_ALL_PROP;
                        }
                    } catch (Exception e) {
                        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    }
                }

                HashMap<String, String> namespaces = new HashMap<>();
                namespaces.put("DAV:", "D");

                List<String> properties = null;
                if (propertyFindType == FIND_BY_PROPERTY) {
                    properties = XMLHelper.getPropertiesFromXML(propNode);
                }

                resp.setStatus(WebdavStatus.SC_MULTI_STATUS);
                resp.setContentType("text/xml; charset=UTF-8");

                // Create multistatus object
                XMLWriter generatedXML = new XMLWriter(resp.getWriter(), namespaces);
                generatedXML.writeXMLHeader();
                generatedXML.writeElement("DAV::multistatus", XMLWriter.OPENING);
                if (depth == 0) {
                    parseProperties(transaction,
                        req,
                        generatedXML,
                        path,
                        propertyFindType,
                        properties,
                        mimeTyper.getMimeType(transaction, path)
                    );
                } else {
                    recursiveParseProperties(transaction,
                        path,
                        req,
                        generatedXML,
                        propertyFindType,
                        properties,
                        depth,
                        mimeTyper.getMimeType(transaction, path)
                    );
                }
                generatedXML.writeElement("DAV::multistatus", XMLWriter.CLOSING);

                generatedXML.sendData();
            } catch (AccessDeniedException e) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            } catch (WebdavException e) {
                log.warn("Sending internal error!");
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (ServletException e) {
                log.error("Could not find properties", e);
            } finally {
                resourceLocks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
            }
        } else {
            Map<String, Integer> errorList = new HashMap<>();
            errorList.put(path, WebdavStatus.SC_LOCKED);
            sendReport(req, resp, errorList);
        }
    }

    /**
     * goes recursive through all folders. used by propfind
     *
     * @param currentPath      the current path
     * @param req              HttpServletRequest
     * @param generatedXML
     * @param propertyFindType
     * @param properties
     * @param depth            depth of the propfind
     * @throws IOException if an error in the underlying store occurs
     */
    private void recursiveParseProperties(
        Transaction transaction,
        String currentPath,
        HttpServletRequest req,
        XMLWriter generatedXML,
        int propertyFindType,
        List<String> properties,
        int depth,
        String mimeType
    ) {

        parseProperties(transaction,
            req,
            generatedXML,
            currentPath,
            propertyFindType,
            properties,
            mimeType
        );

        if (depth > 0) {
            // no need to get name if depth is already zero
            String[] names = store.getChildrenNames(transaction, currentPath);
            names = names == null ? new String[]{} : names;
            for (String name : names) {
                String newPath = currentPath;
                if (!newPath.endsWith("/")) {
                    newPath += "/";
                }
                newPath += name;
                recursiveParseProperties(transaction,
                    newPath,
                    req,
                    generatedXML,
                    propertyFindType,
                    properties,
                    depth - 1,
                    mimeType
                );
            }
        }
    }

    /**
     * Propfind helper method.
     *
     * @param req          The servlet request
     * @param generatedXML XML response to the Propfind request
     * @param path         Path of the current resource
     * @param type         Propfind type
     * @param properties   If the propfind type is find properties by name, then this parameter
     *                     contains those properties
     */
    private void parseProperties(
        Transaction transaction,
        HttpServletRequest req,
        XMLWriter generatedXML,
        String path,
        int type,
        Iterable<String> properties,
        String mimeType
    ) {

        StoredObject so = store.getStoredObject(transaction, path);

        boolean isFolder = so.isFolder();
        final String creationdate = creationDateFormat(so.getCreationDate());
        final String lastModified = lastModifiedDateFormat(so.getLastModified());
        String resourceLength = String.valueOf(so.getResourceLength());

        // ResourceInfo resourceInfo = new ResourceInfo(path, resources);

        generatedXML.writeElement("DAV::response", XMLWriter.OPENING);
        String status = "HTTP/1.1 " + HttpServletResponse.SC_OK + ' ' + WebdavStatus.getStatusText(
            HttpServletResponse.SC_OK);

        // Generating href element
        generatedXML.writeElement("DAV::href", XMLWriter.OPENING);

        String href = req.getContextPath();
        String servletPath = req.getServletPath();
        if (servletPath != null) {
            if (href.endsWith("/") && servletPath.startsWith("/")) {
                href += servletPath.substring(1);
            } else {
                href += servletPath;
            }
        }
        if (href.endsWith("/") && path.startsWith("/")) {
            href += path.substring(1);
        } else {
            href += path;
        }
        if (isFolder && !href.endsWith("/")) {
            href += "/";
        }

        generatedXML.writeText(rewriteUrl(href));

        generatedXML.writeElement("DAV::href", XMLWriter.CLOSING);

        String resourceName = path;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            resourceName = resourceName.substring(lastSlash + 1);
        }

        switch (type) {

            case FIND_ALL_PROP:

                generatedXML.writeElement("DAV::propstat", XMLWriter.OPENING);
                generatedXML.writeElement("DAV::prop", XMLWriter.OPENING);

                generatedXML.writeProperty("DAV::creationdate", creationdate);
                generatedXML.writeElement("DAV::displayname", XMLWriter.OPENING);
                generatedXML.writeData(resourceName);
                generatedXML.writeElement("DAV::displayname", XMLWriter.CLOSING);
                if (isFolder) {
                    generatedXML.writeElement("DAV::resourcetype", XMLWriter.OPENING);
                    generatedXML.writeElement("DAV::collection", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement("DAV::resourcetype", XMLWriter.CLOSING);
                } else {
                    generatedXML.writeProperty("DAV::getlastmodified", lastModified);
                    generatedXML.writeProperty("DAV::getcontentlength", resourceLength);
                    if (mimeType != null) {
                        generatedXML.writeProperty("DAV::getcontenttype", mimeType);
                    }
                    generatedXML.writeProperty("DAV::getetag", getETag(so));
                    generatedXML.writeElement("DAV::resourcetype", XMLWriter.NO_CONTENT);
                }

                writeSupportedLockElements(transaction, generatedXML, path);

                writeLockDiscoveryElements(transaction, generatedXML, path);

                generatedXML.writeProperty("DAV::source", "");
                generatedXML.writeElement("DAV::prop", XMLWriter.CLOSING);
                generatedXML.writeElement("DAV::status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement("DAV::status", XMLWriter.CLOSING);
                generatedXML.writeElement("DAV::propstat", XMLWriter.CLOSING);

                break;

            case FIND_PROPERTY_NAMES:

                generatedXML.writeElement("DAV::propstat", XMLWriter.OPENING);
                generatedXML.writeElement("DAV::prop", XMLWriter.OPENING);

                generatedXML.writeElement("DAV::creationdate", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("DAV::displayname", XMLWriter.NO_CONTENT);
                if (!isFolder) {
                    generatedXML.writeElement("DAV::getcontentlanguage", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement("DAV::getcontentlength", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement("DAV::getcontenttype", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement("DAV::getetag", XMLWriter.NO_CONTENT);
                    generatedXML.writeElement("DAV::getlastmodified", XMLWriter.NO_CONTENT);
                }
                generatedXML.writeElement("DAV::resourcetype", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("DAV::supportedlock", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("DAV::source", XMLWriter.NO_CONTENT);

                generatedXML.writeElement("DAV::prop", XMLWriter.CLOSING);
                generatedXML.writeElement("DAV::status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement("DAV::status", XMLWriter.CLOSING);
                generatedXML.writeElement("DAV::propstat", XMLWriter.CLOSING);

                break;

            case FIND_BY_PROPERTY:

                // Parse the list of properties

                generatedXML.writeElement("DAV::propstat", XMLWriter.OPENING);
                generatedXML.writeElement("DAV::prop", XMLWriter.OPENING);

                Collection<String> propertiesNotFound = new ArrayList<>();
                for (String property : properties) {

                    if ("DAV::creationdate".equals(property)) {
                        generatedXML.writeProperty("DAV::creationdate", creationdate);
                    } else if ("DAV::displayname".equals(property)) {
                        generatedXML.writeElement("DAV::displayname", XMLWriter.OPENING);
                        generatedXML.writeData(resourceName);
                        generatedXML.writeElement("DAV::displayname", XMLWriter.CLOSING);
                    } else if ("DAV::getcontentlanguage".equals(property)) {
                        if (isFolder) {
                            propertiesNotFound.add(property);
                        } else {
                            generatedXML.writeElement("DAV::getcontentlanguage",
                                XMLWriter.NO_CONTENT
                            );
                        }
                    } else if ("DAV::getcontentlength".equals(property)) {
                        if (isFolder) {
                            propertiesNotFound.add(property);
                        } else {
                            generatedXML.writeProperty("DAV::getcontentlength", resourceLength);
                        }
                    } else if ("DAV::getcontenttype".equals(property)) {
                        if (isFolder) {
                            propertiesNotFound.add(property);
                        } else {
                            generatedXML.writeProperty("DAV::getcontenttype", mimeType);
                        }
                    } else if ("DAV::getetag".equals(property)) {
                        if (isFolder || so.isNullResource()) {
                            propertiesNotFound.add(property);
                        } else {
                            generatedXML.writeProperty("DAV::getetag", getETag(so));
                        }
                    } else if ("DAV::getlastmodified".equals(property)) {
                        if (isFolder) {
                            propertiesNotFound.add(property);
                        } else {
                            generatedXML.writeProperty("DAV::getlastmodified", lastModified);
                        }
                    } else if ("DAV::resourcetype".equals(property)) {
                        if (isFolder) {
                            generatedXML.writeElement("DAV::resourcetype", XMLWriter.OPENING);
                            generatedXML.writeElement("DAV::collection", XMLWriter.NO_CONTENT);
                            generatedXML.writeElement("DAV::resourcetype", XMLWriter.CLOSING);
                        } else {
                            generatedXML.writeElement("DAV::resourcetype", XMLWriter.NO_CONTENT);
                        }
                    } else if ("DAV::source".equals(property)) {
                        generatedXML.writeProperty("DAV::source", "");
                    } else if ("DAV::supportedlock".equals(property)) {

                        writeSupportedLockElements(transaction, generatedXML, path);

                    } else if ("DAV::lockdiscovery".equals(property)) {

                        writeLockDiscoveryElements(transaction, generatedXML, path);

                    } else {
                        propertiesNotFound.add(property);
                    }

                }

                generatedXML.writeElement("DAV::prop", XMLWriter.CLOSING);
                generatedXML.writeElement("DAV::status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement("DAV::status", XMLWriter.CLOSING);
                generatedXML.writeElement("DAV::propstat", XMLWriter.CLOSING);

                if (!propertiesNotFound.isEmpty()) {

                    status = "HTTP/1.1 "
                        + HttpServletResponse.SC_NOT_FOUND
                        + ' '
                        + WebdavStatus.getStatusText(HttpServletResponse.SC_NOT_FOUND);

                    generatedXML.writeElement("DAV::propstat", XMLWriter.OPENING);
                    generatedXML.writeElement("DAV::prop", XMLWriter.OPENING);

                    for (String property : propertiesNotFound) {
                        generatedXML.writeElement(property, XMLWriter.NO_CONTENT);
                    }

                    generatedXML.writeElement("DAV::prop", XMLWriter.CLOSING);
                    generatedXML.writeElement("DAV::status", XMLWriter.OPENING);
                    generatedXML.writeText(status);
                    generatedXML.writeElement("DAV::status", XMLWriter.CLOSING);
                    generatedXML.writeElement("DAV::propstat", XMLWriter.CLOSING);

                }

                break;

        }

        generatedXML.writeElement("DAV::response", XMLWriter.CLOSING);

    }

    private void writeSupportedLockElements(
        Transaction transaction, XMLWriter generatedXML, String path
    ) {

        LockedObject lo = resourceLocks.getLockedObjectByPath(transaction, path);

        generatedXML.writeElement("DAV::supportedlock", XMLWriter.OPENING);

        if (lo == null) {
            // both locks (shared/exclusive) can be granted
            generatedXML.writeElement("DAV::lockentry", XMLWriter.OPENING);

            generatedXML.writeElement("DAV::lockscope", XMLWriter.OPENING);
            generatedXML.writeElement("DAV::exclusive", XMLWriter.NO_CONTENT);
            generatedXML.writeElement("DAV::lockscope", XMLWriter.CLOSING);

            generatedXML.writeElement("DAV::locktype", XMLWriter.OPENING);
            generatedXML.writeElement("DAV::write", XMLWriter.NO_CONTENT);
            generatedXML.writeElement("DAV::locktype", XMLWriter.CLOSING);

            generatedXML.writeElement("DAV::lockentry", XMLWriter.CLOSING);

            generatedXML.writeElement("DAV::lockentry", XMLWriter.OPENING);

            generatedXML.writeElement("DAV::lockscope", XMLWriter.OPENING);
            generatedXML.writeElement("DAV::shared", XMLWriter.NO_CONTENT);
            generatedXML.writeElement("DAV::lockscope", XMLWriter.CLOSING);

            generatedXML.writeElement("DAV::locktype", XMLWriter.OPENING);
            generatedXML.writeElement("DAV::write", XMLWriter.NO_CONTENT);
            generatedXML.writeElement("DAV::locktype", XMLWriter.CLOSING);

            generatedXML.writeElement("DAV::lockentry", XMLWriter.CLOSING);

        } else {
            // LockObject exists, checking lock state
            // if an exclusive lock exists, no further lock is possible
            if (lo.isShared()) {

                generatedXML.writeElement("DAV::lockentry", XMLWriter.OPENING);

                generatedXML.writeElement("DAV::lockscope", XMLWriter.OPENING);
                generatedXML.writeElement("DAV::shared", XMLWriter.NO_CONTENT);
                generatedXML.writeElement("DAV::lockscope", XMLWriter.CLOSING);

                generatedXML.writeElement("DAV::locktype", XMLWriter.OPENING);
                generatedXML.writeElement("DAV::" + lo.getType(), XMLWriter.NO_CONTENT);
                generatedXML.writeElement("DAV::locktype", XMLWriter.CLOSING);

                generatedXML.writeElement("DAV::lockentry", XMLWriter.CLOSING);
            }
        }

        generatedXML.writeElement("DAV::supportedlock", XMLWriter.CLOSING);

    }

    private void writeLockDiscoveryElements(
        Transaction transaction, XMLWriter generatedXML, String path
    ) {

        LockedObject lo = resourceLocks.getLockedObjectByPath(transaction, path);

        if (lo != null && !lo.hasExpired()) {

            generatedXML.writeElement("DAV::lockdiscovery", XMLWriter.OPENING);
            generatedXML.writeElement("DAV::activelock", XMLWriter.OPENING);

            generatedXML.writeElement("DAV::locktype", XMLWriter.OPENING);
            generatedXML.writeProperty("DAV::" + lo.getType());
            generatedXML.writeElement("DAV::locktype", XMLWriter.CLOSING);

            generatedXML.writeElement("DAV::lockscope", XMLWriter.OPENING);
            if (lo.isExclusive()) {
                generatedXML.writeProperty("DAV::exclusive");
            } else {
                generatedXML.writeProperty("DAV::shared");
            }
            generatedXML.writeElement("DAV::lockscope", XMLWriter.CLOSING);

            generatedXML.writeElement("DAV::depth", XMLWriter.OPENING);
            if (depth == INFINITY) {
                generatedXML.writeText("Infinity");
            } else {
                generatedXML.writeText(String.valueOf(depth));
            }
            generatedXML.writeElement("DAV::depth", XMLWriter.CLOSING);

            String[] owners = lo.getOwner();
            if (owners != null) {
                for (String owner : owners) {
                    generatedXML.writeElement("DAV::owner", XMLWriter.OPENING);
                    generatedXML.writeElement("DAV::href", XMLWriter.OPENING);
                    generatedXML.writeText(owner);
                    generatedXML.writeElement("DAV::href", XMLWriter.CLOSING);
                    generatedXML.writeElement("DAV::owner", XMLWriter.CLOSING);
                }
            } else {
                generatedXML.writeElement("DAV::owner", XMLWriter.NO_CONTENT);
            }

            int timeout = (int) (lo.getTimeoutMillis() / 1000);
            generatedXML.writeElement("DAV::timeout", XMLWriter.OPENING);
            String timeoutStr = Integer.toString(timeout);
            generatedXML.writeText("Second-" + timeoutStr);
            generatedXML.writeElement("DAV::timeout", XMLWriter.CLOSING);

            String lockToken = lo.getID();

            generatedXML.writeElement("DAV::locktoken", XMLWriter.OPENING);
            generatedXML.writeElement("DAV::href", XMLWriter.OPENING);
            generatedXML.writeText("opaquelocktoken:" + lockToken);
            generatedXML.writeElement("DAV::href", XMLWriter.CLOSING);
            generatedXML.writeElement("DAV::locktoken", XMLWriter.CLOSING);

            generatedXML.writeElement("DAV::activelock", XMLWriter.CLOSING);
            generatedXML.writeElement("DAV::lockdiscovery", XMLWriter.CLOSING);

        } else {
            generatedXML.writeElement("DAV::lockdiscovery", XMLWriter.NO_CONTENT);
        }

    }

}
