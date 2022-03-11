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
package org.projectnessie.tools.compatibility.internal;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.projectnessie.client.api.NessieApi;
import org.projectnessie.tools.compatibility.api.Version;

/** Value object representing the version, type and configuration of a {@link NessieApi}. */
final class ServerKey {

  private final Version version;
  private final String databaseAdapterName;
  private final Map<String, String> databaseAdapterConfig;

  ServerKey(
      Version version, String databaseAdapterName, Map<String, String> databaseAdapterConfig) {
    this.version = Objects.requireNonNull(version);
    this.databaseAdapterName = Objects.requireNonNull(databaseAdapterName);
    this.databaseAdapterConfig = Objects.requireNonNull(databaseAdapterConfig);
  }

  Version getVersion() {
    return version;
  }

  String getDatabaseAdapterName() {
    return databaseAdapterName;
  }

  Map<String, String> getDatabaseAdapterConfig() {
    return databaseAdapterConfig;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ServerKey serverKey = (ServerKey) o;
    return Objects.equals(version, serverKey.version)
        && Objects.equals(databaseAdapterName, serverKey.databaseAdapterName)
        && Objects.equals(databaseAdapterConfig, serverKey.databaseAdapterConfig);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, databaseAdapterName, databaseAdapterConfig);
  }

  @Override
  public String toString() {
    return String.format(
        "server-%s-%s-%s",
        getVersion(),
        databaseAdapterName,
        databaseAdapterConfig.entrySet().stream()
            .map(e -> e.getKey() + '=' + e.getValue())
            .collect(Collectors.joining("_")));
  }
}
