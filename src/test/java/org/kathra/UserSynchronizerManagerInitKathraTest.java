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
import org.kathra.core.model.Group;
import org.kathra.core.model.KeyPair;
import org.kathra.pipelinemanager.client.PipelineManagerClient;
import org.kathra.resourcemanager.client.KeyPairsClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.sourcemanager.model.Folder;
import org.kathra.utils.ApiException;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author Jorge Sainz Raso <jorge.sainzraso@kathra.org>
 */

public class UserSynchronizerManagerInitKathraTest  extends UserSynchronizerTests{

    @Test
    public void post_folders_to_pipeline_manager() throws ApiException {
        MockitoAnnotations.initMocks(this);

        List<KeyPair> mockedKeyPairs = new ArrayList<KeyPair>(1);
        keyPairsClient = mock(KeyPairsClient.class);
        pipelineManager = mock(PipelineManagerClient.class);
        sourceManager = mock(SourceManagerClient.class);
        when(keyPairsClient.getKeyPairs()).thenReturn(mockedKeyPairs);

        init_user_sync_manager();

        userSynchronizerManager.initKathra();
        verify(pipelineManager, times(1)).createFolder(argThat((String path) -> path == "kathra-projects"));
    }

    @Test
    public void post_folders_to_source_manager() throws ApiException {
        MockitoAnnotations.initMocks(this);

        List<KeyPair> mockedKeyPairs = new ArrayList<KeyPair>(1);
        keyPairsClient = mock(KeyPairsClient.class);
        pipelineManager = mock(PipelineManagerClient.class);
        sourceManager = mock(SourceManagerClient.class);
        when(keyPairsClient.getKeyPairs()).thenReturn(mockedKeyPairs);

        init_user_sync_manager();

        userSynchronizerManager.initKathra();
        verify(sourceManager, times(1)).createFolder(argThat((Folder folder) -> folder.getPath() == "kathra-projects"));
    }

    @Test
    public void wont_execute_source_manager_if_error_on_posting_folders_to_pipeline_manager() throws ApiException {
        MockitoAnnotations.initMocks(this);

        List<KeyPair> mockedKeyPairs = new ArrayList<KeyPair>(1);
        keyPairsClient = mock(KeyPairsClient.class);
        pipelineManager = mock(PipelineManagerClient.class);
        sourceManager = mock(SourceManagerClient.class);
        when(keyPairsClient.getKeyPairs()).thenReturn(mockedKeyPairs);

        init_user_sync_manager();

        when(sourceManager.createFolder(any(Folder.class))).thenThrow(ApiException.class);
        try {
            userSynchronizerManager.initKathra();
        } catch (ApiException e) {

        }
        verify(pipelineManager, never()).createFolder(anyString());
    }

    @Test
    public void synchronizeKathraUsers() {

    }
}