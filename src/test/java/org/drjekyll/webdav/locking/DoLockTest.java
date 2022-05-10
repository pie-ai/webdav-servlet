package org.drjekyll.webdav.locking;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.drjekyll.webdav.StoreObjectTestUtil;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;

public class DoLockTest {

    private static final String EXCLUSIVE_LOCK_REQUEST =
        "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
            + "<D:lockinfo xmlns:D='DAV:'>"
            + "<D:lockscope><D:exclusive/></D:lockscope>"
            + "<D:locktype><D:write/></D:locktype>"
            + "<D:owner><D:href>I'am the Lock Owner</D:href></D:owner>"
            + "</D:lockinfo>";

    private static final byte[] EXCLUSIVE_LOCK_REQUEST_BYTE_ARRAY =
        EXCLUSIVE_LOCK_REQUEST.getBytes();

    private static final String SHARED_LOCK_REQUEST = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
        + "<D:lockinfo xmlns:D='DAV:'>"
        + "<D:lockscope><D:shared/></D:lockscope>"
        + "<D:locktype><D:write/></D:locktype>"
        + "<D:owner><D:href>I'am the Lock Owner</D:href></D:owner>"
        + "</D:lockinfo>";

    private static final byte[] SHARED_LOCK_REQUEST_BYTES = SHARED_LOCK_REQUEST.getBytes();

    private static final byte[] RESOURCE_CONTENT = {'<', 'h', 'e', 'l', 'l', 'o', '/', '>'};

    private static final int TEMP_TIMEOUT = 10;

    static boolean exclusive = true;

    static String depthString = "-1";

    static int depth = -1;

    static String timeoutString = "10";

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
    public void testDoLockIfReadOnly() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();

        DoLock doLock = new DoLock(mockStore, resLocks, true);
        doLock.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoRefreshLockOnLockedResource() throws Exception {

        final String lockPath = "/aFileToLock";
        final String lockOwner = "owner";

        ResourceLocks resLocks = new ResourceLocks();
        resLocks.lock(mockTransaction, lockPath, lockOwner, exclusive, depth, TEMP_TIMEOUT, false);

        LockedObject lo = resLocks.getLockedObjectByPath(mockTransaction, lockPath);
        String lockTokenString = lo.getID();
        final String lockToken = "(<opaquelocktoken:" + lockTokenString + ">)";

        final PrintWriter pw = new PrintWriter("/tmp/XMLTestFile");

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(lockPath));

