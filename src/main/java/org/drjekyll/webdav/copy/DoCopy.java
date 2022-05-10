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
package org.drjekyll.webdav.copy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.WebdavStatus;
import org.drjekyll.webdav.exceptions.AccessDeniedException;
import org.drjekyll.webdav.exceptions.ObjectAlreadyExistsException;
import org.drjekyll.webdav.exceptions.ObjectNotFoundException;
import org.drjekyll.webdav.exceptions.WebdavException;
import org.drjekyll.webdav.locking.LockFailedException;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.methods.DeterminableMethod;
import org.drjekyll.webdav.methods.DoDelete;
import org.drjekyll.webdav.methods.Method;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;

@Slf4j
public class DoCopy extends Method {

    private final WebdavStore store;

    private final ResourceLocks resourceLocks;

    private final DoDelete doDelete;

    private final boolean readOnly;

    public DoCopy(
        WebdavStore store, ResourceLocks resourceLocks, DoDelete doDelete, boolean readOnly
    ) {
        this.store = store;
        this.resourceLocks = resourceLocks;
        this.doDelete = doDelete;
        this.readOnly = readOnly;
    }

    @Override
    public void execute(
        Transaction transaction, HttpServletRequest req, HttpServletResponse resp
    ) throws IOException {
        log.trace("-- {}", getClass().getName());

        String path = getRelativePath(req);
        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        } else {

            String tempLockOwner = "doCopy" + System.currentTimeMillis() + req;
            if (resourceLocks.lock(transaction,
                path,
                tempLockOwner,
                false,
                0,
                TEMP_TIMEOUT,
                TEMPORARY
            )) {
                try {
                    copyResource(transaction, req, resp);
                } catch (AccessDeniedException e) {
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                } catch (ObjectAlreadyExistsException e) {
                    resp.sendError(HttpServletResponse.SC_CONFLICT, req.getRequestURI());
                } catch (ObjectNotFoundException e) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
                } catch (WebdavException e) {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                } finally {
                    resourceLocks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
                }
            } else {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

        }

    }

    /**
     * Copy a resource.
     *
     * @param transaction indicates that the method is within the scope of a WebDAV transaction
     * @param req         Servlet request
     * @param resp        Servlet response
     * @return true if the copy is successful
     * @throws WebdavException     if an error in the underlying store occurs
     * @throws IOException         when an error occurs while sending the response
     * @throws LockFailedException
     */
    public boolean copyResource(
        Transaction transaction, HttpServletRequest req, HttpServletResponse resp
    ) throws IOException {

        // Parsing destination header
        String destinationPath = parseDestinationHeader(req, resp);

        if (destinationPath == null) {
            return false;
        }

        String path = getRelativePath(req);

        if (path.equals(destinationPath)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        String parentDestinationPath = getParentPath(getCleanPath(destinationPath));

        if (!checkLocks(transaction, req, resourceLocks, parentDestinationPath)) {
            resp.setStatus(WebdavStatus.SC_LOCKED);
            return false; // parentDestination is locked
        }

        if (!checkLocks(transaction, req, resourceLocks, destinationPath)) {
            resp.setStatus(WebdavStatus.SC_LOCKED);
            return false; // destination is locked
        }

        // Parsing overwrite header

        boolean overwrite = true;
        String overwriteHeader = req.getHeader("Overwrite");

        if (overwriteHeader != null) {
            overwrite = "T".equalsIgnoreCase(overwriteHeader);
        }

        // Overwriting the destination
        String lockOwner = "copyResource" + System.currentTimeMillis() + req;

        if (resourceLocks.lock(transaction,
            destinationPath,
            lockOwner,
            false,
            0,
            TEMP_TIMEOUT,
            TEMPORARY
        )) {
            try {
                StoredObject copySo = store.getStoredObject(transaction, path);
                // Retrieve the resources
                if (copySo == null) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return false;
                }

                if (copySo.isNullResource()) {
                    String methodsAllowed = DeterminableMethod.determineMethodsAllowed(copySo);
                    resp.addHeader("Allow", methodsAllowed);
                    resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    return false;
                }

                Map<String, Integer> errorList = new HashMap<>();

                StoredObject destinationSo = store.getStoredObject(transaction, destinationPath);

                if (overwrite) {

                    // Delete destination resource, if it exists
                    if (destinationSo != null) {
                        doDelete.deleteResource(transaction, destinationPath, errorList, req, resp);

                    } else {
                        resp.setStatus(HttpServletResponse.SC_CREATED);
                    }
                } else {

                    // If the destination exists, then it's a conflict
                    if (destinationSo != null) {
                        resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                        return false;
                    }
                    resp.setStatus(HttpServletResponse.SC_CREATED);

                }
                copy(transaction, path, destinationPath, errorList, req, resp);

                if (!errorList.isEmpty()) {
                    sendReport(req, resp, errorList);
                }

            } finally {
                resourceLocks.unlockTemporaryLockedObjects(transaction, destinationPath, lockOwner);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return false;
        }
        return true;

    }

    /**
     * copies the specified resource(s) to the specified destination. preconditions must be handled
     * by the caller. Standard status codes must be handled by the caller. a multi status report in
     * case of errors is created here.
     *
     * @param transaction     indicates that the method is within the scope of a WebDAV transaction
     * @param sourcePath      path from where to read
     * @param destinationPath path where to write
     * @param req             HttpServletRequest
     * @param resp            HttpServletResponse
     * @throws WebdavException if an error in the underlying store occurs
     * @throws IOException
     */
    private void copy(
        Transaction transaction,
        String sourcePath,
        String destinationPath,
        Map<String, Integer> errorList,
        HttpServletRequest req,
        HttpServletResponse resp
    ) throws IOException {

        StoredObject sourceSo = store.getStoredObject(transaction, sourcePath);
        if (sourceSo.isResource()) {
            store.createResource(transaction, destinationPath);
            long resourceLength = store.setResourceContent(transaction,
                destinationPath,
                store.getResourceContent(transaction, sourcePath),
                null,
                null
            );

            if (resourceLength != -1) {
                StoredObject destinationSo = store.getStoredObject(transaction, destinationPath);
                destinationSo.setResourceLength(resourceLength);
            }

        } else {

            if (sourceSo.isFolder()) {
                copyFolder(transaction, sourcePath, destinationPath, errorList, req, resp);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    /**
     * helper method of copy() recursively copies the FOLDER at source path to destination path
     *
     * @param transaction     indicates that the method is within the scope of a WebDAV transaction
     * @param sourcePath      where to read
     * @param destinationPath where to write
     * @param errorList       all errors that ocurred
     * @param req             HttpServletRequest
     * @param resp            HttpServletResponse
     * @throws WebdavException if an error in the underlying store occurs
     */
    private void copyFolder(
        Transaction transaction,
        String sourcePath,
        String destinationPath,
        Map<String, Integer> errorList,
        HttpServletRequest req,
        HttpServletResponse resp
    ) {

        store.createFolder(transaction, destinationPath);
        boolean infiniteDepth = true;
        String depth = req.getHeader("Depth");
        if (depth != null) {
            if ("0".equals(depth)) {
                infiniteDepth = false;
            }
        }
        if (infiniteDepth) {
            String[] children = store.getChildrenNames(transaction, sourcePath);
            children = children == null ? new String[]{} : children;

            for (int i = children.length - 1; i >= 0; i--) {
                children[i] = '/' + children[i];
                try {
                    StoredObject childSo = store.getStoredObject(transaction,
                        sourcePath + children[i]
                    );
                    if (childSo.isResource()) {
                        store.createResource(transaction, destinationPath + children[i]);
                        long resourceLength = store.setResourceContent(transaction,
                            destinationPath + children[i],
                            store.getResourceContent(transaction, sourcePath + children[i]),
                            null,
                            null
                        );

                        if (resourceLength != -1) {
                            StoredObject destinationSo = store.getStoredObject(transaction,
                                destinationPath + children[i]
                            );
                            destinationSo.setResourceLength(resourceLength);
                        }

                    } else {
                        copyFolder(transaction,
                            sourcePath + children[i],
                            destinationPath + children[i],
                            errorList,
                            req,
                            resp
                        );
                    }
                } catch (AccessDeniedException e) {
                    errorList.put(destinationPath + children[i], HttpServletResponse.SC_FORBIDDEN);
                } catch (ObjectNotFoundException e) {
                    errorList.put(destinationPath + children[i], HttpServletResponse.SC_NOT_FOUND);
                } catch (ObjectAlreadyExistsException e) {
                    errorList.put(destinationPath + children[i], HttpServletResponse.SC_CONFLICT);
                } catch (WebdavException e) {
                    errorList.put(destinationPath + children[i],
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    );
                }
            }
        }
    }

    /**
     * Parses and normalizes the destination header.
     *
     * @param req  Servlet request
     * @param resp Servlet response
     * @return destinationPath
     * @throws IOException if an error occurs while sending response
     */
    @Nullable
    private static String parseDestinationHeader(
        HttpServletRequest req, HttpServletResponse resp
    ) throws IOException {
        String destinationPath = req.getHeader("Destination");

        if (destinationPath == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        // Remove url encoding from destination
        destinationPath = RequestUtil.URLDecode(destinationPath, "UTF8");

        int protocolIndex = destinationPath.indexOf("://");
        if (protocolIndex >= 0) {
            // if the Destination URL contains the protocol, we can safely
            // trim everything upto the first "/" character after "://"
            int firstSeparator = destinationPath.indexOf('/', protocolIndex + 4);
            if (firstSeparator < 0) {
                destinationPath = "/";
            } else {
                destinationPath = destinationPath.substring(firstSeparator);
            }
        } else {
            String hostName = req.getServerName();
            if (hostName != null && destinationPath.startsWith(hostName)) {
                destinationPath = destinationPath.substring(hostName.length());
            }

            int portIndex = destinationPath.indexOf(':');
            if (portIndex >= 0) {
                destinationPath = destinationPath.substring(portIndex);
            }

            if (destinationPath.startsWith(":")) {
                int firstSeparator = destinationPath.indexOf('/');
                if (firstSeparator < 0) {
                    destinationPath = "/";
                } else {
                    destinationPath = destinationPath.substring(firstSeparator);
                }
            }
        }

        // Normalize destination path (remove '.' and' ..')
        destinationPath = normalize(destinationPath);

        String contextPath = req.getContextPath();
        if (contextPath != null && destinationPath.startsWith(contextPath)) {
            destinationPath = destinationPath.substring(contextPath.length());
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo != null) {
            String servletPath = req.getServletPath();
            if (servletPath != null && destinationPath.startsWith(servletPath)) {
                destinationPath = destinationPath.substring(servletPath.length());
            }
        }

        return destinationPath;
    }

    /**
     * Return a context-relative path, beginning with a "/", that represents the canonical version
     * of the specified path after ".." and "." elements are resolved out. If the specified path
     * attempts to go outside the boundaries of the current context (i.e. too many ".." path
     * elements are present), return {@code null} instead.
     *
     * @param path Path to be normalized
     * @return normalized path
     */
    @Nullable
    protected static String normalize(String path) {

        if (path == null) {
            return null;
        }

        // Create a place for the normalized path
        String normalized = path;

        if ("/.".equals(normalized)) {
            return "/";
        }

        // Normalize the slashes and add leading slash if necessary
        if (normalized.indexOf('\\') >= 0) {
            normalized = normalized.replace('\\', '/');
        }
        if (!normalized.startsWith("/")) {
            normalized = '/' + normalized;
        }

        // Resolve occurrences of "//" in the normalized path
        while (true) {
            int index = normalized.indexOf("//");
            if (index < 0) {
                break;
            }
            normalized = normalized.substring(0, index) + normalized.substring(index + 1);
        }

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            int index = normalized.indexOf("/./");
            if (index < 0) {
                break;
            }
            normalized = normalized.substring(0, index) + normalized.substring(index + 2);
        }

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0) {
                return normalized;
            }
            if (index == 0) {
                return null; // Trying to go outside our context
            }
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) + normalized.substring(index + 3);
        }

        // Return the normalized path that we have completed

    }

}
