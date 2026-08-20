package io.akka.vanna.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import io.akka.vanna.domain.SqlGenerationState;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 §3 rules 9 to 14 — the generate-validate-retry loop, against a real running
 * workflow and a stubbed model rather than a workflow in isolation.
 */
public class SqlGenerationWorkflowTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.anthropic.api-key = n/a")
        .withModelProvider(SqlGenerationAgent.class, model);
  }

  private String newProfileWithCustomersTable() {
    String profileId = "profile-" + UUID.randomUUID();
    componentClient
        .forEventSourcedEntity(profileId)
        .method(TrainingProfileEntity::addDdl)
        .invoke(new TrainingProfileEntity.AddDdl("CREATE TABLE customers (id INT, name VARCHAR(50))"));
    return profileId;
  }

  private String start(String profileId, String question) {
    String attemptId = UUID.randomUUID().toString();
    componentClient
        .forWorkflow(attemptId)
        .method(SqlGenerationWorkflow::start)
        .invoke(new SqlGenerationWorkflow.Start(profileId, question));
    return attemptId;
  }

  private SqlGenerationState awaitFinished(String attemptId) {
    var box = new SqlGenerationState[1];
    Awaitility.await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              SqlGenerationState state =
                  componentClient
                      .forWorkflow(attemptId)
                      .method(SqlGenerationWorkflow::getState)
                      .invoke();
              assertThat(state.status())
                  .isIn(SqlGenerationState.Status.SUCCEEDED, SqlGenerationState.Status.FAILED);
              box[0] = state;
            });
    return box[0];
  }

  @Test
  void succeedsOnTheFirstValidGeneration() {
    String profileId = newProfileWithCustomersTable();
    model.fixedResponse("{\"sql\": \"SELECT * FROM customers\"}");

    String attemptId = start(profileId, "list all customers");
    SqlGenerationState state = awaitFinished(attemptId);

    assertThat(state.status()).isEqualTo(SqlGenerationState.Status.SUCCEEDED);
    assertThat(state.attempt()).isEqualTo(1);
    assertThat(state.lastSql()).contains("SELECT * FROM customers");
  }

  @Test
  void rejectedSqlIsRetriedAndCanStillSucceed() {
    // Rule 11: a rejection (rule 10 -- not read-only) is handled exactly like an execution
    // failure -- fed back, and the loop tries again.
    String profileId = newProfileWithCustomersTable();
    model.whenMessage(msg -> msg.contains("was rejected"))
        .reply("{\"sql\": \"SELECT * FROM customers\"}");
    model.whenMessage(msg -> !msg.contains("was rejected"))
        .reply("{\"sql\": \"DROP TABLE customers\"}");

    String attemptId = start(profileId, "list all customers");
    SqlGenerationState state = awaitFinished(attemptId);

    assertThat(state.status()).isEqualTo(SqlGenerationState.Status.SUCCEEDED);
    assertThat(state.attempt()).isEqualTo(2);
    assertThat(state.lastSql()).contains("SELECT * FROM customers");
  }

  @Test
  void anExecutionFailureIsRetriedAndCanStillSucceed() {
    // Rule 12: valid, read-only SQL that fails to execute (an unknown table) counts as a
    // failed attempt the same as a rejected one.
    String profileId = newProfileWithCustomersTable();
    model.whenMessage(msg -> msg.contains("was rejected"))
        .reply("{\"sql\": \"SELECT * FROM customers\"}");
    model.whenMessage(msg -> !msg.contains("was rejected"))
        .reply("{\"sql\": \"SELECT * FROM missing_table\"}");

    String attemptId = start(profileId, "list all customers");
    SqlGenerationState state = awaitFinished(attemptId);

    assertThat(state.status()).isEqualTo(SqlGenerationState.Status.SUCCEEDED);
    assertThat(state.attempt()).isEqualTo(2);
  }

  @Test
  void endsFailedOnceTheAttemptBoundIsReachedWithNoSuccess() {
    // Rule 14, decision A-2 -- the source has no bound at all (question-log row 5); this
    // port's is SqlGenerationWorkflow.MAX_ATTEMPTS.
    String profileId = newProfileWithCustomersTable();
    model.fixedResponse("{\"sql\": \"DROP TABLE customers\"}");

    String attemptId = start(profileId, "list all customers");
    SqlGenerationState state = awaitFinished(attemptId);

    assertThat(state.status()).isEqualTo(SqlGenerationState.Status.FAILED);
    assertThat(state.attempt()).isEqualTo(SqlGenerationWorkflow.MAX_ATTEMPTS);
    assertThat(state.lastError()).isPresent();
  }
}
