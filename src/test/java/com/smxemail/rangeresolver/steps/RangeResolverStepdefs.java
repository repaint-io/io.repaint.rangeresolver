package com.smxemail.rangeresolver.steps;

import dev.zazr.collection.HashSet;
import dev.zazr.collection.Vector;

import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;

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

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import static com.smxemail.rangeresolver.lang.RepositoryResolver.snapshotDependencies;
import static com.smxemail.rangeresolver.lang.RepositoryResolver.unlockDependencies;

@SuppressWarnings("NullAway")
public class RangeResolverStepdefs {
  public static final String SOURCE_REFERENCE = "cucumber";
  public static final String IMPORT_REFERENCE = "cucumber-import";

  private RangeResolver rangeResolver;

  private Exception rangeResolverException;

  private Vector<String> previousImports;

  private Vector<String> infoMessages;

  private Vector<String> warnMessages;

  private String rangeResolverSource;

  private String baseScript = "";

  private ResolutionResult resolutionResult;

  private void info(String message) {
    infoMessages = infoMessages.append(message);
    System.out.printf("%s\n", message);
  }

  private void warn(String message) {
    warnMessages = warnMessages.append(message);
    System.out.printf("%s\n", message);
  }

  private RepositoryResolver repositoryResolver = RepositoryResolver.createRepositoryResolver(
      this::info, this::warn, resolveLocalRepository().getAbsolutePath(), "", "", true);

  @Before
  public void beforeScenario() {
    previousImports = Vector.empty();
    infoMessages = Vector.empty();
    warnMessages = Vector.empty();
    rangeResolver = null;
    resolutionResult = null;
    rangeResolverException = null;
    rangeResolverSource = "";
  }

  private void processResolutionScript(String script) {
    try {
      rangeResolver =
          RangeResolverProcessor.parseRangeResolver(SOURCE_REFERENCE, new StringReader(script), "", "");
    } catch (Exception e) {
      e.printStackTrace();
      rangeResolverException = e;
    }
  }

  @Given("the following base script:")
  public void givenTheFollowingBaseScript(String script) throws IOException {
    baseScript = script;
    processResolutionScript(script);
  }

  @Given("the following raw script:")
  public void givenTheFollowingRawScript(String script) throws IOException {
    processResolutionScript(script);
  }

  @Given("the following script:")
  public void givenTheFollowingScript(String script) throws IOException {
    processResolutionScript(baseScript + "\n\n" + script);
  }

  @When("writing the DSL back out")
  public void whenWritingTheDSLBackOut() {
    var sw = new StringWriter();

    if (resolutionResult != null) {
      rangeResolver = rangeResolver
          .withBlacklisted(resolutionResult.blacklisted())
          .withDeprecated(resolutionResult.deprecated())
          .withResolved(resolutionResult.resolved())
          .withLocked(resolutionResult.locked());
    }

    RepositoryWriter.writeRangeResolveSource(rangeResolver, sw);
    rangeResolverSource = sw.toString();
  }

  @Then("it should match the follow script:")
  public void itShouldMatchTheFollowScript(String script) throws IOException {
    String delta = deltaDiff(rangeResolverSource.trim(), script.trim());
    assertWithMessage("Expected DSL does not match: " + delta)
        .that(rangeResolverSource.trim())
        .isEqualTo(script.trim());
    rangeResolver = RangeResolverProcessor.parseRangeResolver(SOURCE_REFERENCE, new StringReader(script), "", "");
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
      if (artifact.equals(resolve.key())
          && resolve.bounds().toString().toLowerCase().equals(bounds)
          && resolve.range().asVersionRange().equals(range)) {
        return;
      }
    }

