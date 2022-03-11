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
final class ClientKey {

  private final Version version;
  private final String builderClass;
  private final Class<? extends NessieApi> type;
  private final Map<String, String> configs;

  ClientKey(
      Version version,
      String builderClass,
      Class<? extends NessieApi> type,
      Map<String, String> configs) {
    this.version = Objects.requireNonNull(version);
    this.builderClass = Objects.requireNonNull(builderClass);
    this.type = Objects.requireNonNull(type);
    this.configs = Objects.requireNonNull(configs);
  }

  Version getVersion() {
    return version;
  }

  String getBuilderClass() {
    return builderClass;
  }

  Class<? extends NessieApi> getType() {
    return type;
  }

  Map<String, String> getConfigs() {
    return configs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ClientKey clientKey = (ClientKey) o;
    return Objects.equals(version, clientKey.version)
        && Objects.equals(builderClass, clientKey.builderClass)
        && Objects.equals(type, clientKey.type)
        && Objects.equals(configs, clientKey.configs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, builderClass, type, configs);
  }

  @Override
  public String toString() {
    return String.format(
        "client-%s-%s-%s-%s",
        getVersion(),
        type.getName(),
        builderClass,
        configs.entrySet().stream()
            .map(e -> e.getKey() + '=' + e.getValue())
            .collect(Collectors.joining("_")));
  }
}
