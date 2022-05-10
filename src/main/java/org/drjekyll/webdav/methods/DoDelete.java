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
import org.drjekyll.webdav.exceptions.ObjectAlreadyExistsException;
import org.drjekyll.webdav.exceptions.ObjectNotFoundException;
import org.drjekyll.webdav.exceptions.WebdavException;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;

@Slf4j
public class DoDelete extends Method {

    private final WebdavStore store;

    private final ResourceLocks resourceLocks;

    private final boolean readOnly;

    public DoDelete(
        WebdavStore store, ResourceLocks resourceLocks, boolean readOnly
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
                resp.setStatus(WebdavStatus.SC_LOCKED);
                return; // parent is locked
            }

            if (!checkLocks(transaction, req, resourceLocks, path)) {
                resp.setStatus(WebdavStatus.SC_LOCKED);
                return; // resource is locked
            }

            String tempLockOwner = "doDelete" + System.currentTimeMillis() + req;
            if (resourceLocks.lock(transaction,
                path,
                tempLockOwner,
                false,
                0,
                TEMP_TIMEOUT,
                TEMPORARY
            )) {
                try {
                    Map<String, Integer> errorList = new HashMap<>();
                    deleteResource(transaction, path, errorList, req, resp);
                    if (!errorList.isEmpty()) {
                        sendReport(req, resp, errorList);
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
        }

    }

    /**
     * deletes the recources at "path"
     *
     * @param transaction indicates that the method is within the scope of a WebDAV transaction
     * @param path        the folder to be deleted
     * @param errorList   all errors that ocurred
     * @param req         HttpServletRequest
     * @param resp        HttpServletResponse
     * @throws WebdavException if an error in the underlying store occurs
     * @throws IOException     when an error occurs while sending the response
     */
    public void deleteResource(
        Transaction transaction,
        String path,
        Map<String, Integer> errorList,
        HttpServletRequest req,
        HttpServletResponse resp
    ) throws IOException {

        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        } else {

            StoredObject so = store.getStoredObject(transaction, path);
            if (so != null) {

                if (so.isResource()) {
                    store.removeObject(transaction, path);
                } else {
                    if (so.isFolder()) {
                        deleteFolder(transaction, path, errorList, req, resp);
                        store.removeObject(transaction, path);
                    } else {
                        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    }
                }
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }

        }
    }

    /**
     * helper method of deleteResource() deletes the folder and all of its contents
     *
     * @param transaction indicates that the method is within the scope of a WebDAV transaction
     * @param path        the folder to be deleted
     * @param errorList   all errors that ocurred
     * @param req         HttpServletRequest
     * @param resp        HttpServletResponse
     * @throws WebdavException if an error in the underlying store occurs
     */
    private void deleteFolder(
        Transaction transaction,
        String path,
        Map<String, Integer> errorList,
        HttpServletRequest req,
        HttpServletResponse resp
    ) {

        String[] children = store.getChildrenNames(transaction, path);
        children = children == null ? new String[]{} : children;
        StoredObject so;
        for (int i = children.length - 1; i >= 0; i--) {
            children[i] = '/' + children[i];
            try {
                so = store.getStoredObject(transaction, path + children[i]);
                if (so.isResource()) {
                    store.removeObject(transaction, path + children[i]);

                } else {
                    deleteFolder(transaction, path + children[i], errorList, req, resp);

                    store.removeObject(transaction, path + children[i]);

                }
            } catch (AccessDeniedException e) {
                errorList.put(path + children[i], HttpServletResponse.SC_FORBIDDEN);
            } catch (ObjectNotFoundException e) {
                errorList.put(path + children[i], HttpServletResponse.SC_NOT_FOUND);
            } catch (WebdavException e) {
                errorList.put(path + children[i], HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

    }

}