                oneOf(mockReq).getHeader("If");
                will(returnValue(lockToken));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("Goliath"));

                exactly(2).of(mockReq).getHeader("If");
                will(returnValue(lockToken));

                oneOf(mockReq).getHeader("Timeout");
                will(returnValue("Infinite"));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_OK);

                oneOf(mockRes).setContentType("text/xml; charset=UTF-8");

                oneOf(mockRes).getWriter();
                will(returnValue(pw));

                oneOf(mockRes).addHeader("Lock-Token",
                    lockToken.substring(lockToken.indexOf("(") + 1, lockToken.indexOf(")"))
                );
            }
        });

        DoLock doLock = new DoLock(mockStore, resLocks, false);
        doLock.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoExclusiveLockOnResource() throws Exception {

        final String lockPath = "/aFileToLock";

        ResourceLocks resLocks = new ResourceLocks();
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
                will(returnValue(lockPath));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("Goliath"));

                oneOf(mockReq).getHeader("If");
                will(returnValue(null));

                StoredObject so = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, lockPath);
                will(returnValue(so));

                oneOf(mockReq).getInputStream();
                will(returnValue(dsisExclusive));

                oneOf(mockReq).getHeader("Depth");
                will(returnValue(depthString));

                oneOf(mockReq).getHeader("Timeout");
                will(returnValue(timeoutString));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_OK);

                oneOf(mockRes).setContentType("text/xml; charset=UTF-8");

                oneOf(mockRes).getWriter();
                will(returnValue(pw));

                // addHeader("Lock-Token", "(<opaquelocktoken:xxx-xxx-xxx>)")
                oneOf(mockRes).addHeader(with(any(String.class)), with(any(String.class)));
            }
        });

        DoLock doLock = new DoLock(mockStore, resLocks, false);
        doLock.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoSharedLockOnResource() throws Exception {

        final String lockPath = "/aFileToLock";

        ResourceLocks resLocks = new ResourceLocks();
        final PrintWriter pw = new PrintWriter("/tmp/XMLTestFile");

        final ByteArrayInputStream baisShared = new ByteArrayInputStream(SHARED_LOCK_REQUEST_BYTES);
        final DelegatingServletInputStream dsisShared =
            new DelegatingServletInputStream(baisShared);

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(lockPath));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("Goliath"));

                oneOf(mockReq).getHeader("If");
                will(returnValue(null));

                StoredObject so = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, lockPath);
                will(returnValue(so));

                oneOf(mockReq).getInputStream();
                will(returnValue(dsisShared));

                oneOf(mockReq).getHeader("Depth");
                will(returnValue(depthString));

                oneOf(mockReq).getHeader("Timeout");
                will(returnValue(timeoutString));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_OK);

                oneOf(mockRes).setContentType("text/xml; charset=UTF-8");

                oneOf(mockRes).getWriter();
                will(returnValue(pw));

                // addHeader("Lock-Token", "(<opaquelocktoken:xxx-xxx-xxx>)")
                oneOf(mockRes).addHeader(with(any(String.class)), with(any(String.class)));
            }
        });

        DoLock doLock = new DoLock(mockStore, resLocks, false);
        doLock.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoExclusiveLockOnCollection() throws Exception {

        final String lockPath = "/aFolderToLock";

        ResourceLocks resLocks = new ResourceLocks();

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
                will(returnValue(lockPath));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("Goliath"));

                oneOf(mockReq).getHeader("If");
                will(returnValue(null));

                StoredObject so = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, lockPath);
                will(returnValue(so));

                oneOf(mockReq).getInputStream();
                will(returnValue(dsisExclusive));

                oneOf(mockReq).getHeader("Depth");
                will(returnValue(depthString));

                oneOf(mockReq).getHeader("Timeout");
                will(returnValue(timeoutString));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_OK);

                oneOf(mockRes).setContentType("text/xml; charset=UTF-8");

                oneOf(mockRes).getWriter();
                will(returnValue(pw));

                // addHeader("Lock-Token", "(<opaquelocktoken:xxx-xxx-xxx>)")
                oneOf(mockRes).addHeader(with(any(String.class)), with(any(String.class)));
            }
        });

        DoLock doLock = new DoLock(mockStore, resLocks, false);
        doLock.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoSharedLockOnCollection() throws Exception {

        final String lockPath = "/aFolderToLock";

        ResourceLocks resLocks = new ResourceLocks();
        final PrintWriter pw = new PrintWriter("/tmp/XMLTestFile");

        final ByteArrayInputStream baisShared = new ByteArrayInputStream(SHARED_LOCK_REQUEST_BYTES);
        final DelegatingServletInputStream dsisShared =
            new DelegatingServletInputStream(baisShared);

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(lockPath));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("Goliath"));

                oneOf(mockReq).getHeader("If");
                will(returnValue(null));

                StoredObject so = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, lockPath);
                will(returnValue(so));

                oneOf(mockReq).getInputStream();
                will(returnValue(dsisShared));

                oneOf(mockReq).getHeader("Depth");
                will(returnValue(depthString));

                oneOf(mockReq).getHeader("Timeout");
                will(returnValue(timeoutString));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_OK);

                oneOf(mockRes).setContentType("text/xml; charset=UTF-8");

                oneOf(mockRes).getWriter();
                will(returnValue(pw));

                // addHeader("Lock-Token", "(<opaquelocktoken:xxx-xxx-xxx>)")
                oneOf(mockRes).addHeader(with(any(String.class)), with(any(String.class)));
            }
        });

        DoLock doLock = new DoLock(mockStore, resLocks, false);
        doLock.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoLockNullResourceLock() throws Exception {

        final String parentPath = "/parentCollection";
        final String lockPath = parentPath.concat("/aNullResource");

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
                will(returnValue(lockPath));

                LockedObject lockNullResourceLo = null;

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, lockPath);
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

                oneOf(mockStore).getStoredObject(mockTransaction, lockPath);
                will(returnValue(lockNullResourceSo));

                StoredObject parentSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                oneOf(mockStore).createFolder(mockTransaction, parentPath);

                oneOf(mockStore).getStoredObject(mockTransaction, lockPath);
                will(returnValue(lockNullResourceSo));

                oneOf(mockStore).createResource(mockTransaction, lockPath);

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

                lockNullResourceSo = StoreObjectTestUtil.initLockNullStoredObject();

                oneOf(mockStore).getStoredObject(mockTransaction, lockPath);
                will(returnValue(lockNullResourceSo));

                oneOf(mockReq).getInputStream();
                will(returnValue(dsisExclusive));

                oneOf(mockReq).getHeader("Depth");
                will(returnValue(("0")));

                oneOf(mockReq).getHeader("Timeout");
                will(returnValue("Infinite"));

                ResourceLocks resLocks = ResourceLocks.class.getDeclaredConstructor().newInstance();

                oneOf(mockResourceLocks).exclusiveLock(mockTransaction,
                    lockPath,
                    "I'am the Lock Owner",
                    0,
                    604800
                );
                will(returnValue(true));

                lockNullResourceLo = StoreObjectTestUtil.initLockNullLockedObject(resLocks,
                    lockPath
                );

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, lockPath);
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
            }
        });

        DoLock doLock = new DoLock(mockStore, mockResourceLocks, false);
        doLock.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();

    }
}
