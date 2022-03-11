/*
 * Copyright (C) 2020 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.gc.base;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Identify the expired and live contents in a distributed way using the spark and bloom filter by
 * walking all the references (both dead and live).
 */
public class DistributedIdentifyContents {
  private static final Logger LOGGER = LoggerFactory.getLogger(DistributedIdentifyContents.class);

  private final SparkSession session;
  private final GCParams gcParams;

  public DistributedIdentifyContents(SparkSession session, GCParams gcParams) {
    this.session = session;
    this.gcParams = gcParams;
  }

  /**
   * Compute the bloom filter per content id by walking all the live references in a distributed way
   * using spark.
   *
   * @param references list of all the references (JSON serialized)
   * @param bloomFilterSize size of bloom filter to be used
   * @param droppedRefTimeMap map of dropped time for reference@hash (JSON serialized)
   * @return map of {@link ContentBloomFilter} per content-id.
   */
  public Map<String, ContentBloomFilter> getLiveContentsBloomFilters(
      List<String> references, long bloomFilterSize, Map<String, Instant> droppedRefTimeMap) {
    IdentifyContentsPerExecutor executor = new IdentifyContentsPerExecutor(gcParams);
    List<Map<String, ContentBloomFilter>> bloomFilterMaps =
        new JavaSparkContext(session.sparkContext())
            .parallelize(references, getPartitionsCount(gcParams, references))
            .map(executor.computeLiveContentsFunc(bloomFilterSize, droppedRefTimeMap))
            .collect();
    return mergeLiveContentResults(bloomFilterMaps, gcParams.getBloomFilterFpp());
  }

  /**
   * Gets the expired contents per content id by walking all the live and dead references in a
   * distributed way using spark and checking the contents against the live bloom filter results.
   *
   * @param liveContentsBloomFilterMap live contents bloom filter per content id.
   * @param references list of all the references (JSON serialized) to walk (live and dead)
   * @return {@link IdentifiedResult} object.
   */
  public IdentifiedResult getIdentifiedResults(
      Map<String, ContentBloomFilter> liveContentsBloomFilterMap, List<String> references) {

    IdentifyContentsPerExecutor executor = new IdentifyContentsPerExecutor(gcParams);
    List<IdentifiedResult> results =
        new JavaSparkContext(session.sparkContext())
            .parallelize(references, getPartitionsCount(gcParams, references))
            .map(executor.computeExpiredContentsFunc(liveContentsBloomFilterMap))
            .collect();
    IdentifiedResult identifiedResult = new IdentifiedResult();
    results.forEach(
        result -> identifiedResult.getContentValues().putAll(result.getContentValues()));
    return identifiedResult;
  }

  private static int getPartitionsCount(GCParams gcParams, List<String> references) {
    return gcParams.getSparkPartitionsCount() == null
        ? references.size()
        : gcParams.getSparkPartitionsCount();
  }

  private static Map<String, ContentBloomFilter> mergeLiveContentResults(
      List<Map<String, ContentBloomFilter>> bloomFilterMaps, double bloomFilterFpp) {
    Map<String, ContentBloomFilter> output = new HashMap<>();
    bloomFilterMaps.forEach(
        map ->
            map.forEach(
                (k, v) -> {
                  if (output.containsKey(k)) {
                    output.get(k).merge(v);
                  } else {
                    output.put(k, v);
                  }
                }));
    // Since we merged bloom filters log in case their quality deteriorated
    output.entrySet().stream()
        .filter(e -> e.getValue().wasMerged())
        .forEach(
            e -> {
              double fpp = e.getValue().getExpectedFpp();
              if (fpp > bloomFilterFpp) {
                String contentId = e.getKey();
                LOGGER.info(
                    "Fpp of ContentBloomFilter for '{}': {}",
                    contentId,
                    String.format("%.3f", fpp));
              }
            });
    return output;
  }
}
