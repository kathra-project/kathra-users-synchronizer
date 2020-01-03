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

import org.apache.commons.lang3.StringUtils;
import org.kathra.binaryrepositorymanager.client.BinaryRepositoryManagerClient;
import org.kathra.core.model.*;
import org.kathra.pipelinemanager.client.PipelineManagerClient;
import org.kathra.pipelinemanager.model.Credential;
import org.kathra.resourcemanager.client.BinaryRepositoriesClient;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.KeyPairsClient;
import org.kathra.resourcemanager.client.UsersClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.sourcemanager.model.Folder;
import org.kathra.usermanager.client.UserManagerClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.security.AuthentificationUtils;
import org.kathra.utils.serialization.GsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
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

    SourceManagerClient sourceManager;
    PipelineManagerClient pipelineManager;
    UserManagerClient userManager;
    BinaryRepositoriesClient binaryRepositoriesClient;
    BinaryRepositoryManagerClient repositoryManagerNexus;
    BinaryRepositoryManagerClient repositoryManagerHarbor;
    GroupsClient groupsClient;
    UsersClient usersClient;
    KeyPairsClient keyPairsClient;;
    private List<org.kathra.core.model.KeyPair> keyPairsExisting;

    public UserSynchronizerManager(SourceManagerClient sourceManager, PipelineManagerClient pipelineManager,
            UserManagerClient userManager, BinaryRepositoryManagerClient repositoryManagerNexus, BinaryRepositoryManagerClient repositoryManagerHarbor, GroupsClient groupsClient,
            KeyPairsClient keyPairsClient, BinaryRepositoriesClient binaryRepositoriesClient) throws ApiException {

        this.sourceManager = sourceManager;
        this.pipelineManager = pipelineManager;
        this.userManager = userManager;
        this.repositoryManagerNexus = repositoryManagerNexus;
        this.repositoryManagerHarbor = repositoryManagerHarbor;
        this.binaryRepositoriesClient = binaryRepositoriesClient;
        this.groupsClient = groupsClient;
        this.keyPairsClient = keyPairsClient;

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
        org.kathra.core.model.KeyPair keyAdded = keyPairsClient.addKeyPair(kathraKeyPair);
        return keyAdded;
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
        pipelineManager.createFolder(group.getPath() + "/components");
        log.debug("Creating folder OK");
        pipelineManager
                .addMembership(new Membership().memberName(group.getPath()).memberType(Membership.MemberTypeEnum.GROUP)
                        .path(group.getPath() + "/components").role(Membership.RoleEnum.GUEST));
        log.debug("Add membership OK");
        Credential credential = new Credential();
        credential.path(group.getPath() + "/components");
        credential.credentialId(group.getId());
        credential.username(group.getPath() + " - " + group.getId());
        log.debug("Username " + group.getPath() + " - " + group.getId());
        credential.description("SSH Pull Key");
        credential.privateKey(keyPair.getPrivateKey());
        pipelineManager.addCredential(credential);
        log.debug("Add credential OK");
    }

    private Group syncBinaryRespositories(Group group) throws ApiException {
        Group groupWithDetails = groupsClient.getGroup(group.getId());
        List<BinaryRepository> list = new ArrayList<>();

        try {
            for (BinaryRepository repo : groupWithDetails.getBinaryRepositories()) {
                list.add(binaryRepositoriesClient.getBinaryRepository(repo.getId()));
            }
        } catch (Exception e) {
            log.error("Error during get repositories for group "+group.getPath());
            e.printStackTrace();
        }

        for (BinaryRepository.TypeEnum type : BinaryRepository.TypeEnum.values()) {
            try {
                BinaryRepository repository = list.stream().filter(b -> b.getType().equals(type)).findFirst().orElse(null);
                if (repository == null) {
                    repository = createBinaryRepositoryInDb(group, type);
                }
                if (!repository.getStatus().equals(Resource.StatusEnum.READY)) {
                    createBinaryRepositoryIntoProvider(repository);
                    defineTechnicalUserAsMembership(repository);
                    binaryRepositoriesClient.updateBinaryRepositoryAttributes(repository.getId(), new BinaryRepository().status(Resource.StatusEnum.READY));
                }
            } catch (Exception e) {
                log.error("Error during sync repository "+type.toString()+" for group "+group.getPath());
                e.printStackTrace();
            }
        }

        return group;
    }

    private BinaryRepository createBinaryRepositoryInDb(Group group, BinaryRepository.TypeEnum type) throws ApiException {
        BinaryRepository binaryRepository = new BinaryRepository().type(type).name(group + "/" + type.getValue()).group(group);
        return binaryRepositoriesClient.addBinaryRepository(binaryRepository);
    }

    private BinaryRepository createBinaryRepositoryIntoProvider(BinaryRepository binaryRepository) throws Exception {
        BinaryRepositoryManagerClient provider = getBinaryRepositoryManagerProvider(binaryRepository);
        BinaryRepository binaryRepositoryWithUrl = provider.addBinaryRepository(binaryRepository);
        if (StringUtils.isAllEmpty(binaryRepositoryWithUrl.getUrl())) {
            throw new Exception("BinaryRepository's URL should be defined");
        }
        if (StringUtils.isAllEmpty(binaryRepositoryWithUrl.getProvider())) {
            throw new Exception("BinaryRepository's Provider should be defined");
        }
        if (StringUtils.isAllEmpty(binaryRepositoryWithUrl.getProviderId())) {
            throw new Exception("BinaryRepository's ProviderId should be defined");
        }

        return binaryRepository;
    }

    private BinaryRepositoryManagerClient getBinaryRepositoryManagerProvider(BinaryRepository binaryRepository) {
        BinaryRepositoryManagerClient provider;
        switch(binaryRepository.getType()) {
            case HELM:
            case DOCKER_IMAGE:
                provider = this.repositoryManagerHarbor;
                break;
            case JAVA:
            case PYTHON:
                provider = this.repositoryManagerNexus;
                break;
            default:
                throw new IllegalArgumentException();
        }
        return provider;
    }

    private void defineTechnicalUserAsMembership(BinaryRepository binaryRepository) throws ApiException {
        BinaryRepositoryManagerClient provider = getBinaryRepositoryManagerProvider(binaryRepository);
        Group groupWithDetails = groupsClient.getGroup(binaryRepository.getGroup().getId());
        String group_path_clean = groupWithDetails.getPath().replace("/kathra-projects/", "");

        if (groupWithDetails.getTechnicalUser() == null || groupWithDetails.getTechnicalUser().getId() == null) {
            log.debug("Group " + groupWithDetails.getPath() + " doesn't have technicalUser, create new one ");
            syncTechnicalUser(groupWithDetails);
        }
        User user = usersClient.getUser(groupWithDetails.getTechnicalUser().getId());
        Membership membership = new Membership().memberName(user.getName())
                                                .memberType(Membership.MemberTypeEnum.USER)
                                                .role(Membership.RoleEnum.MANAGER)
                                                .path(group_path_clean.split("/")[0]);
        provider.addBinaryRepositoryMembership(binaryRepository.getId(), membership);
    }

    private void syncTechnicalUser(Group group) throws ApiException {
        String username=group.getName()+"_technicalUser";
        User user = usersClient.getUsers().stream().filter(u -> u.getName().equals(username)).findFirst().orElse(null);

        // CREATE IN DB IF DOESN'T EXIST
        if (user == null ) {
            log.debug("User " + user.getName()+ " not found in db.. create new ones");
            byte[] array = new byte[7];
            new Random().nextBytes(array);
            String password = new String(array, Charset.forName("UTF-8"));
            user = new User().name(username).password(password);
            User userWithId = usersClient.addUser(user);
            group.technicalUser(userWithId);
            groupsClient.updateGroupAttributes(group.getId(), new Group().technicalUser(user));
        }
        // CREATE IN USERMANAGER IF DOESN'T EXIST
        if (userManager.getUser(user.getName()) == null) {
            log.debug("User " + user.getName()+ " not found in usermanager.. create new ones");
            if (user.getPassword() == null) {
                throw new IllegalStateException("Technical should contains password");
            }
            userManager.createUser(user);
            //usersClient.updateUserAttributes(user.getId(), userCreatedInUserManager);
        }


        // JOIN TO GROUP IN USERMANAGER IF HE IS NOT A MEMBER
        if (get_group_user_manager_members(userManager.getGroup(group.getPath())).stream().noneMatch(a -> a.getName().equals(username))) {
            log.debug("User " + user.getName()+ " isn't member to group.");
            userManager.assignUserToGroup(user.getName(), group.getPath());
        }
    }

    @Deprecated
    private Group syncBinaryRespositoryManager(Group group) throws ApiException, NoSuchAlgorithmException {
        String group_path = group.getPath();
        String group_path_clean = group_path.replace("/kathra-projects/", "");
        log.debug("Cleaned group path: " + group_path_clean);
        String[] split = group_path_clean.split("/");

        log.debug("Splitted " + Arrays.asList(split));
        if (split[0] == null || split[0].isEmpty() || split[0].equals("/")) {
            log.error("Cannot create repository. Empty path: " + split[0] == null ? "NULL" : split[0].toString());
            throw new ApiException(
                    "Cannot create repository. Empty path: " + split[0] == null ? "NULL" : split[0].toString());
        }
        log.debug("Going to add repository " + split[0]);
        BinaryRepository binaryRepository = repositoryManagerHarbor
                .addBinaryRepository(new BinaryRepository().name(split[0]));
        log.debug("Created container repo");
        log.debug(binaryRepository.toString());
        log.debug(binaryRepository.getName());
        log.debug("" + binaryRepository.getId());
        repositoryManagerHarbor.addBinaryRepositoryMembership(binaryRepository.getId().toString(),
                new Membership().memberName("jenkins.harbor").memberType(Membership.MemberTypeEnum.USER)
                        .role(Membership.RoleEnum.CONTRIBUTOR).path(split[0]));
        return group;
    }

    private Group syncSourceManagerFolder(Group group, org.kathra.core.model.KeyPair keyPair)
            throws ApiException {
        sourceManager.createFolder(new Folder().path(group.getPath() + "/components"));
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
            syncBinaryRespositories(group_to_sync);
            /*
            syncBinaryRespositoryManager(group_to_sync);
            groupsClient.updateGroupAttributes(group_to_sync.getId(),
                    new Group().binaryRepositoryStatus(Group.BinaryRepositoryStatusEnum.READY));
             */
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

    private List<Membership> get_source_manager_memberships_from_group_path(String group_path) throws ApiException {
        String path = group_path + "/components";
        String user_type = String.valueOf(Membership.MemberTypeEnum.USER);
        List<Membership> members = sourceManager.getMemberships(path, user_type);
        if (members == null)
            return new ArrayList<Membership>();
        return members;
    }

    private void synchronizeUsers(Group group_to_sync, List<Assignation> users_source, List<Membership> users_dest)
            throws ApiException {

        List<Membership> users_to_add = new ArrayList<Membership>();
        Map<String, Membership> source_manager_members = users_dest.stream()
                .collect(Collectors.toMap(Membership::getMemberName, g -> g));

        for (Assignation member : users_source) {
            if (!source_manager_members.containsKey(member.getName())) {
                Membership newMember = new Membership();
                newMember.setMemberName(member.getName());
                newMember.setRole(Membership.RoleEnum.MANAGER);
                newMember.setPath(group_to_sync.getPath() + "/components");
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
                userToDelete.setPath(group_to_sync.getPath() + "/components");
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

    private void synchronizeSourceManagerUsersOfGroup(Group user_manager_group, Group group_to_sync)
            throws ApiException {
        try {
            List<Assignation> user_manager_group_members = get_group_user_manager_members(user_manager_group);
            List<Membership> source_manager_group_members = get_source_manager_memberships_from_group_path(
                    group_to_sync.getPath());
            synchronizeUsers(group_to_sync, user_manager_group_members, source_manager_group_members);
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
            synchronizeSourceManagerUsersOfGroup(groupFromUserManager, groupToSync);
            return true;
        }
        if (groupToSync == null) {
            log.debug("Creating new group " + group_path);
            groupToSync = groupsClient.addGroup(groupFromUserManager);
        }

        syncTechnicalUser(groupToSync);
        org.kathra.core.model.KeyPair keyPair = getKeyOrGenerateOne(groupToSync);
        tryToSynchronizeGroupPipeline(groupToSync, keyPair);
        tryToSynchronizeGroupBinary(groupToSync);
        tryToSynchronizeSourceManager(groupToSync, keyPair);

        synchronizeSourceManagerUsersOfGroup(groupFromUserManager, groupToSync);
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
