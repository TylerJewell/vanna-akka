package io.akka.vanna.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Whether generated SQL is allowed to run at all — SPEC-001 §3 rule 10, decision OD-2.
 *
 * <p>The source's {@code is_sql_valid} accepts a string if <em>any</em> statement inside it
 * parses as a {@code SELECT}, so {@code "SELECT 1; DROP TABLE x;"} passes (question-log row 1).
 * This requires <em>every</em> statement to be read-only, and requires at least one statement —
 * a gate meant to keep an LLM's own output from reaching execution that admits a smuggled
 * {@code DROP} gates nothing.
 */
public final class SqlValidator {

  private static final Set<String> READ_ONLY_KEYWORDS = Set.of("SELECT", "WITH");

  private SqlValidator() {}

  public static boolean isReadOnly(String sql) {
    List<String> statements = splitStatements(sql);
    if (statements.isEmpty()) {
      return false;
    }
    return statements.stream().allMatch(SqlValidator::isReadOnlyStatement);
  }

  /** Splits on top-level {@code ;}, respecting single-quoted string literals, and drops any
   * chunk that is blank or comment-only once split. */
  static List<String> splitStatements(String sql) {
    List<String> statements = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inString = false;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '\'') {
        inString = !inString;
        current.append(c);
      } else if (c == ';' && !inString) {
        addIfMeaningful(statements, current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    addIfMeaningful(statements, current.toString());
    return statements;
  }

  private static void addIfMeaningful(List<String> statements, String statement) {
    if (!leadingKeyword(statement).isEmpty()) {
      statements.add(statement);
    }
  }

  private static boolean isReadOnlyStatement(String statement) {
    return READ_ONLY_KEYWORDS.contains(leadingKeyword(statement));
  }

  /** The statement's first word, skipping blank lines and {@code --} line comments. */
  private static String leadingKeyword(String statement) {
    for (String line : statement.split("\\R")) {
      String trimmed = line.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("--")) {
        continue;
      }
      int end = 0;
      while (end < trimmed.length() && Character.isLetter(trimmed.charAt(end))) {
        end++;
      }
      if (end == 0) {
        return "";
      }
      return trimmed.substring(0, end).toUpperCase(Locale.ROOT);
    }
    return "";
  }
}
