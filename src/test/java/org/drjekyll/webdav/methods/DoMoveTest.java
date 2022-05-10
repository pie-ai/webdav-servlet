package org.drjekyll.webdav.methods;

import java.io.ByteArrayInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.drjekyll.webdav.StoreObjectTestUtil;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.copy.DoCopy;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DoMoveTest {

    private static final byte[] RESOURCE_CONTENT = {'<', 'h', 'e', 'l', 'l', 'o', '/', '>'};

    private static final String tmpFolder = "/tmp/tests";

    private static final String sourceCollectionPath = tmpFolder + "/sourceFolder";

    private static final String sourceFilePath = sourceCollectionPath + "/sourceFile";

    private static final String destCollectionPath = tmpFolder + "/destFolder";

    private static final String destFilePath = destCollectionPath + "/destFile";

    private static final String overwritePath = destCollectionPath + "/sourceFolder";

    private final String[] destChildren = {"destFile"};

    private final ByteArrayInputStream bais = new ByteArrayInputStream(RESOURCE_CONTENT);

    private final Mockery mockery = new Mockery();

    private WebdavStore mockStore;

    private HttpServletRequest mockReq;

    private HttpServletResponse mockRes;

    private Transaction mockTransaction;

    private String[] sourceChildren = {"sourceFile"};

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
    public void testMovingOfFileOrFolderIfReadOnlyIsTrue() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, true);
        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, true);

        DoMove doMove = new DoMove(resLocks, doDelete, doCopy, true);

        doMove.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMovingOfaFileIfDestinationNotPresent() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceFilePath));

                exactly(2).of(mockReq).getHeader("Destination");
                will(returnValue(destFilePath));

                oneOf(mockReq).getServerName();
                will(returnValue("serverName"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(destFilePath));

                oneOf(mockReq).getServletPath();
                will(returnValue("/servletPath"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceFilePath));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("F"));

                StoredObject sourceFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                StoredObject destFileSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, destFilePath);
                will(returnValue(destFileSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                oneOf(mockStore).createResource(mockTransaction, destFilePath);

                oneOf(mockStore).getResourceContent(mockTransaction, sourceFilePath);
                will(returnValue(bais));

                oneOf(mockStore).setResourceContent(mockTransaction,
                    destFilePath,
                    bais,
                    null,
                    null
                );
                will(returnValue(8L));

                destFileSo = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, destFilePath);
                will(returnValue(destFileSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                oneOf(mockStore).removeObject(mockTransaction, sourceFilePath);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);
        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);

        DoMove doMove = new DoMove(resLocks, doDelete, doCopy, false);

        doMove.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMovingOfaFileIfDestinationIsPresentAndOverwriteFalse() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceFilePath));

                exactly(2).of(mockReq).getHeader("Destination");
                will(returnValue(destFilePath));

                oneOf(mockReq).getServerName();
                will(returnValue("server_name"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(destFilePath));

                oneOf(mockReq).getServletPath();
                will(returnValue("servlet_path"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceFilePath));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("F"));

                StoredObject sourceFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                StoredObject destFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, destFilePath);
                will(returnValue(destFileSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_PRECONDITION_FAILED);

            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);
        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);

        DoMove doMove = new DoMove(resLocks, doDelete, doCopy, false);

        doMove.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMovingOfaFileIfDestinationIsPresentAndOverwriteTrue() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceFilePath));

                exactly(2).of(mockReq).getHeader("Destination");
                will(returnValue(destFilePath));

                oneOf(mockReq).getServerName();
                will(returnValue("server_name"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(destFilePath));

                oneOf(mockReq).getServletPath();
                will(returnValue("servlet_path"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceFilePath));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("T"));

                StoredObject sourceFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                StoredObject destFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, destFilePath);
                will(returnValue(destFileSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, destFilePath);
                will(returnValue(destFileSo));

                oneOf(mockStore).removeObject(mockTransaction, destFilePath);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                oneOf(mockStore).createResource(mockTransaction, destFilePath);

                oneOf(mockStore).getResourceContent(mockTransaction, sourceFilePath);
                will(returnValue(bais));

                oneOf(mockStore).setResourceContent(mockTransaction,
                    destFilePath,
                    bais,
                    null,
                    null
                );
                will(returnValue(8L));

                oneOf(mockStore).getStoredObject(mockTransaction, destFilePath);
                will(returnValue(destFileSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                oneOf(mockStore).removeObject(mockTransaction, sourceFilePath);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);
        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);

        DoMove doMove = new DoMove(resLocks, doDelete, doCopy, false);

        doMove.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMovingOfaFileIfSourceNotPresent() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceFilePath));

                exactly(2).of(mockReq).getHeader("Destination");
                will(returnValue(destFilePath));

                oneOf(mockReq).getServerName();
                will(returnValue("server_name"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(destFilePath));

                oneOf(mockReq).getServletPath();
                will(returnValue("servlet_path"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceFilePath));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("F"));

                StoredObject sourceFileSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);
        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);

        DoMove doMove = new DoMove(resLocks, doDelete, doCopy, false);

        doMove.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMovingIfSourcePathEqualsDestinationPath() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(destFilePath));

                exactly(2).of(mockReq).getHeader("Destination");
                will(returnValue(destFilePath));

                oneOf(mockReq).getServerName();
                will(returnValue("server_name"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(destFilePath));

                oneOf(mockReq).getServletPath();
                will(returnValue("servlet_path"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(destFilePath));

                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);
        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);

        DoMove doMove = new DoMove(resLocks, doDelete, doCopy, false);

        doMove.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMovingOfaCollectionIfDestinationIsNotPresent() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceCollectionPath));

                exactly(2).of(mockReq).getHeader("Destination");
                will(returnValue(destCollectionPath));

                oneOf(mockReq).getServerName();
                will(returnValue("server_name"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(destCollectionPath));

                oneOf(mockReq).getServletPath();
                will(returnValue("servlet_path"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceCollectionPath));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("F"));

                StoredObject sourceCollectionSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceCollectionSo));

                StoredObject destCollectionSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, destCollectionPath);
                will(returnValue(destCollectionSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_CREATED);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceCollectionSo));

                oneOf(mockStore).createFolder(mockTransaction, destCollectionPath);

                oneOf(mockReq).getHeader("Depth");
                will(returnValue(null));

                String[] sourceChildren = {"sourceFile"};

                oneOf(mockStore).getChildrenNames(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceChildren));

                StoredObject sourceFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction,
                    sourceCollectionPath + "/sourceFile"
                );
                will(returnValue(sourceFileSo));

                oneOf(mockStore).createResource(mockTransaction,
                    destCollectionPath + "/sourceFile"
                );

                oneOf(mockStore).getResourceContent(mockTransaction,
                    sourceCollectionPath + "/sourceFile"
                );
                will(returnValue(bais));

                oneOf(mockStore).setResourceContent(mockTransaction,
                    destCollectionPath + "/sourceFile",
                    bais,
                    null,
                    null
                );
                will(returnValue(8L));

                StoredObject movedSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction,
                    destCollectionPath + "/sourceFile"
                );
                will(returnValue(movedSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceCollectionSo));

                sourceChildren = new String[]{"sourceFile"};

                oneOf(mockStore).getChildrenNames(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceChildren));

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                oneOf(mockStore).removeObject(mockTransaction, sourceFilePath);

                oneOf(mockStore).removeObject(mockTransaction, sourceCollectionPath);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);
        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);

        DoMove doMove = new DoMove(resLocks, doDelete, doCopy, false);

        doMove.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMovingOfaCollectionIfDestinationIsPresentAndOverwriteFalse() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceCollectionPath));

                exactly(2).of(mockReq).getHeader("Destination");
                will(returnValue(destCollectionPath));

                oneOf(mockReq).getServerName();
                will(returnValue("server_name"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(destCollectionPath));

                oneOf(mockReq).getServletPath();
                will(returnValue("servlet_path"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceCollectionPath));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("F"));

                StoredObject sourceCollectionSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceCollectionSo));

                StoredObject destCollectionSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, destCollectionPath);
                will(returnValue(destCollectionSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);
        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);

        DoMove doMove = new DoMove(resLocks, doDelete, doCopy, false);

        doMove.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testMovingOfaCollectionIfDestinationIsPresentAndOverwriteTrue() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceCollectionPath));

                exactly(2).of(mockReq).getHeader("Destination");
                will(returnValue(overwritePath));

                oneOf(mockReq).getServerName();
                will(returnValue("server_name"));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getPathInfo();
                will(returnValue(overwritePath));

                oneOf(mockReq).getServletPath();
                will(returnValue("servlet_path"));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(sourceCollectionPath));

                oneOf(mockReq).getHeader("Overwrite");
                will(returnValue("T"));

                StoredObject sourceCollectionSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceCollectionSo));

                StoredObject destCollectionSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, overwritePath);
                will(returnValue(destCollectionSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, overwritePath);
                will(returnValue(destCollectionSo));

                oneOf(mockStore).getChildrenNames(mockTransaction, overwritePath);
                will(returnValue(destChildren));

                StoredObject destFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, overwritePath + "/destFile");
                will(returnValue(destFileSo));

                oneOf(mockStore).removeObject(mockTransaction, overwritePath + "/destFile");

                oneOf(mockStore).removeObject(mockTransaction, overwritePath);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceCollectionSo));

                oneOf(mockStore).createFolder(mockTransaction, overwritePath);

                oneOf(mockReq).getHeader("Depth");
                will(returnValue(null));

                oneOf(mockStore).getChildrenNames(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceChildren));

                StoredObject sourceFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                oneOf(mockStore).createResource(mockTransaction, overwritePath + "/sourceFile");

                oneOf(mockStore).getResourceContent(mockTransaction, sourceFilePath);
                will(returnValue(bais));

                oneOf(mockStore).setResourceContent(mockTransaction,
                    overwritePath + "/sourceFile",
                    bais,
                    null,
                    null
                );

                StoredObject movedSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, overwritePath + "/sourceFile");
                will(returnValue(movedSo));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NO_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceCollectionSo));

                sourceChildren = new String[]{"sourceFile"};

                oneOf(mockStore).getChildrenNames(mockTransaction, sourceCollectionPath);
                will(returnValue(sourceChildren));

                oneOf(mockStore).getStoredObject(mockTransaction, sourceFilePath);
                will(returnValue(sourceFileSo));

                oneOf(mockStore).removeObject(mockTransaction, sourceFilePath);

                oneOf(mockStore).removeObject(mockTransaction, sourceCollectionPath);
            }
        });

        ResourceLocks resLocks = new ResourceLocks();
        DoDelete doDelete = new DoDelete(mockStore, resLocks, false);
        DoCopy doCopy = new DoCopy(mockStore, resLocks, doDelete, false);

        DoMove doMove = new DoMove(resLocks, doDelete, doCopy, false);

        doMove.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

}
