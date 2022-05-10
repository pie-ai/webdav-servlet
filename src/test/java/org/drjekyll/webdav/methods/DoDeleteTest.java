package org.drjekyll.webdav.methods;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.drjekyll.webdav.StoreObjectTestUtil;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.locking.LockedObject;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DoDeleteTest {

    private static final String TMP_FOLDER = "/tmp/tests";

    private static final String SOURCE_COLLECTION_PATH = TMP_FOLDER + "/sourceFolder";

    private static final String SOURCE_FILE_PATH = SOURCE_COLLECTION_PATH + "/sourceFile";

    private static final int TEMP_TIMEOUT = 10;

    private static final byte[] RESOURCE_CONTENT = {
        '<', 'h', 'e', 'l', 'l', 'o', '/', '>'
    };

    private final Mockery mockery = new Mockery();

    private WebdavStore mockStore;

    private HttpServletRequest mockReq;

    private HttpServletResponse mockRes;

    private Transaction mockTransaction;

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
    }

    @Test
    public void testDeleteIfReadOnlyIsTrue() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, true);
        doDelete.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDeleteFileIfObjectExists() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                StoredObject fileSo = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(fileSo));

                oneOf(mockStore).removeObject(mockTransaction, SOURCE_FILE_PATH);
            }
        });

        DoDelete doDelete = new DoDelete(mockStore, new ResourceLocks(), false);

        doDelete.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDeleteFileIfObjectNotExists() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                StoredObject fileSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(fileSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        });

        DoDelete doDelete = new DoDelete(mockStore, new ResourceLocks(), false);

        doDelete.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDeleteFolderIfObjectExists() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_COLLECTION_PATH));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                StoredObject folderSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_COLLECTION_PATH);
                will(returnValue(folderSo));

                oneOf(mockStore).getChildrenNames(mockTransaction, SOURCE_COLLECTION_PATH);
                will(returnValue(new String[]{"subFolder", "sourceFile"}));

                StoredObject fileSo = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(fileSo));

                oneOf(mockStore).removeObject(mockTransaction, SOURCE_FILE_PATH);

                StoredObject subFolderSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction,
                    SOURCE_COLLECTION_PATH + "/subFolder"
                );
                will(returnValue(subFolderSo));

                oneOf(mockStore).getChildrenNames(mockTransaction,
                    SOURCE_COLLECTION_PATH + "/subFolder"
                );
                will(returnValue(new String[]{"fileInSubFolder"}));

                StoredObject fileInSubFolderSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction,
                    SOURCE_COLLECTION_PATH + "/subFolder/fileInSubFolder"
                );
                will(returnValue(fileInSubFolderSo));

                oneOf(mockStore).removeObject(mockTransaction,
                    SOURCE_COLLECTION_PATH + "/subFolder/fileInSubFolder"
                );

                oneOf(mockStore).removeObject(mockTransaction,
                    SOURCE_COLLECTION_PATH + "/subFolder"
                );

                oneOf(mockStore).removeObject(mockTransaction, SOURCE_COLLECTION_PATH);
            }
        });

        DoDelete doDelete = new DoDelete(mockStore, new ResourceLocks(), false);

        doDelete.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDeleteFolderIfObjectNotExists() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_COLLECTION_PATH));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                StoredObject folderSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_COLLECTION_PATH);
                will(returnValue(folderSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        });

        DoDelete doDelete = new DoDelete(mockStore, new ResourceLocks(), false);

        doDelete.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDeleteFileInFolder() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(SOURCE_FILE_PATH));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                StoredObject fileSo = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, SOURCE_FILE_PATH);
                will(returnValue(fileSo));

                oneOf(mockStore).removeObject(mockTransaction, SOURCE_FILE_PATH);
            }
        });

        DoDelete doDelete = new DoDelete(mockStore, new ResourceLocks(), false);

        doDelete.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDeleteFileInLockedFolderWithWrongLockToken() throws Exception {

        final String lockedFolderPath = "/lockedFolder";
        final String fileInLockedFolderPath = lockedFolderPath.concat("/fileInLockedFolder");

        String owner = new String("owner");
        ResourceLocks resLocks = new ResourceLocks();

        resLocks.lock(mockTransaction, lockedFolderPath, owner, true, -1, TEMP_TIMEOUT, false);
        LockedObject lo = resLocks.getLockedObjectByPath(mockTransaction, lockedFolderPath);
        final String wrongLockToken = "(<opaquelocktoken:" + lo.getID() + "WRONG>)";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(fileInLockedFolderPath));

                oneOf(mockReq).getHeader("If");
                will(returnValue(wrongLockToken));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                StoredObject so = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, fileInLockedFolderPath);
                will(returnValue(so));

                oneOf(mockStore).removeObject(mockTransaction, fileInLockedFolderPath);

            }
        });

        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        doDelete.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDeleteFileInLockedFolderWithRightLockToken() throws Exception {

        final String path = "/lockedFolder/fileInLockedFolder";
        final String parentPath = "/lockedFolder";
        final String owner = new String("owner");
        ResourceLocks resLocks = new ResourceLocks();

        resLocks.lock(mockTransaction, parentPath, owner, true, -1, TEMP_TIMEOUT, false);
        LockedObject lo = resLocks.getLockedObjectByPath(mockTransaction, "/lockedFolder");
        final String rightLockToken = "(<opaquelocktoken:" + lo.getID() + ">)";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("If");
                will(returnValue(rightLockToken));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                StoredObject so = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(so));

                oneOf(mockStore).removeObject(mockTransaction, path);

            }
        });

        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);

        doDelete.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDeleteFileInFolderIfObjectNotExists() throws Exception {

        boolean readOnly = false;

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue("/folder/file"));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                StoredObject nonExistingSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, "/folder/file");
                will(returnValue(nonExistingSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        });

        DoDelete doDelete = new DoDelete(mockStore, new ResourceLocks(), readOnly);

        doDelete.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

}
