package org.drjekyll.webdav.methods;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MethodTest {

    private static final Instant INSTANT = Instant.ofEpochMilli(123456789L);

    @Test
    void appliesLastModifiedDateFormat() {

        String lastModifiedDate = Method.lastModifiedDateFormat(INSTANT);

        assertThat(lastModifiedDate).isEqualTo("Fri, 02 Jan 1970 10:17:36 GMT");

    }

    @Test
    void appliesCreationDateFormat() {

        String creationDate = Method.creationDateFormat(INSTANT);

        assertThat(creationDate).isEqualTo("1970-01-02T10:17:36Z");

    }

}
