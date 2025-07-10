package com.smxemail.rangeresolver.lang;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

import static com.smxemail.rangeresolver.lang.RepositoryResolver.isNewerVersion;

public class RepositoryResolverTest {
  @Test
  public void versionComparision() {
    assertThat(isNewerVersion("1.0.1", "1.0.2")).isFalse();
    assertThat(isNewerVersion("1.0.1", "1.0.1")).isFalse();
    assertThat(isNewerVersion("1.0.2", "1.0.1")).isTrue();
    assertThat(isNewerVersion("2.0.2", "1.0.1")).isTrue();
    assertThat(isNewerVersion("23.2-jre", "22.0")).isTrue();
  }
}
