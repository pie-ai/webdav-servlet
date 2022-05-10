package org.drjekyll.webdav;

import java.util.Date;
import org.drjekyll.webdav.locking.LockedObject;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.store.StoredObject;

public final class StoreObjectTestUtil {

    private StoreObjectTestUtil() {
    }

    public static StoredObject initStoredObject(
        boolean isFolder, byte[] resourceContent
    ) {
        StoredObject so = new StoredObject();
        so.setFolder(isFolder);
        so.setCreationDate(new Date());
        so.setLastModified(new Date());
        if (!isFolder) {
            // so.setResourceContent(resourceContent);
            so.setResourceLength(resourceContent.length);
        } else {
            so.setResourceLength(0L);
        }

        return so;
    }

    public static StoredObject initLockNullStoredObject() {
        StoredObject so = new StoredObject();
        so.setNullResource(true);
        so.setFolder(false);
        so.setCreationDate(null);
        so.setLastModified(null);
        so.setResourceLength(0);

        return so;
    }

    public static LockedObject initLockNullLockedObject(
        ResourceLocks resLocks, String path
    ) {
        LockedObject lo = new LockedObject(resLocks, path, false);
        lo.setExclusive(true);
        return lo;
    }
}
