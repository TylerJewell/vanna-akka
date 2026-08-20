package io.akka.vanna.domain;

import java.util.Optional;

/**
 * One run of the generate-validate-retry loop for one question against one profile —
 * SPEC-001 §2, §3 rules 9 to 14, decision OD-3.
 *
 * <p>{@code attempt} starts at 1 and only ever increases; there is no counterpart in the source,
 * which lets a human caller retry as many or as few times as they like (question-log row 5).
 */
public record SqlGenerationState(
    String profileId,
    String question,
    int attempt,
    int maxAttempts,
    Status status,
    Optional<String> lastSql,
    Optional<String> lastError,
    Optional<String> resultSummary) {

  public enum Status {
    GENERATING,
    VALIDATING,
    EXECUTING,
    SUCCEEDED,
    FAILED
  }

  public static SqlGenerationState start(String profileId, String question, int maxAttempts) {
    return new SqlGenerationState(
        profileId, question, 1, maxAttempts, Status.GENERATING,
        Optional.empty(), Optional.empty(), Optional.empty());
  }

  public SqlGenerationState withGenerated(String sql) {
    return new SqlGenerationState(
        profileId, question, attempt, maxAttempts, Status.VALIDATING,
        Optional.of(sql), lastError, resultSummary);
  }

  /** Rule 13: a failed attempt increments the count and records why. */
  public SqlGenerationState withFailedAttempt(String error) {
    return new SqlGenerationState(
        profileId, question, attempt + 1, maxAttempts, Status.GENERATING,
        lastSql, Optional.of(error), resultSummary);
  }

  public SqlGenerationState succeeded(String summary) {
    return new SqlGenerationState(
        profileId, question, attempt, maxAttempts, Status.SUCCEEDED,
        lastSql, lastError, Optional.of(summary));
  }

  public SqlGenerationState failed(String error) {
    return new SqlGenerationState(
        profileId, question, attempt, maxAttempts, Status.FAILED,
        lastSql, Optional.of(error), resultSummary);
  }

  public SqlGenerationState executing() {
    return new SqlGenerationState(
        profileId, question, attempt, maxAttempts, Status.EXECUTING,
        lastSql, lastError, resultSummary);
  }
}
