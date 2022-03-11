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
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.spark.sql.SparkSession;
import org.projectnessie.api.params.FetchOption;
import org.projectnessie.client.StreamingUtil;
import org.projectnessie.client.api.NessieApiV1;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.RefLogResponse;
import org.projectnessie.model.Reference;
import org.projectnessie.model.ReferenceMetadata;
import org.projectnessie.model.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates the logic to retrieve expired contents by walking over all commits in all
 * named-references.
 */
public class GCImpl {
  private static final Logger LOGGER = LoggerFactory.getLogger(GCImpl.class);
  private final GCParams gcParams;

  /**
   * Instantiates a new GCImpl.
   *
   * @param gcParams GC configuration params
   */
  public GCImpl(GCParams gcParams) {
    this.gcParams = gcParams;
    LOGGER.info("GCImpl with params: {}", gcParams);
  }

  /**
   * Identify the expired contents using a two-step traversal algorithm.
   *
   * <h2>Algorithm for identifying the live contents and return the bloom filter per content-id</h2>
   *
   * <p>Walk through each reference(both live and dead) distributively (one spark task for each
   * reference).
   *
   * <p>While traversing from the head commit in a reference(use DETACHED reference to fetch commits
   * from dead reference), for each live commit (commit that is not expired based on cutoff time)
   * add the contents of put operation to bloom filter.
   *
   * <p>Collect the live content keys for this reference just before cutoff time (at first expired
   * commit head). Which is used to identify the commit head for each live content key at the time
   * of cutoff time to support the time travel.
   *
   * <p>While traversing the expired commits (commit that is expired based on cutoff time), if it is
   * a head commit content for its key, add it to bloom filter. Else move to next expired commit.
   *
   * <p>Stop traversing the expired commits if each live content key has processed one live commit
   * for it. This is an optimization to avoid traversing all the commits.
   *
   * <p>Collect bloom filter per content id from each task and merge them.
   *
   * <h2>Algorithm for identifying the expired contents and return the list of globally expired
   * contents per content id per reference </h2>
   *
   * <p>Walk through each reference(both live and dead) distributively (one spark task for each
   * reference).
   *
   * <p>For each commit in the reference (use DETACHED reference to fetch commits from dead
   * reference) check it against bloom filter to decide whether its contents in put operation are
   * globally expired or not. If globally expired, Add the contents to the expired output for this
   * content id for this reference.
   *
   * <p>Overall the contents after or equal to cutoff time and the contents that are mapped to
   * commit head of live keys at the time of cutoff timestamp will be retained.
   *
   * @param session spark session for distributed computation
   * @return {@link IdentifiedResult} object having expired contents per content id.
   */
  public IdentifiedResult identifyExpiredContents(SparkSession session) {
    try (NessieApiV1 api = GCUtil.getApi(gcParams.getNessieClientConfigs())) {
      DistributedIdentifyContents distributedIdentifyContents =
          new DistributedIdentifyContents(session, gcParams);
      List<Reference> liveReferences = api.getAllReferences().get().getReferences();
      Map<String, Instant> droppedReferenceTimeMap = collectDeadReferences(api);
      // As this list of references is passed from Spark driver to executor,
      // using available Immutables JSON serialization instead of adding java serialization to the
      // classes.
      List<String> allRefs =
          liveReferences.stream().map(GCUtil::serializeReference).collect(Collectors.toList());
      if (droppedReferenceTimeMap.size() > 0) {
        allRefs.addAll(droppedReferenceTimeMap.keySet());
      }
      long bloomFilterSize =
          gcParams.getBloomFilterExpectedEntries() == null
              ? getTotalCommitsInDefaultReference(api)
              : gcParams.getBloomFilterExpectedEntries();
      // Identify the live contents and return the bloom filter per content-id
      Map<String, ContentBloomFilter> liveContentsBloomFilterMap =
          distributedIdentifyContents.getLiveContentsBloomFilters(
              allRefs, bloomFilterSize, droppedReferenceTimeMap);
      // Identify the expired contents
      return distributedIdentifyContents.getIdentifiedResults(liveContentsBloomFilterMap, allRefs);
    }
  }

  private long getTotalCommitsInDefaultReference(NessieApiV1 api) {
    ReferenceMetadata defaultRefMetadata;
    try {
      defaultRefMetadata =
          api.getReference()
              .refName(api.getDefaultBranch().getName())
              .fetch(FetchOption.ALL)
              .get()
              .getMetadata();
    } catch (NessieNotFoundException ex) {
      throw new RuntimeException(ex);
    }
    // As the FetchOption.ALL is used, metadata and its content will not be null.
    return defaultRefMetadata.getNumTotalCommits();
  }

  private static Map<String, Instant> collectDeadReferences(NessieApiV1 api) {
    Map<String, Instant> droppedReferenceTimeMap = new HashMap<>();
    Stream<RefLogResponse.RefLogResponseEntry> reflogStream;
    try {
      reflogStream =
          StreamingUtil.getReflogStream(
              api,
              builder ->
                  builder.filter(
                      String.format(
                          "reflog.operation == '%s' || reflog.operation == " + "'%s'",
                          RefLogResponse.RefLogResponseEntry.DELETE_REFERENCE,
                          RefLogResponse.RefLogResponseEntry.ASSIGN_REFERENCE)),
              OptionalInt.empty());
    } catch (NessieNotFoundException e) {
      throw new RuntimeException(e);
    }
    reflogStream.forEach(
        entry -> {
          String hash;
          switch (entry.getOperation()) {
            case RefLogResponse.RefLogResponseEntry.DELETE_REFERENCE:
              hash = entry.getCommitHash();
              break;
            case RefLogResponse.RefLogResponseEntry.ASSIGN_REFERENCE:
              hash = entry.getSourceHashes().get(0);
              break;
            default:
              throw new RuntimeException(
                  entry.getOperation() + " operation found in dead reflog query");
          }
          switch (entry.getRefType()) {
            case RefLogResponse.RefLogResponseEntry.BRANCH:
              droppedReferenceTimeMap.put(
                  GCUtil.serializeReference(Branch.of(entry.getRefName(), hash)),
                  getInstantFromMicros(entry.getOperationTime()));
              break;
            case RefLogResponse.RefLogResponseEntry.TAG:
              droppedReferenceTimeMap.put(
                  GCUtil.serializeReference(Tag.of(entry.getRefName(), hash)),
                  getInstantFromMicros(entry.getOperationTime()));
              break;
            default:
              throw new RuntimeException(
                  entry.getRefType() + " type reference is found in dead reflog query");
          }
        });
    return droppedReferenceTimeMap;
  }

  private static Instant getInstantFromMicros(Long microsSinceEpoch) {
    return Instant.ofEpochSecond(
        TimeUnit.MICROSECONDS.toSeconds(microsSinceEpoch),
        TimeUnit.MICROSECONDS.toNanos(
            Math.floorMod(microsSinceEpoch, TimeUnit.SECONDS.toMicros(1))));
  }
}
