package neqsim.util.serialization;

/**
 * Implemented by classes that need extra repair after being read from a legacy {@code .neqsim} file.
 *
 * <p>
 * When {@link NeqSimXtream} loads a file written before the metadata entry existed, fields absent from the stored XML
 * are restored from a freshly constructed template instance of the same class. Some state set up by constructors cannot
 * be copied field by field because it refers back to its owner (for example energy ports connected to the owner's
 * energy stream). Such classes rebuild that state here, after the field defaults have been restored and before
 * {@code readResolve}.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public interface LegacyLoadRepairable {
  /**
   * Repairs owner-bound state after a legacy load.
   *
   * @param template a freshly constructed instance of the same class, holding the current constructor defaults; it is
   * discarded afterwards and must not be referenced from this object
   */
  void repairAfterLegacyLoad(Object template);
}
