package com.smxemail.rangeresolver.steps;

import com.smxemail.rangeresolver.api.Alias;
import com.smxemail.rangeresolver.api.Import;
import com.smxemail.rangeresolver.api.RangeResolver;
import com.smxemail.rangeresolver.api.Repository;
import com.smxemail.rangeresolver.api.ResolutionResult;
import com.smxemail.rangeresolver.api.Resolve;
import com.smxemail.rangeresolver.api.Resolved;
import com.smxemail.rangeresolver.lang.RangeResolverProcessor;
import com.smxemail.rangeresolver.lang.RepositoryResolver;
import com.smxemail.rangeresolver.lang.RepositoryWriter;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
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

  private String rangeResolverSource;

  private ResolutionResult resolutionResult;

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
    resolutionResult = null;
    rangeResolverException = null;
    rangeResolverSource = "";
  }

  @Given("^the following script:$")
  public void givenTheFollowingScript(String script) throws IOException {
    try {
      rangeResolver = RangeResolverProcessor.parseRangeResolver(SOURCE_REFERENCE, new StringReader(script));
    } catch (Exception e) {
      rangeResolverException = e;
    }
  }

  @When("^when writing the DSL back out$")
  public void whenWritingTheDSLBackOut() {
    StringWriter sw = new StringWriter();
    RepositoryWriter.writeRangeResolveSource(rangeResolver, sw);
    rangeResolverSource = sw.toString();
  }

  @Then("^it should match the follow script:$")
  public void itShouldMatchTheFollowScript(String script) throws IOException {
    String delta = deltaDiff(rangeResolverSource.trim(), script.trim());
    assertWithMessage("Expected DSL does not match: " + delta).that(rangeResolverSource.trim()).isEqualTo(script.trim());
    rangeResolver = RangeResolverProcessor.parseRangeResolver(SOURCE_REFERENCE, new StringReader(script));
  }

  @Then("^the repository \"([^\"]*)\" should be declared with a grouping of \"([^\"]*)\"$")
  public void theRepositoryShouldBeDeclared(String repository, String grouping) {
    for (Repository rep : rangeResolver.repositories().values()) {
      if (rep.url().equals(repository) && rep.grouping().equals(grouping)) {
        return;
      }
    }

    throw new IllegalStateException("No repository declared for " + repository);
  }

  @Then("^the dependency \"([^\"]*)\" should be resolving against the \"([^\"]*)\" range \"([^\"]*)\"$")
  public void theDependencyShouldBeResolvingAgainstTheRange(String artifact, String bounds, String range) {
    for (Resolve resolve : rangeResolver.resolves()) {
      if (artifact.equals(resolve.key()) && resolve.bounds().toString().toLowerCase().equals(bounds) && resolve.range().asVersionRange().equals(range)) {
        return;
      }
    }

    throw new IllegalStateException(String.format("No resolution for %s %s with range %s found.", bounds, artifact, range));
  }

  @Then("^the dependency \"([^\"]*)\" should use the property \"([^\"]*)\"$")
  public void theDependencyShouldUseTheProperty(String artifact, String property) {
    for (Alias alias : rangeResolver.aliases()) {
      if (artifact.equals(alias.key()) && property.equals(alias.propertyName())) {
        return;
      }
    }
    for (Resolve resolve : rangeResolver.resolves()) {
      if (artifact.equals(resolve.key()) && property.equals(resolve.propertyName())) {
        return;
      }
    }
    throw new IllegalStateException(String.format("No resolution for %s with alias %s found.", artifact, property));
  }

  @And("^the dependency \"([^\"]*)\" should use the classifer \"([^\"]*)\"$")
  public void theDependencyShouldUseTheClassifer(String artifact, String classifier) {
    for (Resolve resolve : rangeResolver.resolves()) {
      if (artifact.equals(resolve.key()) && classifier.equals(resolve.classifier())) {
        return;
      }
    }
    throw new IllegalStateException(String.format("No resolution for %s with classifier %s found.", artifact, classifier));
  }

  @And("^the dependency \"([^\"]*)\" should use the extension \"([^\"]*)\"$")
  public void theDependencyShouldUseTheExtension(String artifact, String extension) {
    for (Resolve resolve : rangeResolver.resolves()) {
      if (artifact.equals(resolve.key()) && extension.equals(resolve.extension())) {
        return;
      }
    }
    throw new IllegalStateException(String.format("No resolution for %s with extension %s found.", artifact, extension));
  }

  @Then("^the dependency \"([^\"]*)\" should be resolved from \"([^\"]*)\"$")
  public void theDependencyShouldBeResolvedFromOn(String artifact, String from) {
    for (Resolved resolved : rangeResolver.resolved()) {
      if (artifact.equals(resolved.key() + ":" + resolved.version()) && resolved.resolvedFrom().equals(from)) {
        return;
      }
    }
    throw new IllegalStateException(String.format("No resolved artifact for %s from %s found.", artifact, from));
  }

  @Given("^that \"([^\"]*)\" was previously imported$")
  public void thatWasPreviouslyImported(String imported) {
    previousImports.add(imported);
  }

  @Then("^resolving with aether$")
  public void resolvingWithAetherShouldSucceed() {
    try {
      resolutionResult = repositoryResolver.resolveRanges(previousImports, rangeResolver, SOURCE_REFERENCE);
    } catch (Exception e) {
      rangeResolverException = e;
    }
  }

  @Then("^it should succeed$")
  public void itShouldSucceed() {
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
  public void theImportShouldBeDeclared(String artifact) {
    for (Import imported : rangeResolver.imports()) {
      if (artifact.equals(imported.key() + ":" + imported.version())) {
        return;
      }
    }

    throw new IllegalStateException(String.format("No imported artifact for %s found.", artifact));
  }

  @Then("^it should fail with \"([^\"]*)\"$")
  public void itShouldFailWith(String message) {
    assertWithMessage("The Script should has expected exception message!").that(rangeResolverException.getMessage()).isEqualTo(message);
  }

  @And("^a warning message \"([^\"]*)\" should be logged$")
  public void aWarningMessageShouldBeLogged(String message) {
    assertWithMessage("The message \"%s\" was not logged.", message).that(warnMessages).contains(message);
  }

  @Then("^the resolution of \"([^\"]*)\" should be \"([^\"]*)\"$")
  public void theResolutionOfShouldBe(String artifact, String version) {
    for (Resolved resolved : resolutionResult.resolved()) {
      if (artifact.equals(resolved.key())) {
        if (version.equals(resolved.version())) {
          return;
        } else {
          throw new IllegalStateException(String.format("Resolution of artifact %s found with unexpected version: %s.", artifact, resolved.version()));
        }
      }
    }

    throw new IllegalStateException(String.format("No imported artifact for %s found.", artifact));
  }

  private String deltaDiff(String actual, String expected) {
    DiffMatchPatch diffMatchPatch = new DiffMatchPatch();
    return diffMatchPatch.patchToText(diffMatchPatch.patchMake(actual, expected));
  }
}
