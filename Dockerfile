# Self-contained NeqSim image.
#
# Strategy: start FROM the already-published NeqSim dev container (Java 21 +
# Maven + Python are already installed there, so no SDKMAN/network setup is
# needed), copy this repo in, compile + package the Java, and wire Python
# (jpype) to the freshly built classes. A user then only needs Docker:
#
#   docker build -t neqsim-dev .
#   docker run -it neqsim-dev bash
#   cd /workspaces/neqsim && ./mvnw -o test -Dtest=SystemSrkEosTest   # Java
#   python3 -c "import sys; sys.path.insert(0,'devtools'); \
#       from neqsim_dev_setup import neqsim_init, neqsim_classes; \
#       ns=neqsim_classes(neqsim_init(project_root='/workspaces/neqsim')); \
#       f=ns.SystemSrkEos(298.15,10.0); f.addComponent('methane',1.0); \
#       f.setMixingRule('classic'); print('NeqSim OK from Python')"          # Python

# Base can be overridden in CI (e.g. pin a tag/digest). Defaults to the
# published NeqSim dev container so Java/Maven/Python are already present.
ARG NEQSIM_BASE=ghcr.io/equinor/neqsim-devcontainer:latest
FROM ${NEQSIM_BASE}

LABEL org.opencontainers.image.source="https://github.com/equinor/neqsim" \
      org.opencontainers.image.description="Self-contained NeqSim: Java 21 + Maven + Python with the source compiled and ready to use from Java and Python (jpype)." \
      org.opencontainers.image.licenses="Apache-2.0"

# --- Copy the NeqSim repo into the image (context = repo root, see .dockerignore)
USER root
WORKDIR /workspaces/neqsim
COPY . /workspaces/neqsim

# Normalize line endings + exec bit on the Maven wrapper. Windows checkouts
# store mvnw with CRLF, which breaks the shebang on Linux (exit 127).
RUN sed -i 's/\r$//' mvnw \
    && chmod +x mvnw \
    && chown -R vscode:vscode /workspaces/neqsim

# --- Build NeqSim as the vscode user (warms ~/.m2, produces the shaded jar) ----
USER vscode
RUN ./mvnw -B -q -DskipTests \
        -Dcheckstyle.skip -Dspotbugs.skip -Dpmd.skip -Dmaven.javadoc.skip=true \
        clean package

# --- Python tooling so the built Java is usable from Python via jpype ----------
ENV PATH="/home/vscode/.local/bin:${PATH}"
RUN python3 -m pip install --user --no-cache-dir --break-system-packages \
        jpype1 matplotlib pandas numpy python-docx \
    && python3 -m pip install --user --no-cache-dir --break-system-packages -e devtools/ \
    && echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc

WORKDIR /workspaces/neqsim
CMD ["bash"]
