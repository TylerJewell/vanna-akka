package io.akka.vanna.domain;

/** A DDL statement in a training profile — SPEC-001 §2. */
public record DdlItem(String id, String ddl, long addedAt) {}
