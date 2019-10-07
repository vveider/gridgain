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

package org.gridgain.dto;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.junit.Test;

/**
 * Test of cluster configuration.
 */
public class IgniteConfigurationWrapperTest {
    /** Mapper. */
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * Test serialisation/deserialization of default configuration.
     */
    @Test
    public void defaultConfiguration() throws IOException {
        try (Ignite ignite = Ignition.start(new IgniteConfiguration())) {
            IgniteConfigurationWrapper cfg0 = new IgniteConfigurationWrapper(ignite.configuration());

            String str = mapper.writeValueAsString(cfg0);

            IgniteConfigurationWrapper cfg = mapper.readValue(str, IgniteConfigurationWrapper.class);
        }
    }
}
