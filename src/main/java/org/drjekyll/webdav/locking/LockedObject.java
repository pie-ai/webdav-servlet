package org.drjekyll.webdav.locking;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * a helper class for ResourceLocks, represents the Locks
 *
 * @author re
 */
@Slf4j
public class LockedObject {

    private final ResourceLocks resourceLocks;

    private final String path;

    private final String id;

    private int lockDepth;

    private long expiresAt;

    private String[] owner;

    private LockedObject[] children;

    private LockedObject parent;

    private boolean exclusive;

    private String type;

    /**
     * @param resLocks  the resourceLocks where locks are stored
     * @param path      the path to the locked object
     * @param temporary indicates if the LockedObject should be temporary or not
     */
    public LockedObject(ResourceLocks resLocks, String path, boolean temporary) {
        this.path = path;
        id = UUID.randomUUID().toString();
        resourceLocks = resLocks;

        if (temporary) {
            resourceLocks.getTempLocks().put(path, this);
            resourceLocks.getTempLocksByID().put(id, this);
        } else {
            resourceLocks.getLocks().put(path, this);
            resourceLocks.getLocksByID().put(id, this);
        }
        resourceLocks.setCleanupCounter(resourceLocks.getCleanupCounter() + 1);
    }

    /**
     * adds a new owner to a lock
     *
     * @param owner string that represents the owner
     * @return true if the owner was added, false otherwise
     */
    public boolean addLockedObjectOwner(String owner) {

        if (getOwner() == null) {
            this.owner = new String[1];
        } else {

            int size = getOwner().length;

            // check if the owner is already here (that should actually not
            // happen)
            for (int i = 0; i < size; i++) {
                if (getOwner()[i].equals(owner)) {
                    return false;
                }
            }

            String[] newLockObjectOwner = new String[size + 1];
            System.arraycopy(getOwner(), 0, newLockObjectOwner, 0, size);
            this.owner = newLockObjectOwner;
        }

        getOwner()[getOwner().length - 1] = owner;
        return true;
    }

    /**
     * owner of the lock. shared locks can have multiple owners. is null if no owner is present Gets
     * the owners for the LockedObject
     *
     * @return owners
     */
    public String[] getOwner() {
        return owner;
    }

    public void setOwner(String[] owner) {
        this.owner = owner;
    }

    /**
     * tries to remove the owner from the lock
     *
     * @param owner string that represents the owner
     */
    public void removeLockedObjectOwner(String owner) {

        try {
            if (getOwner() != null) {
                int size = getOwner().length;
                for (int i = 0; i < size; i++) {
                    // check every owner if it is the requested one
                    if (getOwner()[i].equals(owner)) {
                        // remove the owner
                        size -= 1;
                        String[] newLockedObjectOwner = new String[size];
                        for (int j = 0; j < size; j++) {
                            if (j < i) {
                                newLockedObjectOwner[j] = getOwner()[j];
                            } else {
                                newLockedObjectOwner[j] = getOwner()[j + 1];
                            }
                        }
                        this.owner = newLockedObjectOwner;

                    }
                }
                if (getOwner().length == 0) {
                    this.owner = null;
                }
            }
        } catch (Exception e) {
            log.error("Could not remove locked object owner {}", owner, e);
        }
    }

    /**
     * adds a new child lock to this lock
     *
     * @param newChild new child
     */
    public void addChild(LockedObject newChild) {
        if (getChildren() == null) {
            children = new LockedObject[0];
        }
        int size = getChildren().length;
        LockedObject[] newChildren = new LockedObject[size + 1];
        System.arraycopy(getChildren(), 0, newChildren, 0, size);
        newChildren[size] = newChild;
        children = newChildren;
    }

    /**
     * children of that lock
     */
    public LockedObject[] getChildren() {
        return children;
    }

    public void setChildren(LockedObject[] children) {
        this.children = children;
    }

    /**
     * deletes this Lock object. assumes that it has no children and no owners (does not check this
     * itself)
     */
    public void removeLockedObject() {
        if (!equals(resourceLocks.getRoot()) && !"/".equals(path)) {

            int size = parent.getChildren().length;
            for (int i = 0; i < size; i++) {
                if (parent.getChildren()[i].equals(this)) {
                    LockedObject[] newChildren = new LockedObject[size - 1];
                    for (int i2 = 0; i2 < size - 1; i2++) {
                        if (i2 < i) {
                            newChildren[i2] = parent.getChildren()[i2];
                        } else {
                            newChildren[i2] = parent.getChildren()[i2 + 1];
                        }
                    }
                    if (newChildren.length == 0) {
                        parent.children = null;
                    } else {
                        parent.children = newChildren;
                    }
                    break;
                }
            }

            resourceLocks.getLocksByID().remove(id);
            resourceLocks.getLocks().remove(path);

            // now the garbage collector has some work to do
        }
    }

