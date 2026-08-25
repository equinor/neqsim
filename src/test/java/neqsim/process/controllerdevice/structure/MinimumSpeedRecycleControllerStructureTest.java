package neqsim.process.controllerdevice.structure;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;

/**
 * Tests coordinated pressure control across minimum compressor speed and recycle-valve demand.
 *
 * @author NeqSim
 * @version 1.0
 */
class MinimumSpeedRecycleControllerStructureTest {

  /** Controller stub with a directly configurable response. */
  private static class StubController extends ControllerDeviceBaseClass {
    private static final long serialVersionUID = 1L;
    private double fixedResponse;

    /**
     * Creates a controller stub.
     *
     * @param name controller name
     */
    StubController(String name) {
      super(name);
    }

    /**
     * Sets the fixed controller response.
     *
     * @param fixedResponse response in percent
     */
    void setFixedResponse(double fixedResponse) {
      this.fixedResponse = fixedResponse;
    }

    /**
     * Leaves the configured response unchanged for the transient step.
     *
     * @param initResponse initial response in percent
     * @param dt time step in seconds
     * @param id calculation identifier
     */
    @Override
    public void runTransient(double initResponse, double dt, UUID id) {
      // Fixed response is controlled by the test.
    }

    /**
     * Gets the configured fixed response.
     *
     * @return fixed response in percent
     */
    @Override
    public double getResponse() {
      return fixedResponse;
    }
  }

  /**
   * Verifies the documented five-point speed and inverse recycle-addition mapping.
   */
  @Test
  void mapsPressureOutputAcrossMinimumSpeedTransition() {
    StubController pressure = new StubController("pressure");
    StubController antiSurge = new StubController("anti-surge");
    StubController suctionPressure = new StubController("suction-pressure");
    MinimumSpeedRecycleControllerStructure structure = new MinimumSpeedRecycleControllerStructure(pressure, antiSurge,
        suctionPressure, 75.0, 100.0, 75.0, 100.0);

    structure.update(100.0, 0.0, 0.0);
    Assertions.assertEquals(100.0, structure.getSpeedOutput(), 1.0e-12);
    Assertions.assertEquals(0.0, structure.getRecycleAddition(), 1.0e-12);

    structure.update(87.5, 0.0, 0.0);
    Assertions.assertEquals(87.5, structure.getSpeedOutput(), 1.0e-12);
    Assertions.assertEquals(0.0, structure.getRecycleAddition(), 1.0e-12);

    structure.update(75.0, 0.0, 0.0);
    Assertions.assertEquals(75.0, structure.getSpeedOutput(), 1.0e-12);
    Assertions.assertEquals(0.0, structure.getRecycleAddition(), 1.0e-12);

    structure.update(37.5, 0.0, 0.0);
    Assertions.assertEquals(75.0, structure.getSpeedOutput(), 1.0e-12);
    Assertions.assertEquals(50.0, structure.getRecycleAddition(), 1.0e-12);

    structure.update(0.0, 0.0, 0.0);
    Assertions.assertEquals(75.0, structure.getSpeedOutput(), 1.0e-12);
    Assertions.assertEquals(100.0, structure.getRecycleAddition(), 1.0e-12);
  }

  /**
   * Verifies latching, additive demand, high selection and unwind to the original command.
   */
  @Test
  void latchesSelectedRecycleCommandAndAllowsIndependentOverride() {
    StubController pressure = new StubController("pressure");
    StubController antiSurge = new StubController("anti-surge");
    StubController suctionPressure = new StubController("suction-pressure");
    MinimumSpeedRecycleControllerStructure structure = new MinimumSpeedRecycleControllerStructure(pressure, antiSurge,
        suctionPressure, 75.0, 100.0, 75.0, 100.0);

    structure.update(80.0, 20.0, 10.0);
    Assertions.assertEquals(20.0, structure.getOutput(), 1.0e-12);

    structure.update(37.5, 20.0, 10.0);
    Assertions.assertEquals(20.0, structure.getLatchedRecycleOutput(), 1.0e-12);
    Assertions.assertEquals(70.0, structure.getOutput(), 1.0e-12);
    Assertions.assertTrue(structure.isPressureRecycleDemandSelected());
    Assertions.assertEquals(14.8, structure.getMinimumPressureControllerOutput(), 1.0e-12);

    structure.update(37.5, 80.0, 10.0);
    Assertions.assertEquals(20.0, structure.getLatchedRecycleOutput(), 1.0e-12);
    Assertions.assertEquals(80.0, structure.getOutput(), 1.0e-12);
    Assertions.assertFalse(structure.isPressureRecycleDemandSelected());

    structure.update(75.0, 20.0, 10.0);
    Assertions.assertEquals(0.0, structure.getRecycleAddition(), 1.0e-12);
    Assertions.assertEquals(20.0, structure.getOutput(), 1.0e-12);
    Assertions.assertFalse(structure.isRecycleControlActive());
  }

