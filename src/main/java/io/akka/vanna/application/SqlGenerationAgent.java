package io.akka.vanna.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

/**
 * Asks a language model for SQL — SPEC-001 §3 rule 9.
 *
 * <p>The system message and user message are already assembled by {@link
 * io.akka.vanna.domain.PromptAssembly} before they reach here; this component's only job is the
 * call itself and getting a typed answer back. {@link #generate} is invoked in a session scoped
 * to one {@code SqlGenerationWorkflow} run, so a retry turn's user message only needs to state
 * the new error — the model already has the earlier turns in its own session memory
 * (SPEC-001 OD-1), unlike the source, which restates the whole context on every {@code
 * /fix_sql} call because it keeps no session of its own.
 */
@Component(id = "sql-generation-agent")
public class SqlGenerationAgent extends Agent {

  public record GenerateRequest(String systemMessage, String userMessage) {}

  /** Structured output in place of the source's five-pattern regex fallback chain
   * ({@code extract_sql}) for pulling SQL out of a free-text reply. */
  public record GeneratedSql(String sql) {}

  public Effect<GeneratedSql> generate(GenerateRequest request) {
    return effects()
        .systemMessage(request.systemMessage())
        .userMessage(request.userMessage())
        .responseConformsTo(GeneratedSql.class)
        .thenReply();
  }
}
