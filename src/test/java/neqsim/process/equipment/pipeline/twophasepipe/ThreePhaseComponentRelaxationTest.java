package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Conservative equilibrium relaxation with depleted gas species and a separate aqueous inventory.
 *
 * @author NeqSim
 * @version 1.0
 */
class ThreePhaseComponentRelaxationTest {
  /**
   * Builds a three-phase equilibrium fluid.
   *
   * @return initialized wet gas/oil fluid
   */
  private SystemInterface fluid() {
    SystemInterface fluid = new SystemSrkEos(277.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("n-heptane", 0.15);
    fluid.addComponent("water", 0.05);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertTrue(fluid.hasPhaseType("gas") && fluid.hasPhaseType("oil") && fluid.hasPhaseType("aqueous"));
    return fluid;
  }

  /**
   * Builds one unit-length cell with all three inventories.
   *
   * @return the test grid
   */
  private TwoFluidSection[] cells() {
    TwoFluidSection section = new TwoFluidSection(0.0, 1.0, 0.2, 0.0);
    section.setPressure(50.0e5);
    section.setTemperature(277.15);
    section.setGasMassPerLength(1.0);
    section.setOilMassPerLength(1.0);
    section.setWaterMassPerLength(1.0);
    return new TwoFluidSection[] {section};
  }

  /**
   * A wet source must replenish depleted gas species rather than remove unavailable receiver-composition amounts.
   *
   * @throws Exception if the controlled inventory cannot be constructed
   */
  @Test
  void depletedGasSpeciesHaveConservativeNonnegativeSourceEndpoints() throws Exception {
    SystemInterface fluid = fluid();
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    Field field = TwoFluidComponentTransport.class.getDeclaredField("componentInventoryKg");
    field.setAccessible(true);
    double[][][] inventory = (double[][][]) field.get(transport);
    String[] names = transport.getComponentNames();
    int methane = Arrays.asList(names).indexOf("methane");
    for (String name : new String[] {"n-heptane", "water"}) {
      int component = Arrays.asList(names).indexOf(name);
      double swap = inventory[0][0][component] - 1.0e-16;
      assertTrue(swap > 0.0 && inventory[0][1][methane] > swap);
      inventory[0][0][component] -= swap;
      inventory[0][1][component] += swap;
      inventory[0][0][methane] += swap;
      inventory[0][1][methane] -= swap;
    }
    SystemInterface equilibrium = transport.createThermodynamicState(0, fluid, 50.0e5, 277.15);
    double[][][] before = transport.getInventoryKg();
    double tau = 2.0;
    double[][][] rates = transport.createEquilibriumRelaxationSourceRates(new SystemInterface[] {equilibrium}, cells,
        tau);
    double[][] phaseRates = new double[1][3];
    for (int component = 0; component < names.length; component++) {
      double componentRate = 0.0;
      double totalBefore = 0.0;
      double totalAfter = 0.0;
      for (int phase = 0; phase < 3; phase++) {
        double endpoint = before[0][phase][component] + tau * rates[0][phase][component];
        assertTrue(endpoint >= -1.0e-14, "source endpoint for " + names[component] + " in phase " + phase);
        componentRate += rates[0][phase][component];
        totalBefore += before[0][phase][component];
        totalAfter += endpoint;
        phaseRates[0][phase] += rates[0][phase][component];
      }
      assertEquals(0.0, componentRate, 1.0e-14);
      assertEquals(totalBefore, totalAfter, 1.0e-14);
    }
    for (String name : new String[] {"n-heptane", "water"}) {
      assertTrue(rates[0][0][Arrays.asList(names).indexOf(name)] > 0.0,
          "the depleted gas " + name + " inventory must gain that component");
    }
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeMassTransfer(true);
    equations.setComponentMassTransferSources(rates);
    phaseRates[0] = Arrays.copyOf(equations.calcSourceTerms(cells)[0], 3);
    cells[0].setGasMassPerLength(1.0 + tau * phaseRates[0][0]);
    cells[0].setOilMassPerLength(1.0 + tau * phaseRates[0][1]);
    cells[0].setWaterMassPerLength(1.0 + tau * phaseRates[0][2]);
    transport.advance(tau, new double[2][3], phaseRates, rates, new double[1], cells, fluid, fluid, 1.0e-10);
    assertTrue(transport.createReport(tau, 1, 1.0e-10).isConverged());
    double[][][] after = transport.getInventoryKg();
    for (int phase = 0; phase < 3; phase++) {
      for (int component = 0; component < names.length; component++) {
        assertEquals(before[0][phase][component] + tau * rates[0][phase][component], after[0][phase][component],
            1.0e-12);
      }
    }
  }

  /**
   * A local equilibrium remains a zero-driving-force control with all three phases present.
   */
  @Test
  void wetEquilibriumHasZeroSource() {
    SystemInterface fluid = fluid();
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    SystemInterface equilibrium = transport.createThermodynamicState(0, fluid, 50.0e5, 277.15);
    double[][][] rates = transport.createEquilibriumRelaxationSourceRates(new SystemInterface[] {equilibrium}, cells,
        2.0);
    for (double[] phase : rates[0]) {
      for (double rate : phase) {
        assertEquals(0.0, rate, 1.0e-10);
      }
    }
  }

  /**
   * Dissolved-component exchange between the liquids must not use an absent gas phase as a momentum intermediary.
   */
  @Test
  void liquidExchangeDoesNotCreateAbsentGasMomentum() {
    TwoFluidSection section = cells()[0];
    section.setGasMassPerLength(0.0);
    section.setGasVelocity(8.0);
    section.setOilVelocity(2.0);
    section.setWaterVelocity(-1.0);
    double[][] sources = {{0.0, 0.0}, {0.2, -0.1}, {-0.2, 0.1}};
    double[] momentum = TwoFluidConservationEquations.componentTransferMomentumSources(section, sources);
    assertEquals(0.0, momentum[0], 0.0);
    assertEquals(-0.4, momentum[1], 1.0e-14);
    assertEquals(0.4, momentum[2], 1.0e-14);
    assertEquals(0.0, momentum[0] + momentum[1] + momentum[2], 1.0e-14);
  }

  /**
   * Invalid relaxation inputs fail without mutating component inventories.
   */
  @Test
  void invalidInputsAreRejected() {
    SystemInterface fluid = fluid();
    TwoFluidSection[] cells = cells();
    TwoFluidComponentTransport transport = new TwoFluidComponentTransport(fluid, cells);
    for (double tau : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class,
          () -> transport.createEquilibriumRelaxationSourceRates(new SystemInterface[] {fluid}, cells, tau));
    }
    assertThrows(IllegalArgumentException.class,
        () -> transport.createEquilibriumRelaxationSourceRates(new SystemInterface[0], cells, 1.0));
  }
}
