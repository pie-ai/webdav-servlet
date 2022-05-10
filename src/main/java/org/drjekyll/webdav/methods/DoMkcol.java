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
public class DoMkcol extends Method {

    private final WebdavStore store;

    private final IResourceLocks resourceLocks;

    private final boolean readOnly;

    public DoMkcol(
        WebdavStore store, IResourceLocks resourceLocks, boolean readOnly
    ) {
        this.store = store;
        this.resourceLocks = resourceLocks;
        this.readOnly = readOnly;
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
            String parentPath = getParentPath(getCleanPath(path));

            if (!checkLocks(transaction, req, resourceLocks, parentPath)) {
                // TODO remove
                log.trace("MkCol on locked resource (parentPath) not executable!"
                    + "\n Sending SC_FORBIDDEN (403) error response!");

                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String tempLockOwner = "doMkcol" + System.currentTimeMillis() + req;

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
                    if (parentSo == null) {
                        // parent not exists
                        resp.sendError(HttpServletResponse.SC_CONFLICT);
                        return;
                    }
                    if (parentPath != null && parentSo.isFolder()) {
                        StoredObject so = store.getStoredObject(transaction, path);
                        if (so == null) {
                            store.createFolder(transaction, path);
                            resp.setStatus(HttpServletResponse.SC_CREATED);
                        } else {
                            // object already exists
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
                                    so.setFolder(true);

                                    String[] nullResourceLockOwners = nullResourceLo.getOwner();
                                    String owner = null;
                                    if (nullResourceLockOwners != null) {
                                        owner = nullResourceLockOwners[0];
                                    }

                                    if (resourceLocks.unlock(transaction, lockToken, owner)) {
                                        resp.setStatus(HttpServletResponse.SC_CREATED);
                                    } else {
                                        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                                    }

                                } else {
                                    // TODO remove
                                    log.trace("MkCol on lock-null-resource with wrong lock-token!"
                                        + "\n Sending multistatus error report!");

                                    Map<String, Integer> errorList = new HashMap<>();
                                    errorList.put(path, WebdavStatus.SC_LOCKED);
                                    sendReport(req, resp, errorList);
                                }

                            } else {
                                String methodsAllowed = DeterminableMethod.determineMethodsAllowed(
                                    so);
                                resp.addHeader("Allow", methodsAllowed);
                                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                            }
                        }

                    } else if (parentPath != null && parentSo.isResource()) {
                        // TODO remove
                        log.trace("MkCol on resource is not executable"
                            + "\n Sending SC_METHOD_NOT_ALLOWED (405) error response!");

                        String methodsAllowed =
                            DeterminableMethod.determineMethodsAllowed(parentSo);
                        resp.addHeader("Allow", methodsAllowed);
                        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);

                    } else {
                        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                    }
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

}
