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
package org.projectnessie.api.http;

import java.util.List;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.projectnessie.api.TreeApi;
import org.projectnessie.api.params.CommitLogParams;
import org.projectnessie.api.params.EntriesParams;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.EntriesResponse;
import org.projectnessie.model.LogResponse;
import org.projectnessie.model.Merge;
import org.projectnessie.model.Operations;
import org.projectnessie.model.Reference;
import org.projectnessie.model.Transplant;
import org.projectnessie.model.Validation;

@Consumes(value = MediaType.APPLICATION_JSON)
@Path("trees")
public interface HttpTreeApi extends TreeApi {
  /** Get all references. */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get all references")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Returned references.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(type = SchemaType.ARRAY, implementation = Reference.class))),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
  })
  List<Reference> getAllReferences();

  /** Get details for the default reference. */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("tree")
  @Operation(summary = "Get default branch for commits and reads")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Returns name and latest hash of the default branch.",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON,
                examples = {@ExampleObject(ref = "refObj")},
                schema = @Schema(implementation = Branch.class))),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(responseCode = "404", description = "Default branch not found.")
  })
  Branch getDefaultBranch() throws NessieNotFoundException;

  /**
   * Create a new reference.
   *
   * <p>The type of {@code reference}, which can be either a {@link Branch} or {@link
   * org.projectnessie.model.Tag}, determines the type of the reference to be created.
   *
   * <p>{@link Reference#getName()} defines the the name of the reference to be created, {@link
   * Reference#getHash()} is the hash of the created reference, the HEAD of the created reference.
   * {@code sourceRefName} is the name of the reference which contains {@link Reference#getHash()},
   * and must be present if {@link Reference#getHash()} is present.
   *
   * <p>Specifying no {@link Reference#getHash()} means that the new reference will be created "at
   * the beginning of time".
   */
  @POST
  @Path("tree")
  @Operation(
      summary = "Create a new reference",
      description =
          "The type of 'refObj', which can be either a 'Branch' or 'Tag', determines "
              + "the type of the reference to be created.\n"
              + "\n"
              + "'Reference.name' defines the the name of the reference to be created,"
              + "'Reference.hash' is the hash of the created reference, the HEAD of the "
              + "created reference. 'sourceRefName' is the name of the reference which contains "
              + "'Reference.hash', and must be present if 'Reference.hash' is present.\n"
              + "\n"
              + "Specifying no 'Reference.hash' means that the new reference will be created "
              + "\"at the beginning of time\".")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Created successfully.",
        content = {
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              examples = {@ExampleObject(ref = "refObjNew")},
              schema = @Schema(implementation = Reference.class))
        }),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(responseCode = "403", description = "Not allowed to create reference"),
    @APIResponse(responseCode = "409", description = "Reference already exists"),
  })
  Reference createReference(
      @Nullable
          @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          @Parameter(description = "Source named reference")
          @QueryParam("sourceRefName")
          String sourceRefName,
      @Valid
          @NotNull
          @RequestBody(
              description = "Reference to create.",
              content = {
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = {@ExampleObject(ref = "refObjNew")})
              })
          Reference reference)
      throws NessieNotFoundException, NessieConflictException;

  /** Get details of a particular ref, if it exists. */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("tree/{ref}")
  @Operation(summary = "Fetch details of a reference")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Found and returned reference.",
        content = {
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              examples = {@ExampleObject(ref = "refObj")},
              schema = @Schema(implementation = Reference.class))
        }),
    @APIResponse(responseCode = "400", description = "Invalid input, ref name not valid"),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(responseCode = "403", description = "Not allowed to view the given reference"),
    @APIResponse(responseCode = "404", description = "Ref not found")
  })
  Reference getReferenceByName(
      @NotNull
          @Pattern(
              regexp = Validation.REF_NAME_OR_HASH_REGEX,
              message = Validation.REF_NAME_OR_HASH_MESSAGE)
          @Parameter(
              description = "name of ref to fetch",
              examples = {@ExampleObject(ref = "ref")})
          @PathParam("ref")
          String refName)
      throws NessieNotFoundException;

  /**
   * Retrieve objects for a ref, potentially truncated by the backend.
   *
   * <p>Retrieves up to {@code maxRecords} objects for the given named reference (tag or branch) or
   * the given {@link org.projectnessie.model.Hash}. The backend <em>may</em> respect the given
   * {@code max} records hint, but return less or more entries. Backends may also cap the returned
   * entries at a hard-coded limit, the default REST server implementation has such a hard-coded
   * limit.
   *
   * <p>Invoking {@code getEntries()} does <em>not</em> guarantee to return all commit log entries
   * of a given reference, because the result can be truncated by the backend.
   *
   * <p>To implement paging, check {@link EntriesResponse#hasMore() EntriesResponse.hasMore()} and,
   * if {@code true}, pass the value of {@link EntriesResponse#getToken()
   * EntriesResponse.getToken()} in the next invocation of {@code getEntries()} as the {@code
   * pageToken} parameter.
   *
   * <p>See {@code org.projectnessie.client.StreamingUtil} in {@code nessie-client}.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("tree/{ref}/entries")
  @Operation(
      summary = "Fetch all entries for a given reference",
      description =
          "Retrieves objects for a ref, potentially truncated by the backend.\n"
              + "\n"
              + "Retrieves up to 'maxRecords' entries for the "
              + "given named reference (tag or branch) or the given hash. "
              + "The backend may respect the given 'max' records hint, but return less or more entries. "
              + "Backends may also cap the returned entries at a hard-coded limit, the default "
              + "REST server implementation has such a hard-coded limit.\n"
              + "\n"
              + "To implement paging, check 'hasMore' in the response and, if 'true', pass the value "
              + "returned as 'token' in the next invocation as the 'pageToken' parameter.\n"
              + "\n"
              + "The content and meaning of the returned 'token' is \"private\" to the implementation,"
              + "treat is as an opaque value.\n"
              + "\n"
              + "It is wrong to assume that invoking this method with a very high 'maxRecords' value "
              + "will return all commit log entries.\n"
              + "\n"
              + "The 'query_expression' parameter allows for advanced filtering capabilities using the Common Expression Language (CEL).\n"
              + "An intro to CEL can be found at https://github.com/google/cel-spec/blob/master/doc/intro.md.\n")
  @APIResponses({
    @APIResponse(
        description = "all objects for a reference",
        content = {
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              examples = {@ExampleObject(ref = "entriesResponse")},
              schema = @Schema(implementation = EntriesResponse.class))
        }),
    @APIResponse(responseCode = "200", description = "Returned successfully."),
    @APIResponse(responseCode = "400", description = "Invalid input, ref name not valid"),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(
        responseCode = "403",
        description = "Not allowed to view the given reference or fetch entries for it"),
    @APIResponse(responseCode = "404", description = "Ref not found")
  })
  EntriesResponse getEntries(
      @NotNull
          @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          @Parameter(
              description = "name of ref to fetch from",
              examples = {@ExampleObject(ref = "ref")})
          @PathParam("ref")
          String refName,
      @NotNull @Valid @BeanParam EntriesParams params)
      throws NessieNotFoundException;

  /**
   * Retrieve the commit log for a ref, potentially truncated by the backend.
   *
   * <p>Retrieves up to {@code maxRecords} commit-log-entries starting at the HEAD of the given
   * named reference (tag or branch) or the given {@link org.projectnessie.model.Hash}. The backend
   * <em>may</em> respect the given {@code max} records hint, but return less or more entries.
   * Backends may also cap the returned entries at a hard-coded limit, the default REST server
   * implementation has such a hard-coded limit.
   *
   * <p>Invoking {@code getCommitLog()} does <em>not</em> guarantee to return all commit log entries
   * of a given reference, because the result can be truncated by the backend.
   *
   * <p>To implement paging, check {@link LogResponse#hasMore() LogResponse.hasMore()} and, if
   * {@code true}, pass the value of {@link LogResponse#getToken() LogResponse.getToken()} in the
   * next invocation of {@code getCommitLog()} as the {@code pageToken} parameter.
   *
   * <p>See {@code org.projectnessie.client.StreamingUtil} in {@code nessie-client}.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("tree/{ref}/log")
  @Operation(
      summary = "Get commit log for a reference",
      description =
          "Retrieve the commit log for a ref, potentially truncated by the backend.\n"
              + "\n"
              + "Retrieves up to 'maxRecords' commit-log-entries starting at the HEAD of the "
              + "given named reference (tag or branch) or the given hash. "
              + "The backend may respect the given 'max' records hint, but return less or more entries. "
              + "Backends may also cap the returned entries at a hard-coded limit, the default "
              + "REST server implementation has such a hard-coded limit.\n"
              + "\n"
              + "To implement paging, check 'hasMore' in the response and, if 'true', pass the value "
              + "returned as 'token' in the next invocation as the 'pageToken' parameter.\n"
              + "\n"
              + "The content and meaning of the returned 'token' is \"private\" to the implementation,"
              + "treat is as an opaque value.\n"
              + "\n"
              + "It is wrong to assume that invoking this method with a very high 'maxRecords' value "
              + "will return all commit log entries.\n"
              + "\n"
              + "The 'query_expression' parameter allows for advanced filtering capabilities using the Common Expression Language (CEL).\n"
              + "An intro to CEL can be found at https://github.com/google/cel-spec/blob/master/doc/intro.md.\n")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Returned commits.",
        content = {
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              examples = {@ExampleObject(ref = "logResponse")},
              schema = @Schema(implementation = LogResponse.class))
        }),
    @APIResponse(responseCode = "400", description = "Invalid input, ref name not valid"),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(
        responseCode = "403",
        description = "Not allowed to view the given reference or get commit log for it"),
    @APIResponse(responseCode = "404", description = "Ref doesn't exists")
  })
  LogResponse getCommitLog(
      @NotNull
          @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          @Parameter(
              description = "ref to show log from",
              examples = {@ExampleObject(ref = "ref")})
          @PathParam("ref")
          String ref,
      @NotNull @Valid @BeanParam CommitLogParams params)
      throws NessieNotFoundException;

  /** Update a tag. */
  @PUT
  @Path("tag/{tagName}")
  @Operation(
      summary = "Set a tag to a specific hash via a named-reference.",
      description =
          "This operation takes the name of the tag to reassign and the hash and the name of a "
              + "named-reference via which the caller has access to that hash.")
  @APIResponses({
    @APIResponse(responseCode = "204", description = "Assigned successfully"),
    @APIResponse(responseCode = "400", description = "Invalid input, ref/hash name not valid"),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(responseCode = "403", description = "Not allowed to view or assign tag"),
    @APIResponse(responseCode = "404", description = "One or more references don't exist"),
    @APIResponse(responseCode = "412", description = "Update conflict")
  })
  void assignTag(
      @NotNull
          @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          @Parameter(
              description = "Tag name to reassign",
              examples = {@ExampleObject(ref = "ref")})
          @PathParam("tagName")
          String tagName,
      @NotNull
          @Pattern(regexp = Validation.HASH_REGEX, message = Validation.HASH_MESSAGE)
          @Parameter(
              description = "Expected previous hash of tag",
              examples = {@ExampleObject(ref = "hash")})
          @QueryParam("expectedHash")
          String oldHash,
      @Valid
          @NotNull
          @RequestBody(
              description =
                  "Reference hash to which 'tagName' shall be assigned to. This must be either a "
                      + "'Branch' or 'Tag' via which the hash is visible to the caller.",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      examples = {@ExampleObject(ref = "refObj"), @ExampleObject(ref = "tagObj")}))
          Reference assignTo)
      throws NessieNotFoundException, NessieConflictException;

  /** Delete a tag. */
  @DELETE
  @Path("tag/{tagName}")
  @Operation(summary = "Delete a tag")
  @APIResponses({
    @APIResponse(responseCode = "204", description = "Deleted successfully."),
    @APIResponse(responseCode = "400", description = "Invalid input, ref/hash name not valid"),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(responseCode = "403", description = "Not allowed to view or delete tag"),
    @APIResponse(responseCode = "404", description = "Ref doesn't exists"),
    @APIResponse(responseCode = "409", description = "update conflict"),
  })
  void deleteTag(
      @NotNull
          @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          @Parameter(
              description = "Tag to delete",
              examples = {@ExampleObject(ref = "ref")})
          @PathParam("tagName")
          String tagName,
      @Pattern(regexp = Validation.HASH_REGEX, message = Validation.HASH_MESSAGE)
          @Parameter(
              description = "Expected hash of tag",
              examples = {@ExampleObject(ref = "hash")})
          @QueryParam("expectedHash")
          String hash)
      throws NessieConflictException, NessieNotFoundException;

  /** Update a branch. */
  @PUT
  @Path("branch/{branchName}")
  @Operation(
      summary = "Set a branch to a specific hash via a named-reference.",
      description =
          "This operation takes the name of the branch to reassign and the hash and the name of a "
              + "named-reference via which the caller has access to that hash.")
  @APIResponses({
    @APIResponse(responseCode = "204", description = "Assigned successfully"),
    @APIResponse(responseCode = "400", description = "Invalid input, ref/hash name not valid"),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(responseCode = "403", description = "Not allowed to view or assign branch"),
    @APIResponse(responseCode = "404", description = "One or more references don't exist"),
    @APIResponse(responseCode = "409", description = "Update conflict")
  })
  void assignBranch(
      @NotNull
          @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          @Parameter(
              description = "Tag name to reassign",
              examples = {@ExampleObject(ref = "ref")})
          @PathParam("branchName")
          String branchName,
      @NotNull
          @Pattern(regexp = Validation.HASH_REGEX, message = Validation.HASH_MESSAGE)
          @Parameter(
              description = "Expected previous hash of tag",
              examples = {@ExampleObject(ref = "hash")})
          @QueryParam("expectedHash")
          String oldHash,
      @Valid
          @NotNull
          @RequestBody(
              description =
                  "Reference hash to which 'branchName' shall be assigned to. This must be either a "
                      + "'Branch' or 'Tag' via which the hash is visible to the caller.",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      examples = {@ExampleObject(ref = "refObj"), @ExampleObject(ref = "tagObj")}))
          Reference assignTo)
      throws NessieNotFoundException, NessieConflictException;

  /** Delete a branch. */
  @DELETE
  @Path("branch/{branchName}")
  @Operation(summary = "Delete a branch endpoint")
  @APIResponses({
    @APIResponse(responseCode = "204", description = "Deleted successfully."),
    @APIResponse(responseCode = "400", description = "Invalid input, ref/hash name not valid"),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(responseCode = "403", description = "Not allowed to view or delete branch"),
    @APIResponse(responseCode = "404", description = "Ref doesn't exists"),
    @APIResponse(responseCode = "409", description = "update conflict"),
  })
  void deleteBranch(
      @NotNull
          @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          @Parameter(
              description = "Branch to delete",
              examples = {@ExampleObject(ref = "ref")})
          @PathParam("branchName")
          String branchName,
      @NotNull
          @Pattern(regexp = Validation.HASH_REGEX, message = Validation.HASH_MESSAGE)
          @Parameter(
              description = "Expected hash of tag",
              examples = {@ExampleObject(ref = "hash")})
          @QueryParam("expectedHash")
          String hash)
      throws NessieConflictException, NessieNotFoundException;

  /** cherry pick a set of commits into a branch. */
  @POST
  @Path("branch/{branchName}/transplant")
  @Operation(
      summary = "Transplant commits from 'transplant' onto 'branchName'",
      description =
          "This is done as an atomic operation such that only the last of the sequence is ever "
              + "visible to concurrent readers/writers. The sequence to transplant must be "
              + "contiguous and in order.")
  @APIResponses({
    @APIResponse(responseCode = "204", description = "Merged successfully."),
    @APIResponse(responseCode = "400", description = "Invalid input, ref/hash name not valid"),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(
        responseCode = "403",
        description = "Not allowed to view the given reference or transplant commits"),
    @APIResponse(responseCode = "404", description = "Ref doesn't exists"),
    @APIResponse(responseCode = "409", description = "update conflict")
  })
  void transplantCommitsIntoBranch(
      @NotNull
          @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          @Parameter(
              description = "Branch to transplant into",
              examples = {@ExampleObject(ref = "ref")})
          @PathParam("branchName")
          String branchName,
      @NotNull
          @Pattern(regexp = Validation.HASH_REGEX, message = Validation.HASH_MESSAGE)
          @Parameter(
              description = "Expected hash of tag.",
              examples = {@ExampleObject(ref = "hash")})
          @QueryParam("expectedHash")
          String hash,
      @Parameter(
              description = "commit message",
              examples = {@ExampleObject(ref = "commitMessage")})
          @QueryParam("message")
          String message,
      @Valid
          @RequestBody(
              description = "Hashes to transplant",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      examples = {@ExampleObject(ref = "transplant")}))
          Transplant transplant)
      throws NessieNotFoundException, NessieConflictException;

  /** merge mergeRef onto ref. */
  @POST
  @Path("branch/{branchName}/merge")
  @Operation(
      summary = "Merge commits from 'mergeRef' onto 'branchName'.",
      description =
          "Merge items from an existing hash in 'mergeRef' into the requested branch. "
              + "The merge is always a rebase + fast-forward merge and is only completed if the "
              + "rebase is conflict free. The set of commits added to the branch will be all of "
              + "those until we arrive at a common ancestor. Depending on the underlying "
              + "implementation, the number of commits allowed as part of this operation may be limited.")
  @APIResponses({
    @APIResponse(responseCode = "204", description = "Merged successfully."),
    @APIResponse(responseCode = "400", description = "Invalid input, ref/hash name not valid"),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(
        responseCode = "403",
        description = "Not allowed to view the given reference or merge commits"),
    @APIResponse(responseCode = "404", description = "Ref doesn't exists"),
    @APIResponse(responseCode = "409", description = "update conflict")
  })
  void mergeRefIntoBranch(
      @NotNull
          @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          @Parameter(
              description = "Branch to merge into",
              examples = {@ExampleObject(ref = "ref")})
          @PathParam("branchName")
          String branchName,
      @NotNull
          @Pattern(regexp = Validation.HASH_REGEX, message = Validation.HASH_MESSAGE)
          @Parameter(
              description = "Expected current HEAD of 'branchName'",
              examples = {@ExampleObject(ref = "hash")})
          @QueryParam("expectedHash")
          String hash,
      @Valid
          @NotNull
          @RequestBody(
              description =
                  "Merge operation that defines the source reference name and an optional hash. "
                      + "If 'fromHash' is not present, the current 'sourceRef's HEAD will be used.",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      examples = {@ExampleObject(ref = "merge")}))
          Merge merge)
      throws NessieNotFoundException, NessieConflictException;

  @POST
  @Path("branch/{branchName}/commit")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
      summary =
          "Commit multiple operations against the given branch expecting that branch to have "
              + "the given hash as its latest commit. The hash in the successful response contains the hash of the "
              + "commit that contains the operations of the invocation.")
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Updated successfully.",
        content = {
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              examples = {@ExampleObject(ref = "refObj")},
              schema = @Schema(implementation = Branch.class))
        }),
    @APIResponse(responseCode = "400", description = "Invalid input, ref/hash name not valid"),
    @APIResponse(responseCode = "401", description = "Invalid credentials provided"),
    @APIResponse(
        responseCode = "403",
        description = "Not allowed to view the given reference or perform commits"),
    @APIResponse(responseCode = "404", description = "Provided ref doesn't exists"),
    @APIResponse(responseCode = "409", description = "Update conflict")
  })
  Branch commitMultipleOperations(
      @NotNull
          @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          @Parameter(
              description = "Branch to change, defaults to default branch.",
              examples = {@ExampleObject(ref = "ref")})
          @PathParam("branchName")
          String branchName,
      @NotNull
          @Pattern(regexp = Validation.HASH_REGEX, message = Validation.HASH_MESSAGE)
          @Parameter(
              description = "Expected hash of branch.",
              examples = {@ExampleObject(ref = "hash")})
          @QueryParam("expectedHash")
          String hash,
      @Valid
          @NotNull
          @RequestBody(
              description = "Operations",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_JSON,
                      examples = {@ExampleObject(ref = "operations")}))
          Operations operations)
      throws NessieNotFoundException, NessieConflictException;
}