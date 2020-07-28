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

import org.apache.commons.lang3.StringUtils;
import org.kathra.binaryrepositorymanager.client.BinaryRepositoryManagerClient;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.BinaryRepositoriesClient;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.UsersClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class SyncBinaryRepository {

    private Logger log = LoggerFactory.getLogger("SyncBinaryRepository");

    final private BinaryRepositoriesClient binaryRepositoriesClient;
    final private BinaryRepositoryManagerClient repositoryManagerNexus;
    final private BinaryRepositoryManagerClient repositoryManagerHarbor;
    final private GroupsClient groupsClient;
    final private UsersClient usersClient;

    public SyncBinaryRepository(
            BinaryRepositoryManagerClient repositoryManagerNexus, BinaryRepositoryManagerClient repositoryManagerHarbor,
            GroupsClient groupsClient, UsersClient usersClient, BinaryRepositoriesClient binaryRepositoriesClient) {
        this.repositoryManagerNexus = repositoryManagerNexus;
        this.repositoryManagerHarbor = repositoryManagerHarbor;
        this.binaryRepositoriesClient = binaryRepositoriesClient;
        this.groupsClient = groupsClient;
        this.usersClient = usersClient;

        log.debug("Getting keys ");
    }

    public Group synchronize(Group group) throws Exception {
        Group groupWithDetails = groupsClient.getGroup(group.getId());

        AtomicReference<Exception> exception = new AtomicReference<>();
        List<BinaryRepository> list = groupWithDetails.getBinaryRepositories().parallelStream().map(b -> {
            try {
                return binaryRepositoriesClient.getBinaryRepository(b.getId());
            } catch (Exception e) {
                log.error("Error during get repository "+b.getId()+" for group "+groupWithDetails.getPath());
                exception.set(e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());

        if (exception.get() != null) {
            exception.get().printStackTrace();
            throw exception.get();
        }

        for (BinaryRepository.TypeEnum type : BinaryRepository.TypeEnum.values()) {
            try {
                BinaryRepository repository = list.stream().filter(b -> b.getType().equals(type)).findFirst().orElse(null);
                if (repository == null) {
                    repository = createBinaryRepositoryInDb(groupWithDetails, type);
                }
                initBinaryRepositoryIntoManager(repository.group(groupWithDetails));
                if (!Resource.StatusEnum.READY.equals(repository.getStatus())) {
                    binaryRepositoriesClient.updateBinaryRepositoryAttributes(repository.getId(), new BinaryRepository().status(Resource.StatusEnum.READY));
                }
            } catch (Exception e) {
                log.error("Error during sync repository "+type.toString()+" for group "+groupWithDetails.getPath());
                e.printStackTrace();
            }
        }
        return groupWithDetails;
    }

    private BinaryRepository initBinaryRepositoryIntoManager(BinaryRepository binaryRepository) throws Exception {
        // if provider not defined, create new one
        if (StringUtils.isEmpty(binaryRepository.getProviderId())) {
            binaryRepository = createBinaryRepositoryIntoProvider(binaryRepository);
            defineTechnicalUserAsMembership(binaryRepository);
            defineGroupAsMembership(binaryRepository);
        } else {
            BinaryRepositoryManagerClient provider = getBinaryRepositoryManagerProvider(binaryRepository);
            BinaryRepository result = null;
            try {
                result = provider.getBinaryRepository(binaryRepository.getProviderId());
            } catch (ApiException e) {
                if (KathraException.ErrorCode.NOT_FOUND.getCode() == e.getCode()) {
                    // Repository not found, create new one
                    binaryRepository = createBinaryRepositoryIntoProvider(binaryRepository);
                }
            }
            if (result != null) {
                defineTechnicalUserAsMembership(binaryRepository);
                defineGroupAsMembership(binaryRepository);
            }
        }
        return binaryRepository;
    }


    private BinaryRepository createBinaryRepositoryInDb(Group group, BinaryRepository.TypeEnum type) throws ApiException {
        String name;
        switch (type) {
            case PYTHON:
                name = group.getName()+"-pip";
                break;
            case JAVA:
                name = group.getName()+"-maven";
                break;
            case HELM:
                name = group.getName()+"-chartmuseum";
                break;
            case DOCKER_IMAGE:
                name = group.getName()+"-docker-registry";
                break;
            default:
                throw new IllegalArgumentException("Not managed");
        }
        BinaryRepository binaryRepository = new BinaryRepository().type(type).name(name).group(group);
        return binaryRepositoriesClient.addBinaryRepository(binaryRepository);
    }

    private BinaryRepository createBinaryRepositoryIntoProvider(BinaryRepository binaryRepository) throws Exception {
        if (binaryRepository.getGroup() == null && StringUtils.isEmpty(binaryRepository.getGroup().getName())) {
            throw new IllegalArgumentException("Group's name undefined");
        }
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
        BinaryRepository patch = new BinaryRepository().providerId(binaryRepositoryWithUrl.getProviderId()).provider(binaryRepositoryWithUrl.getProvider()).url(binaryRepositoryWithUrl.getUrl());
        binaryRepositoriesClient.updateBinaryRepositoryAttributes(binaryRepository.getId(), patch);
        binaryRepository.url(patch.getUrl()).providerId(patch.getProviderId()).provider(binaryRepository.getProvider());
        return binaryRepository;
    }

    private BinaryRepositoryManagerClient getBinaryRepositoryManagerProvider(BinaryRepository binaryRepository) {
        switch(binaryRepository.getType()) {
            case HELM:
            case DOCKER_IMAGE:
                return this.repositoryManagerHarbor;
            case JAVA:
            case PYTHON:
                return this.repositoryManagerNexus;
            default:
                throw new IllegalArgumentException("Not implemented");
        }
    }

    private void defineGroupAsMembership(BinaryRepository binaryRepository) throws ApiException {

        BinaryRepositoryManagerClient provider = getBinaryRepositoryManagerProvider(binaryRepository);
        Group groupWithDetails = groupsClient.getGroup(binaryRepository.getGroup().getId());
        Membership membership = new Membership().memberName(groupWithDetails.getPath())
                .memberType(Membership.MemberTypeEnum.GROUP)
                .role(Membership.RoleEnum.MANAGER);

        provider.addBinaryRepositoryMembership(binaryRepository.getProviderId(), membership);
    }

    private void defineTechnicalUserAsMembership(BinaryRepository binaryRepository) throws ApiException {
        if (binaryRepository.getProviderId() == null) {
            throw new IllegalStateException("ProviderId for binaryrepository '"+binaryRepository.getId()+"' undefined");
        }
        BinaryRepositoryManagerClient provider = getBinaryRepositoryManagerProvider(binaryRepository);
        Group groupWithDetails = groupsClient.getGroup(binaryRepository.getGroup().getId());
        if (groupWithDetails.getTechnicalUser() == null || groupWithDetails.getTechnicalUser().getId() == null) {
            log.error("Group " + groupWithDetails.getPath() + " doesn't have technicalUser");
            throw new IllegalStateException("Not technicalUser existings");
        }
        User user = usersClient.getUser(groupWithDetails.getTechnicalUser().getId());
        Membership membership = new Membership().memberName(user.getName())
                .memberType(Membership.MemberTypeEnum.USER)
                .role(Membership.RoleEnum.MANAGER);

        provider.addBinaryRepositoryMembership(binaryRepository.getProviderId(), membership);
    }

    private List<Assignation> get_group_user_manager_members(Group user_manager_group) {
        List<Assignation> members = user_manager_group.getMembers();
        return (members == null) ? new ArrayList<>() : members;
    }

}
