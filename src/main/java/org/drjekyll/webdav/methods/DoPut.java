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
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.WebdavStatus;
import org.drjekyll.webdav.exceptions.AccessDeniedException;
import org.drjekyll.webdav.exceptions.WebdavException;
import org.drjekyll.webdav.locking.IResourceLocks;
import org.drjekyll.webdav.locking.LockedObject;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;

@Slf4j
public class DoPut extends Method {

    private final WebdavStore store;

    private final IResourceLocks resourceLocks;

    private final boolean readOnly;

    private final boolean lazyFolderCreationOnPut;

    private String userAgent;

    public DoPut(
        WebdavStore store,
        IResourceLocks resLocks,
        boolean readOnly,
        boolean lazyFolderCreationOnPut
    ) {
        this.store = store;
        resourceLocks = resLocks;
        this.readOnly = readOnly;
        this.lazyFolderCreationOnPut = lazyFolderCreationOnPut;
    }

    @Override
    public void execute(
        Transaction transaction, HttpServletRequest req, HttpServletResponse resp
    ) throws IOException {
        log.trace("-- {}", getClass().getName());

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        } else {
            String path = getRelativePath(req);
            String parentPath = getParentPath(path);

            userAgent = req.getHeader("User-Agent");

            if (!checkLocks(transaction, req, resourceLocks, parentPath)) {
                resp.setStatus(WebdavStatus.SC_LOCKED);
                return; // parent is locked
            }

            if (!checkLocks(transaction, req, resourceLocks, path)) {
                resp.setStatus(WebdavStatus.SC_LOCKED);
                return; // resource is locked
            }

            String tempLockOwner = "doPut" + System.currentTimeMillis() + req;
            if (resourceLocks.lock(transaction,
                path,
                tempLockOwner,
                false,
                0,
                TEMP_TIMEOUT,
                TEMPORARY
            )) {
                try {
                    StoredObject parentSo = store.getStoredObject(transaction, parentPath);
                    if (parentPath != null && parentSo != null && parentSo.isResource()) {
                        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                        return;

                    }
                    Map<String, Integer> errorList = new HashMap<>();
                    if (parentPath != null && parentSo == null && lazyFolderCreationOnPut) {
                        store.createFolder(transaction, parentPath);

                    } else if (parentPath != null && parentSo == null) {
                        errorList.put(parentPath, HttpServletResponse.SC_NOT_FOUND);
                        sendReport(req, resp, errorList);
                        return;
                    }

                    StoredObject so = store.getStoredObject(transaction, path);

                    if (so == null) {
                        store.createResource(transaction, path);
                        // resp.setStatus(WebdavStatus.SC_CREATED);
                    } else {
                        // This has already been created, just update the data
                        if (so.isNullResource()) {

                            LockedObject nullResourceLo = resourceLocks.getLockedObjectByPath(transaction,
                                path
                            );
                            if (nullResourceLo == null) {
                                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                                return;
                            }
                            String nullResourceLockToken = nullResourceLo.getID();
                            String[] lockTokens = getLockIdFromIfHeader(req);
                            String lockToken;
                            if (lockTokens != null) {
                                lockToken = lockTokens[0];
                            } else {
                                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                                return;
                            }
                            if (lockToken.equals(nullResourceLockToken)) {
                                so.setNullResource(false);
                                so.setFolder(false);

                                String[] nullResourceLockOwners = nullResourceLo.getOwner();
                                String owner = null;
                                if (nullResourceLockOwners != null) {
                                    owner = nullResourceLockOwners[0];
                                }

                                if (!resourceLocks.unlock(transaction, lockToken, owner)) {
                                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                                }
                            } else {
                                errorList.put(path, WebdavStatus.SC_LOCKED);
                                sendReport(req, resp, errorList);
                            }
                        }
                    }
                    // User-Agent workarounds
                    doUserAgentWorkaround(resp);

                    // setting resourceContent
                    long resourceLength = store.setResourceContent(transaction,
                        path,
                        req.getInputStream(),
                        null,
                        null
                    );

                    so = store.getStoredObject(transaction, path);
                    if (resourceLength != -1) {
                        so.setResourceLength(resourceLength);
                    }
                    // Now lets report back what was actually saved

                } catch (AccessDeniedException e) {
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
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
     * @param resp
     */
    private void doUserAgentWorkaround(HttpServletResponse resp) {
        if (userAgent != null
            && userAgent.contains("WebDAVFS")
            && !userAgent.contains("Transmit")) {
            log.trace("DoPut.execute() : do workaround for user agent '{}'", userAgent);
            resp.setStatus(HttpServletResponse.SC_CREATED);
        } else if (userAgent != null && userAgent.contains("Transmit")) {
            // Transmit also uses WEBDAVFS 1.x.x but crashes
            // with SC_CREATED response
            log.trace("DoPut.execute() : do workaround for user agent '{}'", userAgent);
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } else {
            resp.setStatus(HttpServletResponse.SC_CREATED);
        }
    }
}
