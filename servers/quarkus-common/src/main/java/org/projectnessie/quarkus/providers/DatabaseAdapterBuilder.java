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
package org.projectnessie.quarkus.providers;

import org.projectnessie.versioned.persist.adapter.ContentVariantSupplier;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;

/** Factory interface for creating database adapter instances. */
public interface DatabaseAdapterBuilder {

  /**
   * Creates a new database adapter instance.
   *
   * @return new database adapter instance
   * @param contentVariantSupplier provides the kind of content, whether it's only stored on a
   *     reference or requires global state.
   */
  DatabaseAdapter newDatabaseAdapter(ContentVariantSupplier contentVariantSupplier);
}
