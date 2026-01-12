![NeqSim Logo](https://github.com/equinor/neqsim/blob/master/docs/wiki/neqsimlogocircleflatsmall.png)

<!-- BRANCH-ONLY-START: Remove this section before merging to master -->
> **⚠️ Branch: `scrubber-updates`**
> 
> This branch contains enhancements to separator and scrubber mechanical design, including:
> - Primary separation devices (`InletVane`, `InletVaneWithMeshpad`, `InletCyclones`)
> - Demisting internals with carry-over calculations (`DemistingInternal`, `DemistingInternalWithDrainage`)
> - Documentation: [Separators and Internals](docs/wiki/separators_and_internals.md) | [Carry-Over Calculations](docs/wiki/carryover_calculations.md)
> 
> Related PR: [#1766](https://github.com/equinor/neqsim/pull/1766)
<!-- BRANCH-ONLY-END -->

<!-- Badges -->
[![Azure DevOps Build](https://neqsim.visualstudio.com/neqsim_cicd/_apis/build/status/neqsim_build?branchName=master)](https://neqsim.visualstudio.com/neqsim_cicd/_build/latest?definitionId=1&branchName=master)
[![GitHub CI Build](https://github.com/equinor/neqsim/actions/workflows/verify_build.yml/badge.svg?branch=master)](https://github.com/equinor/neqsim/actions/workflows/verify_build.yml?query=branch%3Amaster)
[![CodeQL Analysis](https://github.com/equinor/neqsim/actions/workflows/codeql.yml/badge.svg?branch=master)](https://github.com/equinor/neqsim/security/code-scanning)
[![Coverage Status](https://codecov.io/gh/equinor/neqsim/branch/master/graph/badge.svg)](https://codecov.io/gh/equinor/neqsim)
[![Dep Vulns](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/equinor/neqsim/master/.github/metrics/dependabot-metrics.json)](https://github.com/equinor/neqsim/security/dependabot)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Total Security Alerts](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/equinor/neqsim/master/.github/metrics/security-metrics.json)](https://github.com/equinor/neqsim/security)


NeqSim is the main part of the [NeqSim project](https://equinor.github.io/neqsimhome/). NeqSim (Non-Equilibrium Simulator) is a Java library for estimating fluid properties and process design.
The basis for NeqSim is a library of fundamental mathematical models related to phase behavior and physical properties of fluids.  NeqSim is easily extended with new models. NeqSim development was initiated at the [Norwegian University of Science and Technology (NTNU)](https://www.ntnu.edu/employees/even.solbraa).

## Documentation
[NeqSim User Documentation](https://equinor.github.io/neqsim/)

[Index of reference manual](https://github.com/equinor/neqsim/blob/master/docs/REFERENCE_MANUAL_INDEX.md)

## Releases

[NeqSim releases](https://github.com/equinor/neqsim/releases) are available as a packaged jar file and as source code. NeqSim can be used in a third party application by adding NeqSim jar to the classpath.

## Getting started as a NeqSim Java user

NeqSim can be used in a Java application by adding the neqsim-x.x.x.jar found in [NeqSim releases](https://github.com/equinor/neqsim/releases) to the classpath. A demonstration of downloading the library and running a TPflash  benchmark is illustrated in this [NeqSim Colab demo](https://colab.research.google.com/drive/1XkQ_CrVj2gLTtJvXhFQMWALzXii522CL). Learn and ask questions in [Discussions for use and development of NeqSim](https://github.com/equinor/neqsim/discussions). Also see the [NeqSim JavaDoc](https://htmlpreview.github.io/?https://github.com/equinor/neqsimhome/blob/master/javadoc/site/apidocs/index.html).

## Use of the NeqSim package
NeqSim can be set up as a dependency in a Java project via the [NeqSim GitHub package distribution](https://github.com/equinor/neqsim/packages/42822).

### Using NeqSim from the GitHub Maven package repository
1. Configure authentication for the GitHub Packages repository in your Maven `settings.xml` (use a Personal Access Token with at least the `read:packages` scope):

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>${env.GITHUB_TOKEN}</password>
  </server>
</servers>
```

2. Add the GitHub Packages repository and NeqSim dependency to your project's `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/equinor/neqsim</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.equinor.neqsim</groupId>
    <artifactId>neqsim</artifactId>
    <version>3.1.2</version>
  </dependency>
</dependencies>
```

3. Build your project as normal (`mvn clean install`). Maven will fetch NeqSim from the GitHub Maven package repository using the credentials configured in step 1.

### Using NeqSim from Maven Central
If you prefer to pull NeqSim from Maven Central, you only need to declare the dependency because Maven Central is enabled by default in Maven builds:

```xml
<dependencies>
  <dependency>
    <groupId>com.equinor.neqsim</groupId>
    <artifactId>neqsim</artifactId>
    <version>3.1.2</version>
  </dependency>
</dependencies>
```

Run your Maven build (`mvn clean install`) and NeqSim will be resolved from the Central repository without additional repository configuration.

## Getting Started as a NeqSim Java developer

See the [NeqSim Java Wiki](https://github.com/equinor/neqsim/wiki) for how to use the NeqSim API.
Additional pages are available in the [local wiki](docs/wiki/index.md).
NeqSim can be built using the Maven build system (https://maven.apache.org/). All NeqSim build dependencies are given in the pom.xml file. Learn and ask questions in [Discussions for use and development of NeqSim](https://github.com/equinor/neqsim/discussions).

### Initial setup

The NeqSim source code is downloaded by cloning the library to your local computer (alternatively fork it to your private repository). The following commands are dependent on a local installation of [GIT](https://git-scm.com/) and [Maven](https://maven.apache.org/).

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
./mvnw install
```
> **Note**
> The maven wrapper command is dependent on your OS, for Unix use: ```./mvnw```
> Windows:
> ```mvnw.cmd ```

An interactive demonstration of how to get started as a NeqSim developer is presented in this [NeqSim Colab demo](https://colab.research.google.com/drive/1JiszeCxfpcJZT2vejVWuNWGmd9SJdNC7).

### Opening in VS Code

The repository contains a ready‑to‑use [dev container](.devcontainer/) configuration. After cloning
the project you can open it in VS Code with container support enabled:

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
# Open in VS Code with container support
code .
```

The container image comes with Maven and the recommended extensions already installed.

## Running the tests

The test files are written in JUnit5 and placed in the [test directory](https://github.com/equinor/neqsim/tree/master/src/test). Test code should be written for all new code added to the project, and all tests have to pass before merging into the master branch.  

Test coverage can be examined using [jacoco](https://www.eclemma.org/jacoco/) from maven.  
Generate a coverage report using `./mvnw jacoco:prepare-agent test install jacoco:report` and see results in target/site/jacoco/index.html.
Run `./mvnw checkstyle:check` to verify that your code follows the project's formatting rules.
> **Note**
> The maven wrapper command is dependent on your OS, for Unix use: ```./mvnw```
> Windows:
> ```mvnw.cmd ```


## Deployment

The NeqSim source code is compiled and distributed as a Java library. [NeqSim releases](https://github.com/equinor/neqsim/releases) are available for download from the release pages.

## Built With

[Maven](https://maven.apache.org/) - Dependency Management

## Contributing
See the [getting started as a NeqSim developer](https://github.com/equinor/neqsim/wiki/Getting-started-as-a-NeqSim-developer) documentation. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests. An interactive demonstration of how to get started as a NeqSim developer is presented in this [NeqSim Colab demo](https://colab.research.google.com/drive/1JiszeCxfpcJZT2vejVWuNWGmd9SJdNC7).
See [docs/DEVELOPER_SETUP.md](docs/DEVELOPER_SETUP.md) for a summary of how to clone the project, build it and run the tests. For more details see the [getting started as a NeqSim developer](https://github.com/equinor/neqsim/wiki/Getting-started-as-a-NeqSim-developer) documentation. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests. An interactive demonstration of how to get started as a NeqSim developer is presented in this [NeqSim Colab demo](https://colab.research.google.com/drive/1JiszeCxfpcJZT2vejVWuNWGmd9SJdNC7).
Pull requests will only be accepted if all tests and `./mvnw checkstyle:check` pass.

For guidance on where to place production code, tests, and resources, see [docs/contributing-structure.md](docs/contributing-structure.md).

## Discussion forum

Questions related to neqsim can be posted in the [github discussion pages](https://github.com/equinor/neqsim/discussions).

## Versioning

NeqSim uses [SemVer](https://semver.org/) for versioning.

## Authors and contact persons

Even Solbraa (esolbraa@gmail.com),  Marlene Louise Lund

## Licence

NeqSim is distributed under the [Apache-2.0](https://github.com/equinor/neqsim/blob/master/LICENSE) licence.

## Acknowledgments

A number of master and PhD students at NTNU have contributed to development of NeqSim. We greatly acknowledge their contributions.

## NeqSim modules

NeqSim is built upon seven base modules, each covering a key part of the library:

1. **Thermodynamic routines** – phase-equilibrium models, equation-of-state implementations and flash calculations.
2. **Physical properties routines** – transport and thermophysical property calculations such as density and viscosity.
3. **Fluid mechanic routines** – models for single- and multiphase flow in pipes and networks.
4. **Unit operations** – reusable models of separators, heat exchangers and other equipment.
5. **Chemical reactions routines** – equilibrium and kinetic reaction calculations.
6. **Parameter fitting routines** – tools for estimating model parameters from experimental data.
7. **Process simulation routines** – framework for assembling unit operations into steady-state or dynamic flowsheets.

For a deeper introduction to each module, see [docs/modules.md](docs/modules.md).

## File System

>neqsim/: main library with all modules
>
>neqsim/thermo/: Main path for thermodynamic routines
>neqsim/thermo/util/examples/: examples of use of Thermodynamic Models and Routines
>
>neqsim/thermodynamicoperation: Main path for flash routines (TPflash, phase envelopes, etc.)
>neqsim/thermodynamicoperation/util/example/: examples of use of thermodynamic operations (eg. flash calculations etc.)
>
>neqsim/physicalproperties: Main path for Physical Property methods
>neqsim/physicalproperties/util/examples/: Examples of use of physical properties calculations
>
>neqsim/physicalproperties: Main path for Physical Property methods
>neqsim/physicalproperties/util/examples/: Examples of use of physical properties calculations
>
>neqsim/processsimulation: Main path for Process Simulation Calculations
>neqsim/processsimulation/util/examples/: Examples of use of Process Simulation calculations
>
>changelog.txt : History of what changed between each version.
>license.txt: license document

## Toolboxes

See [NeqSim homepage](https://equinor.github.io/neqsimhome/). NeqSim toolboxes are available via GitHub for alternative programming languages.

* [Matlab](https://github.com/equinor/neqsimmatlab)
* [Python](https://github.com/equinor/neqsimpython)
* [.NET (C#)](https://github.com/equinor/neqsimcapeopen)

## Related open source projects

[NeqSim Python/Colab](https://github.com/EvenSol/NeqSim-Colab)
