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

package org.drjekyll.webdav.methods;

import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TimeZone;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.drjekyll.webdav.MethodExecutor;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.WebdavStatus;
import org.drjekyll.webdav.XMLWriter;
import org.drjekyll.webdav.locking.IResourceLocks;
import org.drjekyll.webdav.locking.LockFailedException;
import org.drjekyll.webdav.locking.LockedObject;
import org.drjekyll.webdav.store.StoredObject;

public abstract class Method implements MethodExecutor {

    /**
     * Array containing the safe characters set.
     */
    protected static final URLEncoder URL_ENCODER;

    /**
     * Default depth is infite.
     */
    protected static final int INFINITY = 3;

    /**
     * Simple date format for the creation date ISO 8601 representation (partial).
     */
    protected static final String CREATION_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    /**
     * Simple date format for the last modified date. (RFC 822 updated by RFC 1123)
     */
    protected static final String LAST_MODIFIED_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";

    protected static final String LOCAL_DATE_FORMAT = "dd/MM/yy' 'HH:mm:ss";

    /**
     * size of the io-buffer
     */
    protected static final int BUF_SIZE = 65536;

    /**
     * Default lock timeout value.
     */
    protected static final int DEFAULT_TIMEOUT = 3600;

    /**
     * Maximum lock timeout.
     */
    protected static final int MAX_TIMEOUT = 604800;

    /**
     * Boolean value to temporary lock resources (for method locks)
     */
    protected static final boolean TEMPORARY = true;

    /**
     * Timeout for temporary locks
     */
    protected static final int TEMP_TIMEOUT = 10;

    private static final ThreadLocal<DateFormat> TH_LASTMODIFIED_DATE_FORMAT = new ThreadLocal<>();

    private static final ThreadLocal<DateFormat> TH_CREATION_DATE_FORMAT = new ThreadLocal<>();

    private static final ThreadLocal<DateFormat> TH_LOCAL_DATE_FORMAT = new ThreadLocal<>();

    static {
        /*
         * GMT timezone - all HTTP dates are on GMT
         */
        URL_ENCODER = new URLEncoder();
        URL_ENCODER.addSafeCharacter('-');
        URL_ENCODER.addSafeCharacter('_');
        URL_ENCODER.addSafeCharacter('.');
        URL_ENCODER.addSafeCharacter('*');
        URL_ENCODER.addSafeCharacter('/');
    }

