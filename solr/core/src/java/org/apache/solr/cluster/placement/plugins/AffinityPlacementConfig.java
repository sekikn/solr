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

package org.apache.solr.cluster.placement.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.apache.solr.cluster.placement.PlacementPluginConfig;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.annotation.JsonProperty;

/** Configuration bean for {@link AffinityPlacementFactory}. */
public class AffinityPlacementConfig implements PlacementPluginConfig {

  public static final long DEFAULT_MINIMAL_FREE_DISK_GB = 20L;
  public static final long DEFAULT_PRIORITIZED_FREE_DISK_GB = 100L;

  public static final AffinityPlacementConfig DEFAULT =
      new AffinityPlacementConfig(DEFAULT_MINIMAL_FREE_DISK_GB, DEFAULT_PRIORITIZED_FREE_DISK_GB);

  /**
   * Name of the system property on a node indicating which (public cloud) Availability Zone that
   * node is in. The value is any string, different strings denote different availability zones.
   *
   * <p>Nodes on which this system property is not defined are considered being in the same
   * Availability Zone {@link #UNDEFINED_AVAILABILITY_ZONE} (hopefully the value of this constant is
   * not the name of a real Availability Zone :).
   */
  public static final String AVAILABILITY_ZONE_SYSPROP = "availability_zone";

  /**
   * Name of the system property on a node indicating the type of replicas allowed on that node. The
   * value of that system property is a comma separated list or a single string of value names of
   * {@link org.apache.solr.cluster.Replica.ReplicaType} (case insensitive). If that property is not
   * defined, that node is considered accepting all replica types (i.e. undefined is equivalent to
   * {@code "NRT,Pull,tlog"}).
   */
  public static final String REPLICA_TYPE_SYSPROP = "replica_type";

  /**
   * Name of the system property on a node indicating the arbitrary "node type" (for example, a node
   * more suitable for the indexing work load could be labeled as <code>node_type: indexing</code>).
   * The value of this system property is a comma-separated list or a single label (labels must not
   * contain commas), which represent a logical OR for the purpose of placement.
   */
  public static final String NODE_TYPE_SYSPROP = "node_type";

  /**
   * Name of the system property on a node indicating the spread domain group. This is used (if
   * {@link #spreadAcrossDomains} is set to true) to indicate this placement plugin that replicas
   * for a particular shard should spread across nodes that have different values for this system
   * property.
   */
  public static final String SPREAD_DOMAIN_SYSPROP = "spread_domain";

  /**
   * This is the "AZ" name for nodes that do not define an AZ. Should not match a real AZ name (I
   * think we're safe)
   */
  public static final String UNDEFINED_AVAILABILITY_ZONE = "uNd3f1NeD";

  /**
   * If a node has strictly less GB of free disk than this value, the node is excluded from
   * assignment decisions. Set to 0 or less to disable.
   */
  @JsonProperty public long minimalFreeDiskGB;

  /**
   * Replica allocation will assign replicas to nodes with at least this number of GB of free disk
   * space regardless of the number of cores on these nodes rather than assigning replicas to nodes
   * with less than this amount of free disk space if that's an option (if that's not an option,
   * replicas can still be assigned to nodes with less than this amount of free space).
   */
  @JsonProperty public long prioritizedFreeDiskGB;

  /**
   * This property defines an additional constraint that primary collections (keys) should be
   * located on the same nodes as the secondary collections (values). The plugin will assume that
   * the secondary collection replicas are already in place and ignore candidate nodes where they
   * are not already present.
   */
  @JsonProperty public Map<String, String> withCollection;

  /**
   * This property defines an additional constraint that the collection must be placed only on the
   * nodes of the correct "node type". The nodes can specify what type they are (one or several
   * types, using a comma-separated list) by defining the {@link #NODE_TYPE_SYSPROP} system
   * property. Similarly, the plugin can be configured to specify that a collection (key in the map)
   * must be placed on one or more node type (value in the map, using comma-separated list of
   * acceptable node types).
   */
  @JsonProperty public Map<String, String> collectionNodeType;
  /**
   * Same as {@link AffinityPlacementConfig#withCollection} but ensures shard to shard
   * correspondence. should be disjoint with {@link AffinityPlacementConfig#withCollection}.
   */
  @JsonProperty public Map<String, String> withCollectionShards;

  /**
   * When this property is set to {@code true}, Solr will try to place replicas for the same shard
   * in nodes that have different value for the {@link #SPREAD_DOMAIN_SYSPROP} System property. If
   * more replicas exist (or are being placed) than the number of different values for {@link
   * #SPREAD_DOMAIN_SYSPROP} System property in nodes in the cluster, Solr will attempt to
   * distribute the placement of the replicas evenly across the domains but will fail the placement
   * if more than {@link #maxReplicasPerShardInDomain} are placed within a single domain. Note that
   * the domain groups are evaluated within a particular AZ (i.e. Solr will not consider the
   * placement of replicas in AZ1 when selecting candidate nodes for replicas in AZ2). Example
   * usages for this config are:
   *
   * <ul>
   *   <li>Rack diversity: You want replicas in different AZs but also, within the AZ you want them
   *       in different racks
   *   <li>Host diversity: You are running multiple Solr instances in the same host physical host.
   *       You want replicas in different AZs but also, within an AZ you want replicas for the same
   *       shard to go in nodes that run in different hosts
   * </ul>
   */
  @JsonProperty public Boolean spreadAcrossDomains = Boolean.FALSE;

  /**
   * Determines the maximum number of replicas of a particular type of a particular shard that can
   * be placed within a single domain (as defined by the @link #SPREAD_DOMAIN_SYSPROP} System
   * property.
   */
  @JsonProperty public Integer maxReplicasPerShardInDomain = -1;

  /** Zero-arguments public constructor required for deserialization - don't use. */
  public AffinityPlacementConfig() {
    this(DEFAULT_MINIMAL_FREE_DISK_GB, DEFAULT_PRIORITIZED_FREE_DISK_GB);
  }

  /**
   * Configuration for the {@link AffinityPlacementFactory}.
   *
   * @param minimalFreeDiskGB minimal free disk GB.
   * @param prioritizedFreeDiskGB prioritized free disk GB.
   */
  public AffinityPlacementConfig(long minimalFreeDiskGB, long prioritizedFreeDiskGB) {
    this(minimalFreeDiskGB, prioritizedFreeDiskGB, Map.of(), Map.of());
  }

  /**
   * Configuration for the {@link AffinityPlacementFactory}.
   *
   * @param minimalFreeDiskGB minimal free disk GB.
   * @param prioritizedFreeDiskGB prioritized free disk GB.
   * @param withCollection configuration of co-located collections: keys are primary collection
   *     names and values are secondary collection names.
   * @param collectionNodeType configuration of reequired node types per collection. Keys are
   *     collection names and values are comma-separated lists of required node types.
   */
  public AffinityPlacementConfig(
      long minimalFreeDiskGB,
      long prioritizedFreeDiskGB,
      Map<String, String> withCollection,
      Map<String, String> withCollectionShards,
      Map<String, String> collectionNodeType) {
    this.minimalFreeDiskGB = minimalFreeDiskGB;
    this.prioritizedFreeDiskGB = prioritizedFreeDiskGB;
    Objects.requireNonNull(withCollection);
    Objects.requireNonNull(withCollectionShards);
    Objects.requireNonNull(collectionNodeType);
    this.withCollection = withCollection;
    this.withCollectionShards = withCollectionShards;
    this.collectionNodeType = collectionNodeType;
  }

  public AffinityPlacementConfig(
      long minimalFreeDiskGB,
      long prioritizedFreeDiskGB,
      Map<String, String> withCollection,
      Map<String, String> collectionNodeType) {
    this(
        minimalFreeDiskGB,
        prioritizedFreeDiskGB,
        withCollection,
        Collections.emptyMap(),
        collectionNodeType);
  }

  public void validate() {
    if (!Collections.disjoint(withCollection.keySet(), withCollectionShards.keySet())) {
      final ArrayList<String> collections = new ArrayList<>(withCollection.keySet());
      collections.retainAll(withCollectionShards.keySet());
      throw new SolrException(
          SolrException.ErrorCode.BAD_REQUEST,
          "withCollection and withCollectionShards should be disjoint. But there are "
              + collections
              + " in common.");
    }
  }
}
