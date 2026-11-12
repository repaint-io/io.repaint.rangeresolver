package io.repaint.rangeresolver.lang;

import io.repaint.rangeresolver.api.Repository;

import org.junit.Test;

import static io.repaint.rangeresolver.lang.RepositoryResolver.isNewerVersion;
import static io.repaint.rangeresolver.lang.RepositoryResolver.repositoryId;

import static org.assertj.core.api.Assertions.assertThat;

public class RepositoryResolverTest {
  @Test
  public void versionComparision() {
    assertThat(isNewerVersion("1.0.1", "1.0.2")).isFalse();
    assertThat(isNewerVersion("1.0.1", "1.0.1")).isFalse();
    assertThat(isNewerVersion("1.0.2", "1.0.1")).isTrue();
    assertThat(isNewerVersion("2.0.2", "1.0.1")).isTrue();
    assertThat(isNewerVersion("23.2-jre", "22.0")).isTrue();
  }

  @Test
  public void repositoryIdUsesNameWhenDeclared() {
    Repository codegenome = Repository.of("", "https://artifacts.codegenomeproject.org/maven", "codegenome");
    Repository central = Repository.of("", "https://repo1.maven.org/maven2", "default");

    assertThat(repositoryId(codegenome)).isEqualTo("codegenome");
    assertThat(repositoryId(central)).isEqualTo("https://repo1.maven.org/maven2");
  }
}
