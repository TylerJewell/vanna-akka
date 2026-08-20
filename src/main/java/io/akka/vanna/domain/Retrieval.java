package io.akka.vanna.domain;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What training material is relevant to a question — SPEC-001 §3 rules 4 to 6, decision A-3.
 *
 * <p>The source ranks by cosine distance over embeddings from a downloaded model
 * (question-log row A-3). This ranks by token-set overlap instead: a pure function of the
 * question and the item's own content, so retrieval needs no model call and no network to run
 * or to test. Ties break by insertion order, oldest first, so the ranking is always the same
 * for the same store and the same question.
 */
public final class Retrieval {

  public static final int DEFAULT_LIMIT = 10;

  private Retrieval() {}

  public static List<QuestionSqlItem> similarQuestionSql(
      TrainingProfileState state, String question, int limit) {
    Set<String> questionTokens = tokenize(question);
    return state.questionSql().values().stream()
        .sorted(byScoreThenAge(item -> tokenize(item.question() + " " + item.sql()), item -> item.addedAt(), questionTokens))
        .limit(limit)
        .toList();
  }

  public static List<DdlItem> relatedDdl(TrainingProfileState state, String question, int limit) {
    Set<String> questionTokens = tokenize(question);
    return state.ddl().values().stream()
        .sorted(byScoreThenAge(item -> tokenize(item.ddl()), item -> item.addedAt(), questionTokens))
        .limit(limit)
        .toList();
  }

  public static List<DocumentationItem> relatedDocumentation(
      TrainingProfileState state, String question, int limit) {
    Set<String> questionTokens = tokenize(question);
    return state.documentation().values().stream()
        .sorted(byScoreThenAge(item -> tokenize(item.text()), item -> item.addedAt(), questionTokens))
        .limit(limit)
        .toList();
  }

  private static <T> Comparator<T> byScoreThenAge(
      java.util.function.Function<T, Set<String>> tokensOf,
      java.util.function.ToLongFunction<T> addedAtOf,
      Set<String> questionTokens) {
    return Comparator
        .comparingDouble((T item) -> jaccard(questionTokens, tokensOf.apply(item)))
        .reversed()
        .thenComparingLong(addedAtOf);
  }

  /** Rule 6: a pure function of the two token sets — no state, no randomness, no network. */
  static double jaccard(Set<String> a, Set<String> b) {
    if (a.isEmpty() || b.isEmpty()) {
      return 0.0;
    }
    Set<String> intersection = new LinkedHashSet<>(a);
    intersection.retainAll(b);
    Set<String> union = new LinkedHashSet<>(a);
    union.addAll(b);
    return (double) intersection.size() / union.size();
  }

  private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");

  static Set<String> tokenize(String text) {
    Set<String> tokens = new LinkedHashSet<>();
    if (text == null) {
      return tokens;
    }
    var matcher = TOKEN.matcher(text.toLowerCase());
    while (matcher.find()) {
      tokens.add(matcher.group());
    }
    return tokens;
  }
}
