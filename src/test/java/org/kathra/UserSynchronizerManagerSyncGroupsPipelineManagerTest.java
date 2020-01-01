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

import org.junit.Test;
import org.kathra.binaryrepositorymanager.client.BinaryRepositoryManagerClient;
import org.kathra.core.model.*;
import org.kathra.core.model.Group.PipelineFolderStatusEnum;
import org.kathra.core.model.Resource.StatusEnum;
import org.kathra.pipelinemanager.client.PipelineManagerClient;
import org.kathra.pipelinemanager.model.Credential;
import org.kathra.resourcemanager.client.BinaryRepositoriesClient;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.KeyPairsClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.usermanager.client.UserManagerClient;
import org.kathra.utils.ApiException;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author Jorge Sainz Raso <jorge.sainzraso@kathra.org>
 */

public class UserSynchronizerManagerSyncGroupsPipelineManagerTest {

    Config config;
    KeycloackSession keycloackSession;
    SourceManagerClient sourceManager;
    PipelineManagerClient pipelineManager;
    UserManagerClient userManager;
    BinaryRepositoryManagerClient repositoryManagerNexus;
    BinaryRepositoryManagerClient repositoryManagerHarbor;
    BinaryRepositoriesClient binaryRepositoriesClient;
    GroupsClient groupsClient;
    KeyPairsClient keyPairsClient;

    UserSynchronizerManager userSynchronizerManager;

    List<Group> groupsFromUserManager;
    List<Group> groupsFromResourceManager;
    List<KeyPair> keyPairs;

    public static final Logger logger = LoggerFactory.getLogger("UserSynchronizerManagerSyncGroupsTest");

    private void setUp() throws ApiException {
        config = mock(Config.class);
        keycloackSession = mock(KeycloackSession.class);
        sourceManager = mock(SourceManagerClient.class);
        pipelineManager = mock(PipelineManagerClient.class);
        userManager = mock(UserManagerClient.class);
        repositoryManagerNexus = mock(BinaryRepositoryManagerClient.class);
        repositoryManagerHarbor = mock(BinaryRepositoryManagerClient.class);
        binaryRepositoriesClient = mock(BinaryRepositoriesClient.class);
        groupsClient = mock(GroupsClient.class);
        keyPairsClient = mock(KeyPairsClient.class);
    }

    private void init_user_sync_manager() throws ApiException {
        userSynchronizerManager = new UserSynchronizerManager(sourceManager, pipelineManager, userManager,
                repositoryManagerNexus, repositoryManagerHarbor, groupsClient, keyPairsClient, binaryRepositoriesClient);
    }

    private void tearDown() throws ApiException {
        if (groupsFromUserManager != null)
            groupsFromUserManager.clear();
        if (groupsFromResourceManager != null)
            groupsFromResourceManager.clear();
        if (keyPairs != null)
            keyPairs.clear();
    }