  /**
   * Verifies that recycle saturation applies the documented dynamic pressure-output floor.
   */
  @Test
  void appliesDynamicMinimumPressureOutputAtRecycleSaturation() {
    StubController pressure = new StubController("pressure");
    StubController antiSurge = new StubController("anti-surge");
    StubController suctionPressure = new StubController("suction-pressure");
    MinimumSpeedRecycleControllerStructure structure = new MinimumSpeedRecycleControllerStructure(pressure, antiSurge,
        suctionPressure, 75.0, 100.0, 75.0, 100.0);

    structure.update(80.0, 20.0, 10.0);
    structure.update(0.0, 20.0, 10.0);

    Assertions.assertEquals(14.8, structure.getMinimumPressureControllerOutput(), 1.0e-12);
    Assertions.assertEquals(14.8, structure.getEffectivePressureControllerOutput(), 1.0e-12);
    Assertions.assertEquals(100.0, structure.getOutput(), 1.0e-12);
  }

  /**
   * Verifies a controller output range extending above 100 percent.
   */
  @Test
  void supportsExtendedMaximumSpeedOutput() {
    StubController pressure = new StubController("pressure");
    StubController antiSurge = new StubController("anti-surge");
    StubController suctionPressure = new StubController("suction-pressure");
    MinimumSpeedRecycleControllerStructure structure = new MinimumSpeedRecycleControllerStructure(pressure, antiSurge,
        suctionPressure, 75.0, 105.0, 75.0, 100.0);

    structure.update(90.0, 0.0, 0.0);
    Assertions.assertEquals(87.5, structure.getSpeedOutput(), 1.0e-12);
    structure.update(105.0, 0.0, 0.0);
    Assertions.assertEquals(100.0, structure.getSpeedOutput(), 1.0e-12);
  }

  /**
   * Verifies that leaving and re-entering recycle control refreshes the latched baseline.
   */
  @Test
  void refreshesLatchedRecycleCommandOnReentry() {
    StubController pressure = new StubController("pressure");
    StubController antiSurge = new StubController("anti-surge");
    StubController suctionPressure = new StubController("suction-pressure");
    MinimumSpeedRecycleControllerStructure structure = new MinimumSpeedRecycleControllerStructure(pressure, antiSurge,
        suctionPressure, 75.0, 100.0, 75.0, 100.0);

    structure.update(80.0, 20.0, 10.0);
    structure.update(50.0, 20.0, 10.0);
    Assertions.assertEquals(20.0, structure.getLatchedRecycleOutput(), 1.0e-12);

    structure.update(80.0, 35.0, 10.0);
    structure.update(50.0, 35.0, 10.0);

    Assertions.assertEquals(35.0, structure.getLatchedRecycleOutput(), 1.0e-12);
    Assertions.assertEquals(68.33333333333333, structure.getOutput(), 1.0e-12);
  }

  /**
   * Verifies that an inactive structure preserves its last coordinated outputs.
   */
  @Test
  void inactiveStructureDoesNotAdvanceOutputs() {
    StubController pressure = new StubController("pressure");
    StubController antiSurge = new StubController("anti-surge");
    StubController suctionPressure = new StubController("suction-pressure");
    MinimumSpeedRecycleControllerStructure structure = new MinimumSpeedRecycleControllerStructure(pressure, antiSurge,
        suctionPressure, 75.0, 100.0, 75.0, 100.0);

    pressure.setFixedResponse(80.0);
    antiSurge.setFixedResponse(20.0);
    suctionPressure.setFixedResponse(10.0);
    structure.runTransient(1.0);
    structure.setActive(false);

    pressure.setFixedResponse(25.0);
    antiSurge.setFixedResponse(90.0);
    structure.runTransient(1.0);

    Assertions.assertEquals(80.0, structure.getSpeedOutput(), 1.0e-12);
    Assertions.assertEquals(20.0, structure.getOutput(), 1.0e-12);
    Assertions.assertFalse(structure.isActive());
  }
}