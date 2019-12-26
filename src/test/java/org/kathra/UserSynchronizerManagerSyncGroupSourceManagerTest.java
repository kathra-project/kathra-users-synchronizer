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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.*;

import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.matchers.Null;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import org.kathra.binaryrepositorymanager.client.BinaryrepositorymanagerClient;
import org.kathra.core.model.Group;
import org.kathra.core.model.KeyPair;
import org.kathra.core.model.Membership;
import org.kathra.core.model.Group.BinaryRepositoryStatusEnum;
import org.kathra.core.model.Group.PipelineFolderStatusEnum;
import org.kathra.core.model.Group.SourceRepositoryStatusEnum;
import org.kathra.core.model.Resource.StatusEnum;
import org.kathra.utils.ApiException;
import org.kathra.core.model.Resource;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kathra.UserSynchronizerTests;

/**
 * @author Jorge Sainz Raso <jorge.sainzraso@kathra.org>
 */

public class UserSynchronizerManagerSyncGroupSourceManagerTest extends UserSynchronizerTests {

        private class AnswerPendingGroup extends MockitoWhenChainingMethod<Group> {
                public Group processReturnObject(Group object) {
                        object.setStatus(Resource.StatusEnum.PENDING);
                        return object;
                }
        }

        private class AnswerSyncGroupPendingGroup extends MockitoWhenChainingMethod<Group> {
                public Group processReturnObject(Group object) {
                        object.setStatus(Resource.StatusEnum.PENDING);
                        object.setPipelineFolderStatus(Group.PipelineFolderStatusEnum.READY);
                        object.setBinaryRepositoryStatus(Group.BinaryRepositoryStatusEnum.READY);
                        return object;
                }
        }

        private class AnswerReadyGroup extends MockitoWhenChainingMethod<Group> {
                public Group processReturnObject(Group object) {
                        object.setStatus(Resource.StatusEnum.READY);
                        /* do not execute the rest of the process */
                        object.setBinaryRepositoryStatus(Group.BinaryRepositoryStatusEnum.READY);
                        return object;
                }
        }

        void setUp() {
                super.setUp(this.getClass().getName());
        }

        private void given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_and_ready_source_manager_from_resource_manager(
                        int... groups) throws ApiException {
                groupsFromResourceManager = new ArrayList<Group>();
                for (int i : groups) {
                        Group g = new Group();
                        g.setPath("/kathra-projects/path" + i);
                        g.setId(Integer.toString(i));
                        g.status(StatusEnum.PENDING);
                        g.setPipelineFolderStatus(PipelineFolderStatusEnum.READY);
                        g.setBinaryRepositoryStatus(BinaryRepositoryStatusEnum.READY);
                        g.setSourceRepositoryStatus(SourceRepositoryStatusEnum.READY);

                        groupsFromResourceManager.add(g);
                }
                when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
        }

        private void given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_from_resource_manager(
                        int... groups) throws ApiException {
                groupsFromResourceManager = new ArrayList<Group>();
                for (int i : groups) {
                        Group g = new Group();
                        g.setPath("/kathra-projects/path" + i);
                        g.setId(Integer.toString(i));
                        g.status(StatusEnum.PENDING);
                        g.setPipelineFolderStatus(PipelineFolderStatusEnum.READY);
                        g.setBinaryRepositoryStatus(BinaryRepositoryStatusEnum.READY);

                        groupsFromResourceManager.add(g);
                }
                when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
        }

        @Test
        public void create_components_folder_if_group_doesnt_exist() throws ApiException, NoSuchAlgorithmException {
                setUp();

                given_groups_from_user_manager(0, 1, 2, 3);
                given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_and_ready_source_manager_from_resource_manager(
                                0, 2);
                given_groups_have_key_pairs(0, 2);
                when(groupsClient.addGroup(any())).then(new AnswerSyncGroupPendingGroup());
                when(keyPairsClient.addKeyPair(any())).then(new MockitoWhenChainingMethod<KeyPair>());

                init_user_sync_manager();

                userSynchronizerManager.synchronizeGroups();

                verify(sourceManager, times(0)).createFolder(
                                argThat(folder -> folder.getPath().equals("/kathra-projects/path0/components")));
                verify(sourceManager, times(1)).createFolder(
                                argThat(folder -> folder.getPath().equals("/kathra-projects/path1/components")));
                verify(sourceManager, times(0)).createFolder(
                                argThat(folder -> folder.getPath().equals("/kathra-projects/path2/components")));
                verify(sourceManager, times(1)).createFolder(
                                argThat(folder -> folder.getPath().equals("/kathra-projects/path3/components")));

                tearDown();
        }

