# NeqSim MCP Server — lightweight runtime image
# Provides thermodynamic calculations and process simulation via MCP (STDIO transport).
#
# Build:
#   docker build -t neqsim-mcp-server .
#
# Run (STDIO — for Claude Desktop, VS Code Copilot, Cursor, etc.):
#   docker run -i --rm neqsim-mcp-server
#
# NOTE: Build the uber-jar first:
#   cd .. && ./mvnw install -DskipTests -Dmaven.javadoc.skip=true
#   cd neqsim-mcp-server && ../mvnw package -DskipTests -Dmaven.javadoc.skip=true

FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.source="https://github.com/equinor/neqsim"
LABEL org.opencontainers.image.description="NeqSim MCP Server — rigorous thermodynamics for LLMs"
LABEL org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /app

COPY target/neqsim-mcp-server-*-runner.jar /app/server.jar

# STDIO transport — stdin/stdout for JSON-RPC, stderr for logs
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
