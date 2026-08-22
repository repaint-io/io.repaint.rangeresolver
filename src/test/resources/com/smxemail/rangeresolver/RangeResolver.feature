Feature: Range Resolver
#  Background:
#    Given the following base script:
#      """
#      // Declare repository locations, independent of build tool.
#      repository https://repo1.maven.org/maven2;
#      """

  Scenario: Parse simple repository declaration
    Given the following raw script:
      """
      // Declare repository locations, independent of build tool.
      repository http://SERVER_HERE/nexus/content/repositories/releases;
      repository http://SERVER_THERE/nexus/content/repositories/snapshots as snapshots;
      """
    Then the repository "http://SERVER_HERE/nexus/content/repositories/releases" should be declared with a grouping of "default"
    And the repository "http://SERVER_THERE/nexus/content/repositories/snapshots" should be declared with a grouping of "snapshots"

  Scenario: Parse import declaration
    Given the following script:
      """
      import com.talios:bill-of-materials:1.0.2;
      """
    Then the import "com.talios:bill-of-materials:1.0.2" should be declared

  Scenario: Parse duplicate import declarations should fail
    Given the following script:
      """
      import com.talios:bill-of-materials:1.0.2;
      import com.talios:bill-of-materials:1.0.2;
      """
    Then it should fail with "Duplicate import declaration found in cucumber"

