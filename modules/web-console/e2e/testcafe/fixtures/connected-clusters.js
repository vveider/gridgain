/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 * 
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {prepareUser, cleanupUser} from '../roles';
import {resolveUrl} from '../environment/envtools';
import {connectedClustersBadge} from '../components/connectedClustersBadge';
import {WebSocketHook} from '../mocks/WebSocketHook';
import {agentStat, FAKE_CLUSTERS} from '../mocks/agentTasks';

fixture('Connected clusters')
    .beforeEach(async (t) => {
        await t.addRequestHooks(t.ctx.ws = new WebSocketHook().use(agentStat(FAKE_CLUSTERS)));
        await prepareUser(t);
    })
    .afterEach(async (t) => {
        await cleanupUser(t);
        t.ctx.ws.destroy();
    });

test('Connected clusters badge', async (t) => {
    await t.navigateTo(resolveUrl('/settings/profile'));
    await t.expect(connectedClustersBadge.textContent).eql('2');
});
