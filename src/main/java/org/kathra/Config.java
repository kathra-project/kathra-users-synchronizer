/*
 * Copyright (c) 2020. The Kathra Authors.
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
 * Contributors:
 *    IRT SystemX (https://www.kathra.org/)
 *
 */

package org.kathra;

import org.kathra.utils.ConfigManager;

public class Config extends ConfigManager {

    private String SOURCE_MANAGER_URL;
    private String PIPELINE_MANAGER_URL;
    private String USER_MANAGER_URL;
    private String RESOURCE_MANAGER_URL;
    private String BINARY_REPOSITORY_MANAGER_URL_NEXUS;
    private String BINARY_REPOSITORY_MANAGER_URL_HARBOR;
    private String USERNAME;
    private String PASSWORD;


    public Config() {
        SOURCE_MANAGER_URL = getProperty("SOURCE_MANAGER_URL");
        if (!SOURCE_MANAGER_URL.startsWith("http"))
            SOURCE_MANAGER_URL = "http://" + SOURCE_MANAGER_URL;

        PIPELINE_MANAGER_URL = getProperty("PIPELINE_MANAGER_URL");
        if (!PIPELINE_MANAGER_URL.startsWith("http"))
            PIPELINE_MANAGER_URL = "http://" + PIPELINE_MANAGER_URL;

        USER_MANAGER_URL = getProperty("USER_MANAGER_URL");
        if (!USER_MANAGER_URL.startsWith("http"))
            USER_MANAGER_URL = "http://" + USER_MANAGER_URL;

        RESOURCE_MANAGER_URL = getProperty("RESOURCE_MANAGER_URL");
        if (!RESOURCE_MANAGER_URL.startsWith("http"))
            RESOURCE_MANAGER_URL = "http://" + RESOURCE_MANAGER_URL;

        BINARY_REPOSITORY_MANAGER_URL_NEXUS = getProperty("BINARY_REPOSITORY_MANAGER_URL_NEXUS");
        if (!BINARY_REPOSITORY_MANAGER_URL_NEXUS.startsWith("http"))
            BINARY_REPOSITORY_MANAGER_URL_NEXUS = "http://" + BINARY_REPOSITORY_MANAGER_URL_NEXUS;

        BINARY_REPOSITORY_MANAGER_URL_HARBOR = getProperty("BINARY_REPOSITORY_MANAGER_URL_HARBOR");
        if (!BINARY_REPOSITORY_MANAGER_URL_HARBOR.startsWith("http"))
            BINARY_REPOSITORY_MANAGER_URL_HARBOR = "http://" + BINARY_REPOSITORY_MANAGER_URL_HARBOR;

        USERNAME = getProperty("USERNAME");
        PASSWORD = getProperty("PASSWORD");
    }

    public String getSourceManagerUrl() {
        return SOURCE_MANAGER_URL;
    }

    public String getPipelineManagerUrl() {
        return PIPELINE_MANAGER_URL;
    }

    public String getUserManagerUrl() {
        return USER_MANAGER_URL;
    }

    public String getResourceManagerUrl() {
        return RESOURCE_MANAGER_URL;
    }

    public String getBinaryRepositoryManagerUrlHarbor() {
        return BINARY_REPOSITORY_MANAGER_URL_HARBOR;
    }
    public String getBinaryRepositoryManagerUrlNexus() {
        return BINARY_REPOSITORY_MANAGER_URL_NEXUS;
    }

    public String getUsername() {
        return USERNAME;
    }

    public String getPassword() {
        return PASSWORD;
    }
}