#  Scenario: Parse circular import declarations should fail
#    Given the following script:
#      """
#      import com.talios:bill-of-materials:1.0.2;
#      """
#    And that "com.talios:bill-of-materials:1.0.2" was previously imported
#    When resolving
#    Then it should fail with "Circular imports detected for com.talios:bill-of-materials:1.0.2 in cucumber-import"

  Scenario: Parse resolution request
    Given the following script:
      """
      resolve lowest com.smxemail:com.smxemail.api:[1.0.0,2.0.0);
      resolve lowest com.smxemail:com.smxemail.impl:[1.0.0,2.0.0);

      alias com.smxemail:com.smxemail.api as test.prop;
      """
    Then the dependency "com.smxemail:com.smxemail.api" should be resolving against the "lowest" range "[1.0.0,2.0.0)"
    And the dependency "com.smxemail:com.smxemail.api" should use the property "test.prop"
    And the dependency "com.smxemail:com.smxemail.impl" should use the property "com.smxemail.impl.version"
    And the dependency "com.smxemail:com.smxemail.impl" should use the classifer ""
    And the dependency "com.smxemail:com.smxemail.impl" should use the extension "pom"

  Scenario: Parse resolution of artifacts with dashes
    Given the following script:
      """
      resolve lowest com.smxemail:com-smxemail-api:[1.0.0,2.0.0);

      locked com.smxemail:com-smxemail-api:1.0.0;
      """
    Then the dependency "com.smxemail:com-smxemail-api" should be resolving against the "lowest" range "[1.0.0,2.0.0)"
    Then the resolved dependency "com.smxemail:com-smxemail-api" should be resolving against version "1.0.0"

  Scenario: Parse resolved artifact
    Given the following script:
      """
      resolved com.smxemail:com.smxemail.api:1.0.2;
      """
    Then the dependency "com.smxemail:com.smxemail.api:1.0.2" should be resolved

  Scenario: The legacy from clause is no longer part of the language
    Given the following raw script:
      """
      resolved com.smxemail:com.smxemail.api:1.0.2 from com.talios:bill-of-materials:1.0.2;
      """
    Then it should fail with "Syntax error in cucumber: line 1:45 mismatched input 'from' expecting ';'"

  Scenario: Syntax errors should fail the parse rather than be silently recovered from
    Given the following raw script:
      """
      resolve lowest com.smxemail:com.smxemail.api:[1.0.0,2.0.0)
      resolve lowest com.smxemail:com.smxemail.impl:[1.0.0,2.0.0);
      """
    Then it should fail with "Syntax error in cucumber: line 2:0 mismatched input 'resolve' expecting {'filtering', 'via', ';'}"

  Scenario: Keywords are usable as group, artifact and grouping names
    Given the following raw script:
      """
      repository http://SERVER_HERE/nexus/content/repositories/snapshots as snapshots;
      resolve lowest com.smxemail:locked:[1.0.0,2.0.0) via snapshots;
      resolved com.smxemail:docker:1.0.0;
      """
    Then it should succeed

  Scenario: Repository URLs may carry a port, credentials and a query string
    Given the following raw script:
      """
      repository https://user:secret@nexus.example.com:8081/repository/releases?foo=bar;
      repository file:///var/tmp/local-repository;
      """
    Then it should succeed

  Scenario: Filtering patterns need not contain regex metacharacters
    Given the following raw script:
      """
      resolve highest com.smxemail:com.smxemail.api:[1.0.0,2.0.0) filtering /release/;
      resolve highest com.smxemail:com.smxemail.spi:[1.0.0,2.0.0) filtering /^1\.2\.\d{2}$/;
      """
    Then it should succeed

  Scenario: Half open version ranges are supported
    Given the following raw script:
      """
      resolve lowest com.smxemail:com.smxemail.api:[1.0.0,);
      resolve highest com.smxemail:com.smxemail.spi:(,2.0.0];
      """
    Then the dependency "com.smxemail:com.smxemail.api" should be resolving against the "lowest" range "[1.0.0,)"
    And the dependency "com.smxemail:com.smxemail.spi" should be resolving against the "highest" range "(,2.0.0]"

  Scenario: A script may end with a comment
    Given the following raw script:
      """
      resolve lowest com.smxemail:com.smxemail.api:[1.0.0,2.0.0);
      // nothing follows this comment
      """
    Then it should succeed

  Scenario: Resolve resolved artifact with Aether
    Given the following script:
      """
      repository https://repo1.maven.org/maven2;
      resolve lowest com.fasterxml.jackson.core:jackson-annotations:[2.7.0,3.0.0];
      """
    When resolving
    Then it should succeed

  Scenario: Resolution filtering should default to preventing SNAPSHOTS selection from Aether
    Given the following script:
      """
      repository https://repo1.maven.org/maven2;
      resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.7.0,3.0.0];
      """
    When resolving
    Then the resolution of "com.fasterxml.jackson.core:jackson-annotations" should be "3.0-rc5"

  Scenario: Resolution filtering should limit range selection from Aether
    Given the following script:
      """
      repository https://repo1.maven.org/maven2;
      resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.7.0,3.0.0] filtering /^.*(SNAPSHOT|PR|rc).*$/;
      """
    When resolving
    Then the resolution of "com.fasterxml.jackson.core:jackson-annotations" should be "2.22"

  Scenario: Resolution filtering should allow for explicit SNAPSHOT selection from Aether
    Given the following script:
      """
      repository https://repo1.maven.org/maven2;
      resolve snapshot com.fasterxml.jackson.core:jackson-annotations:[2.8.0,3.0.0];
      """
    When resolving
    Then the resolution of "com.fasterxml.jackson.core:jackson-annotations" should be "3.0-rc5"

  Scenario: Resolution allow for explicit locked versions from Aether
    Given the following script:
      """
      repository https://repo1.maven.org/maven2;
      resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.8.0];
      """
    When resolving
    Then the resolution of "com.fasterxml.jackson.core:jackson-annotations" should be "2.8.0"

  @docker
  Scenario: Resolution of docker images should work
    Given the following script:
      """
      resolve docker almalinux:8-minimal;
      """
    When resolving
    Then the resolution of "almalinux" should be "sha256:4178368be5c3b49bd1c5708e3a217e837eefa6576226524fe11ae98cb0004846"
    And the dependency "almalinux" should use the property "almalinux.version"
    And writing the DSL back out
    Then it should match the follow script:
      """
      resolve docker almalinux:8-minimal;

      resolved almalinux@sha256:4178368be5c3b49bd1c5708e3a217e837eefa6576226524fe11ae98cb0004846;
      """

  @docker
  Scenario: Parsing of resolved docker images should work
    Given the following script:
      """
      locked almalinux@sha256:dfde3fb768a3200078793476af4d021e58308de1450fc1e1878e2db5e3880e00;
      """
    When importing as unlocked
    And writing the DSL back out
    Then it should match the follow script:
      """
      resolved almalinux@sha256:dfde3fb768a3200078793476af4d021e58308de1450fc1e1878e2db5e3880e00;
      """

  Scenario: Write resolved DSL back out
    Given the following script:
      """
      // comment
      repository https://repo1.maven.org/maven2;
      // comment
      import com.talios:bill-of-materials:1.0.2;
      import com.talios.test:bill-of-materials:1.0.2;
      // comment
      allow unlocked /^com.talios.*$/;
      allow snapshots /^com.talios.*$/;
      // other comment
      allow snapshots /^com.talios.*$/;
      // comment
      resolve lowest com.smxemail:com.smxemail.api:[13.0.0,20.0.0) via releases;
      resolve highest com.talios.test:bill-of-materials:1.0.2;
      deprecate snapshot com.smxemail:com.smxemail.test:[13.0.0,20.0.0);
      resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.8.0,3.0.0] filtering /^.*(SNAPSHOT|rc.*)$/;
      // comment
      alias com.smxemail:com.smxemail.api as test.prop;
      // comment
      resolved com.smxemail:com.smxemail.api:2.0.1;
      // comment
      locked com.smxemail:com.smxemail.api:2.0.1;
      // comment
      deprecated com.smxemail:com.smxemail.spi:2.0.1;
      """
    When writing the DSL back out
    Then it should match the follow script:
      """
      // comment
      repository https://repo1.maven.org/maven2;

      // comment
      import com.talios:bill-of-materials:1.0.2;
      import com.talios.test:bill-of-materials:1.0.2;

      // other comment
      allow snapshots /^com.talios.*$/;
      // comment
      allow unlocked /^com.talios.*$/;

      deprecate snapshot com.smxemail:com.smxemail.test:[13.0.0,20.0.0);

      // comment
      resolve lowest com.smxemail:com.smxemail.api:[13.0.0,20.0.0) via releases;
      resolve highest com.talios.test:bill-of-materials:[1.0.2];
      resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.8.0,3.0.0] filtering /^.*(SNAPSHOT|rc.*)$/;

      // comment
      alias com.smxemail:com.smxemail.api as test.prop;

      // comment
      deprecated com.smxemail:com.smxemail.spi:2.0.1;

      // comment
      resolved com.smxemail:com.smxemail.api:2.0.1;

      // comment
      locked com.smxemail:com.smxemail.api:2.0.1;
      """

  Scenario: Importing DSL as unlocked
    Given the following script:
      """
      locked com.smxemail:com.smxemail.api:2.0.1;
      """
    When importing as unlocked
    And writing the DSL back out
    Then it should match the follow script:
      """
      resolved com.smxemail:com.smxemail.api:2.0.1;
      """

  Scenario: Importing DSL as locked
    Given the following script:
      """
      resolved com.smxemail:com.smxemail.api:2.0.1;
      """
    When writing the DSL back out
    Then it should match the follow script:
      """
      resolved com.smxemail:com.smxemail.api:2.0.1;
      """

  Scenario: Importing DSL as snapshots
    Given the following script:
      """
      resolve lowest com.smxemail:com.smxemail.api:[1.0.0,2.0.0);
      resolve lowest com.smxemail:com.smxemail.impl:[1.0.0,2.0.0);
      """
    When importing as snapshots
    And writing the DSL back out
    Then it should match the follow script:
      """
      resolve snapshot com.smxemail:com.smxemail.api:[1.0.0,2.0.0);
      resolve snapshot com.smxemail:com.smxemail.impl:[1.0.0,2.0.0);
      """
