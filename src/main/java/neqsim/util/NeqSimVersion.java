package neqsim.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the version of the running NeqSim library.
 *
 * <p>
 * Lookup order:
 * </p>
 * <ol>
 * <li>the {@code neqsim.version} system property, when set;</li>
 * <li>the {@code Implementation-Version} entry of the JAR manifest;</li>
 * <li>the Maven metadata ({@code META-INF/maven/com.equinor.neqsim/neqsim/pom.properties}) packaged in released JARs,
 * whose manifests carry no implementation version;</li>
 * <li>{@code "development"} for an unpackaged classpath such as {@code target/classes}.</li>
 * </ol>
 *
 * @author esol
 * @version 1.0
 */
public final class NeqSimVersion {
  /** System property that overrides the detected version. */
  public static final String VERSION_PROPERTY = "neqsim.version";

  /** Version reported when no packaged metadata is available. */
  public static final String DEVELOPMENT_VERSION = "development";

  /** Maven metadata resource packaged with released NeqSim artifacts. */
  private static final String MAVEN_PROPERTIES_RESOURCE = "META-INF/maven/com.equinor.neqsim/neqsim/pom.properties";

  /** Utility class; not instantiable. */
  private NeqSimVersion() {
  }

  /**
   * Returns the version of the running NeqSim library.
   *
   * @return the version, never null or empty
   */
  public static String get() {
    String configured = System.getProperty(VERSION_PROPERTY);
    if (configured != null && !configured.trim().isEmpty()) {
      return configured.trim();
    }

    Package packageInfo = NeqSimVersion.class.getPackage();
    if (packageInfo != null) {
      String implementationVersion = packageInfo.getImplementationVersion();
      if (implementationVersion != null && !implementationVersion.trim().isEmpty()) {
        return implementationVersion.trim();
      }
    }

    ClassLoader classLoader = NeqSimVersion.class.getClassLoader();
    if (classLoader != null) {
      try (InputStream stream = classLoader.getResourceAsStream(MAVEN_PROPERTIES_RESOURCE)) {
        if (stream != null) {
          Properties properties = new Properties();
          properties.load(stream);
          String version = properties.getProperty("version");
          if (version != null && !version.trim().isEmpty()) {
            return version.trim();
          }
        }
      } catch (IOException e) {
        // Fall through to the development marker when packaged metadata cannot be read.
      }
    }

    return DEVELOPMENT_VERSION;
  }
}
