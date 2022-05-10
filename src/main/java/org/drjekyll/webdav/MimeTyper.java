package org.drjekyll.webdav;

@FunctionalInterface
public interface MimeTyper {

    /**
     * Detect the mime type of this object
     *
     * @param transaction
     * @param path
     * @return
     */
    String getMimeType(Transaction transaction, String path);
}
