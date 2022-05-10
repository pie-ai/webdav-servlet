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
import org.drjekyll.webdav.copy.DoCopy;
import org.drjekyll.webdav.exceptions.AccessDeniedException;
import org.drjekyll.webdav.exceptions.ObjectAlreadyExistsException;
import org.drjekyll.webdav.exceptions.WebdavException;
import org.drjekyll.webdav.locking.ResourceLocks;

@Slf4j
public class DoMove extends Method {

    private final ResourceLocks resourceLocks;

    private final DoDelete doDelete;

    private final DoCopy doCopy;

    private final boolean readOnly;

    public DoMove(
        ResourceLocks resourceLocks, DoDelete doDelete, DoCopy doCopy, boolean readOnly
    ) {
        this.resourceLocks = resourceLocks;
        this.doDelete = doDelete;
        this.doCopy = doCopy;
        this.readOnly = readOnly;
    }

    @Override
    public void execute(
        Transaction transaction, HttpServletRequest req, HttpServletResponse resp
    ) throws IOException {

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);

        } else {
            log.trace("-- {}", getClass().getName());

            String sourcePath = getRelativePath(req);

            if (!checkLocks(transaction, req, resourceLocks, sourcePath)) {
                resp.setStatus(WebdavStatus.SC_LOCKED);
                return;
            }

            String destinationPath = req.getHeader("Destination");
            if (destinationPath == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            if (!checkLocks(transaction, req, resourceLocks, destinationPath)) {
                resp.setStatus(WebdavStatus.SC_LOCKED);
                return;
            }

            String tempLockOwner = "doMove" + System.currentTimeMillis() + req;

            Map<String, Integer> errorList = new HashMap<>();
            if (resourceLocks.lock(transaction,
                sourcePath,
                tempLockOwner,
                false,
                0,
                TEMP_TIMEOUT,
                TEMPORARY
            )) {
                try {

                    if (doCopy.copyResource(transaction, req, resp)) {

                        errorList = new HashMap<>();
                        doDelete.deleteResource(transaction, sourcePath, errorList, req, resp);
                        if (!errorList.isEmpty()) {
                            sendReport(req, resp, errorList);
                        }
                    }

                } catch (AccessDeniedException e) {
                    resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                } catch (ObjectAlreadyExistsException e) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, req.getRequestURI());
                } catch (WebdavException e) {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                } finally {
                    resourceLocks.unlockTemporaryLockedObjects(transaction,
                        sourcePath,
                        tempLockOwner
                    );
                }
            } else {
                errorList.put(req.getHeader("Destination"), WebdavStatus.SC_LOCKED);
                sendReport(req, resp, errorList);
            }
        }

    }

}
