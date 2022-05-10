package org.drjekyll.webdav.methods;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.drjekyll.webdav.MethodExecutor;
import org.drjekyll.webdav.Transaction;

@Slf4j
@RequiredArgsConstructor
public class DoNotImplemented implements MethodExecutor {

    private final boolean readOnly;

    @Override
    public void execute(
        Transaction transaction, HttpServletRequest req, HttpServletResponse resp
    ) throws IOException {
        log.trace("-- {}", req.getMethod());

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }
}
