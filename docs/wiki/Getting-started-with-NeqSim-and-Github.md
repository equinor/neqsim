---
title: "Getting started with NeqSim and GitHub"
description: "Install NeqSim from Maven Central or build the current source, then navigate to the maintained Java, thermodynamics, process, pipeline, PVT, and engineering documentation."
---

Use the published Maven artifact for applications. Clone the repository when you want to run the
complete tests, change NeqSim, or contribute documentation and code.

## Install the released library

Add the current project version to a Maven build:

```xml
<dependency>
  <groupId>com.equinor.neqsim</groupId>
  <artifactId>neqsim</artifactId>
  <version>3.18.0</version>
</dependency>
```

Use [Maven Central](https://central.sonatype.com/artifact/com.equinor.neqsim/neqsim) to inspect
published versions and [GitHub Releases](https://github.com/equinor/neqsim/releases) for release
notes and downloadable assets. Older predecessor repositories are not authoritative.

## Build the current source

A Maven wrapper is included, so a separate Maven installation is not required:

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
./mvnw install
```

On Windows, run `mvnw.cmd install`. NeqSim source remains compatible with Java 8; the continuous
integration matrix also exercises newer supported JDKs. See the
[developer setup guide](../development/DEVELOPER_SETUP.md) for platform-specific prerequisites,
formatting, tests, and troubleshooting.

## Choose a starting point

| Goal | Maintained documentation |
| --- | --- |
| Understand the documentation structure | [Documentation landing page](../index.md) |
| Run a first Java calculation | [Java getting-started guide](../java-getting-started.md) |
| Browse the package-level map | [Package documentation](../README.md) |
| Find a specific topic | [Reference manual index](../REFERENCE_MANUAL_INDEX.md) |
| Follow the wiki learning path | [Wiki getting-started guide](getting_started.md) |
| Review common questions | [Frequently asked questions](faq.md) |
| Explore worked examples | [Usage examples](usage_examples.md) |

## Engineering topic map

| Topic | Guide |
| --- | --- |
| Thermodynamic models and flashes | [Thermodynamics guide](thermodynamics_guide.md) |
| Process flowsheets and equipment | [Process simulation guide](process_simulation.md) |
| Pipelines and multiphase flow | [Pipeline documentation index](pipeline_index.md) |
| PVT experiments and workflows | [PVT simulation workflows](pvt_simulation_workflows.md) |
| Design cases and deliverables | [Engineering documentation](../engineering/index.md) |

## Authoritative project resources

- [Source repository](https://github.com/equinor/neqsim)
- [JavaDoc API](https://equinor.github.io/neqsim/javadoc/index.html)
- [GitHub Releases](https://github.com/equinor/neqsim/releases)
- [Java regression examples](https://github.com/equinor/neqsim/tree/master/src/test/java/neqsim)
- [Questions and discussions](https://github.com/equinor/neqsim/discussions)
- [Bug reports and feature requests](https://github.com/equinor/neqsim/issues)

Repository Java tests are valuable usage evidence, but user-facing examples should also be checked
against the current public API and executed before publication.
