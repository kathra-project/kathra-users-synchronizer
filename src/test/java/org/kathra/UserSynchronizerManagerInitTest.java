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
import org.kathra.core.model.KeyPair;
import org.kathra.pipelinemanager.client.PipelineManagerClient;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.KeyPairsClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.usermanager.client.UserManagerClient;
import org.kathra.utils.ApiException;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author Jorge Sainz Raso <jorge.sainzraso@kathra.org>
 */
public class UserSynchronizerManagerInitTest {

    @Mock
    Config config;
    @Mock
    KeycloackSession keycloackSession;
    @Mock
    SourceManagerClient sourceManager;
    @Mock
    PipelineManagerClient pipelineManager;
    @Mock
    UserManagerClient userManager;
    @Mock
    BinaryRepositoryManagerClient repositoryManager;
    @Mock
    GroupsClient groupsClient;
    @Mock
    KeyPairsClient keyPairsClient;

    @Test
    public void init_manager_get_keys_pairs() throws ApiException {
        List<KeyPair> mockedKeyPairs = new ArrayList<KeyPair>(1);
        keyPairsClient = mock(KeyPairsClient.class);
        when(keyPairsClient.getKeyPairs()).thenReturn(mockedKeyPairs);

        new UserSynchronizerManager(sourceManager, pipelineManager, userManager, repositoryManager, groupsClient,
                keyPairsClient);

        verify(keyPairsClient, times(1)).getKeyPairs();

    }
}
