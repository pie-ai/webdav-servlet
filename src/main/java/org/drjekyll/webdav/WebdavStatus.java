package org.drjekyll.webdav;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;

/**
 * Wraps the HttpServletResponse class to abstract the specific protocol used. To support other
 * protocols we would only need to modify this class and the WebDavRetCode classes.
 *
 * @author Marc Eaddy
 * @version 1.0, 16 Nov 1997
 */
public final class WebdavStatus {

    /**
     * Status code (207) indicating that the response requires providing status for multiple
     * independent operations.
     */
    public static final int SC_MULTI_STATUS = 207;

    /**
     * Status code (418) indicating the entity body submitted with the PATCH method was not
     * understood by the resource.
     */
    public static final int SC_UNPROCESSABLE_ENTITY = 418;

    // This one colides with HTTP 1.1
    // "207 Parital Update OK"

    /**
     * Status code (419) indicating that the resource does not have sufficient space to record the
     * state of the resource after the execution of this method.
     */
    public static final int SC_INSUFFICIENT_SPACE_ON_RESOURCE = 419;

    // This one colides with HTTP 1.1
    // "418 Reauthentication Required"

    /**
     * Status code (420) indicating the method was not executed on a particular resource within its
     * scope because some part of the method's execution failed causing the entire method to be
     * aborted.
     */
    public static final int SC_METHOD_FAILURE = 420;

    // This one colides with HTTP 1.1
    // "419 Proxy Reauthentication Required"

    /**
     * Status code (423) indicating the destination resource of a method is locked, and either the
     * request did not contain a valid Lock-Info header, or the Lock-Info header identifies a lock
     * held by another principal.
     */
    public static final int SC_LOCKED = 423;

    /**
     * This map contains the mapping of HTTP and WebDAV status codes to descriptive text. This is a
     * static variable.
     */
    private static final Map<Integer, String> STATUS_CODES = new HashMap<>();

    static {
        // HTTP 1.0 Status Code
        addStatusCodeMap(HttpServletResponse.SC_OK, "OK");
        addStatusCodeMap(HttpServletResponse.SC_CREATED, "Created");
        addStatusCodeMap(HttpServletResponse.SC_ACCEPTED, "Accepted");
        addStatusCodeMap(HttpServletResponse.SC_NO_CONTENT, "No Content");
        addStatusCodeMap(HttpServletResponse.SC_MOVED_PERMANENTLY, "Moved Permanently");
        addStatusCodeMap(HttpServletResponse.SC_MOVED_TEMPORARILY, "Moved Temporarily");
        addStatusCodeMap(HttpServletResponse.SC_NOT_MODIFIED, "Not Modified");
        addStatusCodeMap(HttpServletResponse.SC_BAD_REQUEST, "Bad Request");
        addStatusCodeMap(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        addStatusCodeMap(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        addStatusCodeMap(HttpServletResponse.SC_NOT_FOUND, "Not Found");
        addStatusCodeMap(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        addStatusCodeMap(HttpServletResponse.SC_NOT_IMPLEMENTED, "Not Implemented");
        addStatusCodeMap(HttpServletResponse.SC_BAD_GATEWAY, "Bad Gateway");
        addStatusCodeMap(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service Unavailable");
        addStatusCodeMap(HttpServletResponse.SC_CONTINUE, "Continue");
        addStatusCodeMap(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method Not Allowed");
        addStatusCodeMap(HttpServletResponse.SC_CONFLICT, "Conflict");
        addStatusCodeMap(HttpServletResponse.SC_PRECONDITION_FAILED, "Precondition Failed");
        addStatusCodeMap(HttpServletResponse.SC_REQUEST_URI_TOO_LONG, "Request Too Long");
        addStatusCodeMap(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type");
        // WebDav Status Codes
        addStatusCodeMap(SC_MULTI_STATUS, "Multi-Status");
        addStatusCodeMap(SC_UNPROCESSABLE_ENTITY, "Unprocessable Entity");
        addStatusCodeMap(SC_INSUFFICIENT_SPACE_ON_RESOURCE, "Insufficient Space On Resource");
        addStatusCodeMap(SC_METHOD_FAILURE, "Method Failure");
        addStatusCodeMap(SC_LOCKED, "Locked");
    }

    private WebdavStatus() {
        // enumeration
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Returns the HTTP status text for the HTTP or WebDav status code specified by looking it up in
     * the static mapping. This is a static function.
     *
     * @param nHttpStatusCode [IN] HTTP or WebDAV status code
     * @return A string with a short descriptive phrase for the HTTP status code (e.g., "OK").
     */
    public static String getStatusText(int nHttpStatusCode) {
        Integer intKey = nHttpStatusCode;

        if (STATUS_CODES.containsKey(intKey)) {
            return STATUS_CODES.get(intKey);
        }
        return "";
    }

    // -------------------------------------------------------- Private Methods

    /**
     * Adds a new status code -> status text mapping. This is a static method because the mapping is
     * a static variable.
     *
     * @param nKey   [IN] HTTP or WebDAV status code
     * @param strVal [IN] HTTP status text
     */
    private static void addStatusCodeMap(int nKey, String strVal) {
        STATUS_CODES.put(nKey, strVal);
    }

}
