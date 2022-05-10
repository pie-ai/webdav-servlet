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

package org.drjekyll.webdav;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.Enumeration;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.drjekyll.webdav.copy.DoCopy;
import org.drjekyll.webdav.exceptions.UnauthenticatedException;
import org.drjekyll.webdav.exceptions.WebdavException;
import org.drjekyll.webdav.locking.DoLock;
import org.drjekyll.webdav.locking.DoUnlock;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.methods.DoDelete;
import org.drjekyll.webdav.methods.DoGet;
import org.drjekyll.webdav.methods.DoHead;
import org.drjekyll.webdav.methods.DoMkcol;
import org.drjekyll.webdav.methods.DoMove;
import org.drjekyll.webdav.methods.DoNotImplemented;
import org.drjekyll.webdav.methods.DoOptions;
import org.drjekyll.webdav.methods.DoPut;
import org.drjekyll.webdav.prop.DoPropfind;
import org.drjekyll.webdav.prop.DoProppatch;
import org.drjekyll.webdav.store.LocalFileSystemStore;
import org.drjekyll.webdav.store.WebdavStore;

/**
 * Servlet which provides support for WebDAV level 2.
 * <p>
 * the original class is org.apache.catalina.servlets.WebdavServlet by Remy Maucherat, which was
 * heavily changed
 *
 * @author Remy Maucherat
 */
@Slf4j
public class WebdavServlet extends HttpServlet {

    private static final String ROOTPATH_PARAMETER = "rootpath";

    private static final boolean READ_ONLY = false;

    private static final long serialVersionUID = -8439635344436347628L;

    private final ResourceLocks resourceLocks;

    private final HashMap<String, MethodExecutor> methods = new HashMap<>();

    private WebdavStore store;

    public WebdavServlet() {
        resourceLocks = new ResourceLocks();
    }

    @Override
    public void destroy() {
        if (store != null) {
            store.destroy();
        }
        super.destroy();
    }

    @Override
    public void init() throws ServletException {

        // Parameters from web.xml
        String clazzName = getServletConfig().getInitParameter("ResourceHandlerImplementation");
        if (clazzName == null || clazzName.isEmpty()) {
            clazzName = LocalFileSystemStore.class.getName();
        }

        File root = getFileRoot();

        WebdavStore webdavStore = constructStore(clazzName, root);

        boolean lazyFolderCreationOnPut =
            getInitParameter("lazyFolderCreationOnPut") != null && "1".equals(getInitParameter(
                "lazyFolderCreationOnPut"));

        String dftIndexFile = getInitParameter("default-index-file");
        String insteadOf404 = getInitParameter("instead-of-404");

        int noContentLengthHeader = getIntInitParameter();

        init(webdavStore,
            dftIndexFile,
            insteadOf404,
            noContentLengthHeader,
            lazyFolderCreationOnPut
        );
    }

    private File getFileRoot() {
        String rootPath = getInitParameter(ROOTPATH_PARAMETER);
        if (rootPath == null) {
            throw new WebdavException("missing parameter: " + ROOTPATH_PARAMETER);
        }
        if ("*WAR-FILE-ROOT*".equals(rootPath)) {
            String file = LocalFileSystemStore.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getFile()
                .replace('\\', '/');
            if (file.charAt(0) == '/' && System.getProperty("os.name").contains("Windows")) {
                file = file.substring(1);
            }

            int ix = file.indexOf("/WEB-INF/");
            if (ix == -1) {
                throw new WebdavException(
                    "Could not determine root of war file. Can't extract from path '"
                        + file
                        + "' for this web container");
            }
            rootPath = file.substring(0, ix).replace('/', File.separatorChar);
        }
        return new File(rootPath);
    }

    protected WebdavStore constructStore(String clazzName, File root) throws ServletException {
        try {
            Class<?> clazz = WebdavServlet.class.getClassLoader().loadClass(clazzName);

            Constructor<?> ctor = clazz.getConstructor(File.class);

            return (WebdavStore) ctor.newInstance(new Object[]{root});
        } catch (Exception e) {
            log.error("Could not construct store class {} in root {}", clazzName, root, e);
            throw new ServletException("some problem making store component", e);
        }
    }

    private int getIntInitParameter() {
        return getInitParameter("no-content-length-headers") == null ? -1 : Integer.parseInt(
            getInitParameter("no-content-length-headers"));
    }

