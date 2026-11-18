package neqsim.thermo.system;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseUMRCPA;

/**
 * Thermodynamic system using the fused UMR-CPA equation of state.
 *
 * <p>
 * This model combines the Peng-Robinson physical term with the Universal Mixing Rule (UMR) driven by UNIFAC
 * group-contribution activity coefficients, the 3-parameter Mathias-Copeman alpha function (attractive term 13,
 * {@code _umrmc} group-interaction tables), and a CPA association term for self- and cross-associating compounds
 * (water, glycols, alcohols). The association contribution is added to the Helmholtz energy in
 * {@link PhaseUMRCPA#getF()}.
 * </p>
 *
 * <p>
 * The pressure is the sum of a physical PR contribution and an association contribution: <i>P = P<sub>PR</sub>(UMR
 * mixing, MC alpha) + P<sub>assoc</sub>(CPA)</i>. The structure follows the group-contribution association model used
 * for natural-gas dehydration with triethylene glycol. Pure-component CPA parameters and group-interaction parameters
 * are read from the NeqSim component database; reproducing a specific published parameter set requires loading the
 * matching regressed parameters.
 * </p>
 *
 * @author Even Solbraa
 */
public class SystemUMRCPAEoS extends SystemPrEos {
  /** Canonical system name used by the model switch and mixing-rule selection. */
  static final String MODEL_NAME = "UMR-CPA";
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemUMRCPAEoS.
   */
  public SystemUMRCPAEoS() {
    this(298.15, 1.0);
  }

  /**
   * Constructor for SystemUMRCPAEoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemUMRCPAEoS(double T, double P) {
    super(T, P);
    // Engage the co-volume combining rule used by the UMR-PRU family.
    setBmixType(1);
    modelName = MODEL_NAME;
    // Attractive term 13 selects the 3-parameter Mathias-Copeman alpha and the _umrmc
    // group-interaction tables, matching the UMR-PRU-MC physical term.
    attractiveTermNumber = 13;

    // Recreates phases created in super constructor SystemPrEos.
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseUMRCPA();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(true);
  }

  /**
   * Add a previously characterized cut without refitting its critical properties under the target EOS.
   *
   * @param source source TBP or plus fraction
   */
  void addCharacterizedCut(ComponentInterface source) {
    addTBPfraction(source.getComponentName(), source.getNumberOfmoles(), source.getMolarMass(),
        source.getNormalLiquidDensity(), source.getTC(), source.getPC(), source.getAcentricFactor());
    int index = getNumberOfComponents() - 1;
    String assignedName = getComponent(index).getComponentName();
    if (!source.getComponentName().equals(assignedName)) {
      renameComponent(assignedName, source.getComponentName());
    }
    for (int phase = 0; phase < getMaxNumberOfPhases(); phase++) {
      ComponentInterface cut = getPhase(phase).getComponent(index);
      cut.setNormalBoilingPoint(source.getNormalBoilingPoint());
      cut.setCriticalVolume(source.getCriticalVolume());
      cut.setCriticalViscosity(source.getCriticalViscosity());
      cut.setParachorParameter(source.getParachorParameter());
      cut.setTriplePointTemperature(source.getTriplePointTemperature());
      cut.setHeatOfFusion(source.getHeatOfFusion());
      cut.setCpA(source.getCpA());
      cut.setCpB(source.getCpB());
      cut.setCpC(source.getCpC());
      cut.setCpD(source.getCpD());
      cut.setCpE(source.getCpE());
      if (source.isIsPlusFraction()) {
        cut.setIsPlusFraction(true);
      }
    }
  }
}
