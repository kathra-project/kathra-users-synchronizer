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

import org.junit.Test;
import org.kathra.core.model.Assignation;
import org.kathra.core.model.Group;
import org.kathra.core.model.Group.BinaryRepositoryStatusEnum;
import org.kathra.core.model.Group.PipelineFolderStatusEnum;
import org.kathra.core.model.Group.SourceRepositoryStatusEnum;
import org.kathra.core.model.Membership;
import org.kathra.core.model.Resource;
import org.kathra.core.model.Resource.StatusEnum;
import org.kathra.utils.ApiException;
import org.kathra.utils.serialization.GsonUtils;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

/**
 * @author Jorge Sainz Raso <jorge.sainzraso@kathra.org>
 */

public class UserSynchronizerManagerSyncMembersSourceManagerTest extends UserSynchronizerTests {

        void setUp() {
                super.setUp(this.getClass().getName());
        }

        protected void given_ready_groups_from_resource_manager(int... groups) throws ApiException {
                groupsFromResourceManager = new ArrayList<Group>();
                for (int i : groups) {
                        Group g = new Group();
                        g.setPath("/kathra-projects/path" + i);
                        g.setId(Integer.toString(i));
                        g.status(StatusEnum.READY);
                        groupsFromResourceManager.add(g);
                }
                when(groupsClient.getGroups()).thenReturn(groupsFromResourceManager);
        }

        private class AnswerGetMembershipWithUsers implements Answer<List<Membership>> {

                List<Membership>[] memberships;

                public AnswerGetMembershipWithUsers(List<Membership>[] membership_array) {
                        super();
                        this.memberships = membership_array;
                }

                @Override
                public List<Membership> answer(InvocationOnMock invocation) throws Throwable {
                        Object[] args = invocation.getArguments();
                        /* incoming path sould be: /kathra-projects/path<id>/components */
                        String path = (String) args[0];
                        Pattern p = Pattern.compile("[0-9]+");// . represents single character
                        Matcher matcher = p.matcher(path);
                        logger.debug("Found ?" + matcher.find());
                        String id_str = path.substring(matcher.start(), matcher.end());
                        Integer id = Integer.valueOf(id_str);
                        return this.memberships[id];
                }
        }

        private String createMembershipName(int group, int member) {
                return "group" + group + "member" + member;
        }

        private Membership create_membership(int group, int member) {
                return new Membership().memberName(createMembershipName(group, member));
        }

        private Assignation createAssignation(int group, int member) {
                return new Assignation().name(createMembershipName(group, member));
        }

        private List<Membership>[] create_memberships(int[] groups, int[][] members) {
                List<Membership>[] memberships = new ArrayList[groups.length];

                for (int group_index : groups) {
                        memberships[group_index] = new ArrayList<Membership>();
                        for (int group_member : members[group_index]) {
                                memberships[group_index].add(create_membership(group_index, group_member));
                        }
                }
                return memberships;
        }

        private void given_source_manager_groups_members(int[] groups, int[][] members) throws ApiException {
                List<Membership>[] currentSourceManagerMembers = create_memberships(groups, members);
                when(sourceManager.getMemberships(any(), any()))
                                .then(new AnswerGetMembershipWithUsers(currentSourceManagerMembers));
        }

        protected void given_groups_with_members_from_user_manager(int[] groups, int[][] members) throws ApiException {
                groupsFromUserManager = new ArrayList<Group>();
                for (int group_index : groups) {
                        Group g = new Group();
                        List<Assignation> group_members = new ArrayList<Assignation>();
                        g.setId(Integer.toString(group_index));
                        g.setPath("/kathra-projects/path" + group_index);
                        g.setMembers(group_members);
                        groupsFromUserManager.add(g);
                        for (int j : members[group_index])
                                group_members.add(createAssignation(group_index, j));
                }
                when(userManager.getGroups()).thenReturn(groupsFromUserManager);
        }

        private class MembershipListMatcher implements ArgumentMatcher<List<Membership>> {

                private Map<String, Membership> memberships;

                public MembershipListMatcher(List<Membership> memberships) {
                        this.memberships = memberships.stream()
                                        .collect(Collectors.toMap(m -> m.getMemberName(), m -> m));
                }

