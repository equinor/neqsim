# Getting started

This page explains how to download the source code and build the project using Maven.

## Clone the repository

```bash
git clone https://github.com/equinor/neqsim.git
cd neqsim
```

## Build using Maven

The repository includes the Maven wrapper so you do not need a local Maven installation. Run

```bash
./mvnw install
```

This command downloads dependencies and compiles the project. It also executes the test suite.

See the [README](../../README.md) for more details.
