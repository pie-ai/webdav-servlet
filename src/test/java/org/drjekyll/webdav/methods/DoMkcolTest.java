package org.drjekyll.webdav.methods;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.drjekyll.webdav.StoreObjectTestUtil;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.locking.DoLock;
import org.drjekyll.webdav.locking.IResourceLocks;
import org.drjekyll.webdav.locking.LockedObject;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;

public class DoMkcolTest {

    private static final byte[] RESOURCE_CONTENT = {'<', 'h', 'e', 'l', 'l', 'o', '/', '>'};

    private static final String EXCLUSIVE_LOCK_REQUEST =
        "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
            + "<D:lockinfo xmlns:D='DAV:'>"
            + "<D:lockscope><D:exclusive/></D:lockscope>"
            + "<D:locktype><D:write/></D:locktype>"
            + "<D:owner><D:href>I'am the Lock Owner</D:href></D:owner>"
            + "</D:lockinfo>";

    private static final byte[] EXCLUSIVE_LOCK_REQUEST_BYTE_ARRAY =
        EXCLUSIVE_LOCK_REQUEST.getBytes();

    static String parentPath = "/parentCollection";

    static String mkcolPath = parentPath.concat("/makeCollection");

    private final String owner = "a lock owner";

    private final Mockery mockery = new Mockery();

    private WebdavStore mockStore;

    private HttpServletRequest mockReq;

    private HttpServletResponse mockRes;

    private Transaction mockTransaction;

    private IResourceLocks mockResourceLocks;

    @AfterEach
    public void assertSatisfiedMockery() {
        mockery.assertIsSatisfied();
    }

    @BeforeEach
    public void setUp() {
        mockStore = mockery.mock(WebdavStore.class);
        mockReq = mockery.mock(HttpServletRequest.class);
        mockRes = mockery.mock(HttpServletResponse.class);
        mockTransaction = mockery.mock(Transaction.class);
        mockResourceLocks = mockery.mock(IResourceLocks.class);
    }