                @Override
                public boolean matches(List<Membership> argument) {
                        if (argument == null)
                                return false; // seems that this function is called when the mock is created
                        if (argument.size() != memberships.size()) {
                                logger.debug("Arguments size doesn't match \n" + GsonUtils.toJson(argument) + "\n"
                                                + GsonUtils.toJson(memberships));
                                return false;
                        }
                        logger.debug("memberships: " + GsonUtils.toJson(memberships));
                        for (Membership m : argument) {
                                logger.debug("Searching  " + m.toString());
                                if (!memberships.containsKey(m.getMemberName()))
                                        return false;
                        }
                        logger.debug("memberships match");
                        return true;
                }

        }

        @Test
        public void make_sure_kathrausersynchronizer_is_a_manager() throws ApiException, NoSuchAlgorithmException {
        }

        @Test
        public void sync_code_handles_null_user_manager_groups_members() throws ApiException, NoSuchAlgorithmException {
                setUp();

                int[] groups = { 0, 1 };
                int[][] source_manager_groups_members = { {}, {} };
                given_groups_from_user_manager(groups);
                given_ready_groups_from_resource_manager(groups);
                given_groups_have_key_pairs(groups);
                given_source_manager_groups_members(groups, source_manager_groups_members);
                init_user_sync_manager();

                userSynchronizerManager.synchronizeGroups();

                tearDown();
        }

        @Test
        public void add_new_members_from_user_manager() throws ApiException, NoSuchAlgorithmException {
                setUp();

                int[] groups = { 0, 1 };
                int[][] user_manager_groups_members = { { 0, 1 }, { 5, 6 } };
                int[][] source_manager_groups_members = { {}, {} };
                given_groups_with_members_from_user_manager(groups, user_manager_groups_members);
                given_ready_groups_from_resource_manager(groups);
                given_groups_have_key_pairs(groups);
                given_source_manager_groups_members(groups, source_manager_groups_members);
                init_user_sync_manager();

                userSynchronizerManager.synchronizeGroups();

                List<Membership>[] expectedUsersToAdd = create_memberships(groups, user_manager_groups_members);
                verify(sourceManager, times(1))
                                .addMemberships(argThat(new MembershipListMatcher(expectedUsersToAdd[0])));
                verify(sourceManager, times(1))
                                .addMemberships(argThat(new MembershipListMatcher(expectedUsersToAdd[1])));

                tearDown();
        }

        @Test
        public void remove_unexisting_members_in_user_manager() throws ApiException, NoSuchAlgorithmException {
                setUp();

                int[] groups = { 0, 1 };
                int[][] user_manager_groups_members = { { 1, 3 }, { 5, 7 } };
                int[][] source_manager_groups_members = { { 0, 1, 2, 3 }, { 5, 6, 7 } };
                given_groups_with_members_from_user_manager(groups, user_manager_groups_members);
                given_ready_groups_from_resource_manager(groups);
                given_groups_have_key_pairs(groups);
                given_source_manager_groups_members(groups, source_manager_groups_members);
                init_user_sync_manager();

                userSynchronizerManager.synchronizeGroups();

                int[][] groups_members_to_delete = { { 0, 2 }, { 6 } };
                List<Membership>[] expectedUsersToRemove = create_memberships(groups, groups_members_to_delete);
                verify(sourceManager, times(1))
                                .deleteMemberships(argThat(new MembershipListMatcher(expectedUsersToRemove[0])));
                verify(sourceManager, times(1))
                                .deleteMemberships(argThat(new MembershipListMatcher(expectedUsersToRemove[1])));

                tearDown();
        }

        @Test
        public void do_nothing_if_same_members() throws ApiException, NoSuchAlgorithmException {
                setUp();

                int[] groups = { 0, 1 };
                int[][] user_manager_groups_members = { { 0, 1, 2, 3 }, { 5, 6, 7 } };
                int[][] source_manager_groups_members = { { 0, 1, 2, 3 }, { 5, 6, 7 } };
                given_groups_with_members_from_user_manager(groups, user_manager_groups_members);
                given_ready_groups_from_resource_manager(groups);
                given_groups_have_key_pairs(groups);
                given_source_manager_groups_members(groups, source_manager_groups_members);
                init_user_sync_manager();

                userSynchronizerManager.synchronizeGroups();

                verify(sourceManager, times(0)).addMemberships(any());
                verify(sourceManager, times(0)).deleteMemberships(any());

                tearDown();
        }

}