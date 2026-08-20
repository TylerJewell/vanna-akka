package io.akka.vanna.application;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs generated SQL — SPEC-001 §3 rule 12.
 *
 * <p>Live-database connections are out of scope (docs/scope.md); running SQL is not. Each call
 * gets a fresh, private, in-memory H2 database, seeded from the profile's own DDL, so a syntax
 * or reference error in generated SQL is a real {@link SQLException} caught for the workflow's
 * retry loop rather than a simulated one.
 */
public final class SqlExecutionService {

  private SqlExecutionService() {}

  public record ExecutionResult(int rowCount, List<String> columns) {}

  public static ExecutionResult execute(List<String> ddl, String sql) throws SQLException {
    String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      try (Statement schemaStatement = connection.createStatement()) {
        for (String ddlStatement : ddl) {
          schemaStatement.execute(ddlStatement);
        }
      }
      try (Statement statement = connection.createStatement();
          ResultSet resultSet = statement.executeQuery(sql)) {
        ResultSetMetaData metadata = resultSet.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
          columns.add(metadata.getColumnLabel(i));
        }
        int rowCount = 0;
        while (resultSet.next()) {
          rowCount++;
        }
        return new ExecutionResult(rowCount, columns);
      }
    }
  }
}
