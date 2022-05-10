package org.drjekyll.webdav.methods;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.drjekyll.webdav.Transaction;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DoNotImplementedTest {

    private final Mockery mockery = new Mockery();

    private HttpServletRequest mockReq;

    private HttpServletResponse mockRes;

    private Transaction mockTransaction;

    @AfterEach
    public void assertSatisfiedMockery() {
        mockery.assertIsSatisfied();
    }

    @BeforeEach
    public void setUp() {
        mockReq = mockery.mock(HttpServletRequest.class);
        mockRes = mockery.mock(HttpServletResponse.class);
        mockTransaction = mockery.mock(Transaction.class);
    }

    @Test
    public void testDoNotImplementedIfReadOnlyTrue() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getMethod();
                will(returnValue("notImplementedMethod"));
                oneOf(mockRes).sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        });

        DoNotImplemented doNotImplemented = new DoNotImplemented(true);
        doNotImplemented.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }

    @Test
    public void testDoNotImplementedIfReadOnlyFalse() throws Exception {

        mockery.checking(new Expectations() {
            {
                oneOf(mockReq).getMethod();
                will(returnValue("notImplementedMethod"));
                oneOf(mockRes).sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            }
        });

        DoNotImplemented doNotImplemented = new DoNotImplemented(false);
        doNotImplemented.execute(mockTransaction, mockReq, mockRes);

        mockery.assertIsSatisfied();
    }
}
