package neqsim.process.equipment.separator.entrainment;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Registry / loader for {@link EnhancedEntrainmentProvider} implementations.
 *
 * <p>
 * Discovery is delegated to {@link java.util.ServiceLoader}: every JAR on
 * the classpath that contains a
 * {@code META-INF/services/neqsim.process.equipment.separator.entrainment.EnhancedEntrainmentProvider}
 * file contributes its providers automatically. The built-in provider
 * {@link BuiltInSevenStageProvider} is registered through this same
 * mechanism so the lookup path is uniform; private plug-ins (for example
 * the Equinor π-number plug-in {@code eqn-pi-v1}) are picked up only when
 * their JAR is on the classpath.
 *
 * <p>
 * Usage on a separator:
 *
 * <pre>
 * separator.setEntrainmentProvider("eqn-pi-v1");
 * </pre>
 *
 * If the requested id is not registered, {@link #find(String)} throws
 * {@link IllegalStateException} so the caller fails loudly rather than
 * silently falling back to a different correlation.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class EntrainmentProviderRegistry {

  /**
   * SPI revision supported by this build of public NeqSim. Providers
   * declaring a higher {@link EnhancedEntrainmentProvider#getApiVersion()}
   * are rejected at lookup time so version mismatches fail loudly.
   */
  public static final int CURRENT_API_VERSION = 1;

  /**
   * Private constructor — this is a static utility class.
   */
  private EntrainmentProviderRegistry() {}

  /**
   * Looks up a registered provider by id.
   *
   * @param id the provider id, e.g. {@code "neqsim-7stage"} or
   *        {@code "eqn-pi-v1"}; must not be null
   * @return the registered provider with the supplied id
   * @throws IllegalArgumentException if {@code id} is null
   * @throws IllegalStateException if no provider with the supplied id is
   *         registered (typically because the providing JAR is not on the
   *         classpath)
   */
  public static EnhancedEntrainmentProvider find(String id) {
    if (id == null) {
      throw new IllegalArgumentException("provider id must not be null");
    }
    ServiceLoader<EnhancedEntrainmentProvider> loader =
        ServiceLoader.load(EnhancedEntrainmentProvider.class);
    StringBuilder available = new StringBuilder();
    for (Iterator<EnhancedEntrainmentProvider> it = loader.iterator(); it.hasNext();) {
      EnhancedEntrainmentProvider p = it.next();
      if (id.equals(p.getId())) {
        if (p.getApiVersion() > CURRENT_API_VERSION) {
          throw new IllegalStateException("Entrainment provider '" + id
              + "' requires SPI api version " + p.getApiVersion()
              + " but this build of NeqSim supports up to " + CURRENT_API_VERSION
              + ". Upgrade NeqSim core or use an older plug-in build.");
        }
        return p;
      }
      if (available.length() > 0) {
        available.append(", ");
      }
      available.append(p.getId());
    }
    throw new IllegalStateException("Entrainment provider '" + id
        + "' is not on the classpath. Available providers: ["
        + (available.length() == 0 ? "<none>" : available.toString()) + "]. "
        + "If you expect a private plug-in such as 'eqn-pi-v1', make sure its "
        + "JAR is on the classpath.");
  }

  /**
   * Returns a freshly loaded {@link ServiceLoader} of all registered
   * providers. Useful for tooling that wants to enumerate available models.
   *
   * @return a {@link ServiceLoader} instance — iterate to obtain provider
   *         instances
   */
  public static ServiceLoader<EnhancedEntrainmentProvider> all() {
    return ServiceLoader.load(EnhancedEntrainmentProvider.class);
  }
}
