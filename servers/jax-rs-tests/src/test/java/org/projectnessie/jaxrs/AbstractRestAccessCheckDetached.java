/*
 * Copyright (C) 2022 Dremio
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
package org.projectnessie.jaxrs;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.projectnessie.error.NessieForbiddenException;
import org.projectnessie.jaxrs.ext.NessieAccessChecker;
import org.projectnessie.model.Branch;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.Detached;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.Operation.Put;
import org.projectnessie.model.Tag;
import org.projectnessie.services.authz.AbstractBatchAccessChecker;
import org.projectnessie.services.authz.AccessContext;
import org.projectnessie.services.authz.BatchAccessChecker;
import org.projectnessie.services.authz.Check;
import org.projectnessie.services.authz.Check.CheckType;
import org.projectnessie.versioned.DetachedRef;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

/** See {@link AbstractTestRest} for details about and reason for the inheritance model. */
public abstract class AbstractRestAccessCheckDetached extends AbstractTestRest {

  private static final String VIEW_MSG = "Must not view detached references";
  private static final String COMMITS_MSG = "Must not list from detached references";
  private static final String READ_MSG = "Must not read from detached references";
  private static final String ENTITIES_MSG = "Must not get entities from detached references";

  private static final Map<CheckType, String> CHECK_TYPE_MSG =
      ImmutableMap.of(
          CheckType.VIEW_REFERENCE, VIEW_MSG,
          CheckType.LIST_COMMIT_LOG, COMMITS_MSG,
          CheckType.READ_ENTITY_VALUE, ENTITIES_MSG,
          CheckType.READ_ENTRIES, READ_MSG);

  private BatchAccessChecker newAccessChecker() {
    return new AbstractBatchAccessChecker() {
      @Override
      public Map<Check, String> check() {
        Map<Check, String> failed = new LinkedHashMap<>();
        getChecks()
            .forEach(
                check -> {
                  String msg = CHECK_TYPE_MSG.get(check.type());
                  if (msg != null) {
                    if (check.ref() instanceof DetachedRef) {
                      failed.put(check, msg);
                    } else {
                      assertThat(check.ref().getName()).isNotEqualTo(DetachedRef.REF_NAME);
                    }
                  }
                });
        return failed;
      }
    };
  }

  @Test
  public void detachedRefAccessChecks(
      @NessieAccessChecker
          Consumer<Function<AccessContext, BatchAccessChecker>> accessCheckerConsumer)
      throws Exception {
    accessCheckerConsumer.accept(x -> newAccessChecker());

    Branch main = createBranch("committerAndAuthor");
    Branch merge = createBranch("committerAndAuthorMerge");
    Branch transplant = createBranch("committerAndAuthorTransplant");

    IcebergTable meta1 = IcebergTable.of("meep", 42, 42, 42, 42);
    ContentKey key = ContentKey.of("meep");
    Branch mainCommit =
        getApi()
            .commitMultipleOperations()
            .branchName(main.getName())
            .hash(main.getHash())
            .commitMeta(CommitMeta.builder().message("no security context").build())
            .operation(Put.of(key, meta1))
            .commit();

    Branch detachedAsBranch = Branch.of(Detached.REF_NAME, mainCommit.getHash());
    Tag detachedAsTag = Tag.of(Detached.REF_NAME, mainCommit.getHash());
    Detached detached = Detached.of(mainCommit.getHash());

    assertThat(Stream.of(detached, detachedAsBranch, detachedAsTag))
        .allSatisfy(
            ref ->
                assertAll(
                    () ->
                        assertThatThrownBy(() -> getApi().getCommitLog().reference(ref).get())
                            .describedAs("ref='%s', getCommitLog", ref)
                            .isInstanceOf(NessieForbiddenException.class)
                            .hasMessageContaining(COMMITS_MSG),
                    () ->
                        assertThatThrownBy(
                                () ->
                                    getApi()
                                        .mergeRefIntoBranch()
                                        .fromRef(ref)
                                        .branch(merge)
                                        .merge())
                            .describedAs("ref='%s', mergeRefIntoBranch", ref)
                            .isInstanceOf(NessieForbiddenException.class)
                            .hasMessageContaining(VIEW_MSG),
                    () ->
                        assertThatThrownBy(
                                () ->
                                    getApi()
                                        .transplantCommitsIntoBranch()
                                        .fromRefName(ref.getName())
                                        .hashesToTransplant(singletonList(ref.getHash()))
                                        .branch(transplant)
                                        .transplant())
                            .describedAs("ref='%s', transplantCommitsIntoBranch", ref)
                            .isInstanceOf(NessieForbiddenException.class)
                            .hasMessageContaining(VIEW_MSG),
                    () ->
                        assertThatThrownBy(() -> getApi().getEntries().reference(ref).get())
                            .describedAs("ref='%s', getEntries", ref)
                            .isInstanceOf(NessieForbiddenException.class)
                            .hasMessageContaining(READ_MSG),
                    () ->
                        assertThatThrownBy(
                                () -> getApi().getContent().reference(ref).key(key).get())
                            .describedAs("ref='%s', getContent", ref)
                            .isInstanceOf(NessieForbiddenException.class)
                            .hasMessageContaining(ENTITIES_MSG),
                    () ->
                        assertThatThrownBy(() -> getApi().getDiff().fromRef(ref).toRef(main).get())
                            .describedAs("ref='%s', getDiff1", ref)
                            .isInstanceOf(NessieForbiddenException.class)
                            .hasMessageContaining(VIEW_MSG),
                    () ->
                        assertThatThrownBy(() -> getApi().getDiff().fromRef(main).toRef(ref).get())
                            .describedAs("ref='%s', getDiff2", ref)
                            .isInstanceOf(NessieForbiddenException.class)
                            .hasMessageContaining(VIEW_MSG)));
  }
}
