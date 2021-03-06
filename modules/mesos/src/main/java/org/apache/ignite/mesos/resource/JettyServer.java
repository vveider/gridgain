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

package org.apache.ignite.mesos.resource;

import org.apache.ignite.mesos.ClusterProperties;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

/**
 * Embedded jetty server.
 */
public class JettyServer {
    /** */
    private Server server;

    /**
     * Starts jetty server.
     *
     * @param handler Handler.
     * @param props Cluster properties.
     * @throws Exception If failed.
     */
    public void start(Handler handler, ClusterProperties props) throws Exception {
        if (server == null) {
            server = new Server();

            ServerConnector connector = new ServerConnector(server);

            connector.setHost(props.httpServerHost());
            connector.setPort(props.httpServerPort());
            connector.setIdleTimeout(props.idleTimeout());

            server.addConnector(connector);
            server.setHandler(handler);

            server.start();
        }
        else
            throw new IllegalStateException("Jetty server has already been started.");
    }

    /**
     * Stops server.
     *
     * @throws Exception If failed.
     */
    public void stop() throws Exception {
        if (server != null)
            server.stop();
        else
            throw new IllegalStateException("Jetty server has not yet been started.");
    }
}