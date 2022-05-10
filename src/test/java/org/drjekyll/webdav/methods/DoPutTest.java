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

public class DoPutTest {


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

    static String path = parentPath.concat("/fileToPut");

    static boolean lazyFolderCreationOnPut = true;

    private final Mockery mockery = new Mockery();

    private WebdavStore mockStore;

    private HttpServletRequest mockReq;

    private HttpServletResponse mockRes;

    private IResourceLocks mockResourceLocks;

    private Transaction mockTransaction;

    private ByteArrayInputStream bais;

    private DelegatingServletInputStream dsis;

    @AfterEach
    public void assertSatisfiedMockery() {
        mockery.assertIsSatisfied();
    }

    @BeforeEach
    public void setUp() {
        mockStore = mockery.mock(WebdavStore.class);
        mockReq = mockery.mock(HttpServletRequest.class);
        mockRes = mockery.mock(HttpServletResponse.class);
        mockResourceLocks = mockery.mock(IResourceLocks.class);
        mockTransaction = mockery.mock(Transaction.class);
        bais = new ByteArrayInputStream(RESOURCE_CONTENT);
        dsis = new DelegatingServletInputStream(bais);
    }

    @Test
    public void testDoPutIfReadOnlyTrue() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        });

        DoPut doPut = new DoPut(mockStore, new ResourceLocks(), true, lazyFolderCreationOnPut);
        doPut.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoPutIfReadOnlyFalse() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("Goliath agent"));

                StoredObject parentSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                StoredObject fileSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(fileSo));

                oneOf(mockStore).createResource(mockTransaction, path);

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

                oneOf(mockReq).getInputStream();
                will(returnValue(dsis));

                oneOf(mockStore).setResourceContent(mockTransaction, path, dsis, null, null);
                will(returnValue(8L));

                fileSo = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(fileSo));

                // User-Agent: Goliath --> dont add ContentLength
                // one(mockRes).setContentLength(8);
            }
        });

        DoPut doPut = new DoPut(mockStore, new ResourceLocks(), false, lazyFolderCreationOnPut);
        doPut.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoPutIfLazyFolderCreationOnPutIsFalse() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("Transmit agent"));

                StoredObject parentSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");

            }
        });

        DoPut doPut = new DoPut(mockStore, new ResourceLocks(), false, !lazyFolderCreationOnPut);
        doPut.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoPutIfLazyFolderCreationOnPutIsTrue() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("WebDAVFS/1.5.0 (01500000) ....."));

                StoredObject parentSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                oneOf(mockStore).createFolder(mockTransaction, parentPath);

                StoredObject fileSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(fileSo));

                oneOf(mockStore).createResource(mockTransaction, path);

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

                oneOf(mockReq).getInputStream();
                will(returnValue(dsis));

                oneOf(mockStore).setResourceContent(mockTransaction, path, dsis, null, null);
                will(returnValue(8L));

                fileSo = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(fileSo));

            }
        });

        DoPut doPut = new DoPut(mockStore, new ResourceLocks(), false, lazyFolderCreationOnPut);
        doPut.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoPutIfParentPathIsResource() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("WebDAVFS/1.5.0 (01500000) ....."));

                StoredObject parentSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        });

        DoPut doPut = new DoPut(mockStore, new ResourceLocks(), false, lazyFolderCreationOnPut);
        doPut.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoPutOnALockNullResource() throws Exception {

        ByteArrayInputStream baisExclusive = new ByteArrayInputStream(
            EXCLUSIVE_LOCK_REQUEST_BYTE_ARRAY);

        DelegatingServletInputStream dsisExclusive =
            new DelegatingServletInputStream(baisExclusive);

        final PrintWriter pw = new PrintWriter("/tmp/XMLTestFile");

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                LockedObject lockNullResourceLo = null;

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, path);
                will(returnValue(lockNullResourceLo));

                LockedObject parentLo = null;

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, parentPath);
                will(returnValue(parentLo));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("Transmit agent"));

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

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(lockNullResourceSo));

                StoredObject parentSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                oneOf(mockStore).createFolder(mockTransaction, parentPath);

                parentSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(lockNullResourceSo));

                oneOf(mockStore).createResource(mockTransaction, path);

                lockNullResourceSo = StoreObjectTestUtil.initLockNullStoredObject();

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(lockNullResourceSo));

                oneOf(mockReq).getInputStream();
                will(returnValue(dsisExclusive));

                oneOf(mockReq).getHeader("Depth");
                will(returnValue(("0")));

                oneOf(mockReq).getHeader("Timeout");
                will(returnValue("Infinite"));

                ResourceLocks resLocks = ResourceLocks.class.getDeclaredConstructor().newInstance();

                oneOf(mockResourceLocks).exclusiveLock(mockTransaction,
                    path,
                    "I'am the Lock Owner",
                    0,
                    604800
                );
                will(returnValue(true));

                lockNullResourceLo = StoreObjectTestUtil.initLockNullLockedObject(resLocks, path);

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, path);
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

                // // -----LOCK on a non-existing resource successful------
                // // --------now doPUT on the lock-null resource----------

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("User-Agent");
                will(returnValue("Transmit agent"));

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, parentPath);
                will(returnValue(parentLo));

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, path);
                will(returnValue(lockNullResourceLo));

                final String ifHeaderLockToken = "(<locktoken:" + loId + ">)";

                oneOf(mockReq).getHeader("If");
                will(returnValue(ifHeaderLockToken));

                oneOf(mockResourceLocks).getLockedObjectByID(mockTransaction, loId);
                will(returnValue(lockNullResourceLo));

                oneOf(mockResourceLocks).lock(with(any(Transaction.class)),
                    with(any(String.class)),
                    with(any(String.class)),
                    with(any(boolean.class)),
                    with(any(int.class)),
                    with(any(int.class)),
                    with(any(boolean.class))
                );
                will(returnValue(true));

                parentSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, parentPath);
                will(returnValue(parentSo));

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(lockNullResourceSo));

                oneOf(mockResourceLocks).getLockedObjectByPath(mockTransaction, path);
                will(returnValue(lockNullResourceLo));

                oneOf(mockReq).getHeader("If");
                will(returnValue(ifHeaderLockToken));

                String[] owners = lockNullResourceLo.getOwner();
                String owner = null;
                if (owners != null) {
                    owner = owners[0];
                }

                oneOf(mockResourceLocks).unlock(mockTransaction, loId, owner);
                will(returnValue(true));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                oneOf(mockReq).getInputStream();
                will(returnValue(dsis));

                oneOf(mockStore).setResourceContent(mockTransaction, path, dsis, null, null);
                will(returnValue(8L));

                StoredObject newResourceSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(newResourceSo));

                oneOf(mockResourceLocks).unlockTemporaryLockedObjects(with(any(Transaction.class)),
                    with(any(String.class)),
                    with(any(String.class))
                );
            }
        });

        DoLock doLock = new DoLock(mockStore, mockResourceLocks, false);
        doLock.execute(mockTransaction, mockReq, mockRes);

        DoPut doPut = new DoPut(mockStore, mockResourceLocks, false, lazyFolderCreationOnPut);
        doPut.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }
}