        @Test
        public void create_components_folder_if_group_exists_but_repo_is_not_ready()
                        throws ApiException, NoSuchAlgorithmException {
                setUp();

                given_groups_from_user_manager(0, 1, 2, 3);
                given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_from_resource_manager(0, 1,
                                2, 3);
                given_groups_have_key_pairs(0, 1, 2, 3);
                init_user_sync_manager();

                userSynchronizerManager.synchronizeGroups();

                verify(sourceManager, times(1)).createFolder(
                                argThat(folder -> folder.getPath().equals("/kathra-projects/path0/components")));
                verify(sourceManager, times(1)).createFolder(
                                argThat(folder -> folder.getPath().equals("/kathra-projects/path1/components")));
                verify(sourceManager, times(1)).createFolder(
                                argThat(folder -> folder.getPath().equals("/kathra-projects/path2/components")));
                verify(sourceManager, times(1)).createFolder(
                                argThat(folder -> folder.getPath().equals("/kathra-projects/path3/components")));

                tearDown();
        }

        @Test
        public void create_source_repository_if_can_create_components_folder()
                        throws ApiException, NoSuchAlgorithmException {
                setUp();

                given_groups_from_user_manager(0, 1, 2, 3);
                given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_from_resource_manager(0, 1,
                                2, 3);
                given_groups_have_key_pairs(0, 1, 2, 3);
                init_user_sync_manager();

                userSynchronizerManager.synchronizeGroups();

                verify(sourceManager, times(1))
                                .createSourceRepository(
                                                argThat(deployKeyRepository -> deployKeyRepository.getPath()
                                                                .equals("/kathra-projects/path0/kathra-deploy-key")),
                                                isNull());
                verify(sourceManager, times(1))
                                .createSourceRepository(
                                                argThat(deployKeyRepository -> deployKeyRepository.getPath()
                                                                .equals("/kathra-projects/path1/kathra-deploy-key")),
                                                isNull());
                verify(sourceManager, times(1))
                                .createSourceRepository(
                                                argThat(deployKeyRepository -> deployKeyRepository.getPath()
                                                                .equals("/kathra-projects/path2/kathra-deploy-key")),
                                                isNull());
                verify(sourceManager, times(1))
                                .createSourceRepository(
                                                argThat(deployKeyRepository -> deployKeyRepository.getPath()
                                                                .equals("/kathra-projects/path3/kathra-deploy-key")),
                                                isNull());

                tearDown();
        }

        @Test
        public void add_kathraresourcemanager_membership_as_manager() throws ApiException, NoSuchAlgorithmException {
                setUp();

                given_groups_from_user_manager(0, 1, 2, 3);
                given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_from_resource_manager(0, 1,
                                2, 3);
                given_groups_have_key_pairs(0, 1, 2, 3);
                init_user_sync_manager();

                userSynchronizerManager.synchronizeGroups();

                verify(sourceManager, times(1)).addMemberships(
                                argThat(memberships -> memberships.get(0).getMemberName().equals("kathra-sourcemanager")
                                                && memberships.get(0).getRole() == Membership.RoleEnum.MANAGER
                                                && memberships.get(0).getPath()
                                                                .equals("/kathra-projects/path0/kathra-deploy-key")));
                verify(sourceManager, times(1)).addMemberships(
                                argThat(memberships -> memberships.get(0).getMemberName().equals("kathra-sourcemanager")
                                                && memberships.get(0).getRole() == Membership.RoleEnum.MANAGER
                                                && memberships.get(0).getPath()
                                                                .equals("/kathra-projects/path1/kathra-deploy-key")));
                verify(sourceManager, times(1)).addMemberships(
                                argThat(memberships -> memberships.get(0).getMemberName().equals("kathra-sourcemanager")
                                                && memberships.get(0).getRole() == Membership.RoleEnum.MANAGER
                                                && memberships.get(0).getPath()
                                                                .equals("/kathra-projects/path2/kathra-deploy-key")));
                verify(sourceManager, times(1)).addMemberships(
                                argThat(memberships -> memberships.get(0).getMemberName().equals("kathra-sourcemanager")
                                                && memberships.get(0).getRole() == Membership.RoleEnum.MANAGER
                                                && memberships.get(0).getPath()
                                                                .equals("/kathra-projects/path3/kathra-deploy-key")));

                tearDown();
        }

