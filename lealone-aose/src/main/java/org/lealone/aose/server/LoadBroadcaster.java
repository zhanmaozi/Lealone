/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.aose.server;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.lealone.aose.concurrent.ScheduledExecutors;
import org.lealone.aose.gms.ApplicationState;
import org.lealone.aose.gms.EndpointState;
import org.lealone.aose.gms.Gossiper;
import org.lealone.aose.gms.IEndpointStateChangeSubscriber;
import org.lealone.aose.gms.VersionedValue;
import org.lealone.common.logging.Logger;
import org.lealone.common.logging.LoggerFactory;
import org.lealone.net.NetEndpoint;

public class LoadBroadcaster implements IEndpointStateChangeSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(LoadBroadcaster.class);

    static final int BROADCAST_INTERVAL = 60 * 1000;

    public static final LoadBroadcaster instance = new LoadBroadcaster();

    private final ConcurrentMap<NetEndpoint, Double> loadInfo = new ConcurrentHashMap<>();

    private LoadBroadcaster() {
        Gossiper.instance.register(this);
    }

    public void startBroadcasting() {
        // send the first broadcast "right away" (i.e., in 2 gossip heartbeats, when we should have someone to talk to);
        // after that send every BROADCAST_INTERVAL.
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (logger.isDebugEnabled())
                    logger.debug("Disseminating load info ...");
                Gossiper.instance.addLocalApplicationState(ApplicationState.LOAD,
                        P2pServer.VALUE_FACTORY.load(P2pServer.instance.getLoad()));
            }
        };
        ScheduledExecutors.scheduledTasks.scheduleWithFixedDelay(runnable, 2 * Gossiper.INTERVAL_IN_MILLIS,
                BROADCAST_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public Map<NetEndpoint, Double> getLoadInfo() {
        return Collections.unmodifiableMap(loadInfo);
    }

    @Override
    public void onChange(NetEndpoint endpoint, ApplicationState state, VersionedValue value) {
        if (state != ApplicationState.LOAD)
            return;
        loadInfo.put(endpoint, Double.valueOf(value.value));
    }

    @Override
    public void onJoin(NetEndpoint endpoint, EndpointState epState) {
        VersionedValue localValue = epState.getApplicationState(ApplicationState.LOAD);
        if (localValue != null) {
            onChange(endpoint, ApplicationState.LOAD, localValue);
        }
    }

    @Override
    public void onRemove(NetEndpoint endpoint) {
        loadInfo.remove(endpoint);
    }

    @Override
    public void beforeChange(NetEndpoint endpoint, EndpointState currentState, ApplicationState newStateKey,
            VersionedValue newValue) {
    }

    @Override
    public void onAlive(NetEndpoint endpoint, EndpointState state) {
    }

    @Override
    public void onDead(NetEndpoint endpoint, EndpointState state) {
    }

    @Override
    public void onRestart(NetEndpoint endpoint, EndpointState state) {
    }
}
