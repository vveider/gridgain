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

package org.apache.ignite.internal.processors.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.events.DiscoveryEvent;
import org.apache.ignite.internal.events.DiscoveryCustomEvent;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.lang.IgniteUuid;
import org.apache.ignite.testframework.GridTestNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

/**
 * Tests of {@link ServiceDeploymentProcessId}.
 */
@RunWith(Parameterized.class)
public class ServiceDeploymentProcessIdSelfTest {
    /** Tests discovery event type. */
    private final String testLabel;

    /** Tests discovery event. */
    private final DiscoveryEvent evt;

    /** Tests topology version. */
    private final AffinityTopologyVersion topVer;

    /** Subject under test. */
    private final ServiceDeploymentProcessId sut;

    /**
     * @param data Tests data.
     */
    public ServiceDeploymentProcessIdSelfTest(String testLabel, IgniteBiTuple<DiscoveryEvent, AffinityTopologyVersion> data) {
        this.testLabel = testLabel;
        this.evt = data.get1();
        this.topVer = data.get2();

        if (evt instanceof DiscoveryCustomEvent)
            this.sut = new ServiceDeploymentProcessId(((DiscoveryCustomEvent)evt).customMessage().id());
        else
            this.sut = new ServiceDeploymentProcessId(topVer);
    }

    /**
     * @return Tests data.
     */
    @Parameterized.Parameters(name = "Test event class={0}")
    public static Collection<Object[]> instancesToTest() {
        DiscoveryEvent evt = new DiscoveryEvent(
            new GridTestNode(UUID.randomUUID()), "", 10, new GridTestNode(UUID.randomUUID()));

        DiscoveryCustomEvent customEvt = new DiscoveryCustomEvent();

        customEvt.customMessage(
            new ServiceChangeBatchRequest(Collections.singletonList(
                new ServiceUndeploymentRequest(IgniteUuid.randomUuid())))
        );

        ClusterNode node = new GridTestNode(UUID.randomUUID());

        customEvt.node(node);
        customEvt.eventNode(node);

        return Arrays.asList(new Object[][] {
            {customEvt.getClass().getSimpleName(), new IgniteBiTuple<>(customEvt, new AffinityTopologyVersion(ThreadLocalRandom.current().nextLong()))},
            {evt.getClass().getSimpleName(), new IgniteBiTuple<>(evt, new AffinityTopologyVersion(ThreadLocalRandom.current().nextLong()))}});
    }

    /** */
    @Test
    public void topologyVersion() {
        System.out.println("test with event type: " + testLabel);
        System.out.println("event = " + evt);
        System.out.println("topology version = " + topVer);

        AffinityTopologyVersion topVer = evt instanceof DiscoveryCustomEvent ? null : this.topVer;

        assertEquals(topVer, sut.topologyVersion());
    }

    /** */
    @Test
    public void requestId() {
        System.out.println("test with event type: " + testLabel);
        System.out.println("event = " + evt);
        System.out.println("topology version = " + topVer);

        IgniteUuid reqId = evt instanceof DiscoveryCustomEvent ? ((DiscoveryCustomEvent)evt).customMessage().id() : null;

        assertEquals(reqId, sut.requestId());
    }
}