package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

class UpstreamCompressibleVolumeTest {
  @Test
  void equalSourceAndWithdrawalLeaveTheStateInvariant() {
    UpstreamCompressibleVolume volume = createVolume(100.0);
    volume.setSourceMassFlowRates(10.0, 1.0, 0.5);
    double initialPressure = volume.getPressurePa();
    double initialMass = volume.getTotalMassKg();

    volume.advance(2.0, new double[] { 20.0, 2.0, 1.0 });

    assertEquals(initialPressure, volume.getPressurePa(), 0.0);
    assertEquals(initialMass, volume.getTotalMassKg(), 0.0);
    assertTrue(volume.getMaximumRelativeVolumeResidual() <= 1.0e-10);
  }

  @Test
  void sourceSurplusRaisesPressureAndClosesMassAndVolume() {
    UpstreamCompressibleVolume volume = createVolume(100.0);
    volume.setSourceMassFlowRates(10.0, 1.0, 0.0);
    double initialPressure = volume.getPressurePa();
    double initialMass = volume.getTotalMassKg();

    volume.advance(1.0, new double[] { 9.0, 1.0, 0.0 });

    assertTrue(volume.getPressurePa() > initialPressure);
    assertEquals(initialMass + 1.0, volume.getTotalMassKg(), 1.0e-10);
    assertEquals(11.0, volume.getCumulativeSourceMassKg(), 0.0);
    assertEquals(10.0, volume.getCumulativeWithdrawalMassKg(), 0.0);
    assertTrue(volume.getMaximumRelativeVolumeResidual() <= 1.0e-10);
  }

  @Test
  void phaseReturnFromPipeAddsInventory() {
    UpstreamCompressibleVolume volume = createVolume(100.0);
    double initialOilMass = volume.getPhaseMassKg(1);

    volume.advance(1.0, new double[] { 0.0, -0.25, 0.0 });

    assertEquals(initialOilMass + 0.25, volume.getPhaseMassKg(1), 1.0e-12);
    assertTrue(volume.getPressurePa() > 60.0e5);
  }

  @Test
  void pipeUsesAcceptedPhaseFluxToAdvanceConnectedVolume() {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(2.0, "kg/sec");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("volume-coupled pipe", feed);
    pipe.setLength(100.0);
    pipe.setDiameter(0.30);
    pipe.setNumberOfSections(5);
    pipe.setElevationProfile(new double[5]);
    pipe.setOutletPressure(50.0, "bara");
    pipe.run();

    UpstreamCompressibleVolume volume = pipe.initializeUpstreamCompressibleVolume(1.0e5);
    double initialMass = volume.getTotalMassKg();
    pipe.runTransient(0.01, UUID.randomUUID());

    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    double transferredMass = report.getInletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL);
    assertTrue(transferredMass > 0.0);
    assertEquals(initialMass - transferredMass, volume.getTotalMassKg(), 1.0e-8);
    assertEquals(volume.getPressurePa(), pipe.getInletPressure() * 1.0e5, 0.1);
  }

  @Test
  void phaseDepletionFailsLoudly() {
    UpstreamCompressibleVolume volume = createVolume(1.0);
    assertThrows(IllegalStateException.class, () -> volume.advance(1.0, new double[] { 1.0e6, 0.0, 0.0 }));
  }

  private static UpstreamCompressibleVolume createVolume(double volumeM3) {
    double[] density = { 50.0, 700.0, 1000.0 };
    double[] soundSpeed = { 350.0, 1200.0, 1450.0 };
    double[] mass = { 0.90 * volumeM3 * density[0], 0.08 * volumeM3 * density[1], 0.02 * volumeM3 * density[2] };
    return new UpstreamCompressibleVolume(volumeM3, 60.0e5, mass, density, soundSpeed);
  }
}