    @Test
    public void testMkcolIfReadOnlyIsTrue() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoMkcol doMkcol = new DoMkcol(mockStore, resLocks, true);
        doMkcol.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMkcolSuccess() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(mkcolPath));

                StoredObject parentSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                StoredObject mkcolSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, mkcolPath);
                will(returnValue(mkcolSo));

                oneOf(mockStore).createFolder(mockTransaction, mkcolPath);

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoMkcol doMkcol = new DoMkcol(mockStore, resLocks, false);
        doMkcol.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMkcolIfParentPathIsNoFolder() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(mkcolPath));

                StoredObject parentSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                String methodsAllowed = "OPTIONS, GET, HEAD, POST, DELETE, TRACE, "
                    + "PROPPATCH, COPY, MOVE, LOCK, UNLOCK, PROPFIND";

                oneOf(mockRes).addHeader("Allow", methodsAllowed);

                oneOf(mockRes).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoMkcol doMkcol = new DoMkcol(mockStore, resLocks, false);
        doMkcol.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMkcolIfParentPathIsAFolderButObjectAlreadyExists() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(mkcolPath));

                StoredObject parentSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                StoredObject mkcolSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, mkcolPath);
                will(returnValue(mkcolSo));

                oneOf(mockRes).addHeader(
                    "Allow",
                    "OPTIONS, GET, HEAD, POST, DELETE, TRACE, PROPPATCH, COPY, MOVE, LOCK, UNLOCK, PROPFIND, PUT"
                );

                oneOf(mockRes).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);

            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoMkcol doMkcol = new DoMkcol(mockStore, resLocks, false);
        doMkcol.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMkcolIfParentFolderIsLockedWithRightLockToken() throws Exception {

        ResourceLocks resLocks = new ResourceLocks();
        resLocks.lock(mockTransaction, parentPath, owner, true, -1, 200, false);
        LockedObject lo = resLocks.getLockedObjectByPath(mockTransaction, parentPath);
        final String rightLockToken = "(<opaquelocktoken:" + lo.getID() + ">)";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(mkcolPath));

                oneOf(mockReq).getHeader("If");
                will(returnValue(rightLockToken));

                StoredObject parentSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                StoredObject mkcolSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, mkcolPath);
                will(returnValue(mkcolSo));

                oneOf(mockStore).createFolder(mockTransaction, mkcolPath);

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

            }
        });

        DoMkcol doMkcol = new DoMkcol(mockStore, resLocks, false);
        doMkcol.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMkcolIfParentFolderIsLockedWithWrongLockToken() throws Exception {

        ResourceLocks resLocks = new ResourceLocks();
        resLocks.lock(mockTransaction, parentPath, owner, true, -1, 200, false);
        final String wrongLockToken = "(<opaquelocktoken:" + "aWrongLockToken" + ">)";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(mkcolPath));

                oneOf(mockReq).getHeader("If");
                will(returnValue(wrongLockToken));

                oneOf(mockRes).sendError(HttpServletResponse.SC_CONFLICT);

                StoredObject parentSo = null;
                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

            }
        });

        DoMkcol doMkcol = new DoMkcol(mockStore, resLocks, false);
        doMkcol.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMkcolOnALockNullResource() throws Exception {

        final PrintWriter pw = new PrintWriter("/tmp/XMLTestFile");

        final ByteArrayInputStream baisExclusive = new ByteArrayInputStream(
            EXCLUSIVE_LOCK_REQUEST_BYTE_ARRAY);
        final DelegatingServletInputStream dsisExclusive = new DelegatingServletInputStream(
            baisExclusive);

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(mkcolPath));

                LockedObject lockNullResourceLo = null;

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, mkcolPath);
                will(returnValue(lockNullResourceLo));

                LockedObject parentLo = null;

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, parentPath);
                will(returnValue(parentLo));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("Goliath"));

                oneOf(mockResourceLocks).lock(with(any(Transaction.class)),
                    with(any(String.class)),
                    with(any(String.class)),
                    with(any(boolean.class)),
                    with(any(int.class)),
                    with(any(int.class)),
                    with(any(boolean.class))
                );
                will(returnValue(true));

                oneOf(mockReq).getHeader("If");
                will(returnValue(null));

                StoredObject lockNullResourceSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, mkcolPath);
                will(returnValue(lockNullResourceSo));

                StoredObject parentSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                oneOf(mockStore).createFolder(mockTransaction, parentPath);

                parentSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, mkcolPath);
                will(returnValue(lockNullResourceSo));

                oneOf(mockStore).createResource(mockTransaction, mkcolPath);

                lockNullResourceSo = StoreObjectTestUtil.initLockNullStoredObject();

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

                oneOf(mockStore).getStoredObject(mockTransaction, mkcolPath);
                will(returnValue(lockNullResourceSo));

                oneOf(mockReq).getInputStream();
                will(returnValue(dsisExclusive));

                oneOf(mockReq).getHeader("Depth");
                will(returnValue(("0")));

                oneOf(mockReq).getHeader("Timeout");
                will(returnValue("Infinite"));

                ResourceLocks resLocks = ResourceLocks.class.getDeclaredConstructor().newInstance();

                oneOf(mockResourceLocks).exclusiveLock(mockTransaction,
                    mkcolPath,
                    "I'am the Lock Owner",
                    0,
                    604800
                );
                will(returnValue(true));

                lockNullResourceLo = StoreObjectTestUtil.initLockNullLockedObject(resLocks,
                    mkcolPath
                );

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, mkcolPath);
                will(returnValue(lockNullResourceLo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_OK);

                oneOf(mockRes).setContentType("text/xml; charset=UTF-8");

                oneOf(mockRes).getWriter();
                will(returnValue(pw));

                String loId = null;
                if (lockNullResourceLo != null) {
                    loId = lockNullResourceLo.getID();
                }
                final String lockToken = "<opaquelocktoken:" + loId + ">";

                oneOf(mockRes).addHeader("Lock-Token", lockToken);

                oneOf(mockResourceLocks).unlockTemporaryLockedObjects(with(any(Transaction.class)),
                    with(any(String.class)),
                    with(any(String.class))
                );

                // -----LOCK on a non-existing resource successful------
                // --------now MKCOL on the lock-null resource----------

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(mkcolPath));

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, parentPath);
                will(returnValue(parentLo));

                oneOf(mockResourceLocks).lock(with(any(Transaction.class)),
                    with(any(String.class)),
                    with(any(String.class)),
                    with(any(boolean.class)),
                    with(any(int.class)),
                    with(any(int.class)),
                    with(any(boolean.class))
                );
                will(returnValue(true));

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                oneOf(mockStore).getStoredObject(mockTransaction, mkcolPath);
                will(returnValue(lockNullResourceSo));

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, mkcolPath);
                will(returnValue(lockNullResourceLo));

                final String ifHeaderLockToken = "(<locktoken:" + loId + ">)";

                oneOf(mockReq).getHeader("If");
                will(returnValue(ifHeaderLockToken));

                String[] owners = lockNullResourceLo.getOwner();
                String owner = null;
                if (owners != null) {
                    owner = owners[0];
                }

                oneOf(mockResourceLocks).unlock(mockTransaction, loId, owner);
                will(returnValue(true));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

                oneOf(mockResourceLocks).unlockTemporaryLockedObjects(with(any(Transaction.class)),
                    with(any(String.class)),
                    with(any(String.class))
                );

            }
        });

        DoLock doLock = new DoLock(mockStore, mockResourceLocks, false);
        doLock.execute(mockTransaction, mockReq, mockRes);

        DoMkcol doMkcol = new DoMkcol(mockStore, mockResourceLocks, false);
        doMkcol.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }
}
