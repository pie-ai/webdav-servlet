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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.drjekyll.webdav.methods;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.drjekyll.webdav.MimeTyper;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.exceptions.AccessDeniedException;
import org.drjekyll.webdav.exceptions.ObjectAlreadyExistsException;
import org.drjekyll.webdav.exceptions.WebdavException;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;

@Slf4j
public class DoHead extends Method {

    private final String dftIndexFile;

    private final WebdavStore store;

    private final String insteadOf404;

    private final ResourceLocks resourceLocks;

    private final MimeTyper mimeTyper;

    private final int contentLength;

    public DoHead(
        WebdavStore store,
        String dftIndexFile,
        String insteadOf404,
        ResourceLocks resourceLocks,
        MimeTyper mimeTyper,
        int contentLengthHeader
    ) {
        this.store = store;
        this.dftIndexFile = dftIndexFile;
        this.insteadOf404 = insteadOf404;
        this.resourceLocks = resourceLocks;
        this.mimeTyper = mimeTyper;
        contentLength = contentLengthHeader;
    }

    @Override
    public void execute(
        Transaction transaction, HttpServletRequest req, HttpServletResponse resp
    ) throws IOException {

        // determines if the uri exists.

        String path = getRelativePath(req);
        log.trace("-- {}", getClass().getName());

        StoredObject so;
        boolean bUriExists = false;
        try {
            so = store.getStoredObject(transaction, path);
            if (so == null) {
                if (insteadOf404 != null && !insteadOf404.trim().isEmpty()) {
                    path = insteadOf404;
                    so = store.getStoredObject(transaction, insteadOf404);
                }
            } else {
                bUriExists = true;
            }
        } catch (AccessDeniedException e) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (so != null) {
            if (so.isFolder()) {
                if (dftIndexFile != null && !dftIndexFile.trim().isEmpty()) {
                    resp.sendRedirect(resp.encodeRedirectURL(req.getRequestURI() + dftIndexFile));
                    return;
                }
            } else if (so.isNullResource()) {
                String methodsAllowed = DeterminableMethod.determineMethodsAllowed(so);
                resp.addHeader("Allow", methodsAllowed);
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }

            String tempLockOwner = "doGet" + System.currentTimeMillis() + req;

            if (resourceLocks.lock(transaction,
                path,
                tempLockOwner,
                false,
                0,
                TEMP_TIMEOUT,
                TEMPORARY
            )) {
                try {

                    String eTagMatch = req.getHeader("If-None-Match");
                    if (eTagMatch != null) {
                        if (eTagMatch.equals(getETag(so))) {
                            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                            return;
                        }
                    }

                    if (so.isResource()) {
                        // path points to a file but ends with / or \
                        if (path.endsWith("/") || path.endsWith("\\")) {
                            resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
                        } else {

                            // setting headers
                            long lastModified = so.getLastModified().getTime();
                            resp.setDateHeader("last-modified", lastModified);

                            String eTag = getETag(so);
                            resp.addHeader("ETag", eTag);

                            long resourceLength = so.getResourceLength();

                            if (contentLength == 1) {
                                if (resourceLength > 0) {
                                    if (resourceLength <= Integer.MAX_VALUE) {
                                        resp.setContentLength((int) resourceLength);
                                    } else {
                                        resp.setHeader("content-length",
                                            String.valueOf(resourceLength)
                                        );
                                        // is "content-length" the right header?
                                        // is long a valid format?
                                    }
                                }
                            }

                            String mimeType = mimeTyper.getMimeType(transaction, path);
                            if (mimeType != null) {
                                resp.setContentType(mimeType);
                            } else {
                                int lastSlash = path.replace('\\', '/').lastIndexOf('/');
                                int lastDot = path.indexOf('.', lastSlash);
                                if (lastDot == -1) {
                                    resp.setContentType("text/html");
                                }
                            }

                            doBody(transaction, resp, path);
                        }
                    } else {
                        folderBody(transaction, path, resp, req);
                    }
                } catch (AccessDeniedException e) {
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                } catch (ObjectAlreadyExistsException e) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
                } catch (WebdavException e) {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                } finally {
                    resourceLocks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
                }
            } else {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } else {
            folderBody(transaction, path, resp, req);
        }

        if (!bUriExists) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

    }

    protected void doBody(
        Transaction transaction, HttpServletResponse resp, String path
    ) {
        // no body for HEAD
    }

    protected void folderBody(
        Transaction transaction, String path, HttpServletResponse resp, HttpServletRequest req
    ) throws IOException {
        // no body for HEAD
    }
}
