package org.drjekyll.webdav.prop;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.drjekyll.webdav.MimeTyper;
import org.drjekyll.webdav.StoreObjectTestUtil;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.WebdavStatus;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DoPropfindTest {


    private static final byte[] RESOURCE_CONTENT = {
        '<', 'h', 'e', 'l', 'l', 'o', '/', '>'
    };

    private final Mockery mockery = new Mockery();

    private WebdavStore mockStore;

    private MimeTyper mockMimeTyper;

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
        mockMimeTyper = mockery.mock(MimeTyper.class);
        mockReq = mockery.mock(HttpServletRequest.class);
        mockRes = mockery.mock(HttpServletResponse.class);
        mockTransaction = mockery.mock(Transaction.class);
    }

    @Test
    public void doPropFindOnDirectory() throws Exception {
        final String path = "/";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("Depth");
                will(returnValue("infinity"));

                StoredObject rootSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(rootSo));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getContentLength();
                will(returnValue(0));
                // no content, which means it is a allprop request

                oneOf(mockRes).setStatus(WebdavStatus.SC_MULTI_STATUS);

                oneOf(mockRes).setContentType("text/xml; charset=UTF-8");

                oneOf(mockRes).getWriter();
                will(returnValue(null));

                oneOf(mockMimeTyper).getMimeType(with(any(Transaction.class)), with(equal(path)));
                will(returnValue("text/xml; charset=UTF-8"));

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(rootSo));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getServletPath();
                will(returnValue(path));

                oneOf(mockStore).getChildrenNames(mockTransaction, path);
                will(returnValue(new String[]{"file1", "file2"}));

                StoredObject file1So = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, path + "file1");
                will(returnValue(file1So));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getServletPath();
                will(returnValue(path));

                oneOf(mockStore).getChildrenNames(mockTransaction, path + "file1");
                will(returnValue(new String[]{}));

                StoredObject file2So = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, path + "file2");
                will(returnValue(file2So));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getServletPath();
                will(returnValue(path));

                oneOf(mockStore).getChildrenNames(mockTransaction, path + "file2");
                will(returnValue(new String[]{}));
            }
        });

        DoPropfind doPropfind = new DoPropfind(mockStore, new ResourceLocks(), mockMimeTyper);
        doPropfind.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void doPropFindOnFile() throws Exception {
        final String path = "/testFile";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("Depth");
                will(returnValue("0"));

                StoredObject fileSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(fileSo));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getContentLength();
                will(returnValue(0));
                // no content, which means it is a allprop request

                oneOf(mockRes).setStatus(WebdavStatus.SC_MULTI_STATUS);

                oneOf(mockRes).setContentType("text/xml; charset=UTF-8");

                oneOf(mockRes).getWriter();
                will(returnValue(null));

                oneOf(mockMimeTyper).getMimeType(with(any(Transaction.class)), with(equal(path)));
                will(returnValue("text/xml; charset=UTF-8"));

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(fileSo));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getServletPath();
                will(returnValue("/"));
            }
        });

        DoPropfind doPropfind = new DoPropfind(mockStore, new ResourceLocks(), mockMimeTyper);

        doPropfind.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void doPropFindOnNonExistingResource() throws Exception {
        final String path = "/notExists";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getHeader("Depth");
                will(returnValue("0"));

                StoredObject notExistingSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(notExistingSo));

                oneOf(mockRes).setContentType("text/xml; charset=UTF-8");

                oneOf(mockReq).getRequestURI();
                will(returnValue(path));

                oneOf(mockRes).sendError(HttpServletResponse.SC_NOT_FOUND, path);
            }
        });

        DoPropfind doPropfind = new DoPropfind(mockStore, new ResourceLocks(), mockMimeTyper);

        doPropfind.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

}
