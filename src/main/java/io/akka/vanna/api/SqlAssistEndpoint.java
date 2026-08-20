package io.akka.vanna.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import io.akka.vanna.application.SqlGenerationWorkflow;
import io.akka.vanna.domain.SqlGenerationState;
import java.util.UUID;

/**
 * The surface of the generate-validate-retry loop — SPEC-001 §3 rules 9 to 14.
 *
 * <p>Starting an attempt returns immediately with its id; the loop itself may call a language
 * model more than once, so a caller polls {@link #getAttempt} rather than waiting on one
 * request the way the source's synchronous {@code ask()} does.
 */
@HttpEndpoint("/sql-assist")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class SqlAssistEndpoint {

  private final ComponentClient componentClient;

  public SqlAssistEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record AskRequest(String question) {}

  public record AttemptStarted(String attemptId) {}

  @Post("/{profileId}/ask")
  public AttemptStarted ask(String profileId, AskRequest request) {
    String attemptId = UUID.randomUUID().toString();
    componentClient
        .forWorkflow(attemptId)
        .method(SqlGenerationWorkflow::start)
        .invoke(new SqlGenerationWorkflow.Start(profileId, request.question()));
    return new AttemptStarted(attemptId);
  }

  @Get("/attempts/{attemptId}")
  public SqlGenerationState getAttempt(String attemptId) {
    return componentClient
        .forWorkflow(attemptId)
        .method(SqlGenerationWorkflow::getState)
        .invoke();
  }
}
