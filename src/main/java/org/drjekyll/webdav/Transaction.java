package org.drjekyll.webdav;

import java.security.Principal;

@FunctionalInterface
public interface Transaction {

    Principal getPrincipal();

}
