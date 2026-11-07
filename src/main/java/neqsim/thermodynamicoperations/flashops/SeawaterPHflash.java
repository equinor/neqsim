package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSeawater;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Bounded single-liquid PH flash for fixed-salinity cooling utilities, without a fictitious salt vapor phase.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SeawaterPHflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Specified total enthalpy [J]. */
  private final double specifiedEnthalpy;

  /**
   * Creates a PH calculation on the bulk-solution mass basis.
   *
   * @param system fixed-salinity seawater system
   * @param specifiedEnthalpy total enthalpy [J]
   */
  public SeawaterPHflash(SystemSeawater system, double specifiedEnthalpy) {
    this.system = system;
    this.specifiedEnthalpy = specifiedEnthalpy;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (!Double.isFinite(specifiedEnthalpy)) {
      throw new IllegalArgumentException("Specified seawater enthalpy must be finite");
    }
    if (system.getTotalNumberOfMoles() == 0.0) {
      if (specifiedEnthalpy != 0.0) {
        throw new IllegalArgumentException("An empty system cannot carry nonzero enthalpy");
      }
      return;
    }
    SystemInterface trial = system.clone();
    ThermodynamicOperations operations = new ThermodynamicOperations(trial);
    double low = 273.15;
    double high = 353.0;
    trial.setTemperature(low);
    operations.TPflash();
    double lowEnthalpy = trial.getEnthalpy();
    trial.setTemperature(high);
    operations.TPflash();
    double highEnthalpy = trial.getEnthalpy();
    double tolerance = Math.max(1e-7, Math.max(Math.abs(lowEnthalpy), Math.abs(highEnthalpy)) * 1e-11);
    if (specifiedEnthalpy < lowEnthalpy - tolerance || specifiedEnthalpy > highEnthalpy + tolerance) {
      throw new IllegalArgumentException("Specified enthalpy requires seawater outside 273.15-353 K");
    }
    double temperature = Math.max(low, Math.min(high, system.getTemperature()));
    for (int iteration = 0; iteration < 60; iteration++) {
      trial.setTemperature(temperature);
      operations.TPflash();
      double residual = trial.getEnthalpy() - specifiedEnthalpy;
      if (Math.abs(residual) <= tolerance) {
        system.setTemperature(temperature);
        new ThermodynamicOperations(system).TPflash();
        return;
      }
      if (residual > 0.0) {
        high = temperature;
      } else {
        low = temperature;
      }
      double candidate = temperature - residual / trial.getCp();
      temperature = Double.isFinite(candidate) && candidate > low && candidate < high ? candidate : 0.5 * (low + high);
    }
    throw new IllegalStateException("Seawater PH flash did not converge within its liquid cooling envelope");
  }
}
