/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.cluster.coordination.heartbeat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.curator.test.TestingServer;
import org.apache.nifi.cluster.coordination.ClusterCoordinator;
import org.apache.nifi.cluster.coordination.node.DisconnectionCode;
import org.apache.nifi.cluster.coordination.node.NodeConnectionState;
import org.apache.nifi.cluster.coordination.node.NodeConnectionStatus;
import org.apache.nifi.cluster.protocol.NodeIdentifier;
import org.apache.nifi.reporting.Severity;
import org.apache.nifi.util.NiFiProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestAbstractHeartbeatMonitor {
    private TestingServer zkServer;
    private NodeIdentifier nodeId;
    private TestFriendlyHeartbeatMonitor monitor;

    @Before
    public void setup() throws Exception {
        zkServer = new TestingServer(true);
        zkServer.start();
        nodeId = new NodeIdentifier(UUID.randomUUID().toString(), "localhost", 9999, "localhost", 8888, "localhost", null, false);
    }

    @After
    public void clear() throws IOException {
        if (zkServer != null) {
            zkServer.stop();
            zkServer.close();
        }

        if (monitor != null) {
            monitor.stop();
        }
    }

    /**
     * Verifies that a node that sends a heartbeat that indicates that it is 'connected' is asked to connect to
     * cluster if the cluster coordinator does not know about the node
     *
     * @throws InterruptedException if interrupted
     */
    @Test
    public void testNewConnectedHeartbeatFromUnknownNode() throws IOException, InterruptedException {
        final List<NodeIdentifier> requestedToConnect = Collections.synchronizedList(new ArrayList<>());
        final ClusterCoordinatorAdapter coordinator = new ClusterCoordinatorAdapter() {
            @Override
            public synchronized void requestNodeConnect(final NodeIdentifier nodeId) {
                requestedToConnect.add(nodeId);
            }
        };

        final TestFriendlyHeartbeatMonitor monitor = createMonitor(coordinator);

        // Ensure that we request the Unknown Node connect to the cluster
        final NodeHeartbeat heartbeat = createHeartbeat(nodeId, NodeConnectionState.CONNECTED);
        monitor.addHeartbeat(heartbeat);
        monitor.waitForProcessed();

        assertEquals(1, requestedToConnect.size());
        assertEquals(nodeId, requestedToConnect.get(0));
        assertEquals(1, coordinator.getEvents().size());
    }

    /**
     * Verifies that a node that sends a heartbeat that indicates that it is 'connected' if previously
     * manually disconnected, will be asked to disconnect from the cluster again.
     *
     * @throws InterruptedException if interrupted
     */
    @Test
    public void testHeartbeatFromManuallyDisconnectedNode() throws InterruptedException {
        final Set<NodeIdentifier> requestedToConnect = Collections.synchronizedSet(new HashSet<>());
        final Set<NodeIdentifier> requestedToDisconnect = Collections.synchronizedSet(new HashSet<>());
        final ClusterCoordinatorAdapter adapter = new ClusterCoordinatorAdapter() {
            @Override
            public synchronized void requestNodeConnect(final NodeIdentifier nodeId) {
                super.requestNodeConnect(nodeId);
                requestedToConnect.add(nodeId);
            }

            @Override
            public synchronized void requestNodeDisconnect(final NodeIdentifier nodeId, final DisconnectionCode disconnectionCode, final String explanation) {
                super.requestNodeDisconnect(nodeId, disconnectionCode, explanation);
                requestedToDisconnect.add(nodeId);
            }
        };

        final TestFriendlyHeartbeatMonitor monitor = createMonitor(adapter);

        adapter.requestNodeDisconnect(nodeId, DisconnectionCode.USER_DISCONNECTED, "Unit Testing");
        monitor.addHeartbeat(createHeartbeat(nodeId, NodeConnectionState.CONNECTED));
        monitor.waitForProcessed();

        assertEquals(1, requestedToDisconnect.size());
        assertEquals(nodeId, requestedToDisconnect.iterator().next());
        assertTrue(requestedToConnect.isEmpty());
    }


    @Test
    public void testConnectingNodeMarkedConnectedWhenHeartbeatReceived() throws InterruptedException {
        final Set<NodeIdentifier> requestedToConnect = Collections.synchronizedSet(new HashSet<>());
        final Set<NodeIdentifier> connected = Collections.synchronizedSet(new HashSet<>());
        final ClusterCoordinatorAdapter adapter = new ClusterCoordinatorAdapter() {
            @Override
            public synchronized void requestNodeConnect(final NodeIdentifier nodeId) {
                super.requestNodeConnect(nodeId);
                requestedToConnect.add(nodeId);
            }

            @Override
            public synchronized void finishNodeConnection(final NodeIdentifier nodeId) {
                super.finishNodeConnection(nodeId);
                connected.add(nodeId);
            }
        };

        final TestFriendlyHeartbeatMonitor monitor = createMonitor(adapter);

        adapter.requestNodeConnect(nodeId); // set state to 'connecting'
        requestedToConnect.clear();

        monitor.addHeartbeat(createHeartbeat(nodeId, NodeConnectionState.CONNECTED));
        monitor.waitForProcessed();

        assertEquals(1, connected.size());
        assertEquals(nodeId, connected.iterator().next());
        assertTrue(requestedToConnect.isEmpty());
    }


    @Test
    public void testDisconnectedHeartbeatOnStartup() throws InterruptedException {
        final Set<NodeIdentifier> requestedToConnect = Collections.synchronizedSet(new HashSet<>());
        final Set<NodeIdentifier> connected = Collections.synchronizedSet(new HashSet<>());
        final Set<NodeIdentifier> disconnected = Collections.synchronizedSet(new HashSet<>());
        final ClusterCoordinatorAdapter adapter = new ClusterCoordinatorAdapter() {
            @Override
            public synchronized void requestNodeConnect(final NodeIdentifier nodeId) {
                super.requestNodeConnect(nodeId);
                requestedToConnect.add(nodeId);
            }

            @Override
            public synchronized void finishNodeConnection(final NodeIdentifier nodeId) {
                super.finishNodeConnection(nodeId);
                connected.add(nodeId);
            }

            @Override
            public synchronized void requestNodeDisconnect(final NodeIdentifier nodeId, final DisconnectionCode disconnectionCode, final String explanation) {
                super.requestNodeDisconnect(nodeId, disconnectionCode, explanation);
                disconnected.add(nodeId);
            }
        };

        final TestFriendlyHeartbeatMonitor monitor = createMonitor(adapter);

        requestedToConnect.clear();

        monitor.addHeartbeat(createHeartbeat(nodeId, DisconnectionCode.NODE_SHUTDOWN));
        monitor.waitForProcessed();

        assertTrue(connected.isEmpty());
        assertTrue(requestedToConnect.isEmpty());
        assertTrue(disconnected.isEmpty());
    }

    private NodeHeartbeat createHeartbeat(final NodeIdentifier nodeId, final DisconnectionCode disconnectionCode) {
        final NodeConnectionStatus status = new NodeConnectionStatus(disconnectionCode);
        return new StandardNodeHeartbeat(nodeId, System.currentTimeMillis(), status, false, 0, 0, 0, 0);
    }

    private NodeHeartbeat createHeartbeat(final NodeIdentifier nodeId, final NodeConnectionState state) {
        final NodeConnectionStatus status = new NodeConnectionStatus(state);
        return new StandardNodeHeartbeat(nodeId, System.currentTimeMillis(), status, false, 0, 0, 0, 0);
    }

    private TestFriendlyHeartbeatMonitor createMonitor(final ClusterCoordinator coordinator) {
        monitor = new TestFriendlyHeartbeatMonitor(coordinator, createProperties());
        monitor.start();
        return monitor;
    }

    private Properties createProperties() {
        final Properties properties = new Properties();
        properties.setProperty(NiFiProperties.CLUSTER_PROTOCOL_HEARTBEAT_INTERVAL, "10 ms");
        properties.setProperty(NiFiProperties.ZOOKEEPER_CONNECT_STRING, zkServer.getConnectString());
        properties.setProperty(NiFiProperties.ZOOKEEPER_CONNECT_TIMEOUT, "3 secs");
        properties.setProperty(NiFiProperties.ZOOKEEPER_SESSION_TIMEOUT, "3 secs");
        properties.setProperty(NiFiProperties.ZOOKEEPER_ROOT_NODE, "/nifi");
        return properties;
    }

    private static class ClusterCoordinatorAdapter implements ClusterCoordinator {
        private final Map<NodeIdentifier, NodeConnectionStatus> statuses = new HashMap<>();
        private final List<ReportedEvent> events = new ArrayList<>();

        @Override
        public synchronized void requestNodeConnect(NodeIdentifier nodeId) {
            statuses.put(nodeId, new NodeConnectionStatus(NodeConnectionState.CONNECTING));
        }

        @Override
        public synchronized void finishNodeConnection(NodeIdentifier nodeId) {
            statuses.put(nodeId, new NodeConnectionStatus(NodeConnectionState.CONNECTED));
        }

        @Override
        public synchronized void requestNodeDisconnect(NodeIdentifier nodeId, DisconnectionCode disconnectionCode, String explanation) {
            statuses.put(nodeId, new NodeConnectionStatus(NodeConnectionState.DISCONNECTED));
        }

        @Override
        public synchronized void disconnectionRequestedByNode(NodeIdentifier nodeId, DisconnectionCode disconnectionCode, String explanation) {
            statuses.put(nodeId, new NodeConnectionStatus(NodeConnectionState.DISCONNECTED));
        }

        @Override
        public synchronized NodeConnectionStatus getConnectionStatus(NodeIdentifier nodeId) {
            return statuses.get(nodeId);
        }

        @Override
        public synchronized Set<NodeIdentifier> getNodeIdentifiers(NodeConnectionState state) {
            return statuses.entrySet().stream().filter(p -> p.getValue().getState() == state).map(p -> p.getKey()).collect(Collectors.toSet());
        }

        @Override
        public synchronized boolean isBlockedByFirewall(String hostname) {
            return false;
        }

        @Override
        public synchronized void reportEvent(NodeIdentifier nodeId, Severity severity, String event) {
            events.add(new ReportedEvent(nodeId, severity, event));
        }

        @Override
        public synchronized void setPrimaryNode(NodeIdentifier nodeId) {
        }

        synchronized List<ReportedEvent> getEvents() {
            return new ArrayList<>(events);
        }

        @Override
        public NodeIdentifier getNodeIdentifier(final String uuid) {
            return statuses.keySet().stream().filter(p -> p.getId().equals(uuid)).findFirst().orElse(null);
        }

        @Override
        public Map<NodeConnectionState, List<NodeIdentifier>> getConnectionStates() {
            return statuses.keySet().stream().collect(Collectors.groupingBy(nodeId -> getConnectionStatus(nodeId).getState()));
        }
    }

    public static class ReportedEvent {
        private final NodeIdentifier nodeId;
        private final Severity severity;
        private final String event;

        public ReportedEvent(NodeIdentifier nodeId, Severity severity, String event) {
            this.nodeId = nodeId;
            this.severity = severity;
            this.event = event;
        }

        public NodeIdentifier getNodeId() {
            return nodeId;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getEvent() {
            return event;
        }
    }


    private static class TestFriendlyHeartbeatMonitor extends AbstractHeartbeatMonitor {
        private Map<NodeIdentifier, NodeHeartbeat> heartbeats = new HashMap<>();
        private final Object mutex = new Object();

        public TestFriendlyHeartbeatMonitor(ClusterCoordinator clusterCoordinator, Properties properties) {
            super(clusterCoordinator, properties);
        }

        @Override
        protected synchronized Map<NodeIdentifier, NodeHeartbeat> getLatestHeartbeats() {
            return heartbeats;
        }

        @Override
        public synchronized void monitorHeartbeats() {
            super.monitorHeartbeats();

            synchronized (mutex) {
                mutex.notify();
            }
        }

        synchronized void addHeartbeat(final NodeHeartbeat heartbeat) {
            heartbeats.put(heartbeat.getNodeIdentifier(), heartbeat);
        }

        @Override
        public synchronized void removeHeartbeat(final NodeIdentifier nodeId) {
            heartbeats.remove(nodeId);
        }

        void waitForProcessed() throws InterruptedException {
            synchronized (mutex) {
                mutex.wait();
            }
        }
    }
}
