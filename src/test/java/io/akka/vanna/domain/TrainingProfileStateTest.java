package io.akka.vanna.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SPEC-001 §3 rule 1, exercised directly against the state rather than through the entity.
 *
 * <p>{@link TrainingProfileEntity#addDdl} already refuses to persist a second event for an id
 * it has seen (its own {@code containsKey} guard before {@code effects().persist(...)}), so a
 * mutation probe against the entity's own test cannot tell whether {@link
 * TrainingProfileState#withDdl} enforces rule 1 on its own or is merely never asked to. This
 * pins the state's guard independently -- defense in depth against a replay that, for whatever
 * reason, hands it the same id twice.
 */
class TrainingProfileStateTest {

  @Test
  void withDdlIgnoresAnItemWhoseIdIsAlreadyPresent() {
    var state = TrainingProfileState.empty("p1");
    var first = new DdlItem("h-ddl", "CREATE TABLE t (id INT)", 0);
    var second = new DdlItem("h-ddl", "CREATE TABLE t (id INT, extra INT)", 1);

    state = state.withDdl(first);
    state = state.withDdl(second);

    assertThat(state.ddl()).hasSize(1);
    assertThat(state.ddl().get("h-ddl")).isEqualTo(first);
  }

  @Test
  void withDocumentationIgnoresAnItemWhoseIdIsAlreadyPresent() {
    var state = TrainingProfileState.empty("p1");
    var first = new DocumentationItem("h-doc", "first", 0);
    var second = new DocumentationItem("h-doc", "second", 1);

    state = state.withDocumentation(first).withDocumentation(second);

    assertThat(state.documentation()).hasSize(1);
    assertThat(state.documentation().get("h-doc")).isEqualTo(first);
  }

  @Test
  void withQuestionSqlIgnoresAnItemWhoseIdIsAlreadyPresent() {
    var state = TrainingProfileState.empty("p1");
    var first = new QuestionSqlItem("h-sql", "q", "SELECT 1", 0);
    var second = new QuestionSqlItem("h-sql", "q", "SELECT 2", 1);

    state = state.withQuestionSql(first).withQuestionSql(second);

    assertThat(state.questionSql()).hasSize(1);
    assertThat(state.questionSql().get("h-sql")).isEqualTo(first);
  }
}
