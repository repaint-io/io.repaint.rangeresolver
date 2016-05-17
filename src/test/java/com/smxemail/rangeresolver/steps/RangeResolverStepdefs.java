package com.smxemail.rangeresolver.steps;

import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Repository;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertWithMessage;

public class RangeResolverStepdefs {

  public static final String SOURCE_REFERENCE = "cucumber";

  private RangeResolver rangeResolver;

  private Exception rangeResolverException;

  private List<String> previousImports;

  private List<String> infoMessages;

  private List<String> warnMessages;

  private void info(String message) {
    infoMessages.add(message);
    System.out.printf("%s\n", message);
  }

  private void warn(String message) {
    warnMessages.add(message);
    System.out.printf("%s\n", message);
  }

  private RepositoryResolver repositoryResolver =
      RepositoryResolver.createRepositoryResolver(this::info, this::warn, resolveLocalRepository().getAbsolutePath(), true);

  @Before
  public void beforeScenario() {
    previousImports = new ArrayList<>();
    infoMessages = new ArrayList<>();
    warnMessages = new ArrayList<>();
    rangeResolver = null;
    rangeResolverException = null;
  }

  @Given("^the following script:$")
  public void givenTheFollowingScript(String script) throws IOException {
    try {
      rangeResolver = RangeResolverProcessor.parseRangeResolver(SOURCE_REFERENCE, new StringReader(script));
    } catch (Exception e) {
      rangeResolverException = e;
    }
  }

  @Then("^the repository \"([^\"]*)\" should be declared$")
  public void theRepositoryShouldBeDeclared(String repository) {
    for (Repository rep : rangeResolver.repositories()) {
      if (rep.url().equals(repository)) {
        return;
      }
    }

    throw new IllegalStateException("No repository declared for " + repository);
  }

  @Then("^the dependency \"([^\"]*)\" should be resolving against the \"([^\"]*)\" range \"([^\"]*)\"$")
  public void theDependencyShouldBeResolvingAgainstTheRange(String artifact, String bounds, String range) throws Throwable {
    for (Resolve resolve : rangeResolver.resolves()) {
      if (artifact.equals(resolve.key()) && resolve.bounds().toString().toLowerCase().equals(bounds) && resolve.range().asVersionRange().equals(range)) {
        return;
      }
    }

    throw new IllegalStateException(String.format("No resolution for %s %s with range %s found.", bounds, artifact, range));
  }

  @Then("^the dependency \"([^\"]*)\" should use the property \"([^\"]*)\"$")
  public void theDependencyShouldUseTheProperty(String artifact, String property) throws Throwable {
    for (Resolve resolve : rangeResolver.resolves()) {
      if (artifact.equals(resolve.key()) && property.equals(resolve.propertyName())) {
        return;
      }
    }
    throw new IllegalStateException(String.format("No resolution for %s with alias %s found.", artifact, property));
  }

  @Then("^the dependency \"([^\"]*)\" should be resolved from \"([^\"]*)\"$")
  public void theDependencyShouldBeResolvedFromOn(String artifact, String from) throws Throwable {
    for (Resolved resolved : rangeResolver.resolved()) {
      if (artifact.equals(resolved.key() + ":" + resolved.version()) && resolved.resolvedFrom().equals(from)) {
        return;
      }
    }
    throw new IllegalStateException(String.format("No resolved artifact for %s from %s found.", artifact, from));
  }

  @Given("^that \"([^\"]*)\" was previously imported$")
  public void thatWasPreviouslyImported(String imported) throws Throwable {
    previousImports.add(imported);
  }

  @Then("^resolving with aether$")
  public void resolvingWithAetherShouldSucceed() throws Throwable {
    try {
      repositoryResolver.resolveRanges(previousImports, rangeResolver, SOURCE_REFERENCE);
    } catch (Exception e) {
      rangeResolverException = e;
    }
  }

  @Then("^it should succeed$")
  public void itShouldSucceed() throws Throwable {
    assertWithMessage("It should have succeeded").that(rangeResolverException).isNull();
  }

  private static File resolveLocalRepository() {
    File localRepo = new File("/tmp/repository");
    if (localRepo.exists()) {
      localRepo.mkdir();
    }
    return localRepo;
  }

  @Then("^the import \"([^\"]*)\" should be declared$")
  public void theImportShouldBeDeclared(String artifact) throws Throwable {
    for (Import imported : rangeResolver.imports()) {
      if (artifact.equals(imported.key() + ":" + imported.version())) {
        return;
      }
    }

    throw new IllegalStateException(String.format("No imported artifact for %s found.", artifact));
  }

  @Then("^it should fail with \"([^\"]*)\"$")
  public void itShouldFailWith(String message) throws Throwable {
    assertWithMessage("The Script should has expected exception message!").that(rangeResolverException.getMessage()).isEqualTo(message);
  }

  @And("^a warning message \"([^\"]*)\" should be logged$")
  public void aWarningMessageShouldBeLogged(String message) throws Throwable {
    assertWithMessage("The message \"%s\" was not logged.", message).that(warnMessages).contains(message);
  }
}
