package io.akka.vanna.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import io.akka.vanna.application.TrainingProfileEntity;
import io.akka.vanna.domain.DdlItem;
import io.akka.vanna.domain.DocumentationItem;
import io.akka.vanna.domain.QuestionSqlItem;
import io.akka.vanna.domain.TrainingProfileState;
import java.util.List;

/** Adding to, listing, and removing from a training profile — SPEC-001 §3 rules 1 to 3. */
@HttpEndpoint("/training")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class TrainingEndpoint {

  private final ComponentClient componentClient;

  public TrainingEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record DdlRequest(String ddl) {}

  public record DocumentationRequest(String text) {}

  public record QuestionSqlRequest(String question, String sql) {}

  public record ItemAdded(String id) {}

  public record TrainingDataView(
      List<DdlItem> ddl, List<DocumentationItem> documentation, List<QuestionSqlItem> questionSql) {}

  @Post("/{profileId}/ddl")
  public ItemAdded addDdl(String profileId, DdlRequest request) {
    String id =
        componentClient
            .forEventSourcedEntity(profileId)
            .method(TrainingProfileEntity::addDdl)
            .invoke(new TrainingProfileEntity.AddDdl(request.ddl()));
    return new ItemAdded(id);
  }

  @Post("/{profileId}/documentation")
  public ItemAdded addDocumentation(String profileId, DocumentationRequest request) {
    String id =
        componentClient
            .forEventSourcedEntity(profileId)
            .method(TrainingProfileEntity::addDocumentation)
            .invoke(new TrainingProfileEntity.AddDocumentation(request.text()));
    return new ItemAdded(id);
  }

  @Post("/{profileId}/question-sql")
  public ItemAdded addQuestionSql(String profileId, QuestionSqlRequest request) {
    String id =
        componentClient
            .forEventSourcedEntity(profileId)
            .method(TrainingProfileEntity::addQuestionSql)
            .invoke(new TrainingProfileEntity.AddQuestionSql(request.question(), request.sql()));
    return new ItemAdded(id);
  }

  @Delete("/{profileId}/items/{id}")
  public HttpResponse removeItem(String profileId, String id) {
    boolean removed =
        componentClient
            .forEventSourcedEntity(profileId)
            .method(TrainingProfileEntity::removeTrainingItem)
            .invoke(id);
    return removed ? HttpResponses.ok() : HttpResponses.notFound();
  }

  @Get("/{profileId}")
  public TrainingDataView trainingData(String profileId) {
    TrainingProfileState state =
        componentClient
            .forEventSourcedEntity(profileId)
            .method(TrainingProfileEntity::trainingData)
            .invoke();
    return new TrainingDataView(
        state.ddl().values().stream().toList(),
        state.documentation().values().stream().toList(),
        state.questionSql().values().stream().toList());
  }
}
