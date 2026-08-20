package io.akka.vanna.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 4 to 6, decision OD-4. */
class RetrievalTest {

  private static TrainingProfileState stateWith(QuestionSqlItem... items) {
    Map<String, QuestionSqlItem> byId = new LinkedHashMap<>();
    for (QuestionSqlItem item : items) {
      byId.put(item.id(), item);
    }
    return new TrainingProfileState("p1", Map.of(), Map.of(), byId, items.length);
  }

  @Test
  void higherTokenOverlapRanksFirst() {
    var closer = new QuestionSqlItem("a-sql", "top customers by sales", "SELECT 1", 0);
    var further = new QuestionSqlItem("b-sql", "average order size", "SELECT 2", 1);
    var state = stateWith(further, closer);

    var results = Retrieval.similarQuestionSql(state, "top customers by revenue", 10);

    assertThat(results).hasSize(2);
    assertThat(results.get(0).id()).isEqualTo("a-sql");
  }

  @Test
  void resultsAreCappedAtTheLimit() {
    var items = new QuestionSqlItem[5];
    for (int i = 0; i < 5; i++) {
      items[i] = new QuestionSqlItem("q" + i + "-sql", "question " + i, "SELECT " + i, i);
    }
    var state = stateWith(items);

    assertThat(Retrieval.similarQuestionSql(state, "question", 3)).hasSize(3);
  }

  @Test
  void tiedScoresBreakByInsertionOrderOldestFirst() {
    // Neither item shares any token with the question, so both score zero and the tie
    // must break on addedAt alone -- rule 5.
    var older = new QuestionSqlItem("old-sql", "alpha", "SELECT 1", 0);
    var newer = new QuestionSqlItem("new-sql", "beta", "SELECT 2", 1);
    var state = stateWith(newer, older);

    var results = Retrieval.similarQuestionSql(state, "zzz unrelated question", 10);

    assertThat(results).extracting(QuestionSqlItem::id).containsExactly("old-sql", "new-sql");
  }

  @Test
  void scoringIsAPureFunctionOfTheTwoTexts() {
    var state = stateWith(new QuestionSqlItem("a-sql", "top customers by sales", "SELECT 1", 0));

    var first = Retrieval.similarQuestionSql(state, "top customers", 10);
    var second = Retrieval.similarQuestionSql(state, "top customers", 10);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void ddlAndDocumentationAreScoredIndependentlyOfQuestionSqlPairs() {
    var ddl = new DdlItem("t-ddl", "CREATE TABLE customers (id INT, sales INT)", 0);
    var state =
        new TrainingProfileState(
            "p1", Map.of(ddl.id(), ddl), Map.of(), Map.of(), 1);

    assertThat(Retrieval.relatedDdl(state, "customers", 10)).containsExactly(ddl);
    assertThat(Retrieval.similarQuestionSql(state, "customers", 10)).isEmpty();
  }
}
