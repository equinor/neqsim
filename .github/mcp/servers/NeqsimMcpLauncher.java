import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Starts the NeqSim MCP server from its release uber-jar without Docker.
 *
 * <p>Invoked by the plugin's {@code mcp.json} in Java source-launch mode
 * ({@code java NeqsimMcpLauncher.java}), so it needs a JDK 21+ and nothing else. The
 * launcher resolves the server version from {@code servers/neqsim-mcp-server.properties}
 * beside this file, downloads {@code neqsim-mcp-server-<version>-runner.jar} and its
 * {@code .sha256} from the NeqSim GitHub release into the plugin data directory on first
 * use, verifies the checksum, and then runs the jar as a child process that inherits this
 * process's stdin/stdout so the MCP JSON-RPC stream passes straight through. All launcher
 * output goes to stderr.
 *
 * <p>Resolution order: {@code --root} else {@code $PLUGIN_ROOT} else the folder above this
 * source file ({@code jdk.launcher.sourcefile}); {@code --data} else {@code $PLUGIN_DATA} else
 * {@code ~/.neqsim/mcp-server}; {@code $NEQSIM_MCP_JAR} points at a local jar and skips the
 * download (development builds); {@code $NEQSIM_MCP_VERSION} overrides the pinned version;
 * {@code $NEQSIM_MCP_JAVA_OPTS} adds JVM options (e.g. -Xmx4g). Downloads honour the operating
 * system proxy settings ({@code java.net.useSystemProxies}).
 *
 * <p>Source checkout: the properties file is written by the plugin builder and is absent when
 * the launcher runs from {@code .github/mcp} of an {@code equinor/neqsim} clone (the workspace
 * {@code .vscode/mcp.json}). There the launcher uses a locally built
 * {@code neqsim-mcp-server/target/*-runner.jar} when one exists, and otherwise takes the
 * version from the root {@code pom.xml} {@code <revision>} (without {@code -SNAPSHOT}).
 *
 * @author NeqSim
 * @version 1.0
 */
public class NeqsimMcpLauncher {
  private static final int MIN_JAVA = 21;
  private static final String RELEASES = "https://github.com/equinor/neqsim/releases/download/v";

  /**
   * Entry point.
   *
   * @param args {@code --root DIR}, {@code --data DIR}, {@code --version X.Y.Z}; anything
   *        after {@code --} is passed to the server jar
   * @throws Exception on unrecoverable I/O or process errors
   */
  public static void main(String[] args) throws Exception {
    int feature = Runtime.version().feature();
    if (feature < MIN_JAVA) {
      fail("NeqSim MCP server needs Java " + MIN_JAVA + "+; this is Java " + feature + " ("
          + System.getProperty("java.home") + "). Install a JDK 21+ or point JAVA_HOME at one.");
    }
    Map<String, String> opt = new HashMap<>();
    int passthroughFrom = args.length;
    for (int i = 0; i < args.length; i++) {
      if ("--".equals(args[i])) {
        passthroughFrom = i + 1;
        break;
      }
      if (args[i].startsWith("--") && i + 1 < args.length) {
        opt.put(args[i].substring(2), args[++i]);
      }
    }

    Path root = dir(opt.get("root"), System.getenv("PLUGIN_ROOT"), sourceRoot());
    if (root == null) {
      fail("plugin root unknown: pass --root DIR or set PLUGIN_ROOT");
    }
    Properties props = new Properties();
    Path propsFile = root.resolve("servers").resolve("neqsim-mcp-server.properties");
    if (Files.exists(propsFile)) {
      try (InputStream in = Files.newInputStream(propsFile)) {
        props.load(in);
      }
    }
    Path checkout = root.getParent() == null ? null : root.getParent().getParent();
    String local = System.getenv("NEQSIM_MCP_JAR");
    String version = first(System.getenv("NEQSIM_MCP_VERSION"), opt.get("version"),
        props.getProperty("version"));
    if (version == null && !Files.exists(propsFile) && checkout != null
        && Files.isRegularFile(checkout.resolve("pom.xml"))) {
      Path built = localRunnerJar(checkout);
      if ((local == null || local.isEmpty()) && built != null) {
        local = built.toString();
        System.err.println("[neqsim-mcp] source checkout: using locally built " + built);
      }
      version = pomRevision(checkout.resolve("pom.xml"));
    }
    if (version == null && (local == null || local.isEmpty())) {
      fail("server version unknown: " + propsFile + " is missing or lacks 'version'");
    }
    String jarName = props.getProperty("jar", "neqsim-mcp-server-" + version + "-runner.jar");
    String base = props.getProperty("download", RELEASES + version + "/");

    Path jar;
    if (local != null && !local.isEmpty()) {
      jar = Paths.get(local);
      if (!Files.isRegularFile(jar)) {
        fail("NEQSIM_MCP_JAR does not exist: " + jar);
      }
    } else {
      Path data = dir(opt.get("data"), System.getenv("PLUGIN_DATA"),
          Paths.get(System.getProperty("user.home"), ".neqsim", "mcp-server").toString());
      Files.createDirectories(data);
      jar = data.resolve(jarName);
      if (!Files.isRegularFile(jar)) {
        download(base, jarName, jar);
      }
    }

    String javaExe = ProcessHandle.current().info().command()
        .orElse(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
    // On Windows a piped stdout defaults to the ANSI code page, which corrupts JSON-RPC.
    // HTTP transport is off so parallel plugin/workspace instances never fight over :8080.
    List<String> cmd = new ArrayList<>(List.of(javaExe, "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
        "-Dquarkus.http.host-enabled=false"));
    String extra = System.getenv("NEQSIM_MCP_JAVA_OPTS");
    if (extra != null && !extra.trim().isEmpty()) {
      cmd.addAll(List.of(extra.trim().split("\\s+")));
    }
    cmd.addAll(List.of("-jar", jar.toString()));
    for (int i = passthroughFrom; i < args.length; i++) {
      cmd.add(args[i]);
    }
    ProcessBuilder pb = new ProcessBuilder(cmd).inheritIO();
    Process server = pb.start();
    Runtime.getRuntime().addShutdownHook(new Thread(server::destroy));
    System.exit(server.waitFor());
  }

  /**
   * Downloads the jar and its sha256 side-car, verifies the digest, and moves the jar into place.
   *
   * @param base release download URL prefix ending in {@code /}
   * @param jarName file name of the runner jar
   * @param target final jar path
   * @throws Exception on download or verification failure
   */
  private static void download(String base, String jarName, Path target) throws Exception {
    if (System.getProperty("java.net.useSystemProxies") == null) {
      System.setProperty("java.net.useSystemProxies", "true");
    }
    // Corporate TLS inspection re-signs github.com with a CA that lives in the Windows
    // store, not in the JDK's cacerts; trust the OS store so the download passes.
    if (System.getProperty("os.name", "").toLowerCase().contains("win")
        && System.getProperty("javax.net.ssl.trustStoreType") == null) {
      System.setProperty("javax.net.ssl.trustStore", "NONE");
      System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
    }
    HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(30)).build();
    String expected = fetchText(http, base + jarName + ".sha256").trim().split("\\s+")[0]
        .toLowerCase();
    Path tmp = target.resolveSibling(jarName + ".part");
    System.err.println("[neqsim-mcp] downloading " + base + jarName + " -> " + target);
    HttpRequest req = HttpRequest.newBuilder(URI.create(base + jarName)).GET().build();
    HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
    if (resp.statusCode() != 200) {
      fail("download failed: HTTP " + resp.statusCode() + " for " + base + jarName
          + " (is release v" + jarName.replaceAll("^neqsim-mcp-server-|-runner\\.jar$", "")
          + " published?)");
    }
    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new DigestInputStream(resp.body(), sha)) {
      Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
    }
    String actual = hex(sha.digest());
    if (!actual.equals(expected)) {
      Files.deleteIfExists(tmp);
      fail("sha256 mismatch for " + jarName + ": expected " + expected + ", got " + actual);
    }
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    System.err.println("[neqsim-mcp] verified sha256 " + actual);
  }

  /**
   * GETs a small text resource.
   *
   * @param http client
   * @param url resource URL
   * @return body as UTF-8 text
   * @throws Exception on HTTP failure
   */
  private static String fetchText(HttpClient http, String url) throws Exception {
    HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (resp.statusCode() != 200) {
      fail("download failed: HTTP " + resp.statusCode() + " for " + url);
    }
    return resp.body();
  }

  /**
   * Plugin root derived from this launcher's own location in source-launch mode
   * ({@code <root>/servers/NeqsimMcpLauncher.java}), for clients that do not export
   * {@code PLUGIN_ROOT} to the server process.
   *
   * @return the parent of the {@code servers} folder, or null when not source-launched
   */
  private static String sourceRoot() {
    String source = System.getProperty("jdk.launcher.sourcefile");
    if (source == null || source.isEmpty()) {
      return null;
    }
    Path servers = Paths.get(source).toAbsolutePath().getParent();
    return servers == null || servers.getParent() == null ? null : servers.getParent().toString();
  }

  /**
   * Newest {@code neqsim-mcp-server/target/*-runner.jar} of a source checkout.
   *
   * @param checkout repository root containing {@code pom.xml}
   * @return the jar path, or null when the module has not been built
   * @throws Exception on I/O failure while listing the target folder
   */
  private static Path localRunnerJar(Path checkout) throws Exception {
    Path target = checkout.resolve("neqsim-mcp-server").resolve("target");
    if (!Files.isDirectory(target)) {
      return null;
    }
    try (java.util.stream.Stream<Path> files = Files.list(target)) {
      return files.filter(p -> p.getFileName().toString().endsWith("-runner.jar"))
          .max((a, b) -> {
            try {
              return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
            } catch (java.io.IOException e) {
              return 0;
            }
          }).orElse(null);
    }
  }

  /**
   * Release version from a Maven pom's {@code <revision>} property.
   *
   * @param pom path to {@code pom.xml}
   * @return the revision without a {@code -SNAPSHOT} suffix, or null when absent
   * @throws Exception when the pom cannot be read
   */
  private static String pomRevision(Path pom) throws Exception {
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("<revision>\\s*([^<\\s]+)\\s*</revision>")
        .matcher(Files.readString(pom, StandardCharsets.UTF_8));
    return m.find() ? m.group(1).replace("-SNAPSHOT", "") : null;
  }

  /**
   * First non-empty value.
   *
   * @param values candidates in priority order
   * @return first non-null, non-empty value or null
   */
  private static String first(String... values) {
    for (String v : values) {
      if (v != null && !v.isEmpty()) {
        return v;
      }
    }
    return null;
  }

  /**
   * Resolves the first non-empty candidate to an absolute path.
   *
   * @param values candidates in priority order
   * @return absolute path or null when all candidates are empty
   */
  private static Path dir(String... values) {
    String v = first(values);
    return v == null ? null : Paths.get(v).toAbsolutePath().normalize();
  }

  /**
   * Lower-case hex encoding.
   *
   * @param bytes digest bytes
   * @return hex string
   */
  private static String hex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  /**
   * Prints a message to stderr and exits with status 1.
   *
   * @param message what went wrong and how to fix it
   */
  private static void fail(String message) {
    System.err.println("[neqsim-mcp] " + message);
    System.exit(1);
  }
}
