package io.akka.vanna.application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.Workflow.RecoverStrategy;
import io.akka.vanna.domain.DdlItem;
import io.akka.vanna.domain.DocumentationItem;
import io.akka.vanna.domain.PromptAssembly;
import io.akka.vanna.domain.QuestionSqlItem;
import io.akka.vanna.domain.Retrieval;
import io.akka.vanna.domain.SqlGenerationState;
import io.akka.vanna.domain.SqlValidator;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The generate-validate-retry loop around SQL — SPEC-001 §3 rules 9 to 14, decisions OD-1 to
 * OD-3.
 *
 * <p>The source has no automatic version of this loop: {@code ask()} tries once, and the retry
 * that exists ({@code /fix_sql}) is a step a caller must trigger by hand, as many or as few
 * times as it likes (question-log row 5). This makes it automatic and bounds it at {@link
 * #MAX_ATTEMPTS} (SPEC-001 A-2), because an automatic loop cannot ask a human whether to try
 * again.
 */
@Component(id = "sql-generation-workflow")
public class SqlGenerationWorkflow extends Workflow<SqlGenerationState> {

  private static final Logger logger = LoggerFactory.getLogger(SqlGenerationWorkflow.class);

  static final int MAX_ATTEMPTS = 3;
  private static final String DIALECT = "SQL";

  private final ComponentClient componentClient;

  public SqlGenerationWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record Start(String profileId, String question) {}

  public Effect<Done> start(Start command) {
    if (currentState() != null) {
      return effects().error("this SQL generation attempt has already been started");
    }
    SqlGenerationState initial =
        SqlGenerationState.start(command.profileId(), command.question(), MAX_ATTEMPTS);
    return effects()
        .updateState(initial)
        .transitionTo(SqlGenerationWorkflow::generateStep)
        .thenReply(done());
  }

  public ReadOnlyEffect<SqlGenerationState> getState() {
    if (currentState() == null) {
      return effects().error("no SQL generation attempt with this id");
    }
    return effects().reply(currentState());
  }

  /**
   * Bounds a technical failure (a timed-out or repeatedly erroring step call — a network flake
   * talking to the model or to the training profile) separately from SPEC-001's own attempt
   * count, which bounds retries triggered by the SQL itself (rules 10 to 13). The two are not
   * the same failure and are not counted against the same limit.
   */
  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .stepTimeout(SqlGenerationWorkflow::generateStep, Duration.ofSeconds(60))
        .defaultStepRecovery(
            RecoverStrategy.maxRetries(2).failoverTo(SqlGenerationWorkflow::technicalFailureStep))
        .build();
  }

  /** Rule 9: retrieval, prompt assembly, and the model call. */
  @StepName("generate")
  private StepEffect generateStep() {
    SqlGenerationState state = currentState();
    String profileId = state.profileId();
    String question = state.question();

    List<QuestionSqlItem> examples =
        componentClient
            .forEventSourcedEntity(profileId)
            .method(TrainingProfileEntity::similarQuestionSql)
            .invoke(new TrainingProfileEntity.SimilarityQuery(question, Retrieval.DEFAULT_LIMIT));
    List<DdlItem> relatedDdl =
        componentClient
            .forEventSourcedEntity(profileId)
            .method(TrainingProfileEntity::relatedDdl)
            .invoke(new TrainingProfileEntity.SimilarityQuery(question, Retrieval.DEFAULT_LIMIT));
    List<DocumentationItem> relatedDocs =
        componentClient
            .forEventSourcedEntity(profileId)
            .method(TrainingProfileEntity::relatedDocumentation)
            .invoke(new TrainingProfileEntity.SimilarityQuery(question, Retrieval.DEFAULT_LIMIT));

    String systemMessage =
        PromptAssembly.buildSystemMessage(
            DIALECT,
            relatedDdl.stream().map(DdlItem::ddl).toList(),
            relatedDocs.stream().map(DocumentationItem::text).toList(),
            examples,
            PromptAssembly.DEFAULT_MAX_TOKENS);

    String userMessage =
        state.lastError().isPresent()
            ? PromptAssembly.retryUserMessage(
                question, state.lastSql().orElse(""), state.lastError().get())
            : PromptAssembly.initialUserMessage(question);

    SqlGenerationAgent.GeneratedSql generated =
        componentClient
            .forAgent()
            .inSession(commandContext().workflowId())
            .method(SqlGenerationAgent::generate)
            .invoke(new SqlGenerationAgent.GenerateRequest(systemMessage, userMessage));

    logger.info(
        "generated SQL for attempt {}/{} on profile {}: {}",
        state.attempt(), state.maxAttempts(), profileId, generated.sql());

    return stepEffects()
        .updateState(state.withGenerated(generated.sql()))
        .thenTransitionTo(SqlGenerationWorkflow::validateStep);
  }

  /** Rules 10, 11: the gate, and a rejection handled exactly like an execution failure. */
  @StepName("validate")
  private StepEffect validateStep() {
    String sql = currentState().lastSql().orElseThrow();
    if (SqlValidator.isReadOnly(sql)) {
      return stepEffects().thenTransitionTo(SqlGenerationWorkflow::executeStep);
    }
    return handleFailedAttempt("generated SQL is not read-only, or contains no statement: " + sql);
  }

  /** Rule 12: run it against the profile's own schema. */
  @StepName("execute")
  private StepEffect executeStep() {
    SqlGenerationState state = currentState();
    String sql = state.lastSql().orElseThrow();

    // The prompt only carries the retrieval-limited, top-N related DDL (rule 5); the schema
    // this executes against is the profile's whole DDL, because a table the retrieval step
    // left out of the prompt can still be one the generated SQL correctly refers to.
    List<String> wholeSchema =
        componentClient
            .forEventSourcedEntity(state.profileId())
            .method(TrainingProfileEntity::trainingData)
            .invoke()
            .ddl()
            .values()
            .stream()
            .map(DdlItem::ddl)
            .toList();

    try {
      SqlExecutionService.ExecutionResult result = SqlExecutionService.execute(wholeSchema, sql);
      String summary = result.rowCount() + " row(s), columns: " + result.columns();
      return stepEffects().updateState(state.succeeded(summary)).thenEnd();
    } catch (SQLException e) {
      return handleFailedAttempt(e.getMessage());
    }
  }

  /** Rule 13: try again while the attempt count is under the bound; end at it. */
  private StepEffect handleFailedAttempt(String error) {
    SqlGenerationState state = currentState();
    if (state.attempt() >= state.maxAttempts()) {
      logger.warn(
          "attempt {}/{} failed and the bound is reached, ending FAILED: {}",
          state.attempt(), state.maxAttempts(), error);
      return stepEffects().updateState(state.failed(error)).thenEnd();
    }
    logger.info(
        "attempt {}/{} failed, retrying: {}", state.attempt(), state.maxAttempts(), error);
    return stepEffects()
        .updateState(state.withFailedAttempt(error))
        .thenTransitionTo(SqlGenerationWorkflow::generateStep);
  }

  private StepEffect technicalFailureStep() {
    SqlGenerationState state = currentState();
    logger.error(
        "a workflow step failed repeatedly and exhausted its platform-level retries for {}",
        state.profileId());
    return stepEffects()
        .updateState(state.failed("a workflow step failed repeatedly and exhausted its retries"))
        .thenEnd();
  }
}
