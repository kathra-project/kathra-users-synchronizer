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

import org.apache.commons.lang3.RandomStringUtils;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.UsersClient;
import org.kathra.usermanager.client.UserManagerClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SyncTechnicalUser {


    private Logger log = LoggerFactory.getLogger("SyncTechnicalUser");

    final private UserManagerClient userManager;
    final private GroupsClient groupsClient;
    final private UsersClient usersClient;


    public SyncTechnicalUser(UserManagerClient userManager,
                             GroupsClient groupsClient, UsersClient usersClient) {

        this.userManager = userManager;
        this.groupsClient = groupsClient;
        this.usersClient = usersClient;
    }

    public Group syncTechnicalUser(Group group) throws ApiException {
        String username=group.getName()+"_technicaluser";
        User user = usersClient.getUsers().stream().filter(u -> u.getName().equals(username)).findFirst().orElse(null);

        // CREATE IN DB IF DOESN'T EXIST
        if (user == null ) {
            log.debug("User " + username + " not found in db.. create new ones");
            user = new User().name(username).email(username+"@"+group.getName()+".kathra.org").password(generateSecureRandomPassword());
            user = usersClient.addUser(user);
            group.technicalUser(user);
            groupsClient.updateGroupAttributes(group.getId(), new Group().technicalUser(user));
        }

        if (group.getTechnicalUser() == null) {
            groupsClient.updateGroupAttributes(group.getId(), new Group().technicalUser(user));
        }

        // CHECK USER EXISTS IN USERMANAGER
        User userFromUserManager = null;
        try {
            userFromUserManager = userManager.getUser(user.getName());
        } catch(ApiException e) {
            if (KathraException.ErrorCode.NOT_FOUND.getCode() != e.getCode()) {
                throw e;
            }
        }
        // CREATE IN USERMANAGER IF DOESN'T EXIST
        if (userFromUserManager == null) {
            log.debug("User " + user.getName()+ " not found in usermanager.. create new ones");
            if (user.getPassword() == null) {
                throw new IllegalStateException("Technical should contains password");
            }
            userManager.createUser(user);
        }

        // JOIN TO GROUP IN USERMANAGER IF HE IS NOT A MEMBER
        if (get_group_user_manager_members(userManager.getGroup(group.getPath())).stream().noneMatch(a -> a.getName().equals(username))) {
            log.debug("User " + user.getName()+ " isn't member to group.");
            userManager.assignUserToGroup(user.getName(), group.getPath());
        }

        // USER IS SYNC
        usersClient.updateUserAttributes(user.getId(), new User().status(Resource.StatusEnum.READY));
        return group.technicalUser(user);
    }


    private List<Assignation> get_group_user_manager_members(Group user_manager_group) {
        List<Assignation> members = user_manager_group.getMembers();
        return (members == null) ? new ArrayList<>() : members;
    }


    public String generateSecureRandomPassword() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        return RandomStringUtils.random( 15, characters );
    }

}
