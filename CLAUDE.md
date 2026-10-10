# Building

Build with `mvn clean install`. Do not use `-Ppatch` in this repo, even where a skill says to.
Its ErrorProne fixes reorder imports in `RangeResolverVisitor.java`, and the Spotless check then fails the build.