    throw new IllegalStateException(
        "No resolution for %s %s with range %s found.".formatted(bounds, artifact, range));
  }

  @Then("^the resolved dependency \"([^\"]*)\" should be resolving against version \"([^\"]*)\"$")
  public void theResolvedDependencyShouldBeResolvingAgainstTheRange(String artifact, String version) {
    Vector<Resolved> allResolved = Vector.<Resolved>empty()
        .appendAll(rangeResolver.resolved())
        .appendAll(rangeResolver.deprecated())
        .appendAll(rangeResolver.locked());

    for (Resolved resolved : allResolved) {
      if (artifact.equals(resolved.key()) && resolved.version().equals(version)) {
        return;
      }
    }

    throw new IllegalStateException("No resolution for %s with version %s found.".formatted(artifact, version));
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
    throw new IllegalStateException("No resolution for %s with alias %s found.".formatted(artifact, property));
  }

  @And("^the dependency \"([^\"]*)\" should use the classifer \"([^\"]*)\"$")
  public void theDependencyShouldUseTheClassifer(String artifact, String classifier) {
    for (Resolve resolve : rangeResolver.resolves()) {
      if (artifact.equals(resolve.key()) && classifier.equals(resolve.classifier())) {
        return;
      }
    }
    throw new IllegalStateException(
        "No resolution for %s with classifier %s found.".formatted(artifact, classifier));
  }

  @And("^the dependency \"([^\"]*)\" should use the extension \"([^\"]*)\"$")
  public void theDependencyShouldUseTheExtension(String artifact, String extension) {
    for (Resolve resolve : rangeResolver.resolves()) {
      if (artifact.equals(resolve.key()) && extension.equals(resolve.extension())) {
        return;
      }
    }
    throw new IllegalStateException("No resolution for %s with extension %s found.".formatted(artifact, extension));
  }

  @Then("^the dependency \"([^\"]*)\" should be resolved$")
  public void theDependencyShouldBeResolvedFromOn(String artifact) {
    for (Resolved resolved : rangeResolver.resolved()) {
      if (artifact.equals(resolved.key() + ":" + resolved.version())) {
        return;
      }
    }
    throw new IllegalStateException("No resolved artifact for %s found.".formatted(artifact));
  }

  @Given("^that \"([^\"]*)\" was previously imported$")
  public void thatWasPreviouslyImported(String imported) {
    previousImports = previousImports.append(imported);
  }

  @Then("resolving")
  public void resolvingShouldSucceed() {
    try {
      resolutionResult = repositoryResolver.resolveRanges(rangeResolver, IMPORT_REFERENCE, SOURCE_REFERENCE);
    } catch (Exception e) {
      rangeResolverException = e;
    }
  }

  @Then("it should succeed")
  public void itShouldSucceed() {
    assertWithMessage("It should have succeeded")
        .that(rangeResolverException)
        .isNull();
  }

  private static File resolveLocalRepository() {
    try {
      return Files.createTempDirectory("repository").toFile();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Then("^the import \"([^\"]*)\" should be declared$")
  public void theImportShouldBeDeclared(String artifact) {
    for (Import imported : rangeResolver.imports()) {
      if (artifact.equals(imported.key() + ":" + imported.version())) {
        return;
      }
    }

    throw new IllegalStateException("No imported artifact for %s found.".formatted(artifact));
  }

  @Then("^it should fail with \"([^\"]*)\"$")
  public void itShouldFailWith(String message) {
    if (rangeResolverException == null) {
      fail("The script generated no exceptions, expected: " + message);
    }
    assertWithMessage("The Script should has expected exception message!")
        .that(rangeResolverException.getMessage())
        .isEqualTo(message);
  }

  @And("^a warning message \"([^\"]*)\" should be logged$")
  public void aWarningMessageShouldBeLogged(String message) {
    assertWithMessage("The message \"%s\" was not logged.", message)
        .that(warnMessages)
        .contains(message);
  }

  @Then("^the resolution of \"([^\"]*)\" should be \"([^\"]*)\"$")
  public void theResolutionOfShouldBe(String artifact, String version) {
    if (resolutionResult == null) {
      throw new IllegalStateException("Unable to resolve artifact");
    }

    for (Resolved resolved : resolutionResult.resolved()) {
      if (artifact.equals(resolved.key())) {
        if (version.equals(resolved.version())) {
          return;
        } else {
          throw new IllegalStateException(String.format(
              "Resolution of artifact %s:%s found with unexpected version: %s.",
              artifact, version, resolved.version()));
        }
      }
    }

    throw new IllegalStateException("No resolution of artifact for %s found.".formatted(artifact));
  }

  private String deltaDiff(String actual, String expected) {
    DiffMatchPatch diffMatchPatch = new DiffMatchPatch();
    return diffMatchPatch.patchToText(diffMatchPatch.patchMake(actual, expected));
  }

  @When("importing as unlocked")
  public void importingAsUnlocked() {
    rangeResolver = unlockDependencies(rangeResolver, HashSet.of(Pattern.compile(".*")));
  }

  @When("importing as snapshots")
  public void importingAsSnapshots() {
    rangeResolver = snapshotDependencies(rangeResolver, this::warn, HashSet.of(Pattern.compile(".*")));
  }
}
