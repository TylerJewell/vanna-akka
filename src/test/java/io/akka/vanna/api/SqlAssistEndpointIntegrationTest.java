package io.akka.vanna.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import io.akka.vanna.application.SqlGenerationAgent;
import io.akka.vanna.domain.SqlGenerationState;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rule 14, against the HTTP surface rather than the workflow directly. */
public class SqlAssistEndpointIntegrationTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.anthropic.api-key = n/a")
        .withModelProvider(SqlGenerationAgent.class, model);
  }

  @Test
  void askingStartsAnAttemptThatCanBePolledToCompletion() {
    String profileId = "profile-" + UUID.randomUUID();
    httpClient
        .POST("/training/" + profileId + "/ddl")
        .withRequestBody(new TrainingEndpoint.DdlRequest("CREATE TABLE customers (id INT)"))
        .invoke();
    model.fixedResponse("{\"sql\": \"SELECT * FROM customers\"}");

    var started =
        httpClient
            .POST("/sql-assist/" + profileId + "/ask")
            .withRequestBody(new SqlAssistEndpoint.AskRequest("list all customers"))
            .responseBodyAs(SqlAssistEndpoint.AttemptStarted.class)
            .invoke();
    assertThat(started.status().isSuccess()).isTrue();
    String attemptId = started.body().attemptId();

    Awaitility.await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var response =
                  httpClient
                      .GET("/sql-assist/attempts/" + attemptId)
                      .responseBodyAs(SqlGenerationState.class)
                      .invoke();
              assertThat(response.body().status()).isEqualTo(SqlGenerationState.Status.SUCCEEDED);
            });
  }
}
