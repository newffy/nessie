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
package org.projectnessie.api.params;

import java.util.Objects;
import java.util.StringJoiner;
import javax.annotation.Nullable;
import javax.validation.constraints.Pattern;
import javax.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.projectnessie.model.Validation;

/**
 * The purpose of this class is to include optional parameters that can be passed to {@code
 * HttpRefLogApi#getRefLog(RefLogParams)}.
 *
 * <p>For easier usage of this class, there is {@link RefLogParams#builder()}, which allows
 * configuring/setting the different parameters.
 */
public class RefLogParams extends AbstractParams {

  @Nullable
  @Pattern(regexp = Validation.HASH_REGEX, message = Validation.HASH_MESSAGE)
  @Parameter(
      description =
          "Hash of the reflog (inclusive) to start from (in chronological sense), the 'far' end of the reflog, "
              + "returned "
              + "'late' in the result.")
  @QueryParam("startHash")
  private String startHash;

  @Nullable
  @Pattern(regexp = Validation.HASH_REGEX, message = Validation.HASH_MESSAGE)
  @Parameter(
      description =
          "Hash of the reflog (inclusive) to end at (in chronological sense), the 'near' end of the reflog, returned "
              + "'early' "
              + "in the result.")
  @QueryParam("endHash")
  private String endHash;

  @Nullable
  @Parameter(
      description =
          "A Common Expression Language (CEL) expression. An intro to CEL can be found at https://github.com/google/cel-spec/blob/master/doc/intro.md.\n\n"
              + "Usable variables within the expression are:\n\n"
              + "- 'reflog' with fields 'refLogId' (string), 'refName' (string), 'commitHash' (string), 'parentRefLogId' "
              + "(string), ',operation' (string), 'operationTime' (long)\n\n"
              + "Hint: when filtering entries, you can determine whether entries are \"missing\" (filtered) by "
              + "checking whether 'ReflogResponseEntry.parentRefLogId' is different from the hash of the previous "
              + "reflog in the log response.")
  @QueryParam("filter")
  private String filter;

  public RefLogParams() {}

  @org.immutables.builder.Builder.Constructor
  RefLogParams(
      @Nullable String startHash,
      @Nullable String endHash,
      @Nullable Integer maxRecords,
      @Nullable String pageToken,
      @Nullable String filter) {
    super(maxRecords, pageToken);
    this.startHash = startHash;
    this.endHash = endHash;
    this.filter = filter;
  }

  @Nullable
  public String startHash() {
    return startHash;
  }

  @Nullable
  public String endHash() {
    return endHash;
  }

  @Nullable
  public String filter() {
    return filter;
  }

  public static RefLogParamsBuilder builder() {
    return new RefLogParamsBuilder();
  }

  public static RefLogParams empty() {
    return builder().build();
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", RefLogParams.class.getSimpleName() + "[", "]")
        .add("startHash='" + startHash + "'")
        .add("endHash='" + endHash + "'")
        .add("filter='" + filter + "'")
        .add("maxRecords=" + maxRecords())
        .add("pageToken='" + pageToken() + "'")
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RefLogParams that = (RefLogParams) o;
    return Objects.equals(startHash, that.startHash)
        && Objects.equals(endHash, that.endHash)
        && Objects.equals(filter, that.filter)
        && Objects.equals(maxRecords(), that.maxRecords())
        && Objects.equals(pageToken(), that.pageToken());
  }

  @Override
  public int hashCode() {
    return Objects.hash(startHash, endHash, filter, maxRecords(), pageToken());
  }
}
