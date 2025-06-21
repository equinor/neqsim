# Developer Setup

This document summarizes the basic steps from the NeqSim wiki for setting up a local development environment.
For additional details see the [Getting started as a NeqSim developer](https://github.com/equinor/neqsim/wiki/Getting-started-as-a-NeqSim-developer) wiki page.

## Clone the repository

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
```

## Build the project

NeqSim requires **JDK&nbsp;8** or newer and uses the Maven build system. Use the provided Maven wrapper to build the code:

```bash
./mvnw install
```

(Windows users can run `mvnw.cmd`.)

## Run the test suite

Execute all unit tests with:

```bash
./mvnw test
```

To generate a code coverage report:

```bash
./mvnw jacoco:prepare-agent test install jacoco:report
```

