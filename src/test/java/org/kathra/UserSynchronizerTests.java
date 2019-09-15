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

import org.kathra.sourcemanager.client.SourceManagerClient;

import java.util.ArrayList;
import java.util.List;

import org.kathra.binaryrepositorymanager.client.BinaryRepositoryManagerClient;
import org.kathra.core.model.Assignation;
import org.kathra.core.model.Group;
import org.kathra.core.model.KeyPair;
import org.kathra.core.model.Resource.StatusEnum;
import org.kathra.pipelinemanager.client.PipelineManagerClient;
import org.kathra.pipelinemanager.model.Credential;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.KeyPairsClient;
import org.kathra.usermanager.client.UserManagerClient;
import org.kathra.utils.ApiException;

import org.mockito.ArgumentMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserSynchronizerTests {

    Config config;
    KeycloackSession keycloackSession;
    SourceManagerClient sourceManager;
    PipelineManagerClient pipelineManager;
    UserManagerClient userManager;
    BinaryRepositoryManagerClient repositoryManager;
    GroupsClient groupsClient;
    KeyPairsClient keyPairsClient;

    UserSynchronizerManager userSynchronizerManager;

    List<Group> groupsFromUserManager;
    List<Group> groupsFromResourceManager;
    List<KeyPair> keyPairs;

    Logger logger;

    protected void setUp(String className) {
        config = mock(Config.class);
        keycloackSession = mock(KeycloackSession.class);
        sourceManager = mock(SourceManagerClient.class);
        pipelineManager = mock(PipelineManagerClient.class);
        userManager = mock(UserManagerClient.class);
        repositoryManager = mock(BinaryRepositoryManagerClient.class);
        groupsClient = mock(GroupsClient.class);
        keyPairsClient = mock(KeyPairsClient.class);

        logger = LoggerFactory.getLogger(className);
    }

    protected void tearDown() throws ApiException {
        if (groupsFromUserManager != null)
            groupsFromUserManager.clear();
        if (groupsFromResourceManager != null)
            groupsFromResourceManager.clear();
        if (keyPairs != null)
            keyPairs.clear();
    }

    void init_user_sync_manager() throws ApiException {
        userSynchronizerManager = new UserSynchronizerManager(sourceManager, pipelineManager, userManager,
                repositoryManager, groupsClient, keyPairsClient);
    }

    protected void given_groups_from_user_manager(int... groups) throws ApiException {
        groupsFromUserManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setId(Integer.toString(i));
            g.setPath("/kathra-projects/path" + i);
            groupsFromUserManager.add(g);
        }
        when(userManager.getGroups()).thenReturn(groupsFromUserManager);
    }

    protected void given_pending_groups_from_resource_manager(int... groups) throws ApiException {
        groupsFromResourceManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setPath("/kathra-projects/path" + i);
            g.setId(Integer.toString(i));
            g.status(StatusEnum.PENDING);
            /* do not execute the rest of the process */
            g.setBinaryRepositoryStatus(Group.BinaryRepositoryStatusEnum.READY);

            groupsFromResourceManager.add(g);
        }
        when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
    }

    protected void given_groups_have_key_pairs(int... groups) throws ApiException {
        keyPairs = new ArrayList<KeyPair>();
        for (int i : groups) {
            KeyPair newkey = new KeyPair();
            newkey.id(Integer.toString(i));
            newkey.group(groupsFromUserManager.get(i));
            newkey.privateKey("privateKeyFor" + Integer.toString(i));
            newkey.publicKey("publicKeyFor" + Integer.toString(i));
            keyPairs.add(newkey);
        }
        when(keyPairsClient.getKeyPairs()).thenReturn(keyPairs);
    }

    private class CredentialPathMatcher implements ArgumentMatcher<Credential> {

        private String[] credential_paths;

        public CredentialPathMatcher(String... paths) {
            logger.debug("Creating match class " + paths);
            credential_paths = paths;
        }

        @Override
        public boolean matches(Credential argument) {
            logger.debug("Matcher for " + credential_paths);
            logger.debug(argument == null ? "Argument null" : argument.toString());
            logger.debug(argument == null ? "Argument null" : argument.getPath());
            for (int i = 0; i < credential_paths.length; i++)
                if (argument.getPath().equalsIgnoreCase(credential_paths[i]))
                    return true;
            return false;
        }
    }
}