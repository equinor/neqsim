package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe.BoundaryCondition;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for executable examples in transient_multiphase_pipe.md.
 */
class TransientPipeDocumentationTest {
  @Test
  void explicitOutletPressureExamplePreservesReceivingPressure() {
    SystemInterface fluid = new SystemSrkEos(300.0, 80.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-pentane", 0.2);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("documented inlet", fluid);
    inlet.setFlowRate(5.0, "kg/sec");
    inlet.run();

    TransientPipe pipe = new TransientPipe("documented pipe", inlet);
    pipe.setLength(200.0);
    pipe.setDiameter(0.15);
    pipe.setNumberOfSections(10);
    pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
    pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);

    double specifiedOutletPressure = 30.0;
    pipe.setOutletPressure(specifiedOutletPressure);
    pipe.runTransient(0.0, UUID.randomUUID());

    double[] pressure = pipe.getPressureProfile();
    double initializedOutletPressure = pressure[pressure.length - 1] / 1.0e5;
    assertEquals(specifiedOutletPressure, initializedOutletPressure, 1.0e-6);
  }

  @Test
  void accumulationZoneExampleUsesCurrentPublicFields() {
    TransientPipe pipe = new TransientPipe("documented terrain pipe");
    pipe.setLength(900.0);
    pipe.setDiameter(0.20);
    pipe.setNumberOfSections(9);
    pipe.setElevationProfile(new double[] { 0.0, -2.0, -4.0, -6.0, -4.0, -2.0, 0.0, 0.0, 0.0 });
    pipe.initializePipe();

    LiquidAccumulationTracker tracker = pipe.getAccumulationTracker();
    assertFalse(tracker.getAccumulationZones().isEmpty());

    LiquidAccumulationTracker.AccumulationZone zone = tracker.getAccumulationZones().get(0);
    assertTrue(zone.startPosition >= 0.0);
    assertTrue(zone.endPosition > zone.startPosition);
    assertTrue(zone.maxVolume > 0.0);
    assertEquals(0.0, zone.liquidVolume, 1.0e-12);
    assertFalse(zone.isOverflowing);
  }
}
