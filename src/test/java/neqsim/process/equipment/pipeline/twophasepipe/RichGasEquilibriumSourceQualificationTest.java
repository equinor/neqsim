package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Source-only qualification using the public rich gas and a separate wet extension, not evaporation-rate data.
 *
 * @author NeqSim
 * @version 1.0
 */
class RichGasEquilibriumSourceQualificationTest {
  /**
   * Construct the public six-component rich gas, optionally with additional water.
   *
   * @param wet true for the separately declared wet extension
   * @return flashed gas/oil or gas/oil/aqueous state
   */
  private SystemInterface fluid(boolean wet) {
    SystemInterface f = new SystemSrkEos(277.15, 50.0);
    String[] names = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"};
    double[] amounts = {.916, .050, .020, .007, .003, .004};
    for (int i = 0; i < names.length; i++) {
      f.addComponent(names[i], amounts[i]);
    }
    if (wet) {
      f.addComponent("water", .02);
    }
    f.setMixingRule("classic");
    f.setMultiPhaseCheck(true);
    new ThermodynamicOperations(f).TPflash();
    f.initProperties();
    assertTrue(f.hasPhaseType("gas") && f.hasPhaseType("oil"));
    assertEquals(wet, f.hasPhaseType("aqueous"));
    return f;
  }

  /**
   * A full relaxation interval removes every liquid species at an imposed hot single-gas equilibrium. Nonunit cell
   * lengths and countercurrent velocities must not change species budgets or donor momentum.
   */
  @Test
  void richGasAndWetExtensionReachSingleGasSourceEndpointConservatively() {
    for (boolean wet : new boolean[] {false, true}) {
      for (double length : new double[] {.75, 5000.0 / 24.0}) {
        for (double temperature : new double[] {650.0, 700.0}) {
          SystemInterface f = fluid(wet);
          TwoFluidSection cell = new TwoFluidSection(0.0, length, 1.0148, 0.0);
          cell.setTemperature(277.15);
          cell.setPressure(50.0e5);
          double[] mass = {2.0, 3.0, wet ? 1.0 : 0.0};
          double[] velocity = {-15.0, 3.0, -2.0};
          cell.setGasMassPerLength(mass[0]);
          cell.setOilMassPerLength(mass[1]);
          cell.setWaterMassPerLength(mass[2]);
          cell.setGasVelocity(velocity[0]);
          cell.setOilVelocity(velocity[1]);
          cell.setWaterVelocity(velocity[2]);
          TwoFluidSection[] cells = {cell};
          TwoFluidComponentTransport transport = new TwoFluidComponentTransport(f, cells);
          SystemInterface hot = transport.createThermodynamicState(0, f, 50.0e5, temperature);
          assertEquals(1, hot.getNumberOfPhases());
          assertTrue(hot.hasPhaseType("gas"));
          double tau = 30.0;
          double[][] inventory = transport.getInventoryKg()[0];
          double[][][] rates = transport.createEquilibriumRelaxationSourceRates(new SystemInterface[] {hot}, cells,
              tau);
          String[] names = transport.getComponentNames();
          for (int component = 0; component < names.length; component++) {
            double total = inventory[0][component] + inventory[1][component] + inventory[2][component];
            assertEquals(0.0, rates[0][0][component] + rates[0][1][component] + rates[0][2][component], 1e-14);
            for (int phase = 0; phase < 3; phase++) {
              double endpoint = inventory[phase][component] + tau * length * rates[0][phase][component];
              assertEquals(phase == 0 ? total : 0.0, endpoint, 1e-12, names[component]);
            }
          }
          double[] momentumRate = TwoFluidConservationEquations.componentTransferMomentumSources(cell, rates[0]);
          double totalMomentum = mass[0] * velocity[0] + mass[1] * velocity[1] + mass[2] * velocity[2];
          assertEquals(0.0, momentumRate[0] + momentumRate[1] + momentumRate[2], 1e-13);
          assertEquals(totalMomentum, mass[0] * velocity[0] + tau * momentumRate[0], 1e-12);
          assertEquals(0.0, mass[1] * velocity[1] + tau * momentumRate[1], 1e-12);
          assertEquals(0.0, mass[2] * velocity[2] + tau * momentumRate[2], 1e-12);
          double initialKinetic = .5 * (mass[0] * velocity[0] * velocity[0] + mass[1] * velocity[1] * velocity[1]
              + mass[2] * velocity[2] * velocity[2]);
          double finalKinetic = .5 * totalMomentum * totalMomentum / (mass[0] + mass[1] + mass[2]);
          assertTrue(finalKinetic < initialKinetic);
          TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
          equations.setIncludeMassTransfer(true);
          equations.setIncludeEnergyEquation(true);
          equations.setConservativeFrictionEnabled(true);
          equations.setComponentMassTransferSources(rates);
          double[] source = equations.calcSourceTerms(cells)[0];
          assertEquals(0.0, source[6], 0.0, "internal transfer cannot add total energy");
          for (int phase = 0; phase < 3; phase++) {
            assertEquals(momentumRate[phase], source[phase + 3], 1e-12);
          }
        }
      }
    }
  }

  /** Opposing species transfer carries donor momentum even when every phase has zero net mass transfer. */
  @Test
  void cyclicSpeciesExchangeDissipatesKineticEnergyAtZeroPhaseMassSource() {
    TwoFluidSection cell = new TwoFluidSection(0.0, 1.0, .2, 0.0);
    cell.setGasMassPerLength(1.0);
    cell.setOilMassPerLength(1.0);
    cell.setWaterMassPerLength(1.0);
    cell.setGasVelocity(-12.0);
    cell.setOilVelocity(4.0);
    cell.setWaterVelocity(8.0);
    double[][] rates = {{-.2, .2, 0.0}, {.2, 0.0, -.2}, {0.0, -.2, .2}};
    double[] source = TwoFluidConservationEquations.componentTransferMomentumSources(cell, rates);
    assertEquals(4.0, source[0], 1e-14);
    assertEquals(-3.2, source[1], 1e-14);
    assertEquals(-.8, source[2], 1e-14);
    double[] velocity = {-12.0, 4.0, 8.0};
    double initial = 0.0;
    double endpoint = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(0.0, rates[phase][0] + rates[phase][1] + rates[phase][2], 0.0);
      initial += .5 * velocity[phase] * velocity[phase];
      double newMomentum = velocity[phase] + .5 * source[phase];
      endpoint += .5 * newMomentum * newMomentum;
    }
    assertEquals(112.0, initial, 1e-12);
    assertEquals(81.76, endpoint, 1e-12);
    assertTrue(endpoint < initial);
    assertEquals(0.0, source[0] + source[1] + source[2], 1e-14);
  }
}
