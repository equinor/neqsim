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
 * <p>Version {@code latest} (the plugin default) tracks the newest NeqSim release: the launcher
 * resolves the tag behind {@code releases/latest} at most once per {@link #LATEST_TTL_HOURS}
 * hours, records it in {@code latest-release.txt} in the data directory, downloads the matching
 * jar when it is not cached yet and deletes superseded jars. Offline it keeps using the last
 * resolved version, or the newest cached jar, so a lost network never blocks a chat session.
 * {@code $NEQSIM_MCP_LATEST_TTL_HOURS} changes the check interval ({@code 0} = every start).
 *
 * <p>Offline distribution: a {@code neqsim-mcp-server-<version>-runner.jar} (with its
 * {@code .sha256}) placed beside this launcher in {@code servers/} seeds the jar cache on first
 * start, so an unzipped plugin folder works with no network at all. Online, {@code latest}
 * tracking then continues from that seed.
 *
 * <p>{@code --prefetch} resolves and downloads the jar and exits without starting the server.
 * The plugin's SessionStart hook runs it detached so the ~90 MB download happens while the
 * user reads the first chat prompt and a newer release is fetched a day ahead of use. A lock
 * file beside the jar lets a concurrent server start wait for that download instead of
 * repeating it.
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
  private static final String LATEST_URL = "https://github.com/equinor/neqsim/releases/latest";
  private static final String LATEST = "latest";
  private static final String LATEST_STAMP = "latest-release.txt";
  private static final long LATEST_TTL_HOURS = 24;
  private static final long DOWNLOAD_LOCK_MAX_AGE_MS = 15 * 60_000L;
  private static final long DOWNLOAD_WAIT_MAX_MS = 15 * 60_000L;
  private static final java.util.regex.Pattern JAR_VERSION =
      java.util.regex.Pattern.compile("^neqsim-mcp-server-(.+)-runner\\.jar$");

  /**
   * Entry point.
   *
   * @param args {@code --root DIR}, {@code --data DIR}, {@code --version X.Y.Z|latest},
   *        {@code --prefetch}; anything after {@code --} is passed to the server jar
   * @throws Exception on unrecoverable I/O or process errors
   */
  public static void main(String[] args) throws Exception {
    int feature = Runtime.version().feature();
    if (feature < MIN_JAVA) {
      fail("NeqSim MCP server needs Java " + MIN_JAVA + "+; this is Java " + feature + " ("
          + System.getProperty("java.home") + "). Install a JDK 21+ or point JAVA_HOME at one.");
    }
    Map<String, String> opt = new HashMap<>();
    boolean prefetch = false;
    int passthroughFrom = args.length;
    for (int i = 0; i < args.length; i++) {
      if ("--".equals(args[i])) {
        passthroughFrom = i + 1;
        break;
      }
      if ("--prefetch".equals(args[i])) {
        prefetch = true;
      } else if (args[i].startsWith("--") && i + 1 < args.length) {
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
    Path data = dir(opt.get("data"), System.getenv("PLUGIN_DATA"),
        Paths.get(System.getProperty("user.home"), ".neqsim", "mcp-server").toString());
    if (local == null || local.isEmpty()) {
      seedFromBundle(root.resolve("servers"), data);
    }
    boolean track = LATEST.equalsIgnoreCase(version);
    if (track && (local == null || local.isEmpty())) {
      Files.createDirectories(data);
      version = resolveLatest(data);
    }
    // The pinned jar/download names in the properties file describe a fixed version only.
    String jarName = track ? "neqsim-mcp-server-" + version + "-runner.jar"
        : props.getProperty("jar", "neqsim-mcp-server-" + version + "-runner.jar");
    String base = track ? RELEASES + version + "/"
        : props.getProperty("download", RELEASES + version + "/");

    Path jar;
    if (local != null && !local.isEmpty()) {
      jar = Paths.get(local);
      if (!Files.isRegularFile(jar)) {
        fail("NEQSIM_MCP_JAR does not exist: " + jar);
      }
    } else {
      Files.createDirectories(data);
      jar = data.resolve(jarName);
      if (!Files.isRegularFile(jar)) {
        download(base, jarName, jar);
        if (track) {
          pruneOtherJars(data, jar);
        }
      }
    }
    if (prefetch) {
      System.err.println("[neqsim-mcp] prefetch done: " + jar);
      return;
    }
    System.err.println("[neqsim-mcp] starting " + jar.getFileName()
        + (track ? " (tracking latest release)" : ""));

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
   * <p>When another launcher (the hook's {@code --prefetch} or a second client) already holds
   * the download lock, this call waits for the jar to appear instead of downloading again.
   *
   * @param base release download URL prefix ending in {@code /}
   * @param jarName file name of the runner jar
   * @param target final jar path
   * @throws Exception on download or verification failure
   */
  private static void download(String base, String jarName, Path target) throws Exception {
    Path lock = target.resolveSibling(jarName + ".lock");
    boolean owned = acquireLock(lock);
    if (!owned) {
      System.err.println("[neqsim-mcp] another launcher is downloading " + jarName
          + "; waiting for it");
      long deadline = System.currentTimeMillis() + DOWNLOAD_WAIT_MAX_MS;
      while (!owned && System.currentTimeMillis() < deadline) {
        Thread.sleep(1000);
        if (Files.isRegularFile(target)) {
          return;
        }
        owned = acquireLock(lock); // succeeds once the other side finished or died
      }
      if (Files.isRegularFile(target)) {
        if (owned) {
          Files.deleteIfExists(lock);
        }
        return;
      }
      if (!owned) {
        fail("timed out waiting for another launcher to download " + jarName);
      }
    }
    try {
      fetchAndVerify(base, jarName, target);
    } finally {
      Files.deleteIfExists(lock);
    }
  }

  /**
   * Creates the lock file atomically; a lock older than
   * {@link #DOWNLOAD_LOCK_MAX_AGE_MS} is treated as abandoned and taken over.
   *
   * @param lock lock file path
   * @return true when this process now owns the lock
   * @throws Exception on I/O failure other than the lock already existing
   */
  private static boolean acquireLock(Path lock) throws Exception {
    try {
      Files.createFile(lock);
      return true;
    } catch (java.nio.file.FileAlreadyExistsException e) {
      try {
        long age = System.currentTimeMillis() - Files.getLastModifiedTime(lock).toMillis();
        if (age > DOWNLOAD_LOCK_MAX_AGE_MS) {
          Files.deleteIfExists(lock);
          Files.createFile(lock);
          return true;
        }
      } catch (java.io.IOException ignored) {
        return false;
      }
      return false;
    }
  }

  /**
   * Performs the verified download into a process-unique temp file and moves it into place.
   *
   * @param base release download URL prefix ending in {@code /}
   * @param jarName file name of the runner jar
   * @param target final jar path
   * @throws Exception on download or verification failure
   */
  private static void fetchAndVerify(String base, String jarName, Path target) throws Exception {
    configureNetwork();
    HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(30)).build();
    String expected = fetchText(http, base + jarName + ".sha256").trim().split("\\s+")[0]
        .toLowerCase();
    Path tmp = target.resolveSibling(jarName + ".part-" + ProcessHandle.current().pid());
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
   * Proxy and trust-store settings shared by every HTTP call.
   */
  private static void configureNetwork() {
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
  }

  /**
   * Version of the newest published NeqSim release.
   *
   * <p>Reads {@code latest-release.txt} ({@code <version> <epochMillis>}) from the data
   * directory and reuses it while younger than the TTL. Otherwise asks GitHub where
   * {@code releases/latest} redirects to and records the answer. When the request fails the
   * stale stamp is reused, then the newest cached jar; only with neither does the launcher
   * fail, because there is nothing it could start.
   *
   * @param data plugin data directory holding the stamp and the jar cache
   * @return a concrete version such as {@code 3.21.0}
   * @throws Exception on unrecoverable I/O failure
   */
  private static String resolveLatest(Path data) throws Exception {
    Path stamp = data.resolve(LATEST_STAMP);
    String cached = null;
    long checkedAt = 0;
    if (Files.isRegularFile(stamp)) {
      String[] parts = Files.readString(stamp, StandardCharsets.UTF_8).trim().split("\\s+");
      if (parts.length >= 1 && !parts[0].isEmpty()) {
        cached = parts[0];
      }
      if (parts.length >= 2) {
        try {
          checkedAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException ignored) {
          checkedAt = 0;
        }
      }
    }
    long ttlHours = LATEST_TTL_HOURS;
    String ttlEnv = System.getenv("NEQSIM_MCP_LATEST_TTL_HOURS");
    if (ttlEnv != null && !ttlEnv.trim().isEmpty()) {
      try {
        ttlHours = Long.parseLong(ttlEnv.trim());
      } catch (NumberFormatException ignored) {
        ttlHours = LATEST_TTL_HOURS;
      }
    }
    long now = System.currentTimeMillis();
    if (cached != null && now - checkedAt < ttlHours * 3600_000L) {
      return cached;
    }
    try {
      String found = fetchLatestTag();
      Files.writeString(stamp, found + " " + now + "\n", StandardCharsets.UTF_8);
      if (!found.equals(cached)) {
        System.err.println("[neqsim-mcp] latest NeqSim release is v" + found);
      }
      return found;
    } catch (Exception e) {
      String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      String fallback = cached != null ? cached : newestCachedVersion(data);
      if (fallback == null) {
        fail("cannot determine the latest NeqSim release (" + why
            + ") and no jar is cached in " + data
            + ". Connect once, or set NEQSIM_MCP_VERSION=X.Y.Z / NEQSIM_MCP_JAR.");
      }
      System.err.println("[neqsim-mcp] release check failed (" + why
          + "); using v" + fallback);
      // Refresh the stamp so a long outage does not retry on every start.
      Files.writeString(stamp, fallback + " " + now + "\n", StandardCharsets.UTF_8);
      return fallback;
    }
  }

  /**
   * Tag of the newest release, taken from the redirect GitHub issues for
   * {@code releases/latest}.
   *
   * @return version without the leading {@code v}
   * @throws Exception when GitHub cannot be reached or does not redirect to a tag
   */
  private static String fetchLatestTag() throws Exception {
    configureNetwork();
    HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(10)).build();
    HttpRequest req = HttpRequest.newBuilder(URI.create(LATEST_URL))
        .timeout(Duration.ofSeconds(15)).method("HEAD", HttpRequest.BodyPublishers.noBody())
        .build();
    HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
    String location = resp.headers().firstValue("location").orElse("");
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("/releases/tag/v?([^/?#]+)")
        .matcher(location);
    if (resp.statusCode() / 100 != 3 || !m.find()) {
      throw new java.io.IOException("HTTP " + resp.statusCode() + " from " + LATEST_URL
          + (location.isEmpty() ? "" : " -> " + location));
    }
    return m.group(1);
  }

  /**
   * Highest version among the runner jars already in the cache.
   *
   * @param data jar cache directory
   * @return version string, or null when the cache is empty
   * @throws Exception on I/O failure while listing the directory
   */
  private static String newestCachedVersion(Path data) throws Exception {
    if (!Files.isDirectory(data)) {
      return null;
    }
    try (java.util.stream.Stream<Path> files = Files.list(data)) {
      return files.map(p -> JAR_VERSION.matcher(p.getFileName().toString()))
          .filter(java.util.regex.Matcher::matches).map(m -> m.group(1))
          .max(NeqsimMcpLauncher::compareVersions).orElse(null);
    }
  }

  /**
   * Numeric-aware comparison of dotted versions ({@code 3.21.0 &gt; 3.9.1}).
   *
   * @param a first version
   * @param b second version
   * @return negative, zero or positive as for {@link Comparable}
   */
  private static int compareVersions(String a, String b) {
    String[] pa = a.split("[.-]");
    String[] pb = b.split("[.-]");
    for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
      String x = i < pa.length ? pa[i] : "0";
      String y = i < pb.length ? pb[i] : "0";
      int c;
      if (x.matches("\\d+") && y.matches("\\d+")) {
        c = Long.compare(Long.parseLong(x), Long.parseLong(y));
      } else {
        c = x.compareTo(y);
      }
      if (c != 0) {
        return c;
      }
    }
    return 0;
  }

  /**
   * Deletes every other runner jar in the cache once a newer one is verified, so tracking
   * {@code latest} does not accumulate ~90 MB per release.
   *
   * @param data jar cache directory
   * @param keep the jar that was just installed
   * @throws Exception on I/O failure while listing the directory
   */
  private static void pruneOtherJars(Path data, Path keep) throws Exception {
    try (java.util.stream.Stream<Path> files = Files.list(data)) {
      List<Path> old = files
          .filter(p -> JAR_VERSION.matcher(p.getFileName().toString()).matches()
              && !p.getFileName().equals(keep.getFileName()))
          .toList();
      for (Path p : old) {
        try {
          Files.deleteIfExists(p);
          System.err.println("[neqsim-mcp] removed superseded " + p.getFileName());
        } catch (java.io.IOException e) {
          System.err.println("[neqsim-mcp] could not remove " + p + ": " + e.getMessage());
        }
      }
    }
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
    return newestRunnerJar(checkout.resolve("neqsim-mcp-server").resolve("target"));
  }

  /**
   * Most recently modified {@code *-runner.jar} in a folder.
   *
   * @param dir folder to scan (may not exist)
   * @return the jar path, or null when there is none
   * @throws Exception on I/O failure while listing the folder
   */
  private static Path newestRunnerJar(Path dir) throws Exception {
    if (!Files.isDirectory(dir)) {
      return null;
    }
    try (java.util.stream.Stream<Path> files = Files.list(dir)) {
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
   * Copies a jar shipped beside the launcher into the cache when the cache holds nothing as
   * new. The side-car {@code .sha256} is verified when present. The latest-release stamp is
   * written with an expired timestamp so an online start still checks for something newer
   * while an offline start falls back to the seeded version.
   *
   * @param servers the plugin's {@code servers} folder
   * @param data jar cache directory
   * @throws Exception on I/O failure or checksum mismatch
   */
  private static void seedFromBundle(Path servers, Path data) throws Exception {
    Path bundled = newestRunnerJar(servers);
    if (bundled == null) {
      return;
    }
    java.util.regex.Matcher m = JAR_VERSION.matcher(bundled.getFileName().toString());
    if (!m.matches()) {
      return;
    }
    String version = m.group(1);
    Path target = data.resolve(bundled.getFileName());
    if (Files.isRegularFile(target)) {
      return;
    }
    String cached = newestCachedVersion(data);
    if (cached != null && compareVersions(cached, version) >= 0) {
      return;
    }
    Path sideCar = bundled.resolveSibling(bundled.getFileName() + ".sha256");
    if (Files.isRegularFile(sideCar)) {
      String expected = Files.readString(sideCar, StandardCharsets.UTF_8).trim().split("\\s+")[0]
          .toLowerCase();
      String actual = sha256Of(bundled);
      if (!actual.equals(expected)) {
        fail("sha256 mismatch for bundled " + bundled.getFileName() + ": expected " + expected
            + ", got " + actual);
      }
    }
    Files.createDirectories(data);
    Path tmp = target.resolveSibling(target.getFileName() + ".part-"
        + ProcessHandle.current().pid());
    Files.copy(bundled, tmp, StandardCopyOption.REPLACE_EXISTING);
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    Path stamp = data.resolve(LATEST_STAMP);
    if (!Files.exists(stamp)) {
      Files.writeString(stamp, version + " 0\n", StandardCharsets.UTF_8);
    }
    System.err.println("[neqsim-mcp] seeded cache from bundled " + bundled.getFileName());
  }

  /**
   * SHA-256 of a file.
   *
   * @param file file to digest
   * @return lower-case hex digest
   * @throws Exception on I/O failure
   */
  private static String sha256Of(Path file) throws Exception {
    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new DigestInputStream(Files.newInputStream(file), sha)) {
      byte[] buf = new byte[1 << 16];
      while (in.read(buf) != -1) {
        // digest updated by the stream
      }
    }
    return hex(sha.digest());
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
