/*
 * ====================================================================
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.drjekyll.webdav.store;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class StoredObject {

    private boolean folder;

    private Instant lastModified;

    private Instant creationDate;

    private long resourceLength;

    private String mimeType;

    private boolean isNullRessource;

    /**
     * Determines whether the StoredObject is a folder or a resource
     *
     * @return true if the StoredObject is a resource
     */
    public boolean isResource() {
        return !folder;
    }

    /**
     * Gets the state of the resource
     *
     * @return true if the resource is in lock-null state
     */
    public boolean isNullResource() {
        return isNullRessource;
    }

    /**
     * Sets a StoredObject as a lock-null resource
     *
     * @param f true to set the resource as lock-null resource
     */
    public void setNullResource(boolean f) {
        isNullRessource = f;
        folder = false;
        creationDate = null;
        lastModified = null;
        resourceLength = 0;
        mimeType = null;
    }

}
