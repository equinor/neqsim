package neqsim.process.equipment.compressor;

import java.io.Serializable;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Explicit acoustic similarity coordinates for a fixed compressor geometry.
 *
 * <p>
 * At equal inlet Mach number and flow coefficient, actual volume and speed scale with isentropic sound speed, specific
 * head with its square, and mass flow additionally with density. These are screening coordinates, not a PTC-10
 * conversion or a prediction of unchanged efficiency or surge boundaries. Reynolds number, real-gas path changes and
 * OEM applicability must be checked separately. Nothing here silently changes a compressor chart.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class CompressorMapSimilarity implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final double acousticRatio;
  private final double densityRatio;

  /**
   * Creates a conversion from actual suction conditions to reference-map conditions.
   *
   * @param referenceSoundSpeed sound speed at reference suction conditions in m/s
   * @param actualSoundSpeed sound speed at actual suction conditions in m/s
   * @param referenceDensity reference gas density in kg/m3
   * @param actualDensity actual gas density in kg/m3
   * @throws IllegalArgumentException if a property is nonfinite or nonpositive
   */
  public CompressorMapSimilarity(double referenceSoundSpeed, double actualSoundSpeed, double referenceDensity,
      double actualDensity) {
    positive(referenceSoundSpeed, "Reference sound speed");
    positive(actualSoundSpeed, "Actual sound speed");
    positive(referenceDensity, "Reference density");
    positive(actualDensity, "Actual density");
    acousticRatio = referenceSoundSpeed / actualSoundSpeed;
    densityRatio = referenceDensity / actualDensity;
    positive(acousticRatio, "Acoustic ratio");
    positive(densityRatio, "Density ratio");
  }

  /**
   * Flashes detached systems and uses their EOS isentropic sound speeds and gas densities. Composition, temperature and
   * absolute pressure therefore enter through the EOS.
   *
   * @param reference reference suction fluid
   * @param actual actual suction fluid
   * @return detached conversion
   * @throws IllegalArgumentException for missing or nongaseous suction states
   */
  public static CompressorMapSimilarity fromFluids(SystemInterface reference, SystemInterface actual) {
    SystemInterface ref = gasState(reference);
    SystemInterface act = gasState(actual);
    return new CompressorMapSimilarity(ref.getPhase("gas").getSoundSpeed(), act.getPhase("gas").getSoundSpeed(),
        ref.getDensity("kg/m3"), act.getDensity("kg/m3"));
  }

  /**
   * Converts actual suction volume to reference-map volume at matched Mach number.
   *
   * @param actualFlow actual volume flow in m3/hr, not normal/standard volume
   * @return reference-coordinate volume flow in m3/hr
   */
  public double toReferenceVolumeFlow(double actualFlow) {
    nonNegative(actualFlow, "Volume flow");
    return actualFlow * acousticRatio;
  }

  /**
   * Converts mass flow using both acoustic and density ratios.
   *
   * @param actualMassFlow actual mass flow in kg/s
   * @return reference-coordinate mass flow in kg/s
   */
  public double toReferenceMassFlow(double actualMassFlow) {
    nonNegative(actualMassFlow, "Mass flow");
    return actualMassFlow * acousticRatio * densityRatio;
  }

  /**
   * Converts shaft speed to reference acoustic conditions.
   *
   * @param actualSpeed actual speed in rpm
   * @return reference-coordinate speed in rpm
   */
  public double toReferenceSpeed(double actualSpeed) {
    positive(actualSpeed, "Speed");
    return actualSpeed * acousticRatio;
  }

  /**
   * Converts specific head at equal head coefficient.
   *
   * @param actualHead actual specific head in kJ/kg
   * @return reference-coordinate specific head in kJ/kg
   */
  public double toReferenceHead(double actualHead) {
    nonNegative(actualHead, "Head");
    return actualHead * acousticRatio * acousticRatio;
  }

  /**
   * Scales a reference actual-volume stonewall estimate to actual gas conditions.
   *
   * @param referenceFlow reference stonewall flow in actual m3/hr
   * @return actual-condition screening flow in actual m3/hr
   */
  public double toActualVolumeFlow(double referenceFlow) {
    nonNegative(referenceFlow, "Reference volume flow");
    return referenceFlow / acousticRatio;
  }

  /**
   * Scales reference mass flow to actual gas conditions.
   *
   * @param referenceFlow reference mass flow in kg/s
   * @return actual mass flow in kg/s
   */
  public double toActualMassFlow(double referenceFlow) {
    nonNegative(referenceFlow, "Reference mass flow");
    return referenceFlow / acousticRatio / densityRatio;
  }

  /**
   * Prepares a detached, verified gas state.
   *
   * @param source supplied fluid
   * @return flashed clone with initialized properties
   * @throws IllegalArgumentException if the source is absent or not a single gas phase
   */
  private static SystemInterface gasState(SystemInterface source) {
    if (source == null) {
      throw new IllegalArgumentException("Suction fluid must not be null");
    }
    positive(source.getTemperature(), "Suction temperature");
    positive(source.getPressure(), "Suction pressure");
    SystemInterface clone = source.clone();
    clone.setMultiPhaseCheck(true);
    new ThermodynamicOperations(clone).TPflash();
    clone.initProperties();
    if (clone.getNumberOfPhases() != 1 || !clone.hasPhaseType("gas")) {
      throw new IllegalArgumentException("Similarity requires single-phase gas suction");
    }
    return clone;
  }

  /**
   * Checks a strictly positive finite scalar.
   *
   * @param value scalar
   * @param name diagnostic name
   * @throws IllegalArgumentException if invalid
   */
  private static void positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  /**
   * Checks a finite nonnegative scalar.
   *
   * @param value scalar
   * @param name diagnostic name
   * @throws IllegalArgumentException if invalid
   */
  private static void nonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
  }
}
