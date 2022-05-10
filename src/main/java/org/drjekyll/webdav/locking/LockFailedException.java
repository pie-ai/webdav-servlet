package org.drjekyll.webdav.locking;

import org.drjekyll.webdav.exceptions.WebdavException;

public class LockFailedException extends WebdavException {

    private static final long serialVersionUID = 7341253179712322820L;

    public LockFailedException() {
    }

    public LockFailedException(String message) {
        super(message);
    }

    public LockFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public LockFailedException(Throwable cause) {
        super(cause);
    }
}
