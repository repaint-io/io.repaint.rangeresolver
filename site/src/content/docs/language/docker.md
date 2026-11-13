---
title: Docker images
description: Pinning Docker image tags to immutable digests.
---

```text
resolve docker [<registry>[:<port>]/]<image>:<tag>;
```

Tags such as `latest` or `8-minimal` move over time. A `resolve docker` request pins a tag to the content digest it
currently points at:

```rangeresolver
resolve docker almalinux:8-minimal;
```

After resolution:

```rangeresolver
resolve docker almalinux:8-minimal;

resolved almalinux@sha256:3f9b9fdb5957745afcc9269e82c8c43e3140ab8802172967927070c5bc8be907;
```

The digest is published as the property `<image>.version`, here `almalinux.version`. The image name is used without
its tag.

Images from a private registry include the registry, and optionally its port:

```rangeresolver
resolve docker registry.example.com:5000/almalinux:8;
```

## How it resolves

Docker resolution doesn't query Maven repositories. The resolver runs the local Docker CLI:

1. `docker pull <image>:<tag>`
2. `docker inspect --format={{index .RepoDigests 0}} <image>:<tag>`

The `docker` command must be on the `PATH`, and the daemon must be able to reach the registry. If no digest comes
back, the resolver logs `Unable to resolve image for …` and skips the request.

Docker requests don't take a bound, `via` or `filtering` clause. A Docker request is always re-resolved, so a run
records the tag's current digest even if an older digest was locked.

## Records

Docker records use `image@algorithm:digest` in place of `group:artifact:version`, with the same statements as Maven
records:

```rangeresolver
locked almalinux@sha256:dfde3fb768a3200078793476af4d021e58308de1450fc1e1878e2db5e3880e00;
```
