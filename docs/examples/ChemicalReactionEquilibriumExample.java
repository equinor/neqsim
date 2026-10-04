import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Runs a single-phase water-gas-shift equilibrium calculation with the public reactive-flash API.
 */
public final class ChemicalReactionEquilibriumExample {
  private static final Logger logger =
      LogManager.getLogger(ChemicalReactionEquilibriumExample.class);

  private ChemicalReactionEquilibriumExample() {}

  /**
   * Builds an equimolar CO/water/CO2/hydrogen feed, solves equilibrium, and validates the result.
   *
   * @param args unused command-line arguments
   */
  public static void main(String[] args) {
    SystemInterface system = new SystemSrkEos(600.0, 1.0);
    system.addComponent("CO", 0.25);
    system.addComponent("water", 0.25);
    system.addComponent("CO2", 0.25);
    system.addComponent("hydrogen", 0.25);
    system.setMixingRule("classic");
    system.setMaxNumberOfPhases(1);
    system.setNumberOfPhases(1);
    system.init(0);
    system.init(1);

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.reactiveTPflash();

    double carbonMonoxide = system.getPhase(0).getComponent("CO").getx();
    double water = system.getPhase(0).getComponent("water").getx();
    double carbonDioxide = system.getPhase(0).getComponent("CO2").getx();
    double hydrogen = system.getPhase(0).getComponent("hydrogen").getx();

    requireValidEquilibrium(carbonMonoxide, water, carbonDioxide, hydrogen);
    logger.info(
        "WGS equilibrium at {} K and {} bar: xCO={}, xH2O={}, xCO2={}, xH2={}",
        system.getTemperature(),
        system.getPressure(),
        carbonMonoxide,
        water,
        carbonDioxide,
        hydrogen);
  }

  private static void requireValidEquilibrium(
      double carbonMonoxide, double water, double carbonDioxide, double hydrogen) {
    double[] moleFractions = {carbonMonoxide, water, carbonDioxide, hydrogen};
    for (double moleFraction : moleFractions) {
      if (!Double.isFinite(moleFraction) || moleFraction < 0.0 || moleFraction > 1.0) {
        throw new IllegalStateException("Reactive flash returned an invalid mole fraction");
      }
    }

    if (carbonDioxide <= 0.25
        || carbonMonoxide >= 0.25
        || Math.abs(carbonMonoxide + carbonDioxide - 0.50) > 0.01
        || Math.abs(water + hydrogen - 0.50) > 0.01) {
      throw new IllegalStateException(
          "Reactive flash did not satisfy the expected WGS direction and balances");
    }
  }
}
