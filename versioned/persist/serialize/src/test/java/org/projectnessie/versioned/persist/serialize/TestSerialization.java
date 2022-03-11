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
package org.projectnessie.versioned.persist.serialize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.versioned.persist.serialize.ProtoSerialization.toProto;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.UnsafeByteOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.Key;
import org.projectnessie.versioned.persist.adapter.CommitLogEntry;
import org.projectnessie.versioned.persist.adapter.ContentId;
import org.projectnessie.versioned.persist.adapter.ContentIdAndBytes;
import org.projectnessie.versioned.persist.adapter.ImmutableRepoDescription;
import org.projectnessie.versioned.persist.adapter.ImmutableRepoDescription.Builder;
import org.projectnessie.versioned.persist.adapter.KeyList;
import org.projectnessie.versioned.persist.adapter.KeyWithBytes;
import org.projectnessie.versioned.persist.adapter.KeyWithType;
import org.projectnessie.versioned.persist.adapter.RepoDescription;
import org.projectnessie.versioned.persist.serialize.AdapterTypes.GlobalStateLogEntry;
import org.projectnessie.versioned.persist.serialize.AdapterTypes.GlobalStatePointer;
import org.projectnessie.versioned.persist.serialize.AdapterTypes.NamedReference;
import org.projectnessie.versioned.persist.serialize.AdapterTypes.RefLogEntry;
import org.projectnessie.versioned.persist.serialize.AdapterTypes.RefPointer;

/** (Re-)serialization tests using random data for relevant types. */
class TestSerialization {

  public static class SerializationParam {
    final Supplier<Object> generator;
    final Class<?> type;
    final Function<Object, byte[]> serializer;
    final Function<byte[], Object> deserializer;

    public SerializationParam(
        Supplier<Object> generator,
        Class<?> type,
        Function<Object, byte[]> serializer,
        Function<byte[], Object> deserializer) {
      this.generator = generator;
      this.type = type;
      this.serializer = serializer;
      this.deserializer = deserializer;
    }

    @Override
    public String toString() {
      return type.getSimpleName();
    }
  }

  public static Supplier<Stream<SerializationParam>> params =
      () ->
          Stream.of(
              new SerializationParam(
                  TestSerialization::createEntry,
                  CommitLogEntry.class,
                  o -> toProto((CommitLogEntry) o).toByteArray(),
                  ProtoSerialization::protoToCommitLogEntry),
              new SerializationParam(
                  TestSerialization::createGlobalEntry,
                  GlobalStateLogEntry.class,
                  o -> ((GlobalStateLogEntry) o).toByteArray(),
                  b -> {
                    try {
                      return GlobalStateLogEntry.parseFrom(b);
                    } catch (InvalidProtocolBufferException e) {
                      throw new RuntimeException(e);
                    }
                  }),
              new SerializationParam(
                  TestSerialization::createGlobalState,
                  GlobalStatePointer.class,
                  o -> ((GlobalStatePointer) o).toByteArray(),
                  b -> {
                    try {
                      return GlobalStatePointer.parseFrom(b);
                    } catch (InvalidProtocolBufferException e) {
                      throw new RuntimeException(e);
                    }
                  }),
              new SerializationParam(
                  TestSerialization::createRefLogEntry,
                  RefLogEntry.class,
                  o -> ((RefLogEntry) o).toByteArray(),
                  b -> {
                    try {
                      return RefLogEntry.parseFrom(b);
                    } catch (InvalidProtocolBufferException e) {
                      throw new RuntimeException(e);
                    }
                  }));

  public static Stream<SerializationParam> params() {
    return params.get();
  }

  @ParameterizedTest
  @MethodSource("params")
  @Disabled("exists to compare serialized sizes of different implementations")
  public void entries(SerializationParam ser) {
    Object value = ser.generator.get();
    byte[] serialized = ser.serializer.apply(value);
    System.err.printf("%s serialized size: %s%n", ser, serialized.length);

    Object deserialized = ser.deserializer.apply(serialized);
    assertThat(deserialized).isEqualTo(value);
  }

  @ParameterizedTest
  @MethodSource("params")
  public void serialize1k(SerializationParam ser) {
    Object value = ser.generator.get();
    for (int i = 0; i < 1_000; i++) {
      @SuppressWarnings("unused")
      byte[] serialized = ser.serializer.apply(value);

      Object deserialized = ser.deserializer.apply(serialized);
      assertThat(deserialized).isEqualTo(value);
      byte[] reserialized = ser.serializer.apply(deserialized);
      assertThat(serialized).isEqualTo(reserialized);
    }
  }

  @ParameterizedTest
  @MethodSource("params")
  public void deserialize1k(SerializationParam ser) {
    Object value = ser.generator.get();
    byte[] serialized = ser.serializer.apply(value);

    for (int i = 0; i < 1_000; i++) {
      @SuppressWarnings("unused")
      Object deserialized = ser.deserializer.apply(serialized);
      assertThat(deserialized).isEqualTo(value);
      byte[] reserialized = ser.serializer.apply(deserialized);
      assertThat(serialized).isEqualTo(reserialized);
    }
  }

  static class TypeSerialization<A, P> {
    final Class<A> apiType;
    final Class<P> protoType;
    final Supplier<A> generator;
    final Function<A, P> toProto;
    final Function<byte[], P> parseProto;
    final Function<byte[], A> parseApi;

    TypeSerialization(
        Class<A> apiType,
        Class<P> protoType,
        Supplier<A> generator,
        Function<A, P> toProto,
        Function<byte[], P> parseProto,
        Function<byte[], A> parseApi) {
      this.apiType = apiType;
      this.protoType = protoType;
      this.generator = generator;
      this.toProto = toProto;
      this.parseProto = parseProto;
      this.parseApi = parseApi;
    }

    @Override
    public String toString() {
      return apiType.getSimpleName();
    }
  }

  @SuppressWarnings("rawtypes")
  static List<TypeSerialization> typeSerialization() {
    List<TypeSerialization> params = new ArrayList<>();
    params.add(
        new TypeSerialization<>(
            RepoDescription.class,
            AdapterTypes.RepoProps.class,
            TestSerialization::createRepoDescription,
            ProtoSerialization::toProto,
            v -> {
              try {
                return AdapterTypes.RepoProps.parseFrom(v);
              } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
              }
            },
            ProtoSerialization::protoToRepoDescription));

