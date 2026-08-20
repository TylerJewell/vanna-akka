package io.akka.vanna.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** SPEC-001 §2, rules 1 to 3. */
class TrainingItemIdTest {

  @Test
  void sameContentAndKindProduceTheSameId() {
    String a = TrainingItemId.of("CREATE TABLE t (id INT)", TrainingItemId.Kind.DDL);
    String b = TrainingItemId.of("CREATE TABLE t (id INT)", TrainingItemId.Kind.DDL);
    assertThat(a).isEqualTo(b);
  }

  @Test
  void differentContentProducesDifferentIds() {
    String a = TrainingItemId.of("CREATE TABLE t (id INT)", TrainingItemId.Kind.DDL);
    String b = TrainingItemId.of("CREATE TABLE u (id INT)", TrainingItemId.Kind.DDL);
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void sameContentUnderDifferentKindsProducesDifferentIds() {
    String asDdl = TrainingItemId.of("customers", TrainingItemId.Kind.DDL);
    String asDoc = TrainingItemId.of("customers", TrainingItemId.Kind.DOCUMENTATION);
    assertThat(asDdl).isNotEqualTo(asDoc);
  }

  @Test
  void kindOfReadsTheSuffixBackOut() {
    String id = TrainingItemId.of("some content", TrainingItemId.Kind.QUESTION_SQL);
    assertThat(TrainingItemId.kindOf(id)).isEqualTo(TrainingItemId.Kind.QUESTION_SQL);
  }

  @Test
  void kindOfRejectsAnIdWithNoRecognisedSuffix() {
    assertThatThrownBy(() -> TrainingItemId.kindOf("not-a-training-item-id"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
