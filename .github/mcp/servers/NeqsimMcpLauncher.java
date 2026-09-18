import java.io.IOException;
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
 * <p>Resolution order: {@code --root} else {@code $PLUGIN_ROOT}; {@code --data} else
 * {@code $PLUGIN_DATA} else {@code ~/.neqsim/mcp-server}; {@code $NEQSIM_MCP_JAR} points at
 * a local jar and skips the download (development builds); {@code $NEQSIM_MCP_VERSION}
 * overrides the pinned version; {@code $NEQSIM_MCP_JAVA_OPTS} adds JVM options (e.g. -Xmx4g).
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

    Path root = dir(opt.get("root"), System.getenv("PLUGIN_ROOT"), null);
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
    String version = first(System.getenv("NEQSIM_MCP_VERSION"), opt.get("version"),
        props.getProperty("version"));
    if (version == null) {
      fail("server version unknown: " + propsFile + " is missing or lacks 'version'");
    }
    String jarName = props.getProperty("jar", "neqsim-mcp-server-" + version + "-runner.jar");
    String base = props.getProperty("download", RELEASES + version + "/");

    Path jar;
    String local = System.getenv("NEQSIM_MCP_JAR");
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
