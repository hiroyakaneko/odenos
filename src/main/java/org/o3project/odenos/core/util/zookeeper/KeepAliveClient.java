/*
 * Copyright 2015 NEC Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.o3project.odenos.core.util.zookeeper;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZooKeeper client with a callback handler.
 */
public class KeepAliveClient {

  private static final Logger log =
      LoggerFactory.getLogger(KeepAliveClient.class);

  ZooKeeper zk = null;
  Set<String> paths = new ConcurrentSkipListSet<>();

  Watcher watcher = new Watcher() {
    @Override
    public void process(WatchedEvent event) {
      switch (event.getState()) {
        case Expired:
          log.warn("ZooKeeper session exipired");
          connect();
          paths.forEach((path) -> createPath(path, CreateMode.EPHEMERAL));
          break;
        default:
          break;
      }
    }
  };

  public KeepAliveClient() {
    connect();
  }

  private void connect() {
    zk = ZooKeeperService.zooKeeper(5000, watcher);
  }

  /**
   * Creates a znode on ZooKeeper server.
   * @param path
   * @param mode
   */
  public synchronized void createPath(final String path, CreateMode mode) {
    if (mode == CreateMode.PERSISTENT) {
      try {
        if (zk.exists(path, false) == null) {
          zk.create(path, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
              CreateMode.PERSISTENT);
        }
      } catch (KeeperException | InterruptedException e) {
        log.error("Unable to create a path: " + path, e);
      }
    } else if (mode == CreateMode.EPHEMERAL) {
      zk.create(path, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
          mode, createPathCallback, new byte[0]);
    } else {
      log.warn("Unsupported mode: " + mode.toString());
    }
  }

  /**
   * Deletes a znode on ZooKeeper server.
   * @param path
   */
  public synchronized void deletePath(final String path) {
    try {
      zk.delete(path, -1);
    } catch (InterruptedException | KeeperException e) {
      log.error("Unable to delete a path: " + path);
    }
  }

  /**
   * Callback handler for asynchronous ZooKeeper.create() method.
   */
  StringCallback createPathCallback = new StringCallback() {
    public void processResult(int rc, String path, Object ctx, String name) {
      Code code = Code.get(rc);
      switch (code) {
        case CONNECTIONLOSS:
          paths.add(path);
          paths.forEach((p) -> createPath(p, CreateMode.EPHEMERAL));
          break;
        case OK:
          paths.add(path);
          break;
        case NODEEXISTS:
          log.warn("node exists: " + path);
          break;
        default:
          log.error("process result: " + code.toString());
          break;
      }
    }
  };

}
