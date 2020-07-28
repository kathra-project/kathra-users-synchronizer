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

import org.kathra.binaryrepositorymanager.client.BinaryRepositoryManagerClient;
import org.kathra.core.model.*;
import org.kathra.core.model.Resource.StatusEnum;
import org.kathra.pipelinemanager.client.PipelineManagerClient;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.KeyPairsClient;
import org.kathra.resourcemanager.client.UsersClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.usermanager.client.UserManagerClient;
import org.kathra.utils.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

public class UserSynchronizerTests {

    Config config;
    KeycloackSession keycloackSession;
    SourceManagerClient sourceManager;
    PipelineManagerClient pipelineManager;
    UserManagerClient userManager;
    BinaryRepositoryManagerClient repositoryManagerNexus;
    BinaryRepositoryManagerClient repositoryManagerHarbor;
    GroupsClient groupsClient;
    UsersClient usersClient;
    KeyPairsClient keyPairsClient;

    UserSynchronizerManager userSynchronizerManager;

    List<Group> groupsFromUserManager = new ArrayList<>();
    List<Group> groupsFromResourceManager = new ArrayList<>();
    List<User> usersFromUserManager = new ArrayList<>();
    List<User> usersFromResourceManager = new ArrayList<>();
    List<KeyPair> keyPairs;

    Logger logger;
    SyncTechnicalUser syncUserTechnical;
    SyncBinaryRepository syncBinaryRepository;

    protected void setUp(String className) {
        config = mock(Config.class);
        keycloackSession = mock(KeycloackSession.class);
        sourceManager = mock(SourceManagerClient.class);
        pipelineManager = mock(PipelineManagerClient.class);
        userManager = mock(UserManagerClient.class);
        repositoryManagerNexus = mock(BinaryRepositoryManagerClient.class);
        repositoryManagerHarbor = mock(BinaryRepositoryManagerClient.class);
        syncBinaryRepository = mock(SyncBinaryRepository.class);
        syncUserTechnical = mock(SyncTechnicalUser.class);
        groupsClient = mock(GroupsClient.class);
        usersClient = mock(UsersClient.class);
        keyPairsClient = mock(KeyPairsClient.class);

        logger = LoggerFactory.getLogger(className);
        tearDown();
        mockAddGroup();
        mockAddUser();
        mockKeyPairClient();
    }

    private void mockKeyPairClient() {
        try {
            doAnswer(invocation -> {
                KeyPair keyPair = invocation.getArgument(0);
                if (keyPair == null) {
                    throw new IllegalArgumentException();
                }
                keyPair.id(UUID.randomUUID().toString());
                when(keyPairsClient.getKeyPair(keyPair.getId())).thenReturn(keyPair);
                return keyPair;
            }).when(keyPairsClient).addKeyPair(any());
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    void mockAddGroup() {
        try {
            doAnswer(invocation -> {
                Group group = invocation.getArgument(0);
                if (group == null) {
                    throw new IllegalArgumentException();
                }
                group.id(UUID.randomUUID().toString());
                group.status(StatusEnum.PENDING);
                when(groupsClient.getGroup(group.getId())).thenReturn(group);
                return group;
            }).when(groupsClient).addGroup(any());
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    private void mockAddUser() {
        try {
            doAnswer(invocation -> {
                User user = invocation.getArgument(0);
                if (user == null) {
                    throw new IllegalArgumentException();
                }
                user.id(UUID.randomUUID().toString());
                user.status(StatusEnum.PENDING);
                when(usersClient.getUser(user.getId())).thenReturn(user);
                return user;
            }).when(usersClient).addUser(any());
        } catch (ApiException e) {

        }
    }
    void mockBinaryRepositoryHarbor() {
        try {
            doAnswer(invocation -> {
                BinaryRepository binaryRepository = invocation.getArgument(0);
                if (binaryRepository == null) {
                    throw new IllegalArgumentException();
                }
                binaryRepository.id(UUID.randomUUID().toString()).url("url").providerId("provider").providerId("providerId");
                when(repositoryManagerHarbor.getBinaryRepository(binaryRepository.getId())).thenReturn(binaryRepository);
                return binaryRepository;
            }).when(repositoryManagerHarbor).addBinaryRepository(any());
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }
    void mockBinaryRepositoryNexus() {
        try {
            doAnswer(invocation -> {
                BinaryRepository binaryRepository = invocation.getArgument(0);
                if (binaryRepository == null) {
                    throw new IllegalArgumentException();
                }
                binaryRepository.id(UUID.randomUUID().toString()).url("url").providerId("provider").providerId("providerId");
                when(repositoryManagerNexus.getBinaryRepository(binaryRepository.getId())).thenReturn(binaryRepository);
                return binaryRepository;
            }).when(repositoryManagerNexus).addBinaryRepository(any());
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    protected void tearDown() {
        if (groupsFromUserManager != null)
            groupsFromUserManager.clear();
        if (groupsFromResourceManager != null)
            groupsFromResourceManager.clear();
        if (usersFromUserManager != null)
            usersFromUserManager.clear();
        if (usersFromResourceManager != null)
            usersFromResourceManager.clear();
        if (keyPairs != null)
            keyPairs.clear();
    }

    void init_user_sync_manager() throws ApiException {
        userSynchronizerManager = new UserSynchronizerManager(sourceManager, pipelineManager, userManager,
                groupsClient, keyPairsClient, syncBinaryRepository, syncUserTechnical);
    }

    protected void given_groups_from_user_manager(int... groups) throws ApiException {
        groupsFromUserManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setName("path" + i);
            g.setPath("/kathra-projects/path" + i);
            groupsFromUserManager.add(g);
            when(userManager.getGroup(g.getPath())).thenReturn(g);
        }
        when(userManager.getGroups()).thenReturn(groupsFromUserManager);
    }

    protected void given_pending_groups_from_resource_manager(int... groups) throws ApiException {
        groupsFromResourceManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setName("path" + i);
            g.setPath("/kathra-projects/path" + i);
            g.setId(Integer.toString(i));
            g.status(StatusEnum.PENDING);
            /* do not execute the rest of the process */
            g.setBinaryRepositoryStatus(Group.BinaryRepositoryStatusEnum.READY);
            when(groupsClient.getGroup(g.getId())).thenReturn(g);
            groupsFromResourceManager.add(g);
        }
        when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
    }

    protected void given_groups_have_key_pairs(int... groups) throws ApiException {
        keyPairs = new ArrayList<>();
        for (int i : groups) {
            KeyPair newkey = new KeyPair();
            newkey.id(Integer.toString(i));
            newkey.group(new Group().id(i+""));
            newkey.privateKey("privateKeyFor" + i);
            newkey.publicKey("publicKeyFor" + i);
            keyPairs.add(newkey);
            when(keyPairsClient.getKeyPair(newkey.getId())).thenReturn(newkey);
        }
        when(keyPairsClient.getKeyPairs()).thenReturn(keyPairs);
    }

}