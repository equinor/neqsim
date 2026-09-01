package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/** Regression coverage for the complete GOSP tutorial example. */
class GospTutorialDocumentationTest extends NeqSimTest {
  @Test
  void threeStageTutorialClosesMaterialBalanceAndCalculatesVpcr4() {
    SystemInterface wellFluid = createWellFluid();
    Stream feed = new Stream("well stream", wellFluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(80.0, "C");
    feed.setPressure(50.0, "bara");

    ThreePhaseSeparator hpSeparator = new ThreePhaseSeparator("HP separator", feed);
    ThrottlingValve mpValve = new ThrottlingValve("MP valve", hpSeparator.getOilOutStream());
    mpValve.setOutletPressure(10.0, "bara");
    ThreePhaseSeparator mpSeparator = new ThreePhaseSeparator("MP separator", mpValve.getOutletStream());
    ThrottlingValve lpValve = new ThrottlingValve("LP valve", mpSeparator.getOilOutStream());
    lpValve.setOutletPressure(2.0, "bara");
    ThreePhaseSeparator lpSeparator = new ThreePhaseSeparator("LP separator", lpValve.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(hpSeparator);
    process.add(mpValve);
    process.add(mpSeparator);
    process.add(lpValve);
    process.add(lpSeparator);
    process.run();

    double gasMassFlow = massFlow(hpSeparator.getGasOutStream()) + massFlow(mpSeparator.getGasOutStream())
        + massFlow(lpSeparator.getGasOutStream());
    double waterMassFlow = massFlow(hpSeparator.getWaterOutStream()) + massFlow(mpSeparator.getWaterOutStream())
        + massFlow(lpSeparator.getWaterOutStream());
    StreamInterface exportOil = lpSeparator.getOilOutStream();
    double oilMassFlow = massFlow(exportOil);
    double feedMassFlow = massFlow(feed);
    double recoveredMassFlow = gasMassFlow + waterMassFlow + oilMassFlow;
    double relativeMassBalanceError = Math.abs(recoveredMassFlow - feedMassFlow) / feedMassFlow;
    double vpcr4Bara = exportOil.getRVP(37.8, "C", "bara");

    assertEquals(50000.0, feedMassFlow, 1.0e-6);
    assertTrue(gasMassFlow > 0.0);
    assertTrue(waterMassFlow > 0.0);
    assertTrue(oilMassFlow > 0.0);
    assertTrue(relativeMassBalanceError <= 1.0e-3);
    assertTrue(Double.isFinite(vpcr4Bara));
    assertTrue(vpcr4Bara > 0.0);
  }

  private static double massFlow(StreamInterface stream) {
    return stream.getFlowRate("kg/hr");
  }

  private static SystemInterface createWellFluid() {
    SystemInterface wellFluid = new SystemSrkCPAstatoil(353.15, 50.0);
    wellFluid.addComponent("nitrogen", 0.005);
    wellFluid.addComponent("CO2", 0.020);
    wellFluid.addComponent("methane", 0.350);
    wellFluid.addComponent("ethane", 0.080);
    wellFluid.addComponent("propane", 0.060);
    wellFluid.addComponent("i-butane", 0.020);
    wellFluid.addComponent("n-butane", 0.030);
    wellFluid.addComponent("i-pentane", 0.015);
    wellFluid.addComponent("n-pentane", 0.020);
    wellFluid.addComponent("n-hexane", 0.025);
    wellFluid.addComponent("n-heptane", 0.040);
    wellFluid.addComponent("n-octane", 0.050);
    wellFluid.addComponent("n-nonane", 0.040);
    wellFluid.addComponent("nC10", 0.030);
    wellFluid.addTBPfraction("C11", 0.050, 0.150, 0.78);
    wellFluid.addTBPfraction("C15", 0.040, 0.210, 0.82);
    wellFluid.addTBPfraction("C20", 0.060, 0.350, 0.88);
    wellFluid.addComponent("water", 0.050);
    wellFluid.setMixingRule(10);
    wellFluid.setMultiPhaseCheck(true);
    return wellFluid;
  }
}