    /**
     * deletes this Lock object. assumes that it has no children and no owners (does not check this
     * itself)
     */
    public void removeTempLockedObject() {
        if (!equals(resourceLocks.getTempRoot())) {
            // removing from tree
            if (parent != null && parent.getChildren() != null) {
                int size = parent.getChildren().length;
                for (int i = 0; i < size; i++) {
                    if (parent.getChildren()[i].equals(this)) {
                        LockedObject[] newChildren = new LockedObject[size - 1];
                        for (int i2 = 0; i2 < size - 1; i2++) {
                            if (i2 < i) {
                                newChildren[i2] = parent.getChildren()[i2];
                            } else {
                                newChildren[i2] = parent.getChildren()[i2 + 1];
                            }
                        }
                        if (newChildren.length == 0) {
                            parent.children = null;
                        } else {
                            parent.children = newChildren;
                        }
                        break;
                    }
                }

                resourceLocks.getTempLocksByID().remove(id);
                resourceLocks.getTempLocks().remove(path);

                // now the garbage collector has some work to do
            }
        }
    }

    /**
     * checks if a lock of the given exclusivity can be placed, only considering children up to
     * "depth"
     *
     * @param exclusive wheather the new lock should be exclusive
     * @param depth     the depth to which should be checked
     * @return true if the lock can be placed
     */
    public boolean checkLocks(boolean exclusive, int depth) {
        return checkParents(exclusive) && checkChildren(exclusive, depth);
    }

    /**
     * helper of checkLocks(). looks if the parents are locked
     *
     * @param exclusive wheather the new lock should be exclusive
     * @return true if no locks at the parent path are forbidding a new lock
     */
    private boolean checkParents(boolean exclusive) {
        if ("/".equals(path)) {
            return true;
        }
        if (getOwner() == null) {
            // no owner, checking parents
            return parent != null && parent.checkParents(exclusive);
        }
        // there already is a owner
        return !(this.exclusive || exclusive) && parent.checkParents(false);
    }

    /**
     * helper of checkLocks(). looks if the children are locked
     *
     * @param exclusive wheather the new lock should be exclusive
     * @param depth     depth
     * @return true if no locks at the children paths are forbidding a new lock
     */
    private boolean checkChildren(boolean exclusive, int depth) {
        if (getChildren() == null) {
            // a file

            return getOwner() == null || !(this.exclusive || exclusive);
        }
        // a folder

        if (getOwner() == null) {
            // no owner, checking children

            if (depth == 0) {
                // depth == 0 -> we don't care for children
                return true;
            }
            boolean canLock = true;
            int limit = getChildren().length;
            for (int i = 0; i < limit; i++) {
                if (!getChildren()[i].checkChildren(exclusive, depth - 1)) {
                    canLock = false;
                }
            }
            return canLock;
        }
        // there already is a owner
        return !(this.exclusive || exclusive);

    }

    /**
     * Sets a new timeout for the LockedObject
     *
     * @param timeout
     */
    public void refreshTimeout(int timeout) {
        expiresAt = System.currentTimeMillis() + timeout * 1000L;
    }

    /**
     * Gets the timeout for the LockedObject
     *
     * @return timeout
     */
    public long getTimeoutMillis() {
        return expiresAt - System.currentTimeMillis();
    }

    /**
     * Return true if the lock has expired.
     *
     * @return true if timeout has passed
     */
    public boolean hasExpired() {
        return expiresAt == 0 || System.currentTimeMillis() > expiresAt;
    }

    /**
     * Gets the LockID (locktoken) for the LockedObject
     *
     * @return locktoken
     */
    public String getID() {
        return id;
    }

    /**
     * Gets the path for the LockedObject
     *
     * @return path
     */
    public String getPath() {
        return path;
    }

    /**
     * weather the lock is exclusive or not. if owner=null the exclusive value doesn't matter Gets
     * the exclusivity for the LockedObject
     *
     * @return exclusivity
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Sets the exclusivity for the LockedObject
     *
     * @param exclusive
     */
    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    /**
     * Gets the exclusivity for the LockedObject
     *
     * @return exclusivity
     */
    public boolean isShared() {
        return !exclusive;
    }

    /**
     * weather the lock is a write or read lock Gets the type of the lock
     *
     * @return type
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Describing the depth of a locked collection. If the locked resource is not a collection,
     * depth is 0 / doesn't matter. Gets the depth of the lock
     *
     * @return depth
     */
    public int getLockDepth() {
        return lockDepth;
    }

    public void setLockDepth(int lockDepth) {
        this.lockDepth = lockDepth;
    }

    /**
     * Describing the timeout of a locked object (ms)
     */
    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LockedObject getParent() {
        return parent;
    }

    public void setParent(LockedObject parent) {
        this.parent = parent;
    }
}