    public void init(
        WebdavStore store,
        String dftIndexFile,
        String insteadOf404,
        int nocontentLenghHeaders,
        boolean lazyFolderCreationOnPut
    ) {

        this.store = store;

        MimeTyper mimeTyper = (transaction, path) -> {
            String retVal = this.store.getStoredObject(transaction, path).getMimeType();
            if (retVal == null) {
                return getServletContext().getMimeType(path);
            }
            return retVal;
        };

        register("GET",
            new DoGet(store,
                dftIndexFile,
                insteadOf404,
                resourceLocks,
                mimeTyper,
                nocontentLenghHeaders
            )
        );
        register("HEAD",
            new DoHead(store,
                dftIndexFile,
                insteadOf404,
                resourceLocks,
                mimeTyper,
                nocontentLenghHeaders
            )
        );
        DoDelete doDelete = (DoDelete) register("DELETE",
            new DoDelete(store, resourceLocks, READ_ONLY)
        );
        DoCopy doCopy = (DoCopy) register("COPY",
            new DoCopy(store, resourceLocks, doDelete, READ_ONLY)
        );
        register("LOCK", new DoLock(store, resourceLocks, READ_ONLY));
        register("UNLOCK", new DoUnlock(store, resourceLocks, READ_ONLY));
        register("MOVE", new DoMove(resourceLocks, doDelete, doCopy, READ_ONLY));
        register("MKCOL", new DoMkcol(store, resourceLocks, READ_ONLY));
        register("OPTIONS", new DoOptions(store, resourceLocks));
        register("PUT", new DoPut(store, resourceLocks, READ_ONLY, lazyFolderCreationOnPut));
        register("PROPFIND", new DoPropfind(store, resourceLocks, mimeTyper));
        register("PROPPATCH", new DoProppatch(store, resourceLocks, READ_ONLY));
        register("*NO*IMPL*", new DoNotImplemented(READ_ONLY));
    }

    protected MethodExecutor register(String methodName, MethodExecutor method) {
        methods.put(methodName, method);
        return method;
    }

    /**
     * Handles the special WebDAV methods.
     */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String methodName = req.getMethod();

        if (log.isTraceEnabled()) {
            debugRequest(methodName, req);
        }

        boolean needRollback = false;
        Transaction transaction = null;
        try {
            Principal userPrincipal = getUserPrincipal(req);
            transaction = store.begin(userPrincipal);
            needRollback = true;
            store.checkAuthentication(transaction);
            resp.setStatus(HttpServletResponse.SC_OK);

            try {
                MethodExecutor methodExecutor = methods.get(methodName);
                if (methodExecutor == null) {
                    methodExecutor = methods.get("*NO*IMPL*");
                }

                methodExecutor.execute(transaction, req, resp);

                store.commit(transaction);
                /* Clear not consumed data
                 *
                 * Clear input stream if available otherwise later access
                 * include current input.  These cases occure if the client
                 * sends a request with body to an not existing resource.
                 */
                if (req.getContentLength() != 0 && req.getInputStream().available() > 0) {
                    if (log.isTraceEnabled()) {
                        log.trace("Clear not consumed data!");
                    }
                    while (req.getInputStream().available() > 0) {
                        req.getInputStream().read();
                    }
                }
                needRollback = false;
            } catch (IOException e) {
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                log.error("IOException: {}", sw);
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                store.rollback(transaction);
                throw new ServletException(e);
            }

        } catch (UnauthenticatedException e) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        } catch (WebdavException e) {
            log.error("WebdavException", e);
            throw new ServletException(e);
        } catch (RuntimeException e) {
            log.error("RuntimeException", e);
        } finally {
            if (needRollback) {
                store.rollback(transaction);
            }
        }

    }

    private static void debugRequest(String methodName, HttpServletRequest req) {
        log.trace("-----------");
        log.trace("WebdavServlet\n request: methodName = {}", methodName);
        log.trace("time: {}", System.currentTimeMillis());
        log.trace("path: {}", req.getRequestURI());
        log.trace("-----------");
        Enumeration<?> e = req.getHeaderNames();
        while (e.hasMoreElements()) {
            String s = (String) e.nextElement();
            log.trace("header: {} {}", s, req.getHeader(s));
        }
        e = req.getAttributeNames();
        while (e.hasMoreElements()) {
            String s = (String) e.nextElement();
            log.trace("attribute: {} {}", s, req.getAttribute(s));
        }
        e = req.getParameterNames();
        while (e.hasMoreElements()) {
            String s = (String) e.nextElement();
            log.trace("parameter: {} {}", s, req.getParameter(s));
        }
    }

    /**
     * Method that permit to customize the way user information are extracted from the request,
     * default use JAAS
     *
     * @param req
     * @return
     */
    protected Principal getUserPrincipal(HttpServletRequest req) {
        return req.getUserPrincipal();
    }
}