    private void given_groups_from_user_manager(int... groups) throws ApiException {
        groupsFromUserManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setId(Integer.toString(i));
            g.setPath("path" + i);
            g.setMembers(new ArrayList<Assignation>());
            groupsFromUserManager.add(g);
        }
        when(userManager.getGroups()).thenReturn(groupsFromUserManager);
    }

    private void given_pending_groups_from_resource_manager(int... groups) throws ApiException {
        groupsFromResourceManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setPath("path" + i);
            g.setId(Integer.toString(i));
            g.status(StatusEnum.PENDING);
            /* do not execute the rest of the process */
            g.setBinaryRepositoryStatus(Group.BinaryRepositoryStatusEnum.READY);

            groupsFromResourceManager.add(g);
        }
        when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
    }

    private class CredentialPathMatcher implements ArgumentMatcher<Credential> {

        private String[] credential_paths;

        public CredentialPathMatcher(String... paths) {
            logger.info("Creating match class " + paths);
            credential_paths = paths;
        }

        @Override
        public boolean matches(Credential argument) {
            logger.info("Matcher for " + credential_paths);
            logger.info(argument == null ? "Argument null" : argument.toString());
            logger.info(argument == null ? "Argument null" : argument.getPath());
            for (int i = 0; i < credential_paths.length; i++)
                if (argument.getPath().equalsIgnoreCase(credential_paths[i]))
                    return true;
            return false;
        }
    }

    private class AnswerWithParameter<T> implements Answer<T> {
        public T processReturnObject(T object) {
            return object;
        }

        @Override
        public T answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            logger.info("Returning answer");
            logger.info(args[0] == null ? "NULL" : args[0].toString());
            return processReturnObject((T) args[0]);
        }
    }

    private class AnswerPendingGroup extends AnswerWithParameter<Group> {
        public Group processReturnObject(Group object) {
            object.setStatus(Resource.StatusEnum.PENDING);
            /* do not execute the rest of the process */
            object.setBinaryRepositoryStatus(Group.BinaryRepositoryStatusEnum.READY);
            return object;
        }
    }

    private class AnswerReadyGroup extends AnswerWithParameter<Group> {
        public Group processReturnObject(Group object) {
            object.setStatus(Resource.StatusEnum.READY);
            /* do not execute the rest of the process */
            object.setBinaryRepositoryStatus(Group.BinaryRepositoryStatusEnum.READY);
            return object;
        }
    }

    @Test
    public void create_group_on_resource_manager_if_exists_on_user_manager_but_not_on_resources()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        init_user_sync_manager();

        given_groups_from_user_manager(0, 1, 2, 3, 4, 5);
        given_pending_groups_from_resource_manager(0, 2, 4);
        when(keyPairsClient.addKeyPair(any())).then(new AnswerWithParameter<KeyPair>());
        when(groupsClient.addGroup(any())).then(new AnswerReadyGroup());

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, times(1)).addGroup(argThat(group -> group.getPath().equals("path1")));
        verify(groupsClient, times(1)).addGroup(argThat(group -> group.getPath().equals("path3")));
        verify(groupsClient, times(1)).addGroup(argThat(group -> group.getPath().equals("path5")));

        tearDown();
    }

    @Test
    public void do_not_add_group_if_group_is_on_user_manager_but_not_in_resource_manager()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        init_user_sync_manager();
        given_groups_from_user_manager(0, 2, 4);
        given_pending_groups_from_resource_manager(0, 1, 2, 3, 4, 5);
        when(groupsClient.addGroup(any())).then(new AnswerWithParameter<Group>());
        when(keyPairsClient.addKeyPair(any())).then(new AnswerWithParameter<KeyPair>());

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, never()).addGroup(any());

        tearDown();
    }

    private void given_groups_have_key_pairs(int... groups) throws ApiException {
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

    private boolean isNotEmptyString(String string) {
        boolean result = string != null && !string.isEmpty();
        logger.info("RESULT " + result);
        return result;
    }

    @Test
    public void generates_and_stores_new_ssh_pair_keys_if_group_doesnt_have_keys()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_from_resource_manager(0, 2);
        given_groups_have_key_pairs(0, 2);
        init_user_sync_manager();

        when(groupsClient.addGroup(any())).then(new AnswerPendingGroup());
        when(keyPairsClient.addKeyPair(any())).then(new AnswerWithParameter<KeyPair>());

        userSynchronizerManager.synchronizeGroups();

        verify(keyPairsClient, times(0)).addKeyPair(argThat(keypair -> keypair.getGroup().getId().equals("0")));
        verify(keyPairsClient, times(1)).addKeyPair(argThat(keypair -> keypair.getGroup().getId().equals("1")
                && isNotEmptyString(keypair.getPrivateKey()) && isNotEmptyString(keypair.getPublicKey())));
        verify(keyPairsClient, times(0)).addKeyPair(argThat(keypair -> keypair.getGroup().getId().equals("2")));
        verify(keyPairsClient, times(1)).addKeyPair(argThat(keypair -> keypair.getGroup().getId().equals("3")
                && isNotEmptyString(keypair.getPrivateKey()) && isNotEmptyString(keypair.getPublicKey())));

        tearDown();
    }

    @Test
    public void generates_and_stores_new_ssh_pair_keys_if_group_exists_in_resource_manager_but_still_dont_have_keys()
            throws ApiException, NoSuchAlgorithmException {
        setUp();

        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(1, 3);
        when(groupsClient.addGroup(any())).then(new AnswerWithParameter<Group>());
        when(keyPairsClient.addKeyPair(any())).then(new AnswerWithParameter<KeyPair>());

        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(keyPairsClient, times(1)).addKeyPair(argThat(keypairs -> keypairs.getGroup().getId().equals("0")));
        verify(keyPairsClient, times(1)).addKeyPair(argThat(keypairs -> keypairs.getGroup().getId().equals("2")));

        tearDown();
    }

    @Test
    public void create_group_folder_on_pipeline_manager_if_folder_doesnt_exist_yet()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1)).createFolder(argThat(path -> path.equals("path0/components")));
        verify(pipelineManager, times(1)).createFolder(argThat(path -> path.equals("path1/components")));
        verify(pipelineManager, times(1)).createFolder(argThat(path -> path.equals("path2/components")));
        verify(pipelineManager, times(1)).createFolder(argThat(path -> path.equals("path3/components")));

        tearDown();
    }

    private void given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(int... groups)
            throws ApiException {
        groupsFromResourceManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setPath("path" + i);
            g.setId(Integer.toString(i));
            g.status(StatusEnum.PENDING);
            g.setPipelineFolderStatus(PipelineFolderStatusEnum.PENDING);
            /* do not execute the rest of the process */
            g.setBinaryRepositoryStatus(Group.BinaryRepositoryStatusEnum.READY);
            g.setSourceRepositoryStatus(Group.SourceRepositoryStatusEnum.READY);

            groupsFromResourceManager.add(g);
        }
        when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
    }

    @Test
    public void create_group_folder_on_pipeline_manager_if_folder_exists_but_not_ready()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1)).createFolder(eq("path0/components"));
        verify(pipelineManager, times(1)).createFolder(eq("path1/components"));
        verify(pipelineManager, times(1)).createFolder(eq("path2/components"));
        verify(pipelineManager, times(1)).createFolder(eq("path3/components"));

        tearDown();
    }

    @Test
    public void create_group_membership_on_pipeline_manager_if_folder_doesnt_exist_yet()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("path0")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("path0/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("path1")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("path1/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("path2")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("path2/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("path3")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("path3/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));

        tearDown();
    }

    @Test
    public void create_group_membership_on_pipeline_manager_if_folder_exists_but_not_ready()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("path0")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("path0/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("path1")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("path1/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("path2")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("path2/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("path3")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("path3/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));

        tearDown();
    }

    @Test
    public void set_group_credentials_on_pipeline_manager_if_folder_doesnt_exist_yet()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        when(groupsClient.addGroup(any())).then(new AnswerWithParameter<Group>());

        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("path0/components")
                        && credential.getCredentialId().equals("0") && credential.getUsername().equals("path0 - 0")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("path1/components")
                        && credential.getCredentialId().equals("1") && credential.getUsername().equals("path1 - 1")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("path2/components")
                        && credential.getCredentialId().equals("2") && credential.getUsername().equals("path2 - 2")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("path3/components")
                        && credential.getCredentialId().equals("3") && credential.getUsername().equals("path3 - 3")
                        && credential.getDescription().equals("SSH Pull Key")));

        tearDown();
    }

    @Test
    public void set_group_credentials_on_pipeline_manager_if_folder_exists_but_not_ready()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        when(groupsClient.addGroup(any())).then(new AnswerWithParameter<Group>());

        init_user_sync_manager();
        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("path0/components")
                        && credential.getCredentialId().equals("0") && credential.getUsername().equals("path0 - 0")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("path1/components")
                        && credential.getCredentialId().equals("1") && credential.getUsername().equals("path1 - 1")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("path2/components")
                        && credential.getCredentialId().equals("2") && credential.getUsername().equals("path2 - 2")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("path3/components")
                        && credential.getCredentialId().equals("3") && credential.getUsername().equals("path3 - 3")
                        && credential.getDescription().equals("SSH Pull Key")));

        tearDown();
    }

    @Test
    public void pipeline_folder_status_wont_be_ready_if_cannot_create_folder()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();
        when(groupsClient.addGroup(any())).then(new AnswerWithParameter<Group>());

        when(pipelineManager.createFolder("path0/components")).thenThrow(new ApiException("Foobar"));
        when(pipelineManager.createFolder("path2/components")).thenThrow(new ApiException("Foobar"));

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, times(0)).updateGroupAttributes(eq("0"), any());
        verify(groupsClient, times(1)).updateGroupAttributes(eq("1"), any());
        verify(groupsClient, times(0)).updateGroupAttributes(eq("2"), any());
        verify(groupsClient, times(1)).updateGroupAttributes(eq("3"), any());

        tearDown();
    }

    @Test
    public void pipeline_folder_status_wont_be_ready_if_cannot_add_membership()
            throws NoSuchAlgorithmException, ApiException {
        setUp();

        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        when(pipelineManager.addMembership(argThat(membership -> membership.getMemberName().equals("path1")
                || membership.getMemberName().equals("path3")))).thenThrow(new ApiException("Foobar"));

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("path0")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("path0/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("path2")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("path2/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));

        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("0")), any());
        verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("1")), any());
        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("2")), any());
        verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("3")), any());

        tearDown();
    }

    @Test
    public void pipeline_folder_status_wont_be_ready_if_cannot_add_credential()
            throws ApiException, NoSuchAlgorithmException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        when(groupsClient.addGroup(any())).then(new AnswerWithParameter<Group>());

        when(pipelineManager.addCredential(argThat(new CredentialPathMatcher("path1/components", "path3/components"))))
                .thenThrow(new ApiException("Foobar"));

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("0")),
                argThat(group -> group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("1")),
                argThat(group -> group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("2")),
                argThat(group -> group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("3")),
                argThat(group -> group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));

        tearDown();
    }
}