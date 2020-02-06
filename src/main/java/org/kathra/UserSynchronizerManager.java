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

import org.kathra.core.model.*;
import org.kathra.pipelinemanager.client.PipelineManagerClient;
import org.kathra.pipelinemanager.model.Credential;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.KeyPairsClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.sourcemanager.model.Folder;
import org.kathra.synchronize.services.SyncBinaryRepository;
import org.kathra.synchronize.services.SyncTechnicalUser;
import org.kathra.usermanager.client.UserManagerClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.security.AuthentificationUtils;
import org.kathra.utils.serialization.GsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Jérémy Guillemot <Jeremy.Guillemot@kathra.org>
 */
public class UserSynchronizerManager {

    private Logger log = LoggerFactory.getLogger("UserSynchronizerManager");

    final private SourceManagerClient sourceManager;
    final private PipelineManagerClient pipelineManager;
    final private UserManagerClient userManager;
    final private GroupsClient groupsClient;
    final private KeyPairsClient keyPairsClient;;
    final private List<org.kathra.core.model.KeyPair> keyPairsExisting;
    final private SyncBinaryRepository syncBinaryRepository;
    final private SyncTechnicalUser syncTechnicalUser;

    final private String SOURCE_MANAGER_COMPONENT_PATH="components";
    final private String PIPELINE_MANAGER_COMPONENT_PATH="components";

    public UserSynchronizerManager(SourceManagerClient sourceManager, PipelineManagerClient pipelineManager,
                                   UserManagerClient userManager,
                                   GroupsClient groupsClient,
                                   KeyPairsClient keyPairsClient, SyncBinaryRepository syncBinaryRepository, SyncTechnicalUser syncTechnicalUser) throws ApiException {

        this.sourceManager = sourceManager;
        this.pipelineManager = pipelineManager;
        this.userManager = userManager;
        this.groupsClient = groupsClient;
        this.keyPairsClient = keyPairsClient;
        this.syncBinaryRepository = syncBinaryRepository;
        this.syncTechnicalUser = syncTechnicalUser;

        log.debug("Getting keys ");
        keyPairsExisting = keyPairsClient.getKeyPairs();
        log.debug("Keys gotten " + keyPairsExisting.toString());
    }

    public void initKathra() throws ApiException {
        log.info("Init kathra");
        Folder kathraProjectsFolder = new Folder().path("kathra-projects");
        sourceManager.createFolder(kathraProjectsFolder);
        pipelineManager.createFolder(kathraProjectsFolder.getPath());
        log.info("Init kathra - OK");
    }

    private org.kathra.core.model.KeyPair getKeyOrGenerateOne(Group group)
            throws NoSuchAlgorithmException, ApiException {
        Optional<org.kathra.core.model.KeyPair> keyPair = getKeyPairFromGroup(group);
        log.debug("getting key for  " + group.getId());
        log.debug(keyPair.toString());
        if (keyPair.isPresent()) {
            log.debug("Returning existing key");
            return keyPair.get();
        }
        org.kathra.core.model.KeyPair kathraKeyPair = new org.kathra.core.model.KeyPair();
        kathraKeyPair.group(new Group().id(group.getId()));
        KeyPair keyPairGenerated = generateKeyPair();
        kathraKeyPair.setPrivateKey(AuthentificationUtils.formatPrivateKey(keyPairGenerated.getPrivate()));
        kathraKeyPair.setPublicKey(AuthentificationUtils.formatPublicKey(keyPairGenerated.getPublic()));
        log.debug("generated key ");
        log.debug(kathraKeyPair == null ? "NULL" : kathraKeyPair.toString());
        return keyPairsClient.addKeyPair(kathraKeyPair);
    }

    private boolean groupPipelineShouldBeSync(Group group) {
        if (group.getPipelineFolderStatus() != null
                && group.getPipelineFolderStatus().equals(Group.PipelineFolderStatusEnum.READY))
            return false;
        return true;
    }

    private boolean groupBinaryRepoShouldBeSync(Group group_to_sync) {
        if (group_to_sync.getBinaryRepositoryStatus() != null
                && group_to_sync.getBinaryRepositoryStatus().equals(Group.BinaryRepositoryStatusEnum.READY))
            return false;
        return true;

    }

    private boolean groupSourceManagerShouldBeSync(Group group_to_sync) {
        if (group_to_sync.getSourceRepositoryStatus() != null
                && group_to_sync.getSourceRepositoryStatus().equals(Group.SourceRepositoryStatusEnum.READY))
            return false;
        return true;
    }

    private void syncGroupPipelineManager(Group group, org.kathra.core.model.KeyPair keyPair)
            throws ApiException, NoSuchAlgorithmException {
        syncGroupPipelineManagerPath(group, keyPair, PIPELINE_MANAGER_COMPONENT_PATH);
        syncGroupPipelineManagerPath(group, keyPair, "packages");
    }

    private void syncGroupPipelineManagerPath(Group group, org.kathra.core.model.KeyPair keyPair, String path) throws ApiException {
        pipelineManager.createFolder(group.getPath() + "/" + path);
        log.debug("Creating folder OK");
        pipelineManager
                .addMembership(new Membership().memberName(group.getPath()).memberType(Membership.MemberTypeEnum.GROUP)
                        .path(group.getPath() + "/"+path).role(Membership.RoleEnum.GUEST));
        log.debug("Add membership OK");
        Credential credential = new Credential();
        credential.path(group.getPath() + "/"+path);
        credential.credentialId(group.getId());
        credential.username(group.getPath() + " - " + group.getId());
        log.debug("Username " + group.getPath() + " - " + group.getId());
        credential.description("SSH Pull Key");
        credential.privateKey(keyPair.getPrivateKey());
        pipelineManager.addCredential(credential);
        log.debug("Add credential OK");

    }

    private Group syncSourceManagerFolder(Group group, org.kathra.core.model.KeyPair keyPair)
            throws ApiException {
         syncSourceManagerFolder(group, keyPair, SOURCE_MANAGER_COMPONENT_PATH);
         //syncSourceManagerFolder(group, keyPair, "packages");
         return group;
    }
    private Group syncSourceManagerFolder(Group group, org.kathra.core.model.KeyPair keyPair, String path)
            throws ApiException {
        String group_path = group.getPath();
        sourceManager.createFolder(new Folder().path(group_path + "/"+ path));
        SourceRepository deployKeyRepository = new SourceRepository().path(group.getPath() + "/kathra-deploy-key");
        sourceManager.createSourceRepository(deployKeyRepository, null);
        log.debug("going to add membership 'kathra-sourcemanager' to source manager on deploy key repository path "
                + deployKeyRepository.getPath());
        sourceManager.addMemberships(Collections.singletonList(new Membership().memberName("kathra-sourcemanager")
                .role(Membership.RoleEnum.MANAGER).path(deployKeyRepository.getPath())));
        sourceManager.createDeployKey(group.getId(), keyPair.getPublicKey(), deployKeyRepository.getPath());

        return group;

    }

    private void tryToSynchronizeGroupPipeline(Group group_to_sync, org.kathra.core.model.KeyPair keyPair) {
        if (!groupPipelineShouldBeSync(group_to_sync))
            return;
        log.debug("--- Synchronizing PipelineManager groups and members --- [" + group_to_sync.getPath() + "]");
        try {
            syncGroupPipelineManager(group_to_sync, keyPair);
            groupsClient.updateGroupAttributes(group_to_sync.getId(),
                    new Group().members(new ArrayList()).pipelineFolderStatus(Group.PipelineFolderStatusEnum.READY));
        } catch (Exception e) {
            log.error("Cannot sync group " + group_to_sync.getPath() + " with pipeline manger. Error: " + e.toString());
            e.printStackTrace();
        }

    }

    private void tryToSynchronizeGroupBinary(Group group_to_sync) {
        if (!groupBinaryRepoShouldBeSync(group_to_sync))
            return;
        log.debug(
                "--- Synchronizing BinaryRespositoryManager groups and members --- [" + group_to_sync.getPath() + "]");
        try {
            syncBinaryRepository.synchronize(group_to_sync);
        } catch (Exception e) {
            log.error("Cannot sync group " + group_to_sync.getPath() + " with binary repo manger. Error: "
                    + e.toString());
            e.printStackTrace();
        }
    }

    private void tryToSynchronizeSourceManager(Group group_to_sync, org.kathra.core.model.KeyPair keyPair) {
        if (!groupSourceManagerShouldBeSync(group_to_sync))
            return;
        log.debug("--- Synchronizing SourceManager groups --- [" + group_to_sync.getPath() + "]");
        try {
            syncSourceManagerFolder(group_to_sync, keyPair);
            groupsClient.updateGroupAttributes(group_to_sync.getId(), new Group().members(new ArrayList())
                    .sourceRepositoryStatus(Group.SourceRepositoryStatusEnum.READY));
        } catch (Exception e) {
            log.error("Cannot sync group " + group_to_sync.getPath() + " with source manager. Error: " + e.toString());
            e.printStackTrace();
        }
    }

    private List<Membership> get_source_manager_memberships_from_group_path(String path) throws ApiException {
        String user_type = String.valueOf(Membership.MemberTypeEnum.USER);
        List<Membership> members = sourceManager.getMemberships(path, user_type);
        if (members == null)
            return new ArrayList<Membership>();
        return members;
    }

