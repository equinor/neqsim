package neqsim.process.safety.risk.sis.nog070;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Minimum-SIL catalogue per the Norwegian Oil &amp; Gas guideline 070 (NOG 070).
 *
 * <p>
 * NOG 070 Appendix A lists a minimum SIL for each standard SIF used on the Norwegian Continental Shelf. The values in
 * this catalogue follow the 2020 revision and are the values most commonly cited by Equinor / NCS operators. Project
 * teams should still verify the latest revision before final-design SIL assignment.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * int minSil = Nog070SilCatalogue.getMinimumSil(Nog070SifType.HIPPS_PIPELINE);
 * // minSil == 3
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public final class Nog070SilCatalogue implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final Map<Nog070SifType, Integer> MINIMUM_SIL;

  static {
    EnumMap<Nog070SifType, Integer> m = new EnumMap<Nog070SifType, Integer>(Nog070SifType.class);
    m.put(Nog070SifType.PSD_PROCESS_SEGMENT, Integer.valueOf(2));
    m.put(Nog070SifType.ESD_TOPSIDE_ISOLATION, Integer.valueOf(2));
    m.put(Nog070SifType.ESD_SUBSEA_ISOLATION, Integer.valueOf(3));
    m.put(Nog070SifType.HIPPS_PIPELINE, Integer.valueOf(3));
    m.put(Nog070SifType.BLOWDOWN_HYDROCARBON_SEGMENT, Integer.valueOf(2));
    m.put(Nog070SifType.FG_GAS_DETECTION_ESD, Integer.valueOf(2));
    m.put(Nog070SifType.FG_FIRE_DETECTION_ESD, Integer.valueOf(2));
    m.put(Nog070SifType.FG_HVAC_SHUTDOWN, Integer.valueOf(2));
    m.put(Nog070SifType.FG_DELUGE_RELEASE, Integer.valueOf(2));
    m.put(Nog070SifType.PSD_COMPRESSOR_ANTI_SURGE, Integer.valueOf(2));
    m.put(Nog070SifType.PSD_HIGH_VIBRATION, Integer.valueOf(1));
    m.put(Nog070SifType.PSD_HIGH_LEVEL, Integer.valueOf(2));
    m.put(Nog070SifType.PSD_LOW_LOW_LEVEL, Integer.valueOf(2));
    m.put(Nog070SifType.PSD_BURNER_MANAGEMENT, Integer.valueOf(2));
    m.put(Nog070SifType.ESD_RISER, Integer.valueOf(3));
    m.put(Nog070SifType.ESD_WELLHEAD, Integer.valueOf(2));
    m.put(Nog070SifType.ESD_LOADING_RELEASE, Integer.valueOf(2));
    m.put(Nog070SifType.CUSTOM, Integer.valueOf(0));
    MINIMUM_SIL = Collections.unmodifiableMap(m);
  }

  private Nog070SilCatalogue() {
    // utility class
  }

  /**
   * Returns the minimum SIL for the given SIF type per NOG 070.
   *
   * <p>
   * For {@link Nog070SifType#CUSTOM} this returns 0 - callers must provide their own minimum.
   * </p>
   *
   * @param type the SIF type
   * @return minimum SIL (1-4), or 0 for {@link Nog070SifType#CUSTOM}
   */
  public static int getMinimumSil(Nog070SifType type) {
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    Integer v = MINIMUM_SIL.get(type);
    return v == null ? 0 : v.intValue();
  }

  /**
   * Returns an unmodifiable copy of the full catalogue.
   *
   * @return mapping from SIF type to minimum SIL
   */
  public static Map<Nog070SifType, Integer> getCatalogue() {
    return MINIMUM_SIL;
  }
}
