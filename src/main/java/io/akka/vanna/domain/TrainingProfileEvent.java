package io.akka.vanna.domain;

import akka.javasdk.annotations.TypeName;

/** What happens to one training profile's store — SPEC-001 §2, rules 1 to 3. */
public sealed interface TrainingProfileEvent {

  @TypeName("ddl-added")
  record DdlAdded(String id, String ddl, long addedAt) implements TrainingProfileEvent {}

  @TypeName("documentation-added")
  record DocumentationAdded(String id, String text, long addedAt)
      implements TrainingProfileEvent {}

  @TypeName("question-sql-added")
  record QuestionSqlAdded(String id, String question, String sql, long addedAt)
      implements TrainingProfileEvent {}

  @TypeName("training-item-removed")
  record TrainingItemRemoved(String id) implements TrainingProfileEvent {}
}
