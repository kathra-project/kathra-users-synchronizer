/* 
 * Copyright 2019 The Kathra Authors.
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
 *
 *    IRT SystemX (https://www.kathra.org/)    
 *
 */

package org.kathra;

import org.kathra.binaryrepositorymanager.client.BinaryRepositoryManagerClient;
import org.kathra.core.model.User;
import org.kathra.pipelinemanager.client.PipelineManagerClient;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.KeyPairsClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.usermanager.client.UserManagerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeremy Guillemot <Jeremy.Guillemot@kathra.org>
 */
public class UserSynchronizer {

    public static void main(String[] args) throws Exception {
        Logger log = LoggerFactory.getLogger("UserSynchronizer");
        log.debug("Info init sync");
        Config config = new Config();
        User user = new User().name(config.getUsername()).password(config.getPassword());
        KeycloackSession session = new KeycloackSession(user);
        log.debug("Session received " + session.getAccessToken().toString());
        SourceManagerClient sourceManage = new SourceManagerClient(config.getSourceManagerUrl(), session);
        log.debug("Source manager client initiated");
        PipelineManagerClient pipelineManager = new PipelineManagerClient(config.getPipelineManagerUrl(), session);
        log.debug("Pipeline manager lient initiated");
        UserManagerClient userManager = new UserManagerClient(config.getUserManagerUrl(), session);
        log.debug("User manager client initiated");
        BinaryRepositoryManagerClient repositoryManager = new BinaryRepositoryManagerClient(
                config.getBinaryRepositoryManagerUrl(), session);
        log.debug("Respository Manager client initiated");
        GroupsClient groupsClient = new GroupsClient(config.getResourceManagerUrl(), session);
        log.debug("Groups client initiated");
        KeyPairsClient keyPairsClient = new KeyPairsClient(config.getResourceManagerUrl(), session);
        log.debug("Keys pair client initiated");
        UserSynchronizerManager userSynchronizer = new UserSynchronizerManager(sourceManage, pipelineManager,
                userManager, repositoryManager, groupsClient, keyPairsClient);
        log.debug("User synchronizer manager initiated");
        // userSynchronizer.initKathra();
        userSynchronizer.synchronizeGroups();
    }
}
