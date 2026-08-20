package io.akka.vanna.domain;

import java.util.List;

/**
 * Turning retrieved training material into the text sent to a language model — SPEC-001 §3
 * rules 7 to 9, decision OD-1.
 *
 * <p>Rule 7's budget check is deliberately copied from the source's {@code add_ddl_to_prompt}:
 * each item is admitted independently against an approximate token count ({@code length / 4}),
 * so an oversized item is skipped without stopping the scan and a smaller item listed after it
 * may still be included (question-log row 3). Rule 8 / OD-1 changes only where retrieved
 * question/SQL examples are written down — folded into this same system message as their own
 * section, rather than synthesized as prior conversation turns the way the source's {@code
 * get_sql_prompt} does, because the target's agent component does not accept an arbitrary
 * caller-supplied list of prior turns.
 */
public final class PromptAssembly {

  public static final int DEFAULT_MAX_TOKENS = 14000;

  private PromptAssembly() {}

  static double approxTokenCount(CharSequence text) {
    return text.length() / 4.0;
  }

  /**
   * Rule 7: each item is checked against the running total at the moment it is considered.
   * An item that does not fit is skipped, not a reason to stop looking at the rest.
   */
  static String appendWithBudget(String prompt, String header, List<String> items, int maxTokens) {
    if (items.isEmpty()) {
      return prompt;
    }
    StringBuilder out = new StringBuilder(prompt).append(header);
    for (String item : items) {
      if (approxTokenCount(out) + approxTokenCount(item) < maxTokens) {
        out.append(item).append("\n\n");
      }
    }
    return out.toString();
  }

  public static String buildSystemMessage(
      String dialect,
      List<String> ddl,
      List<String> documentation,
      List<QuestionSqlItem> examples,
      int maxTokens) {
    String prompt =
        "You are a " + dialect + " expert. Please help to generate a SQL query to answer the "
            + "question. Your response should ONLY be based on the given context and follow the "
            + "response guidelines and format instructions.";

    prompt = appendWithBudget(prompt, "\n===Tables \n", ddl, maxTokens);
    prompt = appendWithBudget(prompt, "\n===Additional Context \n\n", documentation, maxTokens);

    if (!examples.isEmpty()) {
      List<String> pairs = examples.stream().map(e -> e.question() + "\n" + e.sql()).toList();
      prompt = appendWithBudget(prompt, "\n===Question-SQL Pairs\n\n", pairs, maxTokens);
    }

    prompt +=
        "\n===Response Guidelines \n"
            + "1. If the provided context is sufficient, generate a valid SQL query without any "
            + "explanations for the question. \n"
            + "2. If the provided context is insufficient, explain why it can't be generated. \n"
            + "3. Use the most relevant table(s). \n"
            + "4. If the question has been asked and answered before, repeat the answer exactly "
            + "as it was given before. \n"
            + "5. Ensure the output SQL is " + dialect + "-compliant, executable, and contains "
            + "only read-only statements. \n";
    return prompt;
  }

  /** The first attempt at a question — no prior error to report. */
  public static String initialUserMessage(String question) {
    return question;
  }

  /**
   * A retry after a failed attempt — SPEC-001 rule 9, row 5's {@code /fix_sql} shape, folded
   * into a single turn because the target's session-backed agent already carries the earlier
   * turns rather than needing them restated.
   */
  public static String retryUserMessage(String question, String previousSql, String error) {
    return "The SQL you generated was rejected: "
        + error
        + "\n\nHere is the SQL you generated: "
        + previousSql
        + "\n\nHere is the question you were answering: "
        + question
        + "\n\nPlease provide corrected SQL.";
  }
}
