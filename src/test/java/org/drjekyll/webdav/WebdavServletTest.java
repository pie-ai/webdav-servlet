package org.drjekyll.webdav;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import org.drjekyll.webdav.store.WebdavStore;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;

public class WebdavServletTest {

    private static final byte[] RESOURCE_CONTENT = {
        '<', 'h', 'e', 'l', 'l', 'o', '/', '>'
    };

    private final String dftIndexFile = "/index.html";

    private final String insteadOf404 = "/insteadOf404";

    private final Mockery mockery = new Mockery();

    private ServletConfig servletConfig;

    private ServletContext servletContext;

    private WebdavStore mockStore;

    private MockServletConfig mockServletConfig;

    private MockServletContext mockServletContext;

    private MockHttpServletRequest mockReq;

    private MockHttpServletResponse mockRes;

    private MockHttpSession mockHttpSession;

    private MockPrincipal mockPrincipal;

    @AfterEach
    public void assertSatisfiedMockery() {
        mockery.assertIsSatisfied();
    }

    @BeforeEach
    public void setUp() {
        servletConfig = mockery.mock(ServletConfig.class);
        servletContext = mockery.mock(ServletContext.class);
        mockStore = mockery.mock(WebdavStore.class);

        mockServletConfig = new MockServletConfig(mockServletContext);
        mockHttpSession = new MockHttpSession(mockServletContext);
        mockServletContext = new MockServletContext();
        mockReq = new MockHttpServletRequest(mockServletContext);
        mockRes = new MockHttpServletResponse();

        mockPrincipal = new MockPrincipal("Admin", new String[]{
            "Admin", "Manager"
        });

    }

    @Test
    public void testInit() {

        mockery.checking(new Expectations() {
        });

        WebdavServlet servlet = new WebdavServlet();
        servlet.init(mockStore, dftIndexFile, insteadOf404, 1, true);

        mockery.assertIsSatisfied();
    }

    // Test successes in eclipse, but fails in "mvn test"
    // first three expectations aren't successful with "mvn test"
    @Test
    public void testInitGenericServlet() throws Exception {

        mockery.checking(new Expectations() {
            {
                allowing(servletConfig).getServletContext();
                will(returnValue(mockServletContext));

                allowing(servletConfig).getServletName();
                will(returnValue("webdav-servlet"));

                allowing(servletContext).log("webdav-servlet: init");

                oneOf(servletConfig).getInitParameter("ResourceHandlerImplementation");
                will(returnValue(""));

                oneOf(servletConfig).getInitParameter("rootpath");
                will(returnValue("./target/tmpTestData/"));

                exactly(2).of(servletConfig).getInitParameter("lazyFolderCreationOnPut");
                will(returnValue("1"));

                oneOf(servletConfig).getInitParameter("default-index-file");
                will(returnValue("index.html"));

                oneOf(servletConfig).getInitParameter("instead-of-404");
                will(returnValue(""));

                exactly(2).of(servletConfig).getInitParameter("no-content-length-headers");
                will(returnValue("0"));
            }
        });

        WebdavServlet servlet = new WebdavServlet();

        servlet.init(servletConfig);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testService() throws Exception {

        mockServletConfig.addInitParameter("ResourceHandlerImplementation", "");
        mockServletConfig.addInitParameter("rootpath", "./target/tmpTestData");
        mockServletConfig.addInitParameter("lazyFolderCreationOnPut", "1");
        mockServletConfig.addInitParameter("default-index-file", dftIndexFile);
        mockServletConfig.addInitParameter("instead-of-404", insteadOf404);
        mockServletConfig.addInitParameter("no-content-length-headers", "0");

        // StringTokenizer headers = new StringTokenizer(
        // "Host Depth Content-Type Content-Length");
        mockReq.setMethod("PUT");
        mockReq.setAttribute("javax.servlet.include.request_uri", null);
        mockReq.setPathInfo("/aPath/toAFile");
        mockReq.setRequestURI("/aPath/toAFile");
        mockReq.addHeader("Host", "www.foo.bar");
        mockReq.addHeader("Depth", "0");
        mockReq.addHeader("Content-Type", "text/xml");
        mockReq.addHeader("Content-Length", "1234");
        mockReq.addHeader("User-Agent", "...some Client with WebDAVFS...");

        mockReq.setSession(mockHttpSession);
        mockPrincipal = new MockPrincipal("Admin", new String[]{
            "Admin", "Manager"
        });
        mockReq.setUserPrincipal(mockPrincipal);
        mockReq.addUserRole("Admin");
        mockReq.addUserRole("Manager");

        mockReq.setContent(RESOURCE_CONTENT);

        mockery.checking(new Expectations() {
            {

            }
        });

        WebdavServlet servlet = new WebdavServlet();

        servlet.init(mockServletConfig);

        servlet.service(mockReq, mockRes);

        mockery.assertIsSatisfied();
    }
}
