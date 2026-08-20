package io.akka.vanna.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.akka.vanna.domain.DdlItem;
import io.akka.vanna.domain.DocumentationItem;
import io.akka.vanna.domain.QuestionSqlItem;
import io.akka.vanna.domain.Retrieval;
import io.akka.vanna.domain.TrainingItemId;
import io.akka.vanna.domain.TrainingProfileEvent;
import io.akka.vanna.domain.TrainingProfileState;
import java.util.List;

/**
 * One caller's training store, and where retrieval against it happens — SPEC-001 §2, §3 rules 1
 * to 6.
 *
 * <p>The source is a single in-process object holding one set of ChromaDB collections; this
 * gives that store an identity — the entity id — so more than one can exist side by side.
 */
@Component(id = "training-profile")
public class TrainingProfileEntity
    extends EventSourcedEntity<TrainingProfileState, TrainingProfileEvent> {

  private final String profileId;

  public TrainingProfileEntity(EventSourcedEntityContext context) {
    this.profileId = context.entityId();
  }

  public record AddDdl(String ddl) {}

  public record AddDocumentation(String text) {}

  public record AddQuestionSql(String question, String sql) {}

  public record SimilarityQuery(String question, int limit) {}

  @Override
  public TrainingProfileState emptyState() {
    return TrainingProfileState.empty(profileId);
  }

  /** Rules 1, 2: the id is content-derived, so adding the same content twice is a no-op. */
  public Effect<String> addDdl(AddDdl command) {
    String id = TrainingItemId.of(command.ddl(), TrainingItemId.Kind.DDL);
    if (currentState().ddl().containsKey(id)) {
      return effects().reply(id);
    }
    return effects()
        .persist(new TrainingProfileEvent.DdlAdded(id, command.ddl(), currentState().nextSeq()))
        .thenReply(s -> id);
  }

  public Effect<String> addDocumentation(AddDocumentation command) {
    String id = TrainingItemId.of(command.text(), TrainingItemId.Kind.DOCUMENTATION);
    if (currentState().documentation().containsKey(id)) {
      return effects().reply(id);
    }
    return effects()
        .persist(
            new TrainingProfileEvent.DocumentationAdded(
                id, command.text(), currentState().nextSeq()))
        .thenReply(s -> id);
  }

  public Effect<String> addQuestionSql(AddQuestionSql command) {
    String content = command.question() + "\n" + command.sql();
    String id = TrainingItemId.of(content, TrainingItemId.Kind.QUESTION_SQL);
    if (currentState().questionSql().containsKey(id)) {
      return effects().reply(id);
    }
    return effects()
        .persist(
            new TrainingProfileEvent.QuestionSqlAdded(
                id, command.question(), command.sql(), currentState().nextSeq()))
        .thenReply(s -> id);
  }

  /** Rule 3: the id's own suffix says which collection to remove it from. */
  public Effect<Boolean> removeTrainingItem(String id) {
    boolean present =
        switch (TrainingItemId.kindOf(id)) {
          case DDL -> currentState().ddl().containsKey(id);
          case DOCUMENTATION -> currentState().documentation().containsKey(id);
          case QUESTION_SQL -> currentState().questionSql().containsKey(id);
        };
    if (!present) {
      return effects().reply(false);
    }
    return effects()
        .persist(new TrainingProfileEvent.TrainingItemRemoved(id))
        .thenReply(s -> true);
  }

  public ReadOnlyEffect<TrainingProfileState> trainingData() {
    return effects().reply(currentState());
  }

  /** Rules 4 to 6. */
  public ReadOnlyEffect<List<QuestionSqlItem>> similarQuestionSql(SimilarityQuery query) {
    return effects()
        .reply(Retrieval.similarQuestionSql(currentState(), query.question(), query.limit()));
  }

  public ReadOnlyEffect<List<DdlItem>> relatedDdl(SimilarityQuery query) {
    return effects().reply(Retrieval.relatedDdl(currentState(), query.question(), query.limit()));
  }

  public ReadOnlyEffect<List<DocumentationItem>> relatedDocumentation(SimilarityQuery query) {
    return effects()
        .reply(Retrieval.relatedDocumentation(currentState(), query.question(), query.limit()));
  }

  @Override
  public TrainingProfileState applyEvent(TrainingProfileEvent event) {
    return switch (event) {
      case TrainingProfileEvent.DdlAdded e ->
          currentState().withDdl(new DdlItem(e.id(), e.ddl(), e.addedAt()));
      case TrainingProfileEvent.DocumentationAdded e ->
          currentState().withDocumentation(new DocumentationItem(e.id(), e.text(), e.addedAt()));
      case TrainingProfileEvent.QuestionSqlAdded e ->
          currentState()
              .withQuestionSql(new QuestionSqlItem(e.id(), e.question(), e.sql(), e.addedAt()));
      case TrainingProfileEvent.TrainingItemRemoved e -> currentState().withoutItem(e.id());
    };
  }
}
