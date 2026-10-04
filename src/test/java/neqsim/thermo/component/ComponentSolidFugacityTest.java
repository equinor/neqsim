package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for the empirical solid reference routes (issue #3913). */
class ComponentSolidFugacityTest extends neqsim.NeqSimTest {
  private PhaseInterface solid() {
    SystemSrkEos fluid = new SystemSrkEos(190.0, 2.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule(2);
    fluid.setSolidPhaseCheck(true);
    PhaseInterface phase = fluid.getPhases()[3];
    phase.setTemperature(190.0);
    phase.setPressure(2.0);
    phase.getComponent(0).setx(1.0);
    return phase;
  }

  @Test
  void disabledComponentPublishesExclusionCoefficient() {
    PhaseInterface phase = solid();
    ComponentSolid component = (ComponentSolid) phase.getComponent(0);
    component.setSolidCheck(false);
    component.setFugacityCoefficient(0.01);
    assertEquals(1e30, component.fugcoef(phase), 0.0);
    assertEquals(1e30, component.getFugacityCoefficient(), 0.0);
  }

  @Test
  void selectingComponentEnablesCachedSolidAfterFluidOnlyFlash() {
    SystemSrkEos fluid = new SystemSrkEos(250.0, 2.0);
    fluid.addComponent("CO2", 0.1);
    fluid.addComponent("methane", 0.9);
    fluid.setMixingRule(2);
    new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid).TPflash();
    fluid.setSolidPhaseCheck("CO2");
    for (PhaseInterface phase : fluid.getPhases()) {
      if (phase != null) {
        assertTrue(phase.getComponent("CO2").doSolidCheck());
        assertTrue(!phase.getComponent("methane").doSolidCheck());
      }
    }
    fluid.setSolidPhaseCheck(false);
    fluid.setSolidPhaseCheck("CO2");
    assertTrue(fluid.getPhases()[3].getComponent("CO2").doSolidCheck());
  }

  @Test
  void liquidReferenceCoefficientDoesNotDivideByZeroComposition() {
    PhaseInterface phase = solid();
    ComponentSolid component = (ComponentSolid) phase.getComponent(0);
    component.setx(1.0);
    double expected = component.fugcoef2(phase);
    assertTrue(Double.isFinite(expected) && expected > 0.0);
    component.setx(0.0);
    assertEquals(expected, component.fugcoef2(phase), Math.abs(expected) * 1e-12);
    assertTrue(Double.isFinite(component.getFugacityCoefficient()));
  }

  @Test
  void vaporReferenceRetainsTabulatedDensityInPoyntingCorrection() {
    ComponentSolid component = (ComponentSolid) solid().getComponent(0);
    // Independent synthetic density: 44.01 g/mol * 30 mol/l = 1320.3 kg/m3.
    component.solidDensityCoefs = new double[] {30.0, 0.0, 0.0, 0.0, 0.0};
    double low = component.fugcoef(190.0, 2.0);
    double high = component.fugcoef(190.0, 10.0);
    double molarVolume = 1.0 / 30000.0;
    double expectedRatio = Math.exp(molarVolume * 8e5 / (component.R * 190.0));
    assertEquals(expectedRatio, high * 10.0 / (low * 2.0), 1e-12);
    assertEquals(component.getPureComponentSolidDensity(190.0), component.soldens, 1e-12);
  }

  @Test
  void vaporReferenceRejectsMissingCorrelationInsteadOfReusingPreviousValue() {
    ComponentSolid component = (ComponentSolid) solid().getComponent(0);
    component.fugcoef(190.0, 2.0);
    component.Hsub = 0.0;
    component.setAntoineASolid(0.0);
    assertThrows(IllegalStateException.class, () -> component.fugcoef(190.0, 2.0));
  }

  @Test
  void vaporReferenceRejectsStatesAboveTriplePoint() {
    ComponentSolid component = (ComponentSolid) solid().getComponent(0);
    assertThrows(IllegalArgumentException.class,
        () -> component.fugcoef(component.getTriplePointTemperature() + 1.0, 2.0));
  }

  @Test
  void phaseEntryPointUsesExplicitVaporReferenceAndRetainsDefault() {
    PhaseInterface phase = solid();
    ComponentSolid component = (ComponentSolid) phase.getComponent(0);
    double liquidReference = component.fugcoef2(phase);
    assertEquals(liquidReference, component.fugcoef(phase), 0.0);
    component.setUseSolidVaporPressure(true);
    assertTrue(component.isUseSolidVaporPressure());
    double vaporReference = component.fugcoef(190.0, 2.0);
    assertEquals(vaporReference, component.fugcoef(phase), 0.0);
    assertEquals(vaporReference, component.getFugacityCoefficient(), 0.0);
    component.setUseSolidVaporPressure(false);
    assertEquals(liquidReference, component.fugcoef(phase), 0.0);
  }

  @Test
  void specializedWaxModelsRejectIncompatibleSolidReferenceSelection() {
    for (ComponentSolid component : new ComponentSolid[] {new ComponentWax("methane", 1.0, 1.0, 0),
        new ComponentWonWax("methane", 1.0, 1.0, 0), new ComponentWaxWilson("methane", 1.0, 1.0, 0),
        new ComponentCoutinhoWax("methane", 1.0, 1.0, 0)}) {
      assertThrows(IllegalArgumentException.class, () -> component.setUseSolidVaporPressure(true));
      assertTrue(!component.isUseSolidVaporPressure());
      component.setUseSolidVaporPressure(false);
    }
  }

  @Test
  void absentDensityUsesDocumentedFallbackButInvalidDensityFails() {
    ComponentSolid component = (ComponentSolid) solid().getComponent(0);
    component.solidDensityCoefs = new double[5];
    assertTrue(Double.isFinite(component.fugcoef(190.0, 2.0)));
    assertEquals(1000.0, component.soldens, 0.0);
    component.solidDensityCoefs[0] = -1.0;
    assertThrows(IllegalStateException.class, () -> component.fugcoef(190.0, 2.0));
    component.solidDensityCoefs[0] = Double.NaN;
    assertThrows(IllegalStateException.class, () -> component.fugcoef(190.0, 2.0));
  }

  @Test
  void correlationAvailabilityIsReevaluatedAfterChangingParameters() {
    ComponentSolid component = (ComponentSolid) solid().getComponent(0);
    double hsub = component.Hsub;
    component.Hsub = 0.0;
    component.setAntoineASolid(1.0);
    component.setAntoineBSolid(-200.0);
    component.setAntoineCSolid(0.0);
    component.fugcoef(190.0, 2.0);
    assertEquals(Math.exp(1.0 - 200.0 / 190.0), component.PvapSolid, 1e-14);
    component.Hsub = hsub;
    component.fugcoef(190.0, 2.0);
    assertEquals(component.getCCsolidVaporPressure(190.0), component.PvapSolid, 1e-14);
  }

  @Test
  void pressureAndTemperatureDerivativesMatchIndependentDifferences() {
    PhaseInterface phase = solid();
    ComponentSolid component = (ComponentSolid) phase.getComponent(0);
    component.setUseSolidVaporPressure(true);
    double step = 0.001;
    double dt = (Math.log(component.fugcoef(190.0 + step, 2.0)) - Math.log(component.fugcoef(190.0 - step, 2.0)))
        / (2.0 * step);
    double dp = (Math.log(component.fugcoef(190.0, 2.0 + step)) - Math.log(component.fugcoef(190.0, 2.0 - step)))
        / (2.0 * step);
    component.fugcoef(190.0, 2.0);
    assertEquals(dt, component.getdfugdt(), 1e-8);
    assertEquals(dp, component.getdfugdp(), 1e-7);
    assertEquals(dt, component.logfugcoefdT(phase), 1e-8);
    assertEquals(dp, component.logfugcoefdP(phase), 1e-7);
    assertEquals(0.0, component.logfugcoefdN(phase)[0], 0.0);
    assertEquals(0.0, component.logfugcoefdNi(phase, 0), 0.0);
    assertEquals(0.0, component.getdfugdx(0), 0.0);
    assertEquals(190.0, component.refPhase.getTemperature(), 0.0);
    assertEquals(component.PvapSolid, component.refPhase.getPressure(), 0.0);
    component.setSolidCheck(false);
    assertEquals(0.0, component.logfugcoefdT(phase), 0.0);
    assertEquals(0.0, component.logfugcoefdP(phase), 0.0);
    assertEquals(1e30, component.getFugacityCoefficient(), 0.0);
  }

  @Test
  void vaporReferenceRejectsInvalidInputsAndMissingReferencePhase() {
    ComponentSolid component = (ComponentSolid) solid().getComponent(0);
    for (double invalid : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> component.fugcoef(invalid, 2.0));
      assertThrows(IllegalArgumentException.class, () -> component.fugcoef(190.0, invalid));
    }
    component.refPhase = null;
    assertThrows(IllegalStateException.class, () -> component.fugcoef(190.0, 2.0));
  }
}
