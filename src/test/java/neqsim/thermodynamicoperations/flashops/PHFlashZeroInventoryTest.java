package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Empty material streams have no caloric temperature constraint and must retain their temperature template.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PHFlashZeroInventoryTest {
  /**
   * A zero-flow tray must not feed an arbitrary near-zero temperature back into a flowing column.
   */
  @Test
  void emptyStreamRetainsTemperatureAndCanResumeFlow() {
    for (boolean pr : new boolean[] {false, true}) {
      SystemInterface fluid = createFluid(pr);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.setTotalNumberOfMoles(0.0);
      double temperature = fluid.getTemperature();
      double pressure = fluid.getPressure();
      new ThermodynamicOperations(fluid).PHflash(0.0);
      assertEquals(temperature, fluid.getTemperature(), 0.0);
      assertEquals(pressure, fluid.getPressure(), 0.0);
      assertEquals(0.0, fluid.getTotalNumberOfMoles(), 0.0);

      fluid.setTotalNumberOfMoles(1.0);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.init(2);
      double enthalpy = fluid.getEnthalpy();
      new ThermodynamicOperations(fluid).PHflash(enthalpy);
      assertEquals(temperature, fluid.getTemperature(), 1.0e-6);
      assertEquals(enthalpy, fluid.getEnthalpy(), 1.0e-5);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        assertEquals(pressure, ((PhaseEos) fluid.getPhase(phase)).calcPressure(), 1.0e-7);
      }
    }
  }

  /**
   * An empty material stream cannot carry a nonzero extensive enthalpy.
   */
  @Test
  void nonzeroEmptyStreamEnthalpyIsRejectedWithoutChangingTemperature() {
    SystemInterface fluid = createFluid(false);
    fluid.setTotalNumberOfMoles(0.0);
    assertThrows(IllegalArgumentException.class, () -> new ThermodynamicOperations(fluid).PHflash(1.0));
    assertEquals(243.15, fluid.getTemperature(), 0.0);
    assertEquals(0.0, fluid.getTotalNumberOfMoles(), 0.0);
  }

  /**
   * Pure-component dispatch and both PH algorithms must preserve the same empty-stream contract.
   */
  @Test
  void emptyPureStreamRetainsTemplateForBothAlgorithms() {
    for (boolean pr : new boolean[] {false, true}) {
      for (int type : new int[] {0, 1}) {
        SystemInterface fluid = pr ? new SystemPrEos(150.0, 10.0) : new SystemSrkEos(150.0, 10.0);
        fluid.addComponent("methane", 1.0);
        fluid.setMixingRule(2);
        new ThermodynamicOperations(fluid).TPflash();
        fluid.setTotalNumberOfMoles(0.0);
        new ThermodynamicOperations(fluid).PHflash(0.0, type);
        assertEquals(150.0, fluid.getTemperature(), 0.0);
        assertEquals(10.0, fluid.getPressure(), 0.0);
        assertEquals(0.0, fluid.getTotalNumberOfMoles(), 0.0);
      }
    }
  }

  /**
   * A small but nonzero inventory must still solve its caloric constraint.
   */
  @Test
  void smallFlowStillSolvesItsEnthalpyTarget() {
    for (double inventory : new double[] {1.0e-6, 1.0e-12}) {
      SystemInterface fluid = createFluid(false);
      fluid.setTotalNumberOfMoles(inventory);
      fluid.setTemperature(280.0);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.init(2);
      double enthalpy = fluid.getEnthalpy();
      fluid.setTemperature(260.0);
      new ThermodynamicOperations(fluid).PHflash(enthalpy);
      fluid.init(2);
      assertEquals(280.0, fluid.getTemperature(), 1.0e-6);
      assertEquals(enthalpy, fluid.getEnthalpy(), Math.abs(enthalpy) * 1.0e-8);
    }
  }

  /**
   * Zero total enthalpy is also a valid target for a flowing stream; its normalization must stay finite.
   */
  @Test
  void flowingZeroEnthalpyTargetHasFiniteResiduals() {
    SystemInterface fluid = createFluid(false);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    PHflash operation = new PHflash(fluid, 0.0, 0);
    assertTrue(Double.isFinite(operation.calcdQdT()));
    assertTrue(Double.isFinite(operation.calcdQdTT()));
    operation.run();
    fluid.init(2);
    assertTrue(Double.isFinite(fluid.getTemperature()) && fluid.getTemperature() > 0.0);
    assertEquals(0.0, fluid.getEnthalpy(), 1.0e-7);
  }

  /**
   * Construct a synthetic two-component column stream.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   * @return configured stream fluid
   */
  private static SystemInterface createFluid(boolean pr) {
    SystemInterface fluid = pr ? new SystemPrEos(243.15, 55.0) : new SystemSrkEos(243.15, 55.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("n-pentane", 0.1);
    fluid.setMixingRule(2);
    return fluid;
  }
}
