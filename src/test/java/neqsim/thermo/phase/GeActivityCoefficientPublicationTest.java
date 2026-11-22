package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.thermo.component.ComponentGEInterface;
import neqsim.thermo.component.ComponentGEUniquac;
import neqsim.thermo.component.ComponentGEUniquacmodifiedHV;
import neqsim.thermo.component.ComponentGEWilson;
import neqsim.thermo.mixingrule.EosMixingRuleHandler;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUEos;

/** Regression coverage for activity-coefficient publication and unsupported UNIQUAC entry points. */
class GeActivityCoefficientPublicationTest extends NeqSimTest {
  private PhaseGEWilson wilsonPhase(String first, String second) {
    SystemGEWilson system = new SystemGEWilson(298.15, 1.0);
    system.addComponent(first, 0.5);
    system.addComponent(second, 0.5);
    system.setMixingRule("classic");
    system.init(0);
    return (PhaseGEWilson) system.getPhase(1);
  }

  @Test
  void wilsonPublishesSolventAndHenryReferenceCoefficients() {
    PhaseGEWilson phase = wilsonPhase("n-butane", "water");
    double excess = phase.getExcessGibbsEnergy(phase, 2, 298.15, 1.0, PhaseType.LIQUID);
    assertTrue(Double.isFinite(excess));
    for (int i = 0; i < 2; i++) {
      ComponentGEWilson component = (ComponentGEWilson) phase.getComponent(i);
      assertTrue(Double.isFinite(component.getGamma()) && component.getGamma() > 0.0);
      assertEquals(Math.log(component.getGamma()), component.getLnGamma(), 1e-12);
      double phi = component.fugcoef(phase);
      assertTrue(Double.isFinite(phi) && phi > 0.0);
      assertEquals(phi, component.getFugacityCoefficient(), 0.0);
    }
    ComponentGEWilson water = (ComponentGEWilson) phase.getComponent(1);
    assertEquals(water.getGamma() * water.getAntoineVaporPressure(298.15), water.fugcoef(phase), 1e-12);
    double infiniteDilution = phase.getActivityCoefficientInfDilWater(0, 1);
    assertTrue(Double.isFinite(infiniteDilution) && infiniteDilution > 0.0);
  }

  @Test
  void wilsonOverloadsPublishAnalyticalBinaryCoefficientsAndRefreshState() {
    PhaseGEWilson phase = wilsonPhase("methanol", "water");
    for (int i = 0; i < 2; i++) {
      phase.getcomponentArray()[i] = new ComponentGEWilson(phase.getComponent(i).getName(), 0.5, 0.5, i) {
        private static final long serialVersionUID = 1L;

        @Override
        public double getCharEnergyParamter(PhaseInterface mixture, int first, int second) {
          return first == second ? 1.0 : first == 0 ? 2.0 : 0.5;
        }
      };
      phase.getComponent(i).setx(0.5);
    }
    ComponentGEWilson first = (ComponentGEWilson) phase.getComponent(0);
    ComponentGEWilson second = (ComponentGEWilson) phase.getComponent(1);
    double expectedFirst = Math.exp(1.0 / 3.0) / 1.5;
    double expectedSecond = Math.exp(-1.0 / 3.0) / 0.75;
    assertEquals(expectedFirst, first.getWilsonActivityCoefficient(phase), 1e-12);
    assertEquals(expectedFirst, first.getGamma(), 1e-12);
    assertEquals(expectedSecond, second.getGamma(phase, 2, 298.15, 1.0, PhaseType.LIQUID), 1e-12);
    assertEquals(expectedSecond, second.getGamma(), 1e-12);
    assertEquals(expectedFirst, first.getGamma(phase, 2, 298.15, 1.0, PhaseType.LIQUID, null, null, null, null), 1e-12);
    assertEquals(Math.log(expectedFirst), first.getLnGamma(), 1e-12);

    PhaseGEWilson copy = (PhaseGEWilson) phase.clone();
    copy.getComponent(0).setx(1.0);
    // setx only assigns positive mole fractions; use a trace component for the pure-component limit.
    copy.getComponent(1).setx(1e-20);
    copy.getExcessGibbsEnergy(copy, 2, 298.15, 1.0, PhaseType.LIQUID);
    assertEquals(1.0, ((ComponentGEInterface) copy.getComponent(0)).getGamma(), 1e-12);
    assertEquals(0.0, copy.getExcessGibbsEnergy(), 1e-12);
    assertNotEquals(first.getGamma(), ((ComponentGEInterface) copy.getComponent(0)).getGamma());
    assertEquals(expectedFirst, first.getGamma(), 1e-12);
  }

  @Test
  void bareUniquacRejectsUseBeforePublishingInvalidResults() {
    assertUnsupported(() -> new PhaseGEUniquac());
    assertUnsupported(() -> new ComponentGEUniquac("methanol", 0.5, 0.5, 0));
    assertUnsupported(() -> new PhaseGEUniquacmodifiedHV());
    PhaseGEWilson phase = wilsonPhase("methanol", "water");
    assertUnsupported(
        () -> new PhaseGEUniquac(phase, new double[2][2], new double[2][2], new String[2][2], new double[2][2]));
    ComponentGEUniquacmodifiedHV component = new ComponentGEUniquacmodifiedHV("water", 1.0, 1.0, 0) {
      private static final long serialVersionUID = 1L;
    };
    assertUnsupported(() -> component.getGamma(phase, 2, 298.15, 1.0, PhaseType.LIQUID));
    assertUnsupported(() -> component.getGamma(phase, 2, 298.15, 1.0, PhaseType.LIQUID, null, null, null, null));
  }

  @Test
  void uniquacSelectorsNeverSilentlyRunNrtl() {
    SystemSrkEos system = new SystemSrkEos(298.15, 1.0);
    system.addComponent("methanol", 0.5);
    system.addComponent("water", 0.5);
    PhaseInterface phase = system.getPhase(0);
    for (String model : new String[] {"UNIQUAQ", "UNIQUAC"}) {
      EosMixingRuleHandler handler = new EosMixingRuleHandler();
      handler.setMixingRuleGEModel(model);
      assertUnsupported(() -> handler.new SRKHuronVidal2(phase, new double[2][2], new double[2][2], new String[2][2]));
      assertUnsupported(() -> handler.new SRKHuronVidal2(phase, new double[2][2], new double[2][2], new double[2][2],
          new String[2][2]));
    }
  }

  @Test
  void supportedUnifacSubclassStillCalculatesActivityCoefficients() {
    SystemUMRPRUEos system = new SystemUMRPRUEos(298.15, 1.0);
    system.addComponent("methanol", 0.5);
    system.addComponent("water", 0.5);
    system.setMixingRule("HV", "UNIFAC_UMRPRU");
    system.init(0);
    system.init(1);
    for (int i = 0; i < 2; i++) {
      double phi = system.getPhase(1).getComponent(i).getFugacityCoefficient();
      assertTrue(Double.isFinite(phi) && phi > 0.0);
    }
  }

  private void assertUnsupported(org.junit.jupiter.api.function.Executable calculation) {
    UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, calculation);
    assertTrue(exception.getMessage().contains("UNIQUAC"));
  }
}