    public static String lastModifiedDateFormat(final Date date) {
        DateFormat df = TH_LASTMODIFIED_DATE_FORMAT.get();
        if (df == null) {
            df = new SimpleDateFormat(LAST_MODIFIED_DATE_FORMAT, Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            TH_LASTMODIFIED_DATE_FORMAT.set(df);
        }
        return df.format(date);
    }

    public static String creationDateFormat(final Date date) {
        DateFormat df = TH_CREATION_DATE_FORMAT.get();
        if (df == null) {
            df = new SimpleDateFormat(CREATION_DATE_FORMAT);
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            TH_CREATION_DATE_FORMAT.set(df);
        }
        return df.format(date);
    }

    public static String getLocalDateFormat(final Date date, final Locale loc) {
        DateFormat df = TH_LOCAL_DATE_FORMAT.get();
        if (df == null) {
            df = new SimpleDateFormat(LOCAL_DATE_FORMAT, loc);
        }
        return df.format(date);
    }


    /**
     * Return the relative path associated with this servlet.
     *
     * @param request The servlet request we are processing
     */
    protected static String getRelativePath(HttpServletRequest request) {

        // Are we being processed by a RequestDispatcher.include()?
        if (request.getAttribute("javax.servlet.include.request_uri") != null) {
            String result = (String) request.getAttribute("javax.servlet.include.path_info");
            if (result == null || result.isEmpty()) {
                return "/";
            }
            return result;
        }

        // No, extract the desired path directly from the request
        String result = request.getPathInfo();
        if (result == null || result.isEmpty()) {
            return "/";
        }
        return result;

    }

    /**
     * creates the parent path from the given path by removing the last '/' and everything after
     * that
     *
     * @param path the path
     * @return parent path
     */
    @Nullable
    protected static String getParentPath(String path) {
        int slash = path.lastIndexOf('/');
        if (slash != -1) {
            return path.substring(0, slash);
        }
        return null;
    }

    /**
     * removes a / at the end of the path string, if present
     *
     * @param path the path
     * @return the path without trailing /
     */
    protected static String getCleanPath(String path) {
        if (path.endsWith("/") && path.length() > 1) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * Return JAXP document builder instance.
     */
    protected static DocumentBuilder getDocumentBuilder() throws ServletException {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            return documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new ServletException("jaxp failed", e);
        }
    }

    /**
     * reads the depth header from the request and returns it as a int
     *
     * @param req the request
     * @return the depth from the depth header
     */
    protected static int getDepth(HttpServletRequest req) {
        String depthStr = req.getHeader("Depth");
        if (depthStr != null) {
            if ("0".equals(depthStr)) {
                return 0;
            }
            if ("1".equals(depthStr)) {
                return 1;
            }
        }
        return INFINITY;
    }

    /**
     * URL rewriter.
     *
     * @param path Path which has to be rewiten
     * @return the rewritten path
     */
    protected static String rewriteUrl(String path) {
        return URL_ENCODER.encode(path);
    }

    /**
     * Get the ETag associated with a file.
     *
     * @param so StoredObject to get resourceLength, lastModified and a hashCode of StoredObject
     * @return the ETag
     */
    protected static String getETag(StoredObject so) {

        String resourceLength = "";
        String lastModified = "";

        if (so != null && so.isResource()) {
            resourceLength = Long.toString(so.getResourceLength());
            lastModified = Long.toString(so.getLastModified().getTime());
        }

        return "W/\"" + resourceLength + '-' + lastModified + '"';

    }

    @Nullable
    protected static String getLockIdFromLockTokenHeader(HttpServletRequest req) {
        String id = req.getHeader("Lock-Token");
        if (id != null) {
            return id.substring(id.indexOf(':') + 1, id.indexOf('>'));

        }
        return null;
    }

    /**
     * Checks if locks on resources at the given path exists and if so checks the If-Header to make
     * sure the If-Header corresponds to the locked resource. Returning true if no lock exists or
     * the If-Header is corresponding to the locked resource
     *
     * @param req           Servlet request
     * @param resourceLocks
     * @param path          path to the resource
     * @return true if no lock on a resource with the given path exists or if the If-Header
     * corresponds to the locked resource
     * @throws LockFailedException
     */
    protected static boolean checkLocks(
        Transaction transaction, HttpServletRequest req, IResourceLocks resourceLocks, String path
    ) {

        LockedObject loByPath = resourceLocks.getLockedObjectByPath(transaction, path);
        if (loByPath != null) {

            if (loByPath.isShared()) {
                return true;
            }

            // the resource is locked
            String[] lockTokens = getLockIdFromIfHeader(req);
            String lockToken;
            if (lockTokens != null) {
                lockToken = lockTokens[0];
            } else {
                return false;
            }
            if (lockToken != null) {
                LockedObject loByIf = resourceLocks.getLockedObjectByID(transaction, lockToken);
                // no locked resource to the given lockToken
                return loByIf == null || loByIf.equals(loByPath);
            }

        }
        return true;
    }

    @Nullable
    protected static String[] getLockIdFromIfHeader(HttpServletRequest req) {
        String[] ids = new String[2];
        String id = req.getHeader("If");

        if (id != null && !id.isEmpty()) {
            if (id.indexOf(">)") == id.lastIndexOf(">)")) {
                id = id.substring(id.indexOf("(<"), id.indexOf(">)"));

                if (id.contains("locktoken:")) {
                    id = id.substring(id.indexOf(':') + 1);
                }
                ids[0] = id;
            } else {
                String firstId = id.substring(id.indexOf("(<"), id.indexOf(">)"));
                if (firstId.contains("locktoken:")) {
                    firstId = firstId.substring(firstId.indexOf(':') + 1);
                }
                ids[0] = firstId;

                String secondId = id.substring(id.lastIndexOf("(<"), id.lastIndexOf(">)"));
                if (secondId.contains("locktoken:")) {
                    secondId = secondId.substring(secondId.indexOf(':') + 1);
                }
                ids[1] = secondId;
            }

        } else {
            return null;
        }
        return ids;
    }

    /**
     * Send a multistatus element containing a complete error report to the client. If the errorList
     * contains only one error, send the error directly without wrapping it in a multistatus
     * message.
     *
     * @param req       Servlet request
     * @param resp      Servlet response
     * @param errorList List of error to be displayed
     */
    protected static void sendReport(
        HttpServletRequest req, HttpServletResponse resp, Map<String, Integer> errorList
    ) throws IOException {

        if (errorList.size() == 1) {
            int code = errorList.values().iterator().next();
            if (Objects.equals(WebdavStatus.getStatusText(code), "")) {
                resp.sendError(code);
            } else {
                resp.sendError(code, WebdavStatus.getStatusText(code));
            }
        } else {
            resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

            HashMap<String, String> namespaces = new HashMap<>();
            namespaces.put("DAV:", "D");

            XMLWriter generatedXML = new XMLWriter(namespaces);
            generatedXML.writeXMLHeader();

            generatedXML.writeElement("DAV::multistatus", XMLWriter.OPENING);

            for (Entry<String, Integer> entry : errorList.entrySet()) {
                String errorPath = entry.getKey();

                int errorCode = entry.getValue();

                generatedXML.writeElement("DAV::response", XMLWriter.OPENING);
                generatedXML.writeElement("DAV::href", XMLWriter.OPENING);
                generatedXML.writeText(errorPath);
                generatedXML.writeElement("DAV::href", XMLWriter.CLOSING);
                generatedXML.writeElement("DAV::status", XMLWriter.OPENING);
                generatedXML.writeText("HTTP/1.1 " + errorCode + ' ' + WebdavStatus.getStatusText(
                    errorCode));
                generatedXML.writeElement("DAV::status", XMLWriter.CLOSING);

                generatedXML.writeElement("DAV::response", XMLWriter.CLOSING);

            }

            generatedXML.writeElement("DAV::multistatus", XMLWriter.CLOSING);

            Writer writer = resp.getWriter();
            writer.write(generatedXML.toString());
            writer.close();
        }
    }

}
