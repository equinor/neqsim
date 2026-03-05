# NeqSim Development Container
# Uses the official devcontainers Java image with Java 11 (matches pom.xml compiler target).
# Python 3.12 is added via devcontainer features for Jupyter notebook development.

ARG VARIANT="11"
FROM mcr.microsoft.com/devcontainers/java:1-${VARIANT}-bookworm

# Install Maven via SDKMAN
ARG INSTALL_MAVEN="false"
ARG MAVEN_VERSION=""
RUN if [ "${INSTALL_MAVEN}" = "true" ]; then \
        su vscode -c "umask 0002 && . /usr/local/sdkman/bin/sdkman-init.sh && sdk install maven \"${MAVEN_VERSION}\""; \
    fi

# Install useful OS packages for development
RUN rm -f /etc/apt/sources.list.d/yarn.list /etc/apt/sources.list.d/yarn.list.save \
    && apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends \
        graphviz \
        fonts-liberation \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME for consistent tooling
ENV JAVA_HOME=/usr/lib/jvm/msopenjdk-current/