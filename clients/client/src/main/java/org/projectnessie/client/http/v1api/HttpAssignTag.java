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
package org.projectnessie.client.http.v1api;

import org.projectnessie.client.api.AssignTagBuilder;
import org.projectnessie.client.http.NessieApiClient;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Reference;

final class HttpAssignTag extends BaseHttpOnTagRequest<AssignTagBuilder>
    implements AssignTagBuilder {

  private Reference assignTo;

  HttpAssignTag(NessieApiClient client) {
    super(client);
  }

  @Override
  public AssignTagBuilder assignTo(Reference assignTo) {
    this.assignTo = assignTo;
    return this;
  }

  @Override
  public void submit() throws NessieNotFoundException, NessieConflictException {
    client.getTreeApi().assignTag(tagName, hash, assignTo);
  }
}