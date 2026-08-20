package io.akka.vanna.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rule 10, decision OD-2. */
class SqlValidatorTest {

  @Test
  void aSingleSelectIsReadOnly() {
    assertThat(SqlValidator.isReadOnly("SELECT * FROM customers")).isTrue();
  }

  @Test
  void aWithCteIsReadOnly() {
    assertThat(SqlValidator.isReadOnly("WITH t AS (SELECT 1) SELECT * FROM t")).isTrue();
  }

  @Test
  void severalSelectStatementsAreAllReadOnly() {
    assertThat(SqlValidator.isReadOnly("SELECT 1; SELECT 2;")).isTrue();
  }

  @Test
  void aBareNonSelectStatementIsRejected() {
    assertThat(SqlValidator.isReadOnly("DROP TABLE customers;")).isFalse();
  }

  @Test
  void emptyOrCommentOnlyInputIsRejected() {
    assertThat(SqlValidator.isReadOnly("")).isFalse();
    assertThat(SqlValidator.isReadOnly("-- just a comment")).isFalse();
  }

  @Test
  void aSelectSmugglingADropIsRejected() {
    // This is the exact case the source's is_sql_valid lets through (question-log row 1) --
    // it is why OD-2 tightens the rule to "every statement", not "any statement".
    assertThat(SqlValidator.isReadOnly("SELECT * FROM customers; DROP TABLE customers;"))
        .isFalse();
    assertThat(SqlValidator.isReadOnly("DROP TABLE customers; SELECT 1;")).isFalse();
  }

  @Test
  void aSemicolonInsideAStringLiteralDoesNotSplitTheStatement() {
    assertThat(SqlValidator.isReadOnly("SELECT * FROM customers WHERE name = 'a;b'")).isTrue();
  }

  @Test
  void anUpdateSmuggledAlongsideASelectIsRejected() {
    assertThat(SqlValidator.isReadOnly("UPDATE customers SET name = 'x'; SELECT * FROM customers;"))
        .isFalse();
  }
}
