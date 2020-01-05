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

public class UserSynchronizerManagerSyncGroupsPipelineManagerTest extends UserSynchronizerTests {

    public static final Logger logger = LoggerFactory.getLogger("UserSynchronizerManagerSyncGroupsTest");

    private void setUp() {
        super.setUp(this.getClass().getName());
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
            throws ApiException {
        setUp();
        init_user_sync_manager();

        given_groups_from_user_manager(0, 1, 2, 3, 4, 5);
        given_pending_groups_from_resource_manager(0, 2, 4);

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, times(1)).addGroup(argThat(group -> group.getPath().equals("/kathra-projects/path1")));
        verify(groupsClient, times(1)).addGroup(argThat(group -> group.getPath().equals("/kathra-projects/path3")));
        verify(groupsClient, times(1)).addGroup(argThat(group -> group.getPath().equals("/kathra-projects/path5")));

        tearDown();
    }


    @Test
    public void do_not_add_group_if_group_is_on_user_manager_but_not_in_resource_manager()
            throws ApiException {
        setUp();
        init_user_sync_manager();
        given_groups_from_user_manager(0, 2, 4);
        given_pending_groups_from_resource_manager(0, 1, 2, 3, 4, 5);

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, never()).addGroup(any());

        tearDown();
    }

    private boolean isNotEmptyString(String string) {
        boolean result = string != null && !string.isEmpty();
        logger.info("RESULT " + result);
        return result;
    }

    @Test
    public void generates_and_stores_new_ssh_pair_keys_if_group_doesnt_have_keys()
            throws ApiException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_from_resource_manager(0,1,2,3);
        given_groups_have_key_pairs(0, 2);
        init_user_sync_manager();

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
            throws ApiException {
        setUp();

        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(1, 3);

        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(keyPairsClient, times(1)).addKeyPair(argThat(keypairs -> keypairs.getGroup().getId().equals("0")));
        verify(keyPairsClient, times(1)).addKeyPair(argThat(keypairs -> keypairs.getGroup().getId().equals("2")));

        tearDown();
    }

    @Test
    public void create_group_folder_on_pipeline_manager_if_folder_doesnt_exist_yet()
            throws ApiException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1)).createFolder(argThat(path -> path.equals("/kathra-projects/path0/components")));
        verify(pipelineManager, times(1)).createFolder(argThat(path -> path.equals("/kathra-projects/path1/components")));
        verify(pipelineManager, times(1)).createFolder(argThat(path -> path.equals("/kathra-projects/path2/components")));
        verify(pipelineManager, times(1)).createFolder(argThat(path -> path.equals("/kathra-projects/path3/components")));

        tearDown();
    }

    private void given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(int... groups)
            throws ApiException {
        groupsFromResourceManager = new ArrayList<>();
        for (int i : groups) {
            Group g = new Group();
            g.setPath("/kathra-projects/path" + i);
            g.setId(Integer.toString(i));
            g.status(StatusEnum.PENDING);
            g.setTechnicalUser(new User().id(g.getName()+"_technicaluser").name(g.getName()+"_technicaluser").password("a password"));
            when(usersClient.getUser(g.getTechnicalUser().getId())).thenReturn(g.getTechnicalUser());
            super.usersFromResourceManager.add(g.getTechnicalUser());
            g.setPipelineFolderStatus(PipelineFolderStatusEnum.PENDING);
            /* do not execute the rest of the process */
            g.setBinaryRepositoryStatus(Group.BinaryRepositoryStatusEnum.READY);
            g.setSourceRepositoryStatus(Group.SourceRepositoryStatusEnum.READY);
            when(groupsClient.getGroup(g.getId())).thenReturn(g);
            groupsFromResourceManager.add(g);
        }
        when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
    }

    @Test
    public void create_group_folder_on_pipeline_manager_if_folder_exists_but_not_ready()
            throws ApiException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1)).createFolder(eq("/kathra-projects/path0/components"));
        verify(pipelineManager, times(1)).createFolder(eq("/kathra-projects/path1/components"));
        verify(pipelineManager, times(1)).createFolder(eq("/kathra-projects/path2/components"));
        verify(pipelineManager, times(1)).createFolder(eq("/kathra-projects/path3/components"));

        tearDown();
    }

    @Test
    public void create_group_membership_on_pipeline_manager_if_folder_doesnt_exist_yet()
            throws ApiException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path0")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("/kathra-projects/path0/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path1")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("/kathra-projects/path1/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path2")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("/kathra-projects/path2/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path3")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("/kathra-projects/path3/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));

        tearDown();
    }

    @Test
    public void create_group_membership_on_pipeline_manager_if_folder_exists_but_not_ready()
            throws ApiException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path0")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("/kathra-projects/path0/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path1")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("/kathra-projects/path1/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path2")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("/kathra-projects/path2/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path3")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("/kathra-projects/path3/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));

        tearDown();
    }

    @Test
    public void set_group_credentials_on_pipeline_manager_if_folder_doesnt_exist_yet()
            throws ApiException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);

        init_user_sync_manager();

        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("/kathra-projects/path0/components")
                        && credential.getCredentialId().equals("0") && credential.getUsername().equals("/kathra-projects/path0 - 0")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("/kathra-projects/path1/components")
                        && credential.getCredentialId().equals("1") && credential.getUsername().equals("/kathra-projects/path1 - 1")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("/kathra-projects/path2/components")
                        && credential.getCredentialId().equals("2") && credential.getUsername().equals("/kathra-projects/path2 - 2")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("/kathra-projects/path3/components")
                        && credential.getCredentialId().equals("3") && credential.getUsername().equals("/kathra-projects/path3 - 3")
                        && credential.getDescription().equals("SSH Pull Key")));

        tearDown();
    }

    @Test
    public void set_group_credentials_on_pipeline_manager_if_folder_exists_but_not_ready()
            throws ApiException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);

        init_user_sync_manager();
        userSynchronizerManager.synchronizeGroups();

        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("/kathra-projects/path0/components")
                        && credential.getCredentialId().equals("0") && credential.getUsername().equals("/kathra-projects/path0 - 0")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("/kathra-projects/path1/components")
                        && credential.getCredentialId().equals("1") && credential.getUsername().equals("/kathra-projects/path1 - 1")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("/kathra-projects/path2/components")
                        && credential.getCredentialId().equals("2") && credential.getUsername().equals("/kathra-projects/path2 - 2")
                        && credential.getDescription().equals("SSH Pull Key")));
        verify(pipelineManager, times(1))
                .addCredential(argThat(credential -> credential.getPath().equals("/kathra-projects/path3/components")
                        && credential.getCredentialId().equals("3") && credential.getUsername().equals("/kathra-projects/path3 - 3")
                        && credential.getDescription().equals("SSH Pull Key")));

        tearDown();
    }

    @Test
    public void pipeline_folder_status_wont_be_ready_if_cannot_create_folder()
            throws ApiException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();
