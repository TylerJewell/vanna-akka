package io.akka.vanna.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * An id derived only from a training item's own content and its kind — SPEC-001 rules 1, 2.
 *
 * <p>The same content hashes to the same id every time, which is what makes adding it twice
 * a no-op (question-log row 2) rather than a duplicate. The kind suffix lets {@link
 * #kindOf(String)} answer rule 3 — which collection an id belongs to — without the caller
 * saying so.
 */
public final class TrainingItemId {

  private TrainingItemId() {}

  public enum Kind {
    DDL("ddl"),
    DOCUMENTATION("doc"),
    QUESTION_SQL("sql");

    public final String suffix;

    Kind(String suffix) {
      this.suffix = suffix;
    }
  }

  public static String of(String content, Kind kind) {
    return sha256Hex(content) + "-" + kind.suffix;
  }

  /** Rule 3: an id's own suffix says which collection it belongs to. */
  public static Kind kindOf(String id) {
    for (Kind kind : Kind.values()) {
      if (id.endsWith("-" + kind.suffix)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("not a training item id: " + id);
  }

  private static String sha256Hex(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
