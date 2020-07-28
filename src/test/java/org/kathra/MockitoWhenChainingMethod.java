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

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockitoWhenChainingMethod<T> implements Answer<T> {

    public T processReturnObject(T object) {
        return object;
    }

    @Override
    public T answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        Object object_to_return = args[0];
        Logger logger = LoggerFactory.getLogger("MockitoWhenChainingMethod_" + object_to_return.getClass().getName());

        logger.debug("Returning answer");
        logger.debug(object_to_return == null ? "NULL" : object_to_return.toString());
        return processReturnObject((T) object_to_return);
    }
}
