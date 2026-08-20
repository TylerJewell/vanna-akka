package io.akka.vanna.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 7 to 9, decision OD-1. */
class PromptAssemblyTest {

  @Test
  void anOversizedItemIsSkippedWithoutStoppingTheScan() {
    // Copies probe_03_prompt_budget.py's case: a 100,000-char item alone blows a 14,000-token
    // budget, but the item listed after it is small enough on its own and still lands.
    String big = "X".repeat(100_000);
    String small = "CREATE TABLE customers (id INT)";

    String prompt =
        PromptAssembly.buildSystemMessage(
            "SQL", List.of(big, small), List.of(), List.of(), PromptAssembly.DEFAULT_MAX_TOKENS);

    assertThat(prompt).doesNotContain(big);
    assertThat(prompt).contains(small);
  }

  @Test
  void ddlAndDocumentationLandInSeparateLabelledSections() {
    String prompt =
        PromptAssembly.buildSystemMessage(
            "SQL",
            List.of("CREATE TABLE t (id INT)"),
            List.of("t holds one row per thing"),
            List.of(),
            PromptAssembly.DEFAULT_MAX_TOKENS);

    assertThat(prompt).contains("===Tables");
    assertThat(prompt).contains("===Additional Context");
    assertThat(prompt.indexOf("===Tables")).isLessThan(prompt.indexOf("===Additional Context"));
  }

  @Test
  void questionSqlExamplesLandInTheirOwnSectionRatherThanMixedWithDdlOrDocs() {
    // OD-1: the source makes each example its own conversation turn; this port folds them
    // into the system message instead, but still keeps them out of the DDL/doc text.
    var example = new QuestionSqlItem("h-sql", "how many customers?", "SELECT COUNT(*) FROM c", 0);

    String prompt =
        PromptAssembly.buildSystemMessage(
            "SQL",
            List.of("CREATE TABLE c (id INT)"),
            List.of(),
            List.of(example),
            PromptAssembly.DEFAULT_MAX_TOKENS);

    assertThat(prompt).contains("===Question-SQL Pairs");
    assertThat(prompt).contains("how many customers?");
    assertThat(prompt).contains("SELECT COUNT(*) FROM c");
  }

  @Test
  void noQuestionSqlSectionWhenThereAreNoExamples() {
    String prompt =
        PromptAssembly.buildSystemMessage(
            "SQL", List.of(), List.of(), List.of(), PromptAssembly.DEFAULT_MAX_TOKENS);

    assertThat(prompt).doesNotContain("===Question-SQL Pairs");
  }

  @Test
  void retryMessageCarriesTheQuestionThePreviousSqlAndTheError() {
    String message = PromptAssembly.retryUserMessage("top 10 customers", "SELET *", "syntax error");

    assertThat(message).contains("top 10 customers");
    assertThat(message).contains("SELET *");
    assertThat(message).contains("syntax error");
  }
}