    private void synchronizeUsers(Group group_to_sync, List<Assignation> users_source, List<Membership> users_dest, String path)
            throws ApiException {

        List<Membership> users_to_add = new ArrayList<Membership>();
        Map<String, Membership> source_manager_members = users_dest.stream()
                .collect(Collectors.toMap(Membership::getMemberName, g -> g));

        for (Assignation member : users_source) {
            if (!source_manager_members.containsKey(member.getName())) {
                Membership newMember = new Membership();
                newMember.setMemberName(member.getName());
                newMember.setRole(Membership.RoleEnum.MANAGER);
                newMember.setPath(group_to_sync.getPath() + "/" + path);
                users_to_add.add(newMember);
            }
            source_manager_members.remove(member.getName());
        }

        if (!users_to_add.isEmpty()) {
            log.debug("Adding users: " + GsonUtils.toJson(users_to_add));
            sourceManager.addMemberships(users_to_add);
        }

        if (!source_manager_members.isEmpty()) {
            List<Membership> users_to_delete = new ArrayList<Membership>(source_manager_members.values());
            for (Membership userToDelete : users_to_delete)
                userToDelete.setPath(group_to_sync.getPath() + "/" + path);
            log.debug("Removing users: " + GsonUtils.toJson(users_to_delete));
            sourceManager.deleteMemberships(users_to_delete);
        }
    }

    private boolean isGroupReady(Group group) {
        return group != null && group.getStatus().equals(Resource.StatusEnum.READY);
    }

    private List<Assignation> get_group_user_manager_members(Group user_manager_group) {
        List<Assignation> members = user_manager_group.getMembers();
        return (members == null) ? new ArrayList<>() : members;
    }

    private void synchronizeSourceManagerUsersOfGroup(Group user_manager_group, Group group_to_sync, String path)
            throws ApiException {
        try {
            List<Assignation> user_manager_group_members = get_group_user_manager_members(user_manager_group);
            List<Membership> source_manager_group_members = get_source_manager_memberships_from_group_path(group_to_sync.getPath()+"/"+path);
            synchronizeUsers(group_to_sync, user_manager_group_members, source_manager_group_members, path);
        } catch (Exception e) {
            log.error("Cannot synchronize users of group " + group_to_sync.getPath() + ". Error: " + e.toString());
            e.printStackTrace();
        }
    }

    public void synchronizeGroups() throws ApiException {
        log.info("Synchronizing groups");

        List<Group> groupsFromUserManager = userManager.getGroups();
        List<Group> groupsFromResourceManager = groupsClient.getGroups();

        log.debug("Groups from user manager: " + GsonUtils.toJson(groupsFromUserManager));
        log.debug("Groups from resource manager: " + GsonUtils.toJson(groupsFromResourceManager));

        Map<String, Group> groupsFromResourceManagers = groupsFromResourceManager.stream()
                .collect(Collectors.toMap(Group::getPath, g -> g));
        log.debug("Groups from resource manager (map): " + groupsFromResourceManagers.toString());
        Exception exceptionOccured = null;
        for (Group groupFromUserManager : groupsFromUserManager) {
            try {
                syncGroup(groupsFromResourceManagers, groupFromUserManager);
            } catch (Exception e) {
                log.error("Cannot synchronize group " + groupFromUserManager.getPath() + ". Error: " + e.toString());
                exceptionOccured = e;
            }
        }
        if (exceptionOccured != null) {
            exceptionOccured.printStackTrace();
            if (exceptionOccured instanceof ApiException)
                throw (ApiException) exceptionOccured;
            if (exceptionOccured instanceof RuntimeException)
                throw (RuntimeException) exceptionOccured;
        }
    }

    private boolean syncGroup(Map<String, Group> groupsFromResourceManagers, Group groupFromUserManager) throws ApiException, NoSuchAlgorithmException {
        String group_path = groupFromUserManager.getPath();
        log.debug("SYNC GROUP loop; Group: " + group_path);
        Group groupToSync;

        groupToSync = groupsFromResourceManagers.get(group_path);
        log.debug("group found? " + (groupToSync == null ? "NO" : group_path));
        if (isGroupReady(groupToSync)) {
            log.info("Group " + group_path + " is ready. Just sync users ");
            synchronizeSourceManagerUsersOfGroup(groupFromUserManager, groupToSync, SOURCE_MANAGER_COMPONENT_PATH);
            return true;
        }
        if (groupToSync == null) {
            log.debug("Creating new group " + group_path);
            groupToSync = groupsClient.addGroup(groupFromUserManager);
        }

        syncTechnicalUser.syncTechnicalUser(groupToSync);
        org.kathra.core.model.KeyPair keyPair = getKeyOrGenerateOne(groupToSync);
        tryToSynchronizeGroupPipeline(groupToSync, keyPair);
        tryToSynchronizeGroupBinary(groupToSync);
        tryToSynchronizeSourceManager(groupToSync, keyPair);

        synchronizeSourceManagerUsersOfGroup(groupFromUserManager, groupToSync, SOURCE_MANAGER_COMPONENT_PATH);
        return false;
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(4096);
        return kpg.generateKeyPair();
    }

    private Optional<org.kathra.core.model.KeyPair> getKeyPairFromGroup(Group group) throws ApiException {
        return keyPairsExisting.parallelStream().filter(keyPair -> keyPair.getGroup() != null
                && keyPair.getGroup().getId() != null && keyPair.getGroup().getId().equals(group.getId())).findFirst();
    }
}