//        when(groupsClient.addGroup(any())).then(new AnswerWithParameter<Group>());

        when(pipelineManager.createFolder("/kathra-projects/path1/components")).thenThrow(new ApiException("Foobar"));
        when(pipelineManager.createFolder("/kathra-projects/path3/components")).thenThrow(new ApiException("Foobar"));

        userSynchronizerManager.synchronizeGroups();


        verify(groupsClient, times(1)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("0")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("1")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(1)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("2")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("3")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));

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

        when(pipelineManager.addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path1")
                || membership.getMemberName().equals("/kathra-projects/path3")))).thenThrow(new ApiException("Foobar"));

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, times(1)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("0")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("1")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(1)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("2")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("3")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));


        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path0")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("/kathra-projects/path0/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));
        verify(pipelineManager, times(1)).addMembership(argThat(membership -> membership.getMemberName().equals("/kathra-projects/path2")
                && membership.getMemberType().equals(Membership.MemberTypeEnum.GROUP)
                && membership.getPath().equals("/kathra-projects/path2/components")
                && membership.getRole().equals(Membership.RoleEnum.GUEST)));


        tearDown();
    }

    @Test
    public void pipeline_folder_status_wont_be_ready_if_cannot_add_credential()
            throws ApiException {
        setUp();
        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_pending_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        when(pipelineManager.addCredential(argThat(new CredentialPathMatcher("/kathra-projects/path1/components", "/kathra-projects/path3/components"))))
                .thenThrow(new ApiException("Foobar"));

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, times(1)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("0")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("1")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(1)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("2")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(
                argThat(resourceId -> resourceId.equals("3")),
                argThat(group -> group.getPipelineFolderStatus() != null && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY)));

        tearDown();
    }
}