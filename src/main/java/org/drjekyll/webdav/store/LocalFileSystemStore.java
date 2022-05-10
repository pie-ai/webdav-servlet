/*
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
 *
 */
package org.drjekyll.webdav.store;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.exceptions.WebdavException;

/**
 * Reference Implementation of WebdavStore
 *
 * @author joa
 * @author re
 */
@Slf4j
public class LocalFileSystemStore implements WebdavStore {

    private static final int BUF_SIZE = 65536;

    private final File root;

    public LocalFileSystemStore(File root) {
        this.root = root;
    }

    @Override
    public void destroy() {
    }

    @Nullable
    @Override
    public Transaction begin(Principal principal) {
        log.trace("LocalFileSystemStore.begin()");
        if (!root.exists()) {
            if (!root.mkdirs()) {
                throw new WebdavException("root path: "
                    + root.getAbsolutePath()
                    + " does not exist and could not be created");
            }
        }
        return null;
    }

    @Override
    public void checkAuthentication(Transaction transaction) {
        log.trace("LocalFileSystemStore.checkAuthentication()");
        // do nothing

    }

    @Override
    public void commit(Transaction transaction) {
        // do nothing
        log.trace("LocalFileSystemStore.commit()");
    }

    @Override
    public void rollback(Transaction transaction) {
        // do nothing
        log.trace("LocalFileSystemStore.rollback()");

    }

    @Override
    public void createFolder(Transaction transaction, String uri) {
        log.trace("LocalFileSystemStore.createFolder({})", uri);
        File file = new File(root, uri);
        if (!file.mkdir()) {
            throw new WebdavException("cannot create folder: " + uri);
        }
    }

    @Override
    public void createResource(Transaction transaction, String uri) {
        log.trace("LocalFileSystemStore.createResource({})", uri);
        try {
            File file = new File(root, uri);
            if (!file.createNewFile()) {
                throw new WebdavException("cannot create file: " + uri);
            }
        } catch (IOException e) {
            log.error("LocalFileSystemStore.createResource({}) failed", uri);
            throw new WebdavException(e);
        }
    }

    @Override
    public InputStream getResourceContent(Transaction transaction, String uri) {
        log.trace("LocalFileSystemStore.getResourceContent({})", uri);

        try {
            File file = new File(root, uri);
            return new BufferedInputStream(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            log.error("LocalFileSystemStore.getResourceContent({}) failed", uri);
            throw new WebdavException(e);
        }
    }

    @Override
    public long setResourceContent(
        Transaction transaction,
        String uri,
        InputStream is,
        String contentType,
        String characterEncoding
    ) {

        log.trace("LocalFileSystemStore.setResourceContent({})", uri);
        File file = new File(root, uri);
        try {
            OutputStream os = new BufferedOutputStream(Files.newOutputStream(file.toPath()),
                BUF_SIZE
            );
            try {
                int read;
                byte[] copyBuffer = new byte[BUF_SIZE];

                while ((read = is.read(copyBuffer, 0, copyBuffer.length)) != -1) {
                    os.write(copyBuffer, 0, read);
                }
            } finally {
                try {
                    is.close();
                } finally {
                    os.close();
                }
            }
        } catch (IOException e) {
            log.error("LocalFileSystemStore.setResourceContent({}) failed", uri);
            throw new WebdavException(e);
        }

        try {
            return file.length();
        } catch (SecurityException e) {
            log.error("LocalFileSystemStore.setResourceContent({}) failed\nCan't get file.length",
                uri
            );
        }

        return -1;
    }

    @Override
    public String[] getChildrenNames(Transaction transaction, String uri) {
        log.trace("LocalFileSystemStore.getChildrenNames({})", uri);
        File file = new File(root, uri);
        String[] childrenNames = null;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            List<String> childList = new ArrayList<>();
            String name;
            for (int i = 0; i < children.length; i++) {
                name = children[i].getName();
                childList.add(name);
                log.trace("Child {}: {}", i, name);
            }
            childrenNames = new String[childList.size()];
            return childList.toArray(childrenNames);
        }
        return childrenNames;
    }

    @Override
    public void removeObject(Transaction transaction, String uri) {
        File file = new File(root, uri);
        boolean success = file.delete();
        log.trace("LocalFileSystemStore.removeObject({})={}", uri, success);
        if (!success) {
            throw new WebdavException("cannot delete object: " + uri);
        }

    }


    @Override
    public long getResourceLength(Transaction transaction, String uri) {
        log.trace("LocalFileSystemStore.getResourceLength({})", uri);
        File file = new File(root, uri);
        return file.length();
    }

    @Override
    public StoredObject getStoredObject(Transaction transaction, String uri) {

        StoredObject so = null;

        File file = new File(root, uri);
        if (file.exists()) {
            so = new StoredObject();
            so.setFolder(file.isDirectory());
            so.setLastModified(Instant.ofEpochMilli(file.lastModified()));
            so.setCreationDate(Instant.ofEpochMilli(file.lastModified()));
            so.setResourceLength(getResourceLength(transaction, uri));
        }

        return so;
    }

}
