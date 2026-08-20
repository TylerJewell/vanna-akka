package io.akka.vanna.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One caller's whole training store — SPEC-001 §2.
 *
 * <p>The source is one process holding one set of ChromaDB collections; a profile gives that
 * store an identity so more than one can exist side by side. {@code nextSeq} is never reset and
 * never reused — it exists only to break a similarity tie by insertion order (SPEC-001 rule 5),
 * not to count anything a caller would see.
 */
public record TrainingProfileState(
    String profileId,
    Map<String, DdlItem> ddl,
    Map<String, DocumentationItem> documentation,
    Map<String, QuestionSqlItem> questionSql,
    long nextSeq) {

  public static TrainingProfileState empty(String profileId) {
    return new TrainingProfileState(
        profileId, Map.of(), Map.of(), Map.of(), 0);
  }

  /** Rule 1: adding content already present under its own id is a no-op. */
  public TrainingProfileState withDdl(DdlItem item) {
    if (ddl.containsKey(item.id())) {
      return this;
    }
    Map<String, DdlItem> next = new LinkedHashMap<>(ddl);
    next.put(item.id(), item);
    return new TrainingProfileState(profileId, next, documentation, questionSql, nextSeq + 1);
  }

  public TrainingProfileState withDocumentation(DocumentationItem item) {
    if (documentation.containsKey(item.id())) {
      return this;
    }
    Map<String, DocumentationItem> next = new LinkedHashMap<>(documentation);
    next.put(item.id(), item);
    return new TrainingProfileState(profileId, ddl, next, questionSql, nextSeq + 1);
  }

  public TrainingProfileState withQuestionSql(QuestionSqlItem item) {
    if (questionSql.containsKey(item.id())) {
      return this;
    }
    Map<String, QuestionSqlItem> next = new LinkedHashMap<>(questionSql);
    next.put(item.id(), item);
    return new TrainingProfileState(profileId, ddl, documentation, next, nextSeq + 1);
  }

  /** Rule 3: which collection an id is removed from is read from the id itself. */
  public TrainingProfileState withoutItem(String id) {
    return switch (TrainingItemId.kindOf(id)) {
      case DDL -> {
        if (!ddl.containsKey(id)) yield this;
        Map<String, DdlItem> next = new LinkedHashMap<>(ddl);
        next.remove(id);
        yield new TrainingProfileState(profileId, next, documentation, questionSql, nextSeq);
      }
      case DOCUMENTATION -> {
        if (!documentation.containsKey(id)) yield this;
        Map<String, DocumentationItem> next = new LinkedHashMap<>(documentation);
        next.remove(id);
        yield new TrainingProfileState(profileId, ddl, next, questionSql, nextSeq);
      }
      case QUESTION_SQL -> {
        if (!questionSql.containsKey(id)) yield this;
        Map<String, QuestionSqlItem> next = new LinkedHashMap<>(questionSql);
        next.remove(id);
        yield new TrainingProfileState(profileId, ddl, documentation, next, nextSeq);
      }
    };
  }
}
