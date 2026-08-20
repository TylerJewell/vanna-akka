package io.akka.vanna.domain;

/** A question and the SQL that answers it, in a training profile — SPEC-001 §2. */
public record QuestionSqlItem(String id, String question, String sql, long addedAt) {}
