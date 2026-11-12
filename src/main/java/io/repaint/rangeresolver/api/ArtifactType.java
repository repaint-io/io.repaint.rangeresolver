package io.repaint.rangeresolver.api;

/// The kind of artifact being resolved.
public enum ArtifactType {
  /// A Maven artifact identified by group, artifact and version.
  MAVEN,
  /// A Docker image identified by name and digest.
  DOCKER
}
