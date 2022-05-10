package org.drjekyll.webdav.prop;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
import org.springframework.mock.web.DelegatingServletInputStream;

public class DoProppatchTest {


    private static final byte[] RESOURCE_CONTENT = {
        '<', 'h', 'e', 'l', 'l', 'o', '/', '>'
    };

    private final Mockery mockery = new Mockery();

    private ByteArrayInputStream bais = new ByteArrayInputStream(RESOURCE_CONTENT);

    private DelegatingServletInputStream dsis = new DelegatingServletInputStream(bais);

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
    public void doProppatchIfReadOnly() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        });

        DoProppatch doProppatch = new DoProppatch(mockStore, new ResourceLocks(), true);

        doProppatch.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void doProppatchOnNonExistingResource() throws Exception {

        final String path = "/notExists";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                StoredObject notExistingSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(notExistingSo));

                oneOf(mockRes).sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        });

        DoProppatch doProppatch = new DoProppatch(mockStore, new ResourceLocks(), false);

        doProppatch.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void doProppatchOnRequestWithNoContent() throws Exception {

        final String path = "/testFile";

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                StoredObject testFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(testFileSo));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getContentLength();
                will(returnValue(0));

                oneOf(mockRes).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

                oneOf(mockReq).getHeader("If");
                will(returnValue(null));

            }
        });

        DoProppatch doProppatch = new DoProppatch(mockStore, new ResourceLocks(), false);

        doProppatch.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void doProppatchOnResource() throws Exception {

        final String path = "/testFile";
        final PrintWriter pw = new PrintWriter("/tmp/XMLTestFile");

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                StoredObject testFileSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, path);
                will(returnValue(testFileSo));

                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue(path));

                oneOf(mockReq).getContentLength();
                will(returnValue(8));

                oneOf(mockReq).getInputStream();
                will(returnValue(dsis));

                oneOf(mockRes).setStatus(WebdavStatus.SC_MULTI_STATUS);

                oneOf(mockRes).setContentType("text/xml; charset=UTF-8");

                oneOf(mockRes).getWriter();
                will(returnValue(pw));

                oneOf(mockReq).getContextPath();
                will(returnValue(""));

                oneOf(mockReq).getHeader("If");
                will(returnValue(null));

            }
        });

        DoProppatch doProppatch = new DoProppatch(mockStore, new ResourceLocks(), false);

        doProppatch.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

}
