package io.akka.vanna.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.akka.vanna.domain.TrainingProfileEvent;
import io.akka.vanna.domain.TrainingProfileState;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 1 to 3 — training-store dedup by content. */
class TrainingProfileEntityTest {

  private static EventSourcedTestKit<TrainingProfileState, TrainingProfileEvent, TrainingProfileEntity>
      kit() {
    return EventSourcedTestKit.of("profile-1", TrainingProfileEntity::new);
  }

  @Test
  void addingTheSameDdlTwiceDoesNotDuplicate() {
    var testKit = kit();
    String ddl = "CREATE TABLE customers (id INT)";

    String first = testKit.method(TrainingProfileEntity::addDdl)
        .invoke(new TrainingProfileEntity.AddDdl(ddl)).getReply();
    String second = testKit.method(TrainingProfileEntity::addDdl)
        .invoke(new TrainingProfileEntity.AddDdl(ddl)).getReply();

    assertThat(first).isEqualTo(second);
    assertThat(testKit.getState().ddl()).hasSize(1);
    assertThat(testKit.getAllEvents()).hasSize(1);
  }

  @Test
  void addingDifferentDocumentationCreatesTwoItems() {
    var testKit = kit();
    testKit.method(TrainingProfileEntity::addDocumentation)
        .invoke(new TrainingProfileEntity.AddDocumentation("first fact"));
    testKit.method(TrainingProfileEntity::addDocumentation)
        .invoke(new TrainingProfileEntity.AddDocumentation("second fact"));

    assertThat(testKit.getState().documentation()).hasSize(2);
  }

  @Test
  void addingTheSameQuestionSqlPairTwiceDoesNotDuplicate() {
    var testKit = kit();
    var add = new TrainingProfileEntity.AddQuestionSql("how many customers?", "SELECT COUNT(*) FROM c");
    testKit.method(TrainingProfileEntity::addQuestionSql).invoke(add);
    testKit.method(TrainingProfileEntity::addQuestionSql).invoke(add);

    assertThat(testKit.getState().questionSql()).hasSize(1);
  }

  @Test
  void removingByIdDispatchesToTheRightCollectionFromTheIdAlone() {
    var testKit = kit();
    String id = testKit.method(TrainingProfileEntity::addDdl)
        .invoke(new TrainingProfileEntity.AddDdl("CREATE TABLE t (id INT)")).getReply();

    boolean removed = testKit.method(TrainingProfileEntity::removeTrainingItem).invoke(id).getReply();

    assertThat(removed).isTrue();
    assertThat(testKit.getState().ddl()).isEmpty();
  }

  @Test
  void removingAnIdThatIsNotPresentReportsFalseRatherThanFailing() {
    var testKit = kit();
    String neverAdded =
        io.akka.vanna.domain.TrainingItemId.of("nope", io.akka.vanna.domain.TrainingItemId.Kind.DOCUMENTATION);

    boolean removed =
        testKit.method(TrainingProfileEntity::removeTrainingItem).invoke(neverAdded).getReply();

    assertThat(removed).isFalse();
  }
}