    params.add(
        new TypeSerialization<>(
            CommitLogEntry.class,
            AdapterTypes.CommitLogEntry.class,
            TestSerialization::createEntry,
            ProtoSerialization::toProto,
            v -> {
              try {
                return AdapterTypes.CommitLogEntry.parseFrom(v);
              } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
              }
            },
            ProtoSerialization::protoToCommitLogEntry));

    params.add(
        new TypeSerialization<>(
            ContentIdAndBytes.class,
            AdapterTypes.ContentIdWithBytes.class,
            TestSerialization::createContentIdWithBytes,
            ProtoSerialization::toProto,
            v -> {
              try {
                return AdapterTypes.ContentIdWithBytes.parseFrom(v);
              } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
              }
            },
            v -> {
              try {
                return ProtoSerialization.protoToContentIdAndBytes(
                    AdapterTypes.ContentIdWithBytes.parseFrom(v));
              } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
              }
            }));
    params.add(
        new TypeSerialization<>(
            ContentId.class,
            AdapterTypes.ContentIdWithType.class,
            TestSerialization::createContentId,
            ProtoSerialization::toProto,
            v -> {
              try {
                return AdapterTypes.ContentIdWithType.parseFrom(v);
              } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
              }
            },
            v -> {
              try {
                return ProtoSerialization.protoToContentId(
                    AdapterTypes.ContentIdWithType.parseFrom(v));
              } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
              }
            }));
    params.add(
        new TypeSerialization<>(
            KeyWithBytes.class,
            AdapterTypes.KeyWithBytes.class,
            TestSerialization::createKeyWithBytes,
            ProtoSerialization::toProto,
            v -> {
              try {
                return AdapterTypes.KeyWithBytes.parseFrom(v);
              } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
              }
            },
            v -> {
              try {
                return ProtoSerialization.protoToKeyWithBytes(
                    AdapterTypes.KeyWithBytes.parseFrom(v));
              } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
              }
            }));
    params.add(
        new TypeSerialization<>(
            KeyWithType.class,
            AdapterTypes.KeyWithType.class,
            TestSerialization::createKeyWithType,
            ProtoSerialization::toProto,
            v -> {
              try {
                return AdapterTypes.KeyWithType.parseFrom(v);
              } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
              }
            },
            v -> {
              try {
                return ProtoSerialization.protoToKeyWithType(AdapterTypes.KeyWithType.parseFrom(v));
              } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
              }
            }));
    return params;
  }

  @ParameterizedTest
  @MethodSource("typeSerialization")
  public <A, P extends MessageLite> void typeSerialization(TypeSerialization<A, P> param) {
    for (int i = 0; i < 500; i++) {
      A entry = param.generator.get();

      P proto = param.toProto.apply(entry);
      byte[] serialized = proto.toByteArray();
      P deserialized = param.parseProto.apply(serialized);

      assertThat(proto).isEqualTo(deserialized);

      A deserializedEntry = param.parseApi.apply(serialized);
      assertThat(entry).isEqualTo(deserializedEntry);
    }
  }

  @RepeatedTest(50)
  public void stableOrderOfRepoDescProps() {
    ImmutableRepoDescription.Builder repoDescription = RepoDescription.builder().repoVersion(42);
    Map<String, String> props = new HashMap<>();
    for (int i = 0; i < 20; i++) {
      String key = randomString(50);
      String value = randomString(50);
      props.put(key, value);
      repoDescription.putProperties(key, value);
    }
    AdapterTypes.RepoProps repoProps = toProto(repoDescription.build());

    List<AdapterTypes.Entry> expected =
        props.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(
                e ->
                    AdapterTypes.Entry.newBuilder()
                        .setKey(e.getKey())
                        .setValue(e.getValue())
                        .build())
            .collect(Collectors.toList());
    assertThat(repoProps.getPropertiesList()).containsExactlyElementsOf(expected);
  }

  public static ContentId randomId() {
    return ContentId.of(UUID.randomUUID().toString());
  }

  public static Key randomKey() {
    ThreadLocalRandom r = ThreadLocalRandom.current();
    return Key.of("some" + r.nextInt(10000), "other" + r.nextInt(10000));
  }

  public static long randomLong() {
    return ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE);
  }

  public static Hash randomHash() {
    return Hash.of(randomBytes(16));
  }

  public static ByteString randomBytes(int num) {
    byte[] raw = new byte[num];
    ThreadLocalRandom.current().nextBytes(raw);
    return UnsafeByteOperations.unsafeWrap(raw);
  }

  public static String randomString(int num) {
    StringBuilder sb = new StringBuilder(num);
    for (int i = 0; i < num; i++) {
      sb.append((char) ThreadLocalRandom.current().nextInt(32, 126));
    }
    return sb.toString();
  }

  static GlobalStateLogEntry createGlobalEntry() {
    GlobalStateLogEntry.Builder entry =
        GlobalStateLogEntry.newBuilder().setCreatedTime(System.nanoTime() / 1000L);
    for (int i = 0; i < 20; i++) {
      entry.addParents(randomBytes(32));
    }
    for (int i = 0; i < 20; i++) {
      entry.addPuts(
          AdapterTypes.ContentIdWithBytes.newBuilder()
              .setContentId(AdapterTypes.ContentId.newBuilder().setId(randomString(64)).build())
              .setTypeUnused(2)
              .setValue(randomBytes(120))
              .build());
    }
    return entry.build();
  }

  static GlobalStatePointer createGlobalState() {
    GlobalStatePointer.Builder state = GlobalStatePointer.newBuilder().setGlobalId(randomBytes(32));
    for (int i = 0; i < 50; i++) {
      state.addNamedReferences(
          NamedReference.newBuilder()
              .setName(randomString(32))
              .setRef(
                  RefPointer.newBuilder()
                      .setType(RefPointer.Type.Branch)
                      .setHash(randomBytes(32))));
    }
    return state.build();
  }

  static RepoDescription createRepoDescription() {
    Builder builder = RepoDescription.builder().repoVersion(ThreadLocalRandom.current().nextInt());
    IntStream.range(0, ThreadLocalRandom.current().nextInt(20))
        .forEach(i -> builder.putProperties("key" + i, randomString(20)));
    return builder.build();
  }

  static CommitLogEntry createEntry() {
    return CommitLogEntry.of(
        System.nanoTime() / 1000L,
        randomHash(),
        randomLong(),
        Arrays.asList(
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash(),
            randomHash()),
        randomBytes(256),
        Arrays.asList(
            KeyWithBytes.of(randomKey(), randomId(), (byte) 1, randomBytes(32)),
            KeyWithBytes.of(randomKey(), randomId(), (byte) 1, randomBytes(32)),
            KeyWithBytes.of(randomKey(), randomId(), (byte) 1, randomBytes(32)),
            KeyWithBytes.of(randomKey(), randomId(), (byte) 1, randomBytes(32)),
            KeyWithBytes.of(randomKey(), randomId(), (byte) 1, randomBytes(32)),
            KeyWithBytes.of(randomKey(), randomId(), (byte) 1, randomBytes(32)),
            KeyWithBytes.of(randomKey(), randomId(), (byte) 1, randomBytes(32))),
        Arrays.asList(randomKey(), randomKey(), randomKey(), randomKey(), randomKey()),
        42,
        KeyList.of(
            IntStream.range(0, 20)
                .mapToObj(
                    i -> KeyWithType.of(randomKey(), ContentId.of(randomString(60)), (byte) 0))
                .collect(Collectors.toList())),
        IntStream.range(0, 20).mapToObj(i -> randomHash()).collect(Collectors.toList()));
  }

  static KeyWithType createKeyWithType() {
    return KeyWithType.of(
        randomKey(),
        ContentId.of(randomString(64)),
        (byte) ThreadLocalRandom.current().nextInt(0, 127));
  }

  static KeyWithBytes createKeyWithBytes() {
    return KeyWithBytes.of(
        randomKey(),
        ContentId.of(randomString(64)),
        (byte) ThreadLocalRandom.current().nextInt(0, 127),
        randomBytes(120));
  }

  static ContentId createContentId() {
    return ContentId.of(randomString(64));
  }

  static ContentIdAndBytes createContentIdWithBytes() {
    return ContentIdAndBytes.of(ContentId.of(randomString(64)), randomBytes(120));
  }

  static RefLogEntry createRefLogEntry() {
    RefLogEntry.Builder entry =
        RefLogEntry.newBuilder().setOperationTime(System.nanoTime() / 1000L);
    for (int i = 0; i < 20; i++) {
      entry.addParents(randomHash().asBytes());
    }
    for (int i = 0; i < 20; i++) {
      entry.addSourceHashes(randomHash().asBytes());
    }
    entry.setRefLogId(randomHash().asBytes());
    entry.setRefName(ByteString.copyFromUtf8("temp"));
    entry.setRefType(RefLogEntry.RefType.Branch);
    entry.setCommitHash(randomHash().asBytes());
    entry.setOperation(RefLogEntry.Operation.MERGE);
    return entry.build();
  }
}
