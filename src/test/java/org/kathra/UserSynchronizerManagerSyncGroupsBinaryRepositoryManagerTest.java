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

import org.junit.Ignore;
import org.junit.Test;

import static org.mockito.Mockito.*;

import org.kathra.binaryrepositorymanager.client.BinaryRepositoryManagerClient;
import org.kathra.core.model.*;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.kathra.core.model.Group.BinaryRepositoryStatusEnum;
import org.kathra.core.model.Group.PipelineFolderStatusEnum;
import org.kathra.core.model.Resource.StatusEnum;
import org.kathra.pipelinemanager.client.PipelineManagerClient;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.KeyPairsClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.usermanager.client.UserManagerClient;
import org.kathra.utils.ApiException;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jorge Sainz Raso <jorge.sainzraso@kathra.org>
 */

public class UserSynchronizerManagerSyncGroupsBinaryRepositoryManagerTest extends UserSynchronizerTests {

    public static final Logger logger = LoggerFactory.getLogger("UserSynchronizerManagerSyncGroupsTest");

    private void setUp() throws ApiException {
        config = mock(Config.class);
        keycloackSession = mock(KeycloackSession.class);
        sourceManager = mock(SourceManagerClient.class);
        pipelineManager = mock(PipelineManagerClient.class);
        userManager = mock(UserManagerClient.class);
        repositoryManagerNexus = mock(BinaryRepositoryManagerClient.class);
        repositoryManagerHarbor = mock(BinaryRepositoryManagerClient.class);
        groupsClient = mock(GroupsClient.class);
        keyPairsClient = mock(KeyPairsClient.class);

    }

    private void given_kathra_project_groups_from_user_manager(int... groups) throws ApiException {
        groupsFromUserManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setId(Integer.toString(i));
            g.setPath("/kathra-projects/path" + i);
            g.setMembers(new ArrayList<Assignation>());
            groupsFromUserManager.add(g);
        }
        when(userManager.getGroups()).thenReturn(groupsFromUserManager);
    }

    private void given_kathra_project_pending_groups_with_ready_pipeline_folder_status_from_resource_manager(
            int... groups) throws ApiException {
        groupsFromResourceManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setPath("/kathra-projects/path" + i);
            g.setId(Integer.toString(i));
            g.status(StatusEnum.PENDING);
            g.setPipelineFolderStatus(PipelineFolderStatusEnum.READY);
            g.setBinaryRepositoryStatus(BinaryRepositoryStatusEnum.PENDING);

            groupsFromResourceManager.add(g);
        }
        when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
    }

    private void given_kathra_project_pending_binary_repo_status_from_resource_manager(int... groups)
            throws ApiException {
        groupsFromResourceManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setPath("/kathra-projects/path" + i);
            g.setId(Integer.toString(i));
            g.status(StatusEnum.PENDING);
            g.setPipelineFolderStatus(PipelineFolderStatusEnum.READY);
            g.setSourceRepositoryStatus(Group.SourceRepositoryStatusEnum.READY);
            g.setBinaryRepositoryStatus(BinaryRepositoryStatusEnum.PENDING);

            groupsFromResourceManager.add(g);
        }
        when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
    }

    private void given_pending_groups_with_ready_pipeline_folder_status_from_resource_manager(int... groups)
            throws ApiException {
        groupsFromResourceManager = new ArrayList<Group>();
        for (int i : groups) {
            Group g = new Group();
            g.setPath("/kathra-projects/path" + i);
            g.setId(Integer.toString(i));
            g.status(StatusEnum.PENDING);
            g.setPipelineFolderStatus(PipelineFolderStatusEnum.READY);
            g.setBinaryRepositoryStatus(BinaryRepositoryStatusEnum.PENDING);

            groupsFromResourceManager.add(g);
        }
        when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
    }

    private void given_pending_groups_with_ready_pipeline_ready_binary_status_from_resource_manager(int... groups)
            throws ApiException {
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

    private class BinaryRepositoryNameMatcher implements ArgumentMatcher<BinaryRepository> {

        private String[] names;

        public BinaryRepositoryNameMatcher(String... names) {
            logger.info("Creating match class " + names);
            this.names = names;
            logger.info("total" + this.names.length);
        }

        @Override
        public boolean matches(BinaryRepository argument) {
            if (argument == null)
                return false; // seems that this function is called when the mock is created
            logger.info(argument.toString());
            for (int i = 0; i < this.names.length; i++) {
                logger.info("Argument:  " + argument.toString());
                logger.info("Comparing " + argument.getName());
                if (argument.getName().equalsIgnoreCase(this.names[i]))
                    return true;
            }

            return false;
        }
    }

    private class BinaryRepositoryIdMatcher implements ArgumentMatcher<BinaryRepository> {

        private String[] ids;

        public BinaryRepositoryIdMatcher(String... ids) {
            logger.info("Creating match class " + ids);
            this.ids = ids;
        }

        @Override
        public boolean matches(BinaryRepository argument) {
            for (int i = 0; i < this.ids.length; i++)
                if (argument.getId() == this.ids[i])
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
            return object;
        }
    }

    private class AnswerReadyGroup extends AnswerWithParameter<Group> {
        public Group processReturnObject(Group object) {
            object.setStatus(Resource.StatusEnum.READY);
            return object;
        }
    }

    private class AnswerContainerRepositoryWithId extends AnswerWithParameter<BinaryRepository> {
        public BinaryRepository processReturnObject(BinaryRepository object) {
            String name = object.getName();
            logger.info("AnswerContainerRepositoryWithId " + name);
            Pattern p = Pattern.compile("[0-9]+");// . represents single character
            Matcher matcher = p.matcher(name);
            logger.info("Found ?" + matcher.find());
            logger.info("Found number on " + matcher.group() + " starting at " + matcher.start() + " ending at "
                    + matcher.end());
            String number_str = name.substring(matcher.start(), matcher.end());
            logger.info("Object created with id " + number_str);
            object.setId(number_str);
            return object;
        }
    }
    
    @Test
    @Ignore
    public void create_container_repository_if_group_doesnt_exist() throws ApiException, NoSuchAlgorithmException {
        setUp();

        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_ready_pipeline_ready_binary_status_from_resource_manager(0, 2);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();
        when(groupsClient.addGroup(any())).then(new AnswerPendingGroup());
        when(repositoryManagerHarbor.addBinaryRepository(any())).then(new AnswerContainerRepositoryWithId());

        userSynchronizerManager.synchronizeGroups();

        verify(repositoryManagerHarbor, times(0)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path0")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path1")));
        verify(repositoryManagerHarbor, times(0)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path2")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path3")));

        tearDown();
    }

    @Test
    @Ignore
    public void create_container_repository_if_group_exists_but_container_not_ready()
            throws ApiException, NoSuchAlgorithmException {
        setUp();

        given_groups_from_user_manager(0, 1, 2, 3);
        given_pending_groups_with_ready_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();
        when(repositoryManagerHarbor.addBinaryRepository(any())).then(new AnswerContainerRepositoryWithId());

        userSynchronizerManager.synchronizeGroups();

        verify(repositoryManagerHarbor, times(1)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path0")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path1")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path2")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path3")));

        tearDown();
    }

    @Test
    @Ignore
    public void when_kathra_project_group_link_it_to_group_path() throws ApiException, NoSuchAlgorithmException {
        setUp();

        given_kathra_project_groups_from_user_manager(0, 1, 2, 3);
        given_kathra_project_pending_groups_with_ready_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();
        when(groupsClient.addGroup(any())).then(new AnswerPendingGroup());
        when(repositoryManagerHarbor.addBinaryRepository(any())).then(new AnswerContainerRepositoryWithId());

        userSynchronizerManager.synchronizeGroups();

        verify(repositoryManagerHarbor, times(1)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path0")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path1")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path2")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepository(
                argThat(BinaryRepository -> BinaryRepository.getName().equals("path3")));

        tearDown();
    }

    @Test
    @Ignore
    public void set_jenkins_harbor_user_as_member_once_container_repo_has_been_created()
            throws ApiException, NoSuchAlgorithmException {
        setUp();

        given_kathra_project_groups_from_user_manager(0, 1, 2, 3);
        given_kathra_project_pending_groups_with_ready_pipeline_folder_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        when(repositoryManagerHarbor.addBinaryRepository(any())).then(new AnswerContainerRepositoryWithId());

        userSynchronizerManager.synchronizeGroups();

        verify(repositoryManagerHarbor, times(1)).addBinaryRepositoryMembership(eq("0"),
                argThat(BinaryRepositoryMembership -> BinaryRepositoryMembership.getMemberName()
                        .equals("jenkins.harbor")
                        && BinaryRepositoryMembership.getMemberType().equals(Membership.MemberTypeEnum.USER)
                        && BinaryRepositoryMembership.getRole().equals(Membership.RoleEnum.CONTRIBUTOR)
                        && BinaryRepositoryMembership.getPath().equals("path0")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepositoryMembership(eq("1"),
                argThat(BinaryRepositoryMembership -> BinaryRepositoryMembership.getMemberName()
                        .equals("jenkins.harbor")
                        && BinaryRepositoryMembership.getMemberType().equals(Membership.MemberTypeEnum.USER)
                        && BinaryRepositoryMembership.getRole().equals(Membership.RoleEnum.CONTRIBUTOR)
                        && BinaryRepositoryMembership.getPath().equals("path1")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepositoryMembership(eq("2"),
                argThat(BinaryRepositoryMembership -> BinaryRepositoryMembership.getMemberName()
                        .equals("jenkins.harbor")
                        && BinaryRepositoryMembership.getMemberType().equals(Membership.MemberTypeEnum.USER)
                        && BinaryRepositoryMembership.getRole().equals(Membership.RoleEnum.CONTRIBUTOR)
                        && BinaryRepositoryMembership.getPath().equals("path2")));
        verify(repositoryManagerHarbor, times(1)).addBinaryRepositoryMembership(eq("3"),
                argThat(BinaryRepositoryMembership -> BinaryRepositoryMembership.getMemberName()
                        .equals("jenkins.harbor")
                        && BinaryRepositoryMembership.getMemberType().equals(Membership.MemberTypeEnum.USER)
                        && BinaryRepositoryMembership.getRole().equals(Membership.RoleEnum.CONTRIBUTOR)
                        && BinaryRepositoryMembership.getPath().equals("path3")));

        tearDown();
    }

    @Test
    @Ignore
    public void set_binary_repo_as_ready_if_everything_went_well() throws ApiException, NoSuchAlgorithmException {
        setUp();

        given_groups_from_user_manager(0, 1, 2, 3);
        given_kathra_project_pending_binary_repo_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();
        when(repositoryManagerHarbor.addBinaryRepository(any())).then(new AnswerContainerRepositoryWithId());

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("0")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));
        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("1")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));
        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("2")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));
        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("3")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));

        tearDown();
    }

    @Test
    @Ignore
    public void binary_kept_as_no_ready_if_cannot_create_container() throws ApiException, NoSuchAlgorithmException {
        setUp();

        given_kathra_project_groups_from_user_manager(0, 1, 2, 3);
        given_kathra_project_pending_binary_repo_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();

        when(repositoryManagerHarbor
                .addBinaryRepository(argThat(new BinaryRepositoryNameMatcher("path0", "path1"))))
                        .thenThrow(new ApiException("Foobar"));

        when(repositoryManagerHarbor
                .addBinaryRepository(argThat(new BinaryRepositoryNameMatcher("path2", "path3"))))
                        .then(new AnswerContainerRepositoryWithId());

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("0")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("1")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));
        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("2")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));
        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("3")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));

        tearDown();
    }

    @Test
    @Ignore
    public void binary_kept_as_no_ready_if_cannot_set_membership() throws ApiException, NoSuchAlgorithmException {
        setUp();

        given_kathra_project_groups_from_user_manager(0, 1, 2, 3);
        given_kathra_project_pending_binary_repo_status_from_resource_manager(0, 1, 2, 3);
        given_groups_have_key_pairs(0, 1, 2, 3);
        init_user_sync_manager();
        when(repositoryManagerHarbor.addBinaryRepository(any())).then(new AnswerContainerRepositoryWithId());

        doThrow(new ApiException("Foobar")).when(repositoryManagerHarbor).addBinaryRepositoryMembership(AdditionalMatchers.or(eq("2"), eq("3")), any());

        userSynchronizerManager.synchronizeGroups();

        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("0")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));
        verify(groupsClient, times(1)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("1")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("2")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));
        verify(groupsClient, times(0)).updateGroupAttributes(argThat(resourceId -> resourceId.equals("3")),
                argThat(group -> group.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY)));

        tearDown();
    }

}