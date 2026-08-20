package io.akka.vanna.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 1 to 3, against the HTTP surface. */
public class TrainingEndpointIntegrationTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    // This suite never calls the model, but Bootstrap checks for a key on every service
    // start regardless of which endpoint a test exercises.
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
        "akka.javasdk.agent.anthropic.api-key = n/a");
  }

  @Test
  void addingTheSameDdlTwiceThroughTheApiDoesNotDuplicate() {
    String profileId = "profile-" + UUID.randomUUID();
    var request = new TrainingEndpoint.DdlRequest("CREATE TABLE customers (id INT)");

    var first =
        httpClient
            .POST("/training/" + profileId + "/ddl")
            .withRequestBody(request)
            .responseBodyAs(TrainingEndpoint.ItemAdded.class)
            .invoke();
    var second =
        httpClient
            .POST("/training/" + profileId + "/ddl")
            .withRequestBody(request)
            .responseBodyAs(TrainingEndpoint.ItemAdded.class)
            .invoke();

    assertThat(first.body().id()).isEqualTo(second.body().id());

    var data =
        httpClient
            .GET("/training/" + profileId)
            .responseBodyAs(TrainingEndpoint.TrainingDataView.class)
            .invoke();
    assertThat(data.body().ddl()).hasSize(1);
  }

  @Test
  void aRemovedItemIsGoneFromTrainingData() {
    String profileId = "profile-" + UUID.randomUUID();
    var added =
        httpClient
            .POST("/training/" + profileId + "/documentation")
            .withRequestBody(new TrainingEndpoint.DocumentationRequest("customers table holds people"))
            .responseBodyAs(TrainingEndpoint.ItemAdded.class)
            .invoke();

    var deleted = httpClient.DELETE("/training/" + profileId + "/items/" + added.body().id()).invoke();
    assertThat(deleted.status().isSuccess()).isTrue();

    var data =
        httpClient
            .GET("/training/" + profileId)
            .responseBodyAs(TrainingEndpoint.TrainingDataView.class)
            .invoke();
    assertThat(data.body().documentation()).isEmpty();
  }
}
