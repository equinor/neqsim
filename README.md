# NeqSim

[![Build Status](https://neqsim.visualstudio.com/neqsim_cicd/_apis/build/status/neqsim_build?branchName=master)](https://neqsim.visualstudio.com/neqsim_cicd/_build/latest?definitionId=1&branchName=master)
![Build maven](https://github.com/equinor/neqsim/workflows/Build%20maven/badge.svg?branch=master)  
NeqSim is the main part of the [NeqSim project](https://equinor.github.io/neqsimhome/). NeqSim (Non-Equilibrium Simulator) is a Java library for estimation of fluid behavior and process design for oil and gas production.
The basis for NeqSim is a library of fundamental mathematical models related to phase behavior and physical properties of oil and gas.  NeqSim is easilly extended with new models. NeqSim development was initiated at the [Norwegian University of Science and Technology (NTNU)](https://www.ntnu.edu/employees/even.solbraa).

## Releases

[NeqSim releases](https://github.com/equinor/neqsim/releases) are available as java packages (jar files) and as source code. NeqSim can be used in a third party application by adding NeqSim.jar to the classpath.

## Getting started as a NeqSim Java user

NeqSim can be used in a Java application by adding the neqsim-x.x.x.jar found in [NeqSim releases](https://github.com/equinor/neqsim/releases) to the classpath. A demonstration of dowloading the library and running a TPflash  bencmark is illustrated in this [NeqSim Colab demo](https://colab.research.google.com/drive/1XkQ_CrVj2gLTtJvXhFQMWALzXii522CL). Learn and ask questions in [Discussions for use and development of NeqSim](https://github.com/equinor/neqsim/discussions). Also see the [NeqSim JavaDoc](https://htmlpreview.github.io/?https://github.com/equinor/neqsimhome/blob/master/javadoc/site/apidocs/index.html).

## Getting Started as a NeqSim Java developer

See the [NeqSim Java Wiki](https://github.com/equinor/neqsim/wiki) for how to use the NeqSim API.
NeqSim can be built using the Maven build system (https://maven.apache.org/). All NeqSim build dependencies are given in the pom.xml file. Learn and ask questions in [Discussions for use and development of NeqSim](https://github.com/equinor/neqsim/discussions).

### Initial setup

The NeqSim source code is downloaded by cloning the library to your local computer (alternatively fork it to your private reprository). The following commands are dependent on a local installation of [GIT](https://git-scm.com/) and [Maven](https://maven.apache.org/).

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
mvn install
```

An interactive demonstration of how to get started as a NeqSim developer is presented in this [NeqSim Colab demo](https://colab.research.google.com/drive/1JiszeCxfpcJZT2vejVWuNWGmd9SJdNC7).  

## Running the tests

The test files are written in JUnit5 and placed in the [test directory](https://github.com/equinor/neqsim/tree/master/src/test). All test have to be passed before merging to the master. Test code shuld be written for all new code added to the project.  

Test coverage can be examined using [jacoco](https://www.eclemma.org/jacoco/) from maven.  
Generate a coverage report using `mvn jacoco:prepare-agent test install jacoco:report` and see results in target/site/jacoco/index.html.

## Deployment

The NeqSim source code is compiled and distributed as a Java library. [NeqSim releases](https://github.com/equinor/neqsim/releases) are available for download from the release pages.

## Built With

[Maven](https://maven.apache.org/) - Dependency Management

## Contributing

See the [getting started as a NeqSim developer](https://github.com/equinor/neqsim/wiki/Getting-started-as-a-NeqSim-developer) documentation. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests. An interactive demonstration of how to get started as a NeqSim developer is presented in this [NeqSim Colab demo](https://colab.research.google.com/drive/1JiszeCxfpcJZT2vejVWuNWGmd9SJdNC7).  

## Discussion forum

Questions related to neqsim can be posted in the [github discussion pages](https://github.com/equinor/neqsim/discussions).

## Versioning

NeqSim use [SemVer](https://semver.org/) for versioning.

## Authors and contact persons

Even Solbraa (esolbraa@gmail.com),  Marlene Louise Lund

## Licence

NeqSim is distributed under the [Apache-2.0](https://github.com/equinor/neqsim/blob/master/LICENSE) licence.

## Acknowledgments

A number of master and PhD students at NTNU have contributed to development of NeqSim. We greatly acknowledge their contributions.

## NeqSim modules

NeqSim is built upon six base modules:

1. Thermodynamic Routines
2. Physical Properties Routines
3. Fluid Mechanic Routines
4. Unit Operations
5. Chemical Reactions Routines
6. Parameter Fitting Routines
7. Process simulation routines

## File System

>neqsim/: main library with all modules
>
>neqsim/thermo/: Main path for thermodynamic routines
>neqsim/thermo/util/examples/: examples of use of Thermodynamic Models and Routines
>
>neqsim/thermodynamicOperation: Main path for flash routines (TPflash, phase envelopes, etc.)
>neqsim/thermodynamicOperation/util/example/: examples of use of thermodynamic operations (eg. flash calculations etc.)
>
>neqsim/physicalProperties: Main path for Physical Property methods
>neqsim/physicalProperties/util/examples/: Examples of use of physical properties calculations
>
>neqsim/physicalProperties: Main path for Physical Property methods
>neqsim/physicalProperties/util/examples/: Examples of use of physical properties calculations
>
>neqsim/processSimulation: Main path for Process Simulation Calculations
>neqsim/processSimulation/util/examples/: Examples of use of Process Simulation calculations
>
>changelog.txt : History of what changed between each version.
>license.txt: license document

## Toolboxes

See [NeqSim homepage](https://equinor.github.io/neqsimhome/). NeqSim toolboxes are avalable via GitHub for alternative programming languages.

* [Matlab](https://github.com/equinor/neqsimmatlab)
* [Python](https://github.com/equinor/neqsimpython)
* [.NET (C#)](https://github.com/equinor/neqsimcapeopen)

## Related open source projects

[NeqSim Python/Colab](https://github.com/EvenSol/NeqSim-Colab)
