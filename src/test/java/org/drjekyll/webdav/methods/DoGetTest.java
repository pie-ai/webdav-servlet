package org.drjekyll.webdav.methods;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.drjekyll.webdav.MimeTyper;
import org.drjekyll.webdav.StoreObjectTestUtil;
import org.drjekyll.webdav.TestingOutputStream;
import org.drjekyll.webdav.Transaction;
import org.drjekyll.webdav.locking.ResourceLocks;
import org.drjekyll.webdav.store.StoredObject;
import org.drjekyll.webdav.store.WebdavStore;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DoGetTest {

    private static final byte[] RESOURCE_CONTENT = {
        '<', 'h', 'e', 'l', 'l', 'o', '/', '>'
    };

    private final Mockery mockery = new Mockery();

    private TestingOutputStream tos = new TestingOutputStream();

    private ByteArrayInputStream bais = new ByteArrayInputStream(RESOURCE_CONTENT);

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
    public void testAccessOfaMissingPageResultsIn404() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue("/index.html"));

                StoredObject indexSo = null;

                exactly(2).of(mockStore).getStoredObject(mockTransaction, "/index.html");
                will(returnValue(indexSo));

                oneOf(mockReq).getRequestURI();
                will(returnValue("/index.html"));

                oneOf(mockRes).sendError(HttpServletResponse.SC_NOT_FOUND, "/index.html");

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        });

        DoGet doGet = new DoGet(mockStore, null, null, new ResourceLocks(), mockMimeTyper, 0);

        doGet.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testAccessOfaPageResultsInPage() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue("/index.html"));

                StoredObject indexSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, "/index.html");
                will(returnValue(indexSo));

                oneOf(mockReq).getHeader("If-None-Match");
                will(returnValue(null));

                oneOf(mockRes).setDateHeader("last-modified", indexSo.getLastModified().toEpochMilli());

                oneOf(mockRes).addHeader(with(any(String.class)), with(any(String.class)));

                oneOf(mockMimeTyper).getMimeType(with(any(Transaction.class)),
                    with(equal("/index.html"))
                );
                will(returnValue("text/foo"));

                oneOf(mockRes).setContentType("text/foo");

                StoredObject so = StoreObjectTestUtil.initStoredObject(false, RESOURCE_CONTENT);

                oneOf(mockStore).getStoredObject(mockTransaction, "/index.html");
                will(returnValue(so));

                oneOf(mockRes).getOutputStream();
                will(returnValue(tos));

                oneOf(mockStore).getResourceContent(mockTransaction, "/index.html");
                will(returnValue(bais));
            }
        });

        DoGet doGet = new DoGet(mockStore, null, null, new ResourceLocks(), mockMimeTyper, 0);

        doGet.execute(mockTransaction, mockReq, mockRes);

        assertThat(tos.toString()).isEqualTo("<hello/>");

        mockery.assertIsSatisfied();
    }

    @Test
    public void testAccessOfaDirectoryResultsInRudimentaryChildList() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue("/foo/"));

                StoredObject fooSo = StoreObjectTestUtil.initStoredObject(true, null);
                StoredObject aaa = StoreObjectTestUtil.initStoredObject(true, null);
                StoredObject bbb = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, "/foo/");
                will(returnValue(fooSo));

                oneOf(mockReq).getHeader("If-None-Match");
                will(returnValue(null));

                oneOf(mockStore).getStoredObject(mockTransaction, "/foo/");
                will(returnValue(fooSo));

                oneOf(mockReq).getLocale();
                will(returnValue(Locale.GERMAN));

                oneOf(mockRes).setContentType("text/html");
                oneOf(mockRes).setCharacterEncoding("UTF8");

                tos = new TestingOutputStream();

                oneOf(mockRes).getOutputStream();
                will(returnValue(tos));

                oneOf(mockStore).getChildrenNames(mockTransaction, "/foo/");
                will(returnValue(new String[]{"AAA", "BBB"}));

                oneOf(mockStore).getStoredObject(mockTransaction, "/foo//AAA");
                will(returnValue(aaa));

                oneOf(mockStore).getStoredObject(mockTransaction, "/foo//BBB");
                will(returnValue(bbb));

            }
        });

        DoGet doGet = new DoGet(mockStore, null, null, new ResourceLocks(), mockMimeTyper, 0);

        doGet.execute(mockTransaction, mockReq, mockRes);

        assertThat(tos.toString().length() > 0).isTrue();

        mockery.assertIsSatisfied();
    }

    @Test
    public void testAccessOfaDirectoryResultsInRedirectIfDefaultIndexFilePresent()
        throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue("/foo/"));

                StoredObject fooSo = StoreObjectTestUtil.initStoredObject(true, null);

                oneOf(mockStore).getStoredObject(mockTransaction, "/foo/");
                will(returnValue(fooSo));

                oneOf(mockReq).getRequestURI();
                will(returnValue("/foo/"));

                oneOf(mockRes).encodeRedirectURL("/foo//indexFile");

                oneOf(mockRes).sendRedirect("");
            }
        });

        DoGet doGet = new DoGet(mockStore,
            "/indexFile",
            null,
            new ResourceLocks(),
            mockMimeTyper,
            0
        );

        doGet.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testAccessOfaMissingPageResultsInPossibleAlternatveTo404() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getAttribute("javax.servlet.include.request_uri");
                will(returnValue(null));

                oneOf(mockReq).getPathInfo();
                will(returnValue("/index.html"));

                StoredObject indexSo = null;

                oneOf(mockStore).getStoredObject(mockTransaction, "/index.html");
                will(returnValue(indexSo));

                StoredObject alternativeSo = StoreObjectTestUtil.initStoredObject(false,
                    RESOURCE_CONTENT
                );

                oneOf(mockStore).getStoredObject(mockTransaction, "/alternative");
                will(returnValue(alternativeSo));

                oneOf(mockReq).getHeader("If-None-Match");
                will(returnValue(null));

                oneOf(mockRes).setDateHeader("last-modified", alternativeSo.getLastModified().toEpochMilli()
                );

                oneOf(mockRes).addHeader(with(any(String.class)), with(any(String.class)));

                oneOf(mockMimeTyper).getMimeType(with(any(Transaction.class)),
                    with(equal("/alternative"))
                );
                will(returnValue("text/foo"));

                oneOf(mockRes).setContentType("text/foo");

                oneOf(mockStore).getStoredObject(mockTransaction, "/alternative");
                will(returnValue(alternativeSo));

                oneOf(mockRes).getOutputStream();
                will(returnValue(tos));

                oneOf(mockStore).getResourceContent(mockTransaction, "/alternative");
                will(returnValue(bais));

                oneOf(mockRes).setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        });

        DoGet doGet = new DoGet(mockStore,
            null,
            "/alternative",
            new ResourceLocks(),
            mockMimeTyper,
            0
        );

        doGet.execute(mockTransaction, mockReq, mockRes);

        assertThat(tos.toString()).isEqualTo("<hello/>");

        mockery.assertIsSatisfied();
    }

}
