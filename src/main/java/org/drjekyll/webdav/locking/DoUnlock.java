package org.drjekyll.webdav.locking;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.WebdavStatus;
import org.drjekyll.webdav.methods.DeterminableMethod;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;

@Slf4j
public class DoUnlock extends DeterminableMethod {

    private final WebdavStore store;

    private final IResourceLocks resourceLocks;

    private final boolean readOnly;

    public DoUnlock(
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
            return;
        }

        String path = getRelativePath(req);
        String tempLockOwner = "doUnlock" + System.currentTimeMillis() + req;
        try {
            if (resourceLocks.lock(transaction,
                path,
                tempLockOwner,
                false,
                0,
                TEMP_TIMEOUT,
                TEMPORARY
            )) {

                String lockId = getLockIdFromLockTokenHeader(req);
                LockedObject lo;
                if (lockId != null && (
                    lo = resourceLocks.getLockedObjectByID(transaction, lockId)
                ) != null) {

                    String[] owners = lo.getOwner();
                    String owner = null;
                    if (lo.isShared()) {
                        // more than one owner is possible
                        if (owners != null) {
                            for (String s : owners) {
                                // remove owner from LockedObject
                                lo.removeLockedObjectOwner(s);
                            }
                        }
                    } else {
                        // exclusive, only one lock owner
                        if (owners != null) {
                            owner = owners[0];
                        }
                    }

                    if (resourceLocks.unlock(transaction, lockId, owner)) {
                        StoredObject so = store.getStoredObject(transaction, path);
                        if (so.isNullResource()) {
                            store.removeObject(transaction, path);
                        }

                        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    } else {
                        log.trace("DoUnlock failure at {}", lo.getPath());
                        resp.sendError(WebdavStatus.SC_METHOD_FAILURE);
                    }

                } else {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                }
            }
        } catch (LockFailedException e) {
            log.error("Lock failed", e);
        } finally {
            resourceLocks.unlockTemporaryLockedObjects(transaction, path, tempLockOwner);
        }
    }

}
