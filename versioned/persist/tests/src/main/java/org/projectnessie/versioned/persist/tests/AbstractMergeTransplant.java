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
package org.projectnessie.versioned.persist.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.projectnessie.versioned.persist.adapter.DatabaseAdapterConfig.DEFAULT_KEY_LIST_DISTANCE;

import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.Key;
import org.projectnessie.versioned.ReferenceConflictException;
import org.projectnessie.versioned.persist.adapter.CommitLogEntry;
import org.projectnessie.versioned.persist.adapter.ContentId;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;
import org.projectnessie.versioned.persist.adapter.ImmutableCommitAttempt;
import org.projectnessie.versioned.persist.adapter.KeyWithBytes;

/** Check that merge and transplant operations work correctly. */
public abstract class AbstractMergeTransplant {

  private final DatabaseAdapter databaseAdapter;

  protected AbstractMergeTransplant(DatabaseAdapter databaseAdapter) {
    this.databaseAdapter = databaseAdapter;
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        3,
        10,
        DEFAULT_KEY_LIST_DISTANCE,
        DEFAULT_KEY_LIST_DISTANCE + 1,
        100,
      })
  void merge(int numCommits) throws Exception {
    AtomicInteger unifier = new AtomicInteger();
    Function<ByteString, ByteString> metadataUpdater =
        commitMeta ->
            ByteString.copyFromUtf8(
                commitMeta.toStringUtf8() + " transplanted " + unifier.getAndIncrement());

    Hash[] commits =
        mergeTransplant(
            numCommits,
            (target, expectedHead, branch, commitHashes, i) ->
                databaseAdapter.merge(commitHashes[i], target, expectedHead, metadataUpdater));

    BranchName branch = BranchName.of("branch");
    BranchName branch2 = BranchName.of("branch2");
    databaseAdapter.create(branch2, databaseAdapter.hashOnReference(branch, Optional.empty()));
    assertThatThrownBy(
            () ->
                databaseAdapter.merge(
                    databaseAdapter.hashOnReference(branch, Optional.empty()),
                    branch2,
                    Optional.empty(),
                    Function.identity()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("No hashes to merge from '");
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        3,
        10,
        DEFAULT_KEY_LIST_DISTANCE,
        DEFAULT_KEY_LIST_DISTANCE + 1,
        100,
      })
  void transplant(int numCommits) throws Exception {
    AtomicInteger unifier = new AtomicInteger();
    Function<ByteString, ByteString> metadataUpdater =
        commitMeta ->
            ByteString.copyFromUtf8(
                commitMeta.toStringUtf8() + " transplanted " + unifier.getAndIncrement());

    Hash[] commits =
        mergeTransplant(
            numCommits,
            (target, expectedHead, branch, commitHashes, i) ->
                databaseAdapter.transplant(
                    target,
                    expectedHead,
                    Arrays.asList(commitHashes).subList(0, i + 1),
                    metadataUpdater));

    BranchName conflict = BranchName.of("conflict");

    // no conflict, when transplanting the commits from against the current HEAD of the
    // conflict-branch
    Hash noConflictHead = databaseAdapter.hashOnReference(conflict, Optional.empty());
    Hash transplanted =
        databaseAdapter.transplant(
            conflict, Optional.of(noConflictHead), Arrays.asList(commits), metadataUpdater);
    int offset = unifier.get();

    try (Stream<CommitLogEntry> log =
        databaseAdapter.commitLog(transplanted).limit(commits.length)) {
      AtomicInteger testOffset = new AtomicInteger(offset);
      assertThat(log.map(CommitLogEntry::getMetadata).map(ByteString::toStringUtf8))
          .containsExactlyElementsOf(
              IntStream.range(0, commits.length)
                  .map(i -> commits.length - i - 1)
                  .mapToObj(i -> "commit " + i + " transplanted " + testOffset.decrementAndGet())
                  .collect(Collectors.toList()));
    }

    // again, no conflict (same as above, just again)
    transplanted =
        databaseAdapter.transplant(
            conflict, Optional.empty(), Arrays.asList(commits), metadataUpdater);
    offset = unifier.get();

    try (Stream<CommitLogEntry> log =
        databaseAdapter.commitLog(transplanted).limit(commits.length)) {
      AtomicInteger testOffset = new AtomicInteger(offset);
      assertThat(log.map(CommitLogEntry::getMetadata).map(ByteString::toStringUtf8))
          .containsExactlyElementsOf(
              IntStream.range(0, commits.length)
                  .map(i -> commits.length - i - 1)
                  .mapToObj(i -> "commit " + i + " transplanted " + testOffset.decrementAndGet())
                  .collect(Collectors.toList()));
    }

    assertThatThrownBy(
            () ->
                databaseAdapter.transplant(
                    conflict, Optional.empty(), Collections.emptyList(), Function.identity()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No hashes to transplant given.");
  }

  @FunctionalInterface
  interface MergeOrTransplant {
    void apply(
        BranchName target,
        Optional<Hash> expectedHead,
        BranchName branch,
        Hash[] commitHashes,
        int i)
        throws Exception;
  }

  private Hash[] mergeTransplant(int numCommits, MergeOrTransplant mergeOrTransplant)
      throws Exception {
    BranchName main = BranchName.of("main");
    BranchName branch = BranchName.of("branch");
    BranchName conflict = BranchName.of("conflict");

    databaseAdapter.create(branch, databaseAdapter.hashOnReference(main, Optional.empty()));

    Hash[] commits = new Hash[numCommits];
    for (int i = 0; i < commits.length; i++) {
      ImmutableCommitAttempt.Builder commit =
          ImmutableCommitAttempt.builder()
              .commitToBranch(branch)
              .commitMetaSerialized(ByteString.copyFromUtf8("commit " + i));
      for (int k = 0; k < 3; k++) {
        commit.addPuts(
            KeyWithBytes.of(
                Key.of("key", Integer.toString(k)),
                ContentId.of("C" + k),
                (byte) 0,
                ByteString.copyFromUtf8("value " + i + " for " + k)));
      }
      commits[i] = databaseAdapter.commit(commit.build());
    }

    for (int i = 0; i < commits.length; i++) {
      BranchName target = BranchName.of("transplant-" + i);
      databaseAdapter.create(target, databaseAdapter.hashOnReference(main, Optional.empty()));

      mergeOrTransplant.apply(target, Optional.empty(), branch, commits, i);

      try (Stream<CommitLogEntry> targetLog =
          databaseAdapter.commitLog(databaseAdapter.hashOnReference(target, Optional.empty()))) {
        assertThat(targetLog).hasSize(i + 1);
      }
    }

    // prepare conflict for keys 0 + 1

    Hash conflictBase =
        databaseAdapter.create(conflict, databaseAdapter.hashOnReference(main, Optional.empty()));
    ImmutableCommitAttempt.Builder commit =
        ImmutableCommitAttempt.builder()
            .commitToBranch(conflict)
            .commitMetaSerialized(ByteString.copyFromUtf8("commit conflict"));
    for (int k = 0; k < 2; k++) {
      commit.addPuts(
          KeyWithBytes.of(
              Key.of("key", Integer.toString(k)),
              ContentId.of("C" + k),
              (byte) 0,
              ByteString.copyFromUtf8("conflict value for " + k)));
    }
    databaseAdapter.commit(commit.build());

    assertThatThrownBy(
            () -> mergeOrTransplant.apply(conflict, Optional.of(conflictBase), branch, commits, 2))
        .isInstanceOf(ReferenceConflictException.class)
        .hasMessage("The following keys have been changed in conflict: 'key.0', 'key.1'");

    return commits;
  }
}
