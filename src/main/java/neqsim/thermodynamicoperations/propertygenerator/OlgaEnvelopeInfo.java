package neqsim.thermodynamicoperations.propertygenerator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Phase-envelope extents of the water-free feed, used for the table header and for envelope grid refinement.
 *
 * <p>
 * Water dominates the envelope of a wet feed (its dew line reaches far above the hydrocarbon cricondentherm), so the
 * critical point and the refinement window are taken from a water-free copy. The table header keywords CRITICALPRESSURE
 * and CRITICALTEMPERATURE are therefore those of the water-free feed. Water is removed when it is the last component
 * and otherwise reduced to a trace; the full feed is used if the water-free envelope fails.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
final class OlgaEnvelopeInfo {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(OlgaEnvelopeInfo.class);
  /** Water mole fraction left when water cannot be removed. */
  private static final double TRACE_WATER = 1.0e-10;

  /** Critical temperature [K], NaN when unavailable. */
  double criticalTemperature = Double.NaN;
  /** Critical pressure [bara], NaN when unavailable. */
  double criticalPressure = Double.NaN;
  /** Cricondenbar pressure [bara], NaN when unavailable. */
  double cricondenbarPressure = Double.NaN;
  /** Cricondentherm temperature [K], NaN when unavailable. */
  double cricondenthermTemperature = Double.NaN;
  /** Lowest envelope temperature [K], NaN when unavailable. */
  double minimumTemperature = Double.NaN;
  /** Lowest envelope pressure [bara], NaN when unavailable. */
  double minimumPressure = Double.NaN;

  /**
   * Computes the envelope extents.
   *
   * @param feed feed, not modified
   * @return envelope extents, with NaN entries when no envelope could be traced
   */
  static OlgaEnvelopeInfo of(SystemInterface feed) {
    OlgaEnvelopeInfo info = new OlgaEnvelopeInfo();
    if (feed.hasComponent("water") && feed.getNumberOfComponents() > 1 && info.trace(waterFree(feed))) {
      return info;
    }
    info.trace(feed.clone());
    return info;
  }

  /**
   * Builds a water-free copy of the feed.
   *
   * @param feed feed containing water
   * @return water-free copy
   */
  private static SystemInterface waterFree(SystemInterface feed) {
    SystemInterface dry = feed.clone();
    dry.init(0);
    int waterIndex = dry.getPhase(0).getComponent("water").getComponentNumber();
    if (waterIndex == dry.getNumberOfComponents() - 1) {
      dry.removeComponent("water");
    } else {
      double[] fractions = dry.getMolarComposition();
      fractions[waterIndex] = TRACE_WATER;
      dry.setMolarComposition(fractions);
    }
    return dry;
  }

  /**
   * Traces the envelope and stores its extents.
   *
   * @param system fluid to trace
   * @return true when an envelope with a finite cricondenbar was traced
   */
  private boolean trace(SystemInterface system) {
    try {
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.calcPTphaseEnvelope();
      double[] cricondenbar = ops.get("cricondenbar");
      double[] cricondentherm = ops.get("cricondentherm");
      if (cricondenbar == null || cricondentherm == null || !(cricondenbar[1] > 0.0) || !(cricondentherm[0] > 0.0)) {
        return false;
      }
      cricondenbarPressure = cricondenbar[1];
      cricondenthermTemperature = cricondentherm[0];
      criticalTemperature = system.getTC();
      criticalPressure = system.getPC();
      minimumTemperature = minimum(ops.get("dewT"), ops.get("bubT"));
      minimumPressure = minimum(ops.get("dewP"), ops.get("bubP"));
      return true;
    } catch (Exception ex) {
      logger.debug("Phase envelope failed: {}", ex.getMessage());
      return false;
    }
  }

  /**
   * Returns the smallest positive finite value of two arrays.
   *
   * @param first first array, may be null
   * @param second second array, may be null
   * @return minimum, or NaN when both are empty
   */
  private static double minimum(double[] first, double[] second) {
    double result = Double.NaN;
    for (double[] array : new double[][] {first, second}) {
      if (array == null) {
        continue;
      }
      for (double value : array) {
        if (Double.isFinite(value) && value > 0.0 && (Double.isNaN(result) || value < result)) {
          result = value;
        }
      }
    }
    return result;
  }
}
