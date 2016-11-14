Feature: Range Resolver

  Scenario: Parse simple repository declaration
    Given the following script:
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

  Scenario: Parse circular import declarations should fail
    Given the following script:
      """
      import com.talios:bill-of-materials:1.0.2;
      """
    And that "com.talios:bill-of-materials:1.0.2" was previously imported
    When resolving with aether
    Then it should fail with "Circular imports detected for com.talios:bill-of-materials:1.0.2 in cucumber"

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

  Scenario: Parse resolved artifact
    Given the following script:
      """
      resolved com.smxemail:com.smxemail.api:1.0.2 from com.talios:bill-of-materials:1.0.2;
      """
    Then the dependency "com.smxemail:com.smxemail.api:1.0.2" should be resolved from "com.talios:bill-of-materials:1.0.2"


  Scenario: Resolve resolved artifact with Aether
    Given the following script:
      """
      repository http://nexus.smxemail.com/nexus/content/repositories/releases;
      resolve lowest com.smxemail:com.smxemail.api:[13.0.0,20.0.0);
      """
    When resolving with aether
    Then it should succeed

  Scenario: Resolution filtering should default to preventing SNAPSHOTS selection from Aether
    Given the following script:
      """
      repository http://nexus.smxemail.com/nexus/content/groups/public/;
      resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.7.0,3.0.0];
      """
    When resolving with aether
    Then the resolution of "com.fasterxml.jackson.core:jackson-annotations" should be "2.8.4"

  Scenario: Resolution filtering should limit range selection from Aether
    Given the following script:
      """
      repository http://nexus.smxemail.com/nexus/content/groups/public/;
      resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.7.0,3.0.0] filtering /^.*(SNAPSHOT|rc.*)$/;
      """
    When resolving with aether
    Then the resolution of "com.fasterxml.jackson.core:jackson-annotations" should be "2.8.4"

  Scenario: Resolution filtering should allow for explicit SNAPSHOT selection from Aether
    Given the following script:
      """
      repository http://nexus.smxemail.com/nexus/content/groups/public/;
      resolve snapshot com.fasterxml.jackson.core:jackson-annotations:[2.8.0,3.0.0];
      """
    When resolving with aether
    Then the resolution of "com.fasterxml.jackson.core:jackson-annotations" should be "2.9.0-SNAPSHOT"

  Scenario: Resolution allow for explicit locked versions from Aether
    Given the following script:
      """
      repository http://nexus.smxemail.com/nexus/content/groups/public/;
      resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.8.0];
      """
    When resolving with aether
    Then the resolution of "com.fasterxml.jackson.core:jackson-annotations" should be "2.8.0"

  Scenario: Write resolved DSL back out
    Given the following script:
      """
      // comment
      repository http://nexus.smxemail.com/nexus/content/repositories/releases as releases;
      repository http://nexus.smxemail.com/nexus/group/public;
      // comment
      import com.talios:bill-of-materials:1.0.2;
      import com.talios.test:bill-of-materials:1.0.2;
      // comment
      allow unlocked /^com.talios.*$/;
      allow snapshots /^com.talios.*$/;
      // comment
      resolve lowest com.smxemail:com.smxemail.api:[13.0.0,20.0.0) via releases;
      resolve highest com.talios.test:bill-of-materials:1.0.2;
      resolve snapshot com.smxemail:com.smxemail.test:[13.0.0,20.0.0);
      resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.8.0,3.0.0] filtering /^.*(SNAPSHOT|rc.*)$/;
      // comment
      alias com.smxemail:com.smxemail.api as test.prop;
      // comment
      resolved com.smxemail:com.smxemail.api:2.0.1 from com.talios:bill-of-materials:1.0.2;
      // comment
      locked com.smxemail:com.smxemail.api:2.0.1 from com.talios:bill-of-materials:1.0.2;
      """
    When when writing the DSL back out
    Then it should match the follow script:
      """
      // comment
      repository http://nexus.smxemail.com/nexus/content/repositories/releases as releases;
      repository http://nexus.smxemail.com/nexus/group/public;

      // comment
      import com.talios:bill-of-materials:1.0.2;
      import com.talios.test:bill-of-materials:1.0.2;

      // comment
      allow unlocked /^com.talios.*$/;
      allow snapshots /^com.talios.*$/;

      // comment
      resolve lowest com.smxemail:com.smxemail.api:[13.0.0,20.0.0) via releases;
      resolve highest com.talios.test:bill-of-materials:[1.0.2];
      resolve snapshot com.smxemail:com.smxemail.test:[13.0.0,20.0.0);
      resolve highest com.fasterxml.jackson.core:jackson-annotations:[2.8.0,3.0.0] filtering /^.*(SNAPSHOT|rc.*)$/;

      // comment
      alias com.smxemail:com.smxemail.api as test.prop;

      // comment
      resolved com.smxemail:com.smxemail.api:2.0.1 from com.talios:bill-of-materials:1.0.2;

      // comment
      locked com.smxemail:com.smxemail.api:2.0.1 from com.talios:bill-of-materials:1.0.2;
      """

  Scenario: Importing DSL as unlocked
    Given the following script:
      """
      resolved com.smxemail:com.smxemail.api:2.0.1 from com.talios:bill-of-materials:1.0.2;
      locked com.smxemail:com.smxemail.api:2.0.1 from com.talios:bill-of-materials:1.0.2;
      """
    When importing as unlocked
    And when writing the DSL back out
    Then it should match the follow script:
      """
      resolved com.smxemail:com.smxemail.api:2.0.1 from com.talios:bill-of-materials:1.0.2;
      resolved com.smxemail:com.smxemail.api:2.0.1 from com.talios:bill-of-materials:1.0.2;
      """

  Scenario: Importing DSL as snapshots
    Given the following script:
      """
      resolve lowest com.smxemail:com.smxemail.api:[1.0.0,2.0.0);
      resolve lowest com.smxemail:com.smxemail.impl:[1.0.0,2.0.0);
      """
    When importing as snapshots
    And when writing the DSL back out
    Then it should match the follow script:
      """
      resolve snapshot com.smxemail:com.smxemail.api:[1.0.0,2.0.0);
      resolve snapshot com.smxemail:com.smxemail.impl:[1.0.0,2.0.0);
      """
