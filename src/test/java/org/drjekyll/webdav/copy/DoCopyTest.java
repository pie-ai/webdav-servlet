package org.drjekyll.webdav.copy;

import java.io.ByteArrayInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.drjekyll.webdav.StoreObjectTestUtil;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.locking.LockedObject;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.methods.DoDelete;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DoCopyTest {

    private static final String TMP_FOLDER = "/tmp/tests";

    private static final String SOURCE_COLLECTION_PATH = TMP_FOLDER + "/sourceFolder";

    private static final String SOURCE_FILE_PATH = SOURCE_COLLECTION_PATH + "/sourceFile";

    private static final String DEST_COLLECTION_PATH = TMP_FOLDER + "/destFolder";

    private static final String DEST_FILE_PATH = DEST_COLLECTION_PATH + "/destFile";

    private static final byte[] RESOURCE_CONTENT = {'<', 'h', 'e', 'l', 'l', 'o', '/', '>'};

    private static final long RESOURCE_LENGTH = RESOURCE_CONTENT.length;

    private static final int TEMP_TIMEOUT = 10;

    private final Mockery mockery = new Mockery();

    private String[] sourceChildren = {"sourceFile"};

    private WebdavStore mockStore;

    private HttpServletRequest mockReq;

    private HttpServletResponse mockRes;

    private Transaction mockTransaction;

    private ByteArrayInputStream bais;

    private ByteArrayInputStream dsis;

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
        bais = new ByteArrayInputStream(RESOURCE_CONTENT);
        dsis = bais;
    }

    @Test
    public void testDoCopyIfReadOnly() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, true);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, true);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoCopyOfLockNullResource() throws Exception {

        final String parentPath = "/lockedFolder";
        final String path = parentPath.concat("/nullFile");

        String owner = new String("owner");
        ResourceLocks resLocks = new ResourceLocks();
        resLocks.lock(mockTransaction, parentPath, owner, true, 1, TEMP_TIMEOUT, false);

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue("/destination"));

                oneOf(mockReq).getServerName();
                will(returnValue("myServer"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue("/destination"));

                oneOf(mockReq).getServletPath();
                will(returnValue("/servletPath"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("T"));

                StoredObject so = StoreObjectTestUtil.initLockNullStoredObject();

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(so));

                oneOf(mockRes).addHeader("Allow", "OPTIONS, MKCOL, PUT, PROPFIND, LOCK, UNLOCK");

                oneOf(mockRes).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        });

        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoCopyIfParentIsLockedWithWrongLockToken() throws Exception {

        String owner = new String("owner");
        ResourceLocks resLocks = new ResourceLocks();
        resLocks.lock(mockTransaction, DEST_COLLECTION_PATH, owner, true, 1, TEMP_TIMEOUT, false);

        final LockedObject lo = resLocks.getLockedObjectByPath(mockTransaction,
            DEST_COLLECTION_PATH
        );
        final String wrongLockToken = "(<opaquelocktoken:" + lo.getID() + "WRONG>)";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue(DEST_FILE_PATH));

                oneOf(mockReq).getServerName();
                will(returnValue("myServer"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(DEST_FILE_PATH));

                oneOf(mockReq).getServletPath();
                will(returnValue("/servletPath"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getHeader("If");
                will(returnValue(wrongLockToken));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue(null));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                StoredObject destFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );
                atLeast(1).of(mockStore).getStoredObject(mockTransaction, DEST_FILE_PATH);
                will(returnValue(destFileSo));

                oneOf(mockStore).removeObject(mockTransaction, DEST_FILE_PATH);

                oneOf(mockStore).createResource(mockTransaction, DEST_FILE_PATH);

                oneOf(mockStore).setResourceContent(mockTransaction,
                    DEST_FILE_PATH,
                    dsis,
                    null,
                    null
                );
                will(returnValue(RESOURCE_LENGTH));

                StoredObject sourceFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );
                atLeast(1).of(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(sourceFileSo));

                oneOf(mockStore).getResourceContent(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(dsis));

            }
        });

        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoCopyIfParentIsLockedWithRightLockToken() throws Exception {

        String owner = new String("owner");
        ResourceLocks resLocks = new ResourceLocks();

        resLocks.lock(mockTransaction, DEST_COLLECTION_PATH, owner, true, 1, TEMP_TIMEOUT, false);

        final LockedObject lo = resLocks.getLockedObjectByPath(mockTransaction,
            DEST_COLLECTION_PATH
        );
        final String rightLockToken = "(<opaquelocktoken:" + lo.getID() + ">)";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue(DEST_FILE_PATH));

                oneOf(mockReq).getServerName();
                will(returnValue("myServer"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(DEST_FILE_PATH));

                oneOf(mockReq).getServletPath();
                will(returnValue("/servletPath"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getHeader("If");
                will(returnValue(rightLockToken));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("F"));

                StoredObject sourceFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(sourceFileSo));

                StoredObject destFileSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_FILE_PATH);
                will(returnValue(destFileSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(sourceFileSo));

                oneOf(mockStore).createResource(mockTransaction, DEST_FILE_PATH);

                oneOf(mockStore).getResourceContent(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(dsis));

                oneOf(mockStore).setResourceContent(mockTransaction,
                    DEST_FILE_PATH,
                    dsis,
                    null,
                    null
                );
                will(returnValue(RESOURCE_LENGTH));

                destFileSo = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_FILE_PATH);
                will(returnValue(destFileSo));

            }
        });

        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoCopyIfDestinationPathInvalid() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue(null));

                oneOf(mockRes).sendError(HttpServletResponse.SC_BAD_REQUEST);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();

    }

    @Test
    public void testDoCopyIfSourceEqualsDestination() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getServerName();
                will(returnValue("serverName"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getServletPath();
                will(returnValue("/servletPath"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);

            }
        });

        ResourceLocks resLocks = new ResourceLocks();

        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoCopyFolderIfNoLocks() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_COLLECTION_PATH));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue(DEST_COLLECTION_PATH));

                oneOf(mockReq).getServerName();
                will(returnValue("serverName"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(DEST_COLLECTION_PATH));

                oneOf(mockReq).getServletPath();
                will(returnValue("/servletPath"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_COLLECTION_PATH));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("F"));

                StoredObject sourceCollectionSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_COLLECTION_PATH);
                will(returnValue(sourceCollectionSo));

                StoredObject destCollectionSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_COLLECTION_PATH);
                will(returnValue(destCollectionSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_COLLECTION_PATH);
                will(returnValue(sourceCollectionSo));

                oneOf(mockStore).createFolder(mockTransaction, DEST_COLLECTION_PATH);

                oneOf(mockReq).getHeader("Depth");
                will(returnValue("-1"));

                sourceChildren = new String[]{"sourceFile"};

                oneOf(mockStore).getChildrenNames(mockTransaction, SOURCE_COLLECTION_PATH);
                will(returnValue(sourceChildren));

                StoredObject sourceFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(sourceFileSo));

                oneOf(mockStore).createResource(mockTransaction,
                    DEST_COLLECTION_PATH + "/sourceFile"
                );

                oneOf(mockStore).getResourceContent(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(dsis));

                oneOf(mockStore).setResourceContent(mockTransaction,
                    DEST_COLLECTION_PATH + "/sourceFile",
                    dsis,
                    null,
                    null
                );

                StoredObject destFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction,
                    DEST_COLLECTION_PATH + "/sourceFile"
                );
                will(returnValue(destFileSo));

            }
        });

        ResourceLocks resLocks = new ResourceLocks();

        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoCopyIfSourceDoesntExist() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue(DEST_FILE_PATH));

                oneOf(mockReq).getServerName();
                will(returnValue("serverName"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(DEST_FILE_PATH));

                oneOf(mockReq).getServletPath();
                will(returnValue("/servletPath"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("F"));

                StoredObject notExistSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(notExistSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_NOT_FOUND);

            }
        });

        ResourceLocks resLocks = new ResourceLocks();

        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoCopyIfDestinationAlreadyExistsAndOverwriteTrue() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                StoredObject sourceSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(sourceSo));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue(DEST_FILE_PATH));

                oneOf(mockReq).getServerName();
                will(returnValue("serverName"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue("/folder/destFolder"));

                oneOf(mockReq).getServletPath();
                will(returnValue("/servletPath"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                StoredObject existingDestSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_FILE_PATH);
                will(returnValue(existingDestSo));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("T"));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_FILE_PATH);
                will(returnValue(existingDestSo));

                oneOf(mockStore).removeObject(mockTransaction, DEST_FILE_PATH);

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(sourceSo));

                oneOf(mockStore).createResource(mockTransaction, DEST_FILE_PATH);

                oneOf(mockStore).getResourceContent(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(dsis));

                oneOf(mockStore).setResourceContent(mockTransaction,
                    DEST_FILE_PATH,
                    dsis,
                    null,
                    null
                );

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_FILE_PATH);
                will(returnValue(existingDestSo));

            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();

    }

    @Test
    public void testDoCopyIfDestinationAlreadyExistsAndOverwriteFalse() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                StoredObject sourceSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(sourceSo));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue("serverName".concat(DEST_FILE_PATH)));

                oneOf(mockReq).getServerName();
                will(returnValue("serverName"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(DEST_FILE_PATH));

                oneOf(mockReq).getServletPath();
                will(returnValue("/servletPath"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                StoredObject existingDestSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_FILE_PATH);
                will(returnValue(existingDestSo));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("F"));

                oneOf(mockRes).sendError(HttpServletResponse.SC_PRECONDITION_FAILED);

            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();

    }

    @Test
    public void testDoCopyIfOverwriteTrue() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                StoredObject sourceSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(sourceSo));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue("http://destination:80".concat(DEST_FILE_PATH)));

                oneOf(mockReq).getContextPath();
                will(returnValue("http://destination:80"));

                oneOf(mockReq).getPathInfo();
                will(returnValue(DEST_COLLECTION_PATH));

                oneOf(mockReq).getServletPath();
                will(returnValue("http://destination:80"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getAttribute("javax.servlet.include.path_info");
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("T"));

                StoredObject destFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_FILE_PATH);
                will(returnValue(destFileSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_FILE_PATH);
                will(returnValue(destFileSo));

                oneOf(mockStore).removeObject(mockTransaction, DEST_FILE_PATH);

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(sourceSo));

                oneOf(mockStore).createResource(mockTransaction, DEST_FILE_PATH);

                oneOf(mockStore).getResourceContent(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(dsis));

                oneOf(mockStore).setResourceContent(mockTransaction,
                    DEST_FILE_PATH,
                    dsis,
                    null,
                    null
                );

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_FILE_PATH);
                will(returnValue(destFileSo));
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();

    }

    @Test
    public void testDoCopyIfOverwriteFalse() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                StoredObject sourceSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(sourceSo));

                oneOf(mockReq).getHeader("Destination");
                will(returnValue("http://destination:80".concat(DEST_COLLECTION_PATH)));

                oneOf(mockReq).getContextPath();
                will(returnValue("http://destination:80"));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getServletPath();
                will(returnValue("http://destination:80"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getAttribute("javax.servlet.include.path_info");
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("F"));

                StoredObject existingDestSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, DEST_COLLECTION_PATH);
                will(returnValue(existingDestSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);
        doCopy.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();

    }
}
