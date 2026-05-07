package neqsim.process.mechanicaldesign.separator.pressuredrop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.entrainment.InletDeviceModel;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link SeparatorPressureDropCalculator} and the
 * {@code Separator.setEnhancedPressureDropCalculation(boolean)} integration.
 *
 * @author NeqSim
 * @version 1.0
 */
public class SeparatorPressureDropCalculatorTest {

  /**
   * Builds a small high-pressure dry-gas feed.
   *
   * @return a flashed stream at 30 bara, 25 C, 50 000 kg/hr
   */
  private Stream buildGasFeed() {
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.04);
    fluid.addComponent("propane", 0.01);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(30.0, "bara");
    feed.run();
    return feed;
  }

  @Test
  public void disabledByDefault_outletPressureUnchanged() {
    Stream feed = buildGasFeed();
    Separator sep = new Separator("V-100", feed);
    sep.run();
    double outletP = sep.getGasOutStream().getPressure();
    assertEquals(30.0, outletP, 1.0e-6,
        "With enhanced pressure drop disabled, outlet pressure should equal feed pressure");
    assertNull(sep.getLastPressureDropBreakdown(),
        "No breakdown should be produced when feature is disabled");
  }

  @Test
  public void noInletDevice_usesBordaCarnot() {
    Stream feed = buildGasFeed();
    Separator sep = new Separator("V-100", feed);
    SeparatorMechanicalDesign md = sep.getMechanicalDesign();
    md.calcDesign();
    // With no inlet device set, the calculator should pick Borda-Carnot
    sep.setEnhancedPressureDropCalculation(true);
    sep.run();
    PressureDropBreakdown bd = sep.getLastPressureDropBreakdown();
    assertNotNull(bd, "Breakdown should be populated");
    PressureDropContribution exp = bd.find("inletExpansion");
    assertNotNull(exp, "Inlet expansion contribution expected");
    assertEquals("Borda-Carnot", exp.getSource());
    assertTrue(exp.getDpPa() > 0.0, "Borda-Carnot dp must be positive");
  }

  @Test
  public void inletDevice_usesDeviceK() {
    Stream feed = buildGasFeed();
    Separator sep = new Separator("V-100", feed);
    sep.setInletDeviceType(InletDeviceModel.InletDeviceType.HALF_PIPE);
    sep.getMechanicalDesign().calcDesign();
    sep.setEnhancedPressureDropCalculation(true);
    sep.run();
    PressureDropBreakdown bd = sep.getLastPressureDropBreakdown();
    assertNotNull(bd);
    PressureDropContribution dev = bd.find("inletDevice");
    assertNotNull(dev, "When an inlet device is fitted, an inletDevice contribution is expected");
    assertEquals(InletDeviceModel.InletDeviceType.HALF_PIPE.getPressureDropCoefficient(),
        dev.getLossCoefficient(), 1.0e-9);
  }

  @Test
  public void outletContraction_defaultsToHalf() {
    Stream feed = buildGasFeed();
    Separator sep = new Separator("V-100", feed);
    sep.getMechanicalDesign().calcDesign();
    sep.setEnhancedPressureDropCalculation(true);
    sep.run();
    PressureDropBreakdown bd = sep.getLastPressureDropBreakdown();
    PressureDropContribution oc = bd.find("outletContraction");
    assertNotNull(oc);
    assertEquals(0.5, oc.getLossCoefficient(), 1.0e-9);
    assertEquals("default", oc.getSource());
  }

  @Test
  public void userOverride_inletExpansionK() {
    Stream feed = buildGasFeed();
    Separator sep = new Separator("V-100", feed);
    SeparatorMechanicalDesign md = sep.getMechanicalDesign();
    md.calcDesign();
    md.setInletExpansionLossCoefficient(1.234);
    sep.setEnhancedPressureDropCalculation(true);
    sep.run();
    PressureDropBreakdown bd = sep.getLastPressureDropBreakdown();
    PressureDropContribution exp = bd.find("inletExpansion");
    assertNotNull(exp);
    assertEquals(1.234, exp.getLossCoefficient(), 1.0e-12);
    assertEquals("user", exp.getSource());
  }

  @Test
  public void totalIsAppliedToOutletPressure() {
    Stream feed = buildGasFeed();
    Separator sep = new Separator("V-100", feed);
    sep.getMechanicalDesign().calcDesign();
    sep.setEnhancedPressureDropCalculation(true);
    sep.run();
    double outletP = sep.getGasOutStream().getPressure();
    PressureDropBreakdown bd = sep.getLastPressureDropBreakdown();
    assertEquals(30.0 - bd.getTotalBar(), outletP, 1.0e-6,
        "Outlet pressure must equal inlet minus total computed dp");
  }

  @Test
  public void scrubberWithMeshAndCyclones_addsBothContributions() {
    Stream feed = buildGasFeed();
    GasScrubber sc = new GasScrubber("V-200", feed);
    sc.run();
    GasScrubberMechanicalDesign md = sc.getMechanicalDesign();
    md.calcDesign();
    md.setMeshPad(md.getInnerDiameter() > 0
        ? Math.PI * md.getInnerDiameter() * md.getInnerDiameter() / 4.0 : 1.0, 150.0);
    md.setMistEliminatorDpCoeff(0.5);
    md.setDemistingCyclones(50, 0.05);
    sc.setEnhancedPressureDropCalculation(true);
    sc.run();
    PressureDropBreakdown bd = sc.getLastPressureDropBreakdown();
    assertNotNull(bd);
    assertNotNull(bd.find("mesh"), "mesh contribution expected on scrubber with mesh pad");
    assertNotNull(bd.find("cyclones"),
        "cyclones contribution expected on scrubber with demisting cyclones");
    // sum of contributions equals total
    double sum = 0.0;
    for (PressureDropContribution c : bd.getContributions()) {
      sum += c.getDpPa();
    }
    assertEquals(sum, bd.getTotalPa(), 1.0e-9);
  }

  @Test
  public void breakdownIsSerialisable() {
    Stream feed = buildGasFeed();
    Separator sep = new Separator("V-100", feed);
    sep.getMechanicalDesign().calcDesign();
    sep.setEnhancedPressureDropCalculation(true);
    sep.run();
    PressureDropBreakdown bd = sep.getLastPressureDropBreakdown();
    assertTrue(bd instanceof java.io.Serializable);
    for (PressureDropContribution c : bd.getContributions()) {
      assertTrue(c instanceof java.io.Serializable);
    }
  }
}
