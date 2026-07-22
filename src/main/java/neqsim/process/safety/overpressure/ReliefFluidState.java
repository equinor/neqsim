package neqsim.process.safety.overpressure;

import java.util.List;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Package-private immutable snapshot of the relieving-fluid properties evaluated at a given relief pressure and
 * temperature.
 *
 * <p>
 * The snapshot is produced by flashing a clone of a NeqSim fluid at the relieving conditions and reading the
 * specific-heat ratio, molar mass, compressibility, density and phase distribution. It is used by the TR3001
 * relief-load calculators so the same property-evaluation logic is shared and any flash failure degrades gracefully to
 * documented default values.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
final class ReliefFluidState {
  /** Specific-heat ratio Cp/Cv (dimensionless). */
  final double specificHeatRatio;
  /** Molar mass in kg/mol. */
  final double molarMassKgPerMol;
  /** Compressibility factor Z (dimensionless). */
  final double compressibility;
  /** Bulk density in kg/m^3. */
  final double densityKgPerM3;
  /** Relieving temperature in K. */
  final double temperatureK;
  /** Phase classification of the relieving stream. */
  final ReliefPhase phase;

  /**
   * Creates an immutable relieving-fluid property snapshot.
   *
   * @param specificHeatRatio specific-heat ratio Cp/Cv (dimensionless)
   * @param molarMassKgPerMol molar mass in kg/mol
   * @param compressibility compressibility factor Z (dimensionless)
   * @param densityKgPerM3 bulk density in kg/m^3
   * @param temperatureK relieving temperature in K
   * @param phase phase classification of the relieving stream
   */
  private ReliefFluidState(double specificHeatRatio, double molarMassKgPerMol, double compressibility,
      double densityKgPerM3, double temperatureK, ReliefPhase phase) {
    this.specificHeatRatio = specificHeatRatio;
    this.molarMassKgPerMol = molarMassKgPerMol;
    this.compressibility = compressibility;
    this.densityKgPerM3 = densityKgPerM3;
    this.temperatureK = temperatureK;
    this.phase = phase;
  }

  /**
   * Evaluates the relieving-fluid properties by flashing a clone of the supplied fluid at the given pressure and
   * temperature. Any failure falls back to documented light-hydrocarbon defaults and records a warning.
   *
   * @param fluid the NeqSim fluid to evaluate; not null
   * @param pressureBara the relieving pressure in bara; must be positive
   * @param temperatureK the relieving temperature in K; must be positive
   * @param warnings list to which warning messages are appended; not null
   * @return an immutable {@link ReliefFluidState}
   */
  static ReliefFluidState evaluate(SystemInterface fluid, double pressureBara, double temperatureK,
      List<String> warnings) {
    double k = 1.3;
    double mm = 0.020;
    double z = 1.0;
    double rho = 10.0;
    ReliefPhase phase = ReliefPhase.VAPOUR;
    try {
      SystemInterface state = fluid.clone();
      state.setPressure(pressureBara, "bara");
      state.setTemperature(temperatureK);
      new ThermodynamicOperations(state).TPflash();
      state.initProperties();

      double kappa = state.getKappa();
      if (!Double.isNaN(kappa) && kappa > 1.0) {
        k = kappa;
      } else {
        warnings.add("Fluid Cp/Cv non-physical at relief conditions; using default specific-heat ratio 1.3");
      }

      double molar = state.getMolarMass("kg/mol");
      if (!Double.isNaN(molar) && molar > 0.0) {
        mm = molar;
      } else {
        warnings.add("Fluid molar mass non-physical; using default 0.020 kg/mol");
      }

      try {
        double zz = state.getZ();
        if (!Double.isNaN(zz) && zz > 0.0) {
          z = zz;
        }
      } catch (Exception ex) {
        warnings.add("Failed to read compressibility factor (" + ex.getMessage() + "); using Z = 1.0");
      }

      try {
        double density = state.getDensity("kg/m3");
        if (!Double.isNaN(density) && density > 0.0) {
          rho = density;
        }
      } catch (Exception ex) {
        warnings.add("Failed to read density (" + ex.getMessage() + "); using 10 kg/m3");
      }

      phase = classifyPhase(state);
    } catch (Exception ex) {
      warnings.add("Relief-fluid flash failed (" + ex.getMessage() + "); using default vapour properties");
    }
    return new ReliefFluidState(k, mm, z, rho, temperatureK, phase);
  }

  /**
   * Classifies the phase of a flashed fluid state into vapour, liquid or two-phase.
   *
   * @param state the flashed NeqSim fluid state; not null
   * @return the {@link ReliefPhase} classification
   */
  private static ReliefPhase classifyPhase(SystemInterface state) {
    if (state.getNumberOfPhases() > 1) {
      return ReliefPhase.TWO_PHASE;
    }
    if (state.hasPhaseType("gas")) {
      return ReliefPhase.VAPOUR;
    }
    return ReliefPhase.LIQUID;
  }
}