        @Test
        public void set_deploy_key_if_can_create_source_repository() throws ApiException, NoSuchAlgorithmException {
                setUp();

                given_groups_from_user_manager(0, 1, 2, 3);
                given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_from_resource_manager(0, 1,
                                2, 3);
                given_groups_have_key_pairs(0, 1, 2, 3);
                init_user_sync_manager();

                userSynchronizerManager.synchronizeGroups();

                verify(sourceManager, times(1)).createDeployKey(eq("0"), any(),
                                eq("/kathra-projects/path0/kathra-deploy-key"));
                verify(sourceManager, times(1)).createDeployKey(eq("1"), any(),
                                eq("/kathra-projects/path1/kathra-deploy-key"));
                verify(sourceManager, times(1)).createDeployKey(eq("2"), any(),
                                eq("/kathra-projects/path2/kathra-deploy-key"));
                verify(sourceManager, times(1)).createDeployKey(eq("3"), any(),
                                eq("/kathra-projects/path3/kathra-deploy-key"));

                tearDown();
        }

        @Test
        public void update_source_manager_state_if_everything_went_ok() throws ApiException, NoSuchAlgorithmException {
                setUp();

                given_groups_from_user_manager(0, 1, 2, 3);
                given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_from_resource_manager(0, 1,
                                2, 3);
                given_groups_have_key_pairs(0, 1, 2, 3);
                init_user_sync_manager();

                userSynchronizerManager.synchronizeGroups();

                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("0")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("1")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("2")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("3")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));

                tearDown();

        }

        @Test
        public void do_not_update_source_manager_state_if_cannot_create_folder_repository()
                        throws ApiException, NoSuchAlgorithmException {
                setUp();

                given_groups_from_user_manager(0, 1, 2, 3);
                given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_from_resource_manager(0, 1,
                                2, 3);
                given_groups_have_key_pairs(0, 1, 2, 3);
                init_user_sync_manager();

                when(sourceManager.createFolder(
                                argThat(folder -> folder.getPath().equals("/kathra-projects/path1/components")
                                                || folder.getPath().equals("/kathra-projects/path3/components"))))
                                                                .thenThrow(new ApiException("Foobar"));

                userSynchronizerManager.synchronizeGroups();

                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("0")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("1")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("2")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("3")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));

                tearDown();
        }

        @Test
        public void do_not_update_source_manager_state_if_cannot_create_source_repository()
                        throws ApiException, NoSuchAlgorithmException {
                setUp();

                given_groups_from_user_manager(0, 1, 2, 3);
                given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_from_resource_manager(0, 1,
                                2, 3);
                given_groups_have_key_pairs(0, 1, 2, 3);
                init_user_sync_manager();

                when(sourceManager.createSourceRepository(argThat(deployKeyRepository -> deployKeyRepository.getPath()
                                .equals("/kathra-projects/path1/kathra-deploy-key")
                                || deployKeyRepository.getPath().equals("/kathra-projects/path3/kathra-deploy-key")),
                                any())).thenThrow(new ApiException("Foobar"));

                userSynchronizerManager.synchronizeGroups();

                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("0")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("1")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("2")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("3")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));

                tearDown();

        }

        @Test
        public void do_not_update_source_manager_state_if_cannot_add_kathra_sourcemanager_membership()
                        throws ApiException, NoSuchAlgorithmException {

                setUp();

                given_groups_from_user_manager(0, 1, 2, 3);
                given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_from_resource_manager(0, 1,
                                2, 3);
                given_groups_have_key_pairs(0, 1, 2, 3);
                init_user_sync_manager();

                when(sourceManager.addMemberships(argThat(memberships -> memberships.get(0).getPath()
                                .equals("/kathra-projects/path1/kathra-deploy-key")
                                || memberships.get(0).getPath().equals("/kathra-projects/path3/kathra-deploy-key"))))
                                                .thenThrow(new ApiException("Foobar"));

                userSynchronizerManager.synchronizeGroups();

                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("0")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("1")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("2")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("3")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));

                tearDown();
        }

        @Test
        public void do_not_update_source_manager_state_if_cannot_set_deployment_key()
                        throws ApiException, NoSuchAlgorithmException {
                setUp();

                given_groups_from_user_manager(0, 1, 2, 3);
                given_pending_groups_with_ready_pipeline_folder_and_ready_binary_repo_status_from_resource_manager(0, 1,
                                2, 3);
                given_groups_have_key_pairs(0, 1, 2, 3);
                init_user_sync_manager();

                when(sourceManager.createDeployKey(AdditionalMatchers.or(eq("1"), eq("3")), any(), any()))
                                .thenThrow(new ApiException("Foobar"));

                userSynchronizerManager.synchronizeGroups();

                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("0")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("1")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("2")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));
                verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("3")),
                                argThat(group -> group.getSourceRepositoryStatus()
                                                .equals(Group.SourceRepositoryStatusEnum.READY)));

                tearDown();
        }
}