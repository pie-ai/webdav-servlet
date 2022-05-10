/*
 * Copyright 2005-2006 webdav-servlet group.
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

package org.drjekyll.webdav.locking;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.drjekyll.webdav.Transaction;

/**
 * simple locking management for concurrent data access, NOT the webdav locking. ( could that be
 * used instead? )
 * <p>
 * IT IS ACTUALLY USED FOR DOLOCK
 *
 * @author re
 */
@Slf4j
public class ResourceLocks implements IResourceLocks {

    /**
     * after creating this much LockedObjects, a cleanup deletes unused LockedObjects
     */
    private static final int CLEANUP_LIMIT = 100000;

    private static final boolean TEMPORARY = true;

    private final Map<String, LockedObject> locks = new HashMap<>();

    private final Map<String, LockedObject> locksByID = new HashMap<>();

    private final Map<String, LockedObject> tempLocks = new HashMap<>();

    private final Map<String, LockedObject> tempLocksByID = new HashMap<>();

    private final LockedObject root;

    private final LockedObject tempRoot;

    private int cleanupCounter;

    public ResourceLocks() {
        root = new LockedObject(this, "/", true);
        tempRoot = new LockedObject(this, "/", false);
    }

    @Override
    public synchronized boolean lock(
        Transaction transaction,
        String path,
        String owner,
        boolean exclusive,
        int depth,
        int timeout,
        boolean temporary
    ) {

        LockedObject lo;

        if (temporary) {
            lo = generateTempLockedObjects(transaction, path);
            lo.setType("read");
        } else {
            lo = generateLockedObjects(transaction, path);
            lo.setType("write");
        }

        if (lo.checkLocks(exclusive, depth)) {

            lo.setExclusive(exclusive);
            lo.setLockDepth(depth);
            lo.setExpiresAt(System.currentTimeMillis() + timeout * 1000L);
            if (lo.getParent() != null) {
                lo.getParent().setExpiresAt(lo.getExpiresAt());
                if (lo.getParent().equals(root)) {
                    LockedObject rootLo = getLockedObjectByPath(transaction, root.getPath());
                    rootLo.setExpiresAt(lo.getExpiresAt());
                } else if (lo.getParent().equals(tempRoot)) {
                    LockedObject tempRootLo = getTempLockedObjectByPath(transaction,
                        tempRoot.getPath()
                    );
                    tempRootLo.setExpiresAt(lo.getExpiresAt());
                }
            }
            if (lo.addLockedObjectOwner(owner)) {
                return true;
            }
            log.trace("Couldn't set owner \"{}\" to resource at '{}'", owner, path);
            return false;
        }
        // can not lock
        log.trace(
            "Lock resource at {} failed because\na parent or child resource is currently locked",
            path
        );
        return false;
    }

    @Override
    public synchronized boolean unlock(
        Transaction transaction, String id, String owner
    ) {

        if (locksByID.containsKey(id)) {
            String path = locksByID.get(id).getPath();
            if (locks.containsKey(path)) {
                LockedObject lo = locks.get(path);
                lo.removeLockedObjectOwner(owner);

                if (lo.getChildren() == null && lo.getOwner() == null) {
                    lo.removeLockedObject();
                }

            } else {
                // there is no lock at that path. someone tried to unlock it
                // anyway. could point to a problem
                log.trace("ResourceLocks.unlock(): no lock for path {}", path);
                return false;
            }

            if (cleanupCounter > CLEANUP_LIMIT) {
                cleanupCounter = 0;
                cleanLockedObjects(transaction, root, !TEMPORARY);
            }
        }
        checkTimeouts(transaction, !TEMPORARY);

        return true;

    }

    @Override
    public synchronized void unlockTemporaryLockedObjects(
        Transaction transaction, String path, String owner
    ) {
        if (tempLocks.containsKey(path)) {
            LockedObject lo = tempLocks.get(path);
            lo.removeLockedObjectOwner(owner);

        } else {
            // there is no lock at that path. someone tried to unlock it
            // anyway. could point to a problem
            log.trace("ResourceLocks.unlock(): no lock for path {}", path);
        }

        if (cleanupCounter > CLEANUP_LIMIT) {
            cleanupCounter = 0;
            cleanLockedObjects(transaction, tempRoot, TEMPORARY);
        }

        checkTimeouts(transaction, TEMPORARY);

    }

    @Override
    public void checkTimeouts(Transaction transaction, boolean temporary) {
        if (temporary) {
            Collection<LockedObject> lockedObjects = tempLocks.values();
            Collection<LockedObject> toRemove = lockedObjects.stream().filter(currentLockedObject ->
                currentLockedObject.getExpiresAt()
                    < System.currentTimeMillis()).collect(Collectors.toList());
            for (LockedObject lockedObject : toRemove) {
                lockedObject.removeTempLockedObject();
            }
        } else {
            Collection<LockedObject> lockedObjects = locks.values();
            Collection<LockedObject> toRemove = lockedObjects.stream().filter(currentLockedObject ->
                currentLockedObject.getExpiresAt()
                    < System.currentTimeMillis()).collect(Collectors.toList());
            for (LockedObject lockedObject : toRemove) {
                lockedObject.removeTempLockedObject();
            }
        }

    }

    @Override
    public boolean exclusiveLock(
        Transaction transaction, String path, String owner, int depth, int timeout
    ) {
        return lock(transaction, path, owner, true, depth, timeout, false);
    }

    @Override
    public boolean sharedLock(
        Transaction transaction, String path, String owner, int depth, int timeout
    ) {
        return lock(transaction, path, owner, false, depth, timeout, false);
    }

    @Nullable
    @Override
    public LockedObject getLockedObjectByID(Transaction transaction, String id) {
        if (locksByID.containsKey(id)) {
            return locksByID.get(id);
        }
        return null;
    }

    @Nullable
    @Override
    public LockedObject getLockedObjectByPath(
        Transaction transaction, String path
    ) {
        if (locks.containsKey(path)) {
            return locks.get(path);
        }
        return null;
    }

    @Nullable
    @Override
    public LockedObject getTempLockedObjectByID(
        Transaction transaction, String id
    ) {
        if (tempLocksByID.containsKey(id)) {
            return tempLocksByID.get(id);
        }
        return null;
    }

    @Nullable
    @Override
    public LockedObject getTempLockedObjectByPath(
        Transaction transaction, String path
    ) {
        if (tempLocks.containsKey(path)) {
            return tempLocks.get(path);
        }
        return null;
    }

    /**
     * generates real LockedObjects for the resource at path and its parent folders. does not create
     * new LockedObjects if they already exist
     *
     * @param transaction
     * @param path        path to the (new) LockedObject
     * @return the LockedObject for path.
     */
    private LockedObject generateLockedObjects(
        Transaction transaction, String path
    ) {
        if (locks.containsKey(path)) {
            // there is already a LockedObject on the specified path
            return locks.get(path);
        }
        LockedObject returnObject = new LockedObject(this, path, !TEMPORARY);
        String parentPath = getParentPath(path);
        if (parentPath != null) {
            LockedObject parentLockedObject = generateLockedObjects(transaction, parentPath);
            parentLockedObject.addChild(returnObject);
            returnObject.setParent(parentLockedObject);
        }
        return returnObject;

    }

    /**
     * generates temporary LockedObjects for the resource at path and its parent folders. does not
     * create new LockedObjects if they already exist
     *
     * @param transaction
     * @param path        path to the (new) LockedObject
     * @return the LockedObject for path.
     */
    private LockedObject generateTempLockedObjects(
        Transaction transaction, String path
    ) {
        if (tempLocks.containsKey(path)) {
            // there is already a LockedObject on the specified path
            return tempLocks.get(path);
        }
        LockedObject returnObject = new LockedObject(this, path, TEMPORARY);
        String parentPath = getParentPath(path);
        if (parentPath != null) {
            LockedObject parentLockedObject = generateTempLockedObjects(transaction, parentPath);
            parentLockedObject.addChild(returnObject);
            returnObject.setParent(parentLockedObject);
        }
        return returnObject;

    }

    /**
     * deletes unused LockedObjects and resets the counter. works recursively starting at the given
     * LockedObject
     *
     * @param transaction
     * @param lo          LockedObject
     * @param temporary   Clean temporary or real locks
     * @return if cleaned
     */
    private static boolean cleanLockedObjects(
        Transaction transaction, LockedObject lo, boolean temporary
    ) {

        if (lo.getChildren() == null) {
            if (lo.getOwner() == null) {
                if (temporary) {
                    lo.removeTempLockedObject();
                } else {
                    lo.removeLockedObject();
                }

                return true;
            }
            return false;
        }
        boolean canDelete = true;
        int limit = lo.getChildren().length;
        for (int i = 0; i < limit; i++) {
            if (cleanLockedObjects(transaction, lo.getChildren()[i], temporary)) {

                // because the deleting shifts the array
                i--;
                limit--;
            } else {
                canDelete = false;
            }
        }
        if (canDelete) {
            if (lo.getOwner() == null) {
                if (temporary) {
                    lo.removeTempLockedObject();
                } else {
                    lo.removeLockedObject();
                }
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * creates the parent path from the given path by removing the last '/' and everything after
     * that
     *
     * @param path the path
     * @return parent path
     */
    @Nullable
    private static String getParentPath(String path) {
        int slash = path.lastIndexOf('/');
        if (slash == -1) {
            return null;
        }
        if (slash == 0) {
            // return "root" if parent path is empty string
            return "/";
        }
        return path.substring(0, slash);
    }

    /**
     * keys: path value: Temporary LockedObject from that path
     */
    public Map<String, LockedObject> getTempLocks() {
        return tempLocks;
    }

    /**
     * keys: id value: Temporary LockedObject from that id
     */
    public Map<String, LockedObject> getTempLocksByID() {
        return tempLocksByID;
    }

    /**
     * keys: path value: LockedObject from that path
     */
    public Map<String, LockedObject> getLocks() {
        return locks;
    }

    /**
     * keys: id value: LockedObject from that id
     */
    public Map<String, LockedObject> getLocksByID() {
        return locksByID;
    }

    public int getCleanupCounter() {
        return cleanupCounter;
    }

    public void setCleanupCounter(int cleanupCounter) {
        this.cleanupCounter = cleanupCounter;
    }

    public LockedObject getRoot() {
        return root;
    }

    public LockedObject getTempRoot() {
        return tempRoot;
    }
}
