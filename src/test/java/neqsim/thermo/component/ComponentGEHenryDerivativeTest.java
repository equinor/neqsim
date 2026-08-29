package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePitzer;
import neqsim.thermo.system.SystemPitzer;

/** Tests temperature and pressure derivatives of aqueous Henry-reference fugacity coefficients. */
class ComponentGEHenryDerivativeTest {
  private static final double TEMPERATURE = 298.15;

  @Test
  void activeCorrelationReturnsLogarithmicDerivative() {
    ComponentGeNRTL component = new ComponentGeNRTL("CO2", 1.0, 1.0, 0);
    component.setHenryCoefParameter(new double[] { 10.0, -1200.0, 1.5, 0.002 });
    PhasePitzer phase = phaseAt(TEMPERATURE);

    double expected = finiteDifferenceLogHenry(component, TEMPERATURE);

    assertEquals(expected, component.fugcoefDiffTemp(phase), 1.0e-9);
    assertEquals(expected, component.logfugcoefdT(phase), 1.0e-9);
    assertEquals(1200.0 / (TEMPERATURE * TEMPERATURE) + 1.5 / TEMPERATURE + 0.002, component.fugcoefDiffTemp(phase),
        1.0e-12);
    phase.setPressure(10.0);
    assertEquals(-0.1, component.logfugcoefdP(phase), 1.0e-15);
  }

  @Test
  void pitzerNeutralGasUsesSameLogarithmicDerivative() {
    ComponentGePitzer component = new ComponentGePitzer("CO2", 1.0, 1.0, 0);
    component.setHenryCoefParameter(new double[] { 8.0, -800.0, 0.5, 0.001 });
    PhasePitzer phase = phaseAt(TEMPERATURE);

    assertEquals(finiteDifferenceLogHenry(component, TEMPERATURE), component.fugcoefDiffTemp(phase), 1.0e-9);
  }

  @Test
  void failClosedCorrelationHasZeroReferenceDerivative() {
    ComponentGeNRTL component = new ComponentGeNRTL("CO2", 1.0, 1.0, 0);
    component.setHenryCoefParameter(new double[] { 1000.0, 0.0, 0.0, 0.0 });

    assertTrue(Double.isInfinite(component.getHenryCoef(TEMPERATURE)));
    assertEquals(0.0, component.fugcoefDiffTemp(phaseAt(TEMPERATURE)), 0.0);
  }

  @Test
  void unsupportedPitzerHydrocarbonHasZeroReferenceDerivative() {
    ComponentGePitzer component = new ComponentGePitzer("methane", 1.0, 1.0, 0);
    component.setHenryCoefParameter(new double[] { 0.0, 0.0, 0.0, 0.01 });

    double rawLogDerivative = component.getHenryCoefdT(TEMPERATURE) / component.getHenryCoef(TEMPERATURE);

    assertEquals(0.01, rawLogDerivative, 1.0e-12);
    assertEquals(0.0, component.fugcoefDiffTemp(phaseAt(TEMPERATURE)), 0.0);
  }

  @Test
  void waterContainingGePhaseUsesQualifiedIapwsReferenceForLegacySolventRow() {
    PhasePitzer waterPhase = aqueousPhase(false);
    String[][] gases = { { "hydrogen", "H2" }, { "helium", "He" }, { "argon", "Ar" } };
    for (String[] gas : gases) {
      ExposedNrtlComponent component = new ExposedNrtlComponent(gas[0]);
      component.forceReferenceStateType("solvent");
      component.setHenryCoefParameter(new double[] { 1000.0, 0.0, 0.0, 0.0 });

      assertTrue(component.exposesUsesHenryReference(waterPhase), gas[0]);
      assertEquals(IapwsHenryLaw.getHenryCoefficientBar(gas[1], TEMPERATURE),
          component.exposesEffectiveHenry(waterPhase), 1.0e-10, gas[0]);
      assertEquals(IapwsHenryLaw.getLnHenryCoefficientTemperatureDerivative(gas[1], TEMPERATURE),
          component.exposesLnHenryDerivative(waterPhase), 1.0e-12, gas[0]);
      assertEquals(IapwsHenryLaw.getLnHenryCoefficientTemperatureDerivative(gas[1], TEMPERATURE),
          component.fugcoefDiffTemp(waterPhase), 1.0e-12, gas[0]);
    }
  }

  @Test
  void pitzerMapsPureWaterReferenceAndPreservesUnqualifiedBrineCompatibility() {
    ExposedPitzerComponent methane = new ExposedPitzerComponent("methane");
    ExposedPitzerComponent carbonDioxide = new ExposedPitzerComponent("CO2");
    carbonDioxide.setHenryCoefParameter(new double[] { 8.0, -800.0, 0.5, 0.001 });
    PhasePitzer waterPhase = aqueousPhase(false);
    PhasePitzer unqualifiedBrine = aqueousPhase(true);
    PhasePitzer traceIonTopology = aqueousPhaseWithSalt(1.0e-30);
    double expectedMolalityReference = IapwsHenryLaw.getHenryCoefficientBar("CH4", TEMPERATURE)
        * IapwsHenryLaw.WATER_MOLAR_MASS_KG_PER_MOL;

    assertEquals(expectedMolalityReference, methane.exposesEffectiveHenry(waterPhase), 1.0e-10);
    assertEquals(IapwsHenryLaw.getLnHenryCoefficientTemperatureDerivative("CH4", TEMPERATURE),
        methane.exposesLnHenryDerivative(waterPhase), 1.0e-12);
    assertEquals(carbonDioxide.exposesLegacyHenry(TEMPERATURE), carbonDioxide.exposesEffectiveHenry(waterPhase), 0.0);
    assertEquals(ComponentGE.INSOLUBLE_HENRY_COEFFICIENT, methane.exposesEffectiveHenry(unqualifiedBrine), 0.0);
    assertEquals(0.0, methane.exposesLnHenryDerivative(unqualifiedBrine), 0.0);
    assertEquals(carbonDioxide.exposesLegacyHenry(TEMPERATURE), carbonDioxide.exposesEffectiveHenry(unqualifiedBrine),
        0.0);
    assertEquals(carbonDioxide.exposesLegacyLnHenryDerivative(TEMPERATURE),
        carbonDioxide.exposesLnHenryDerivative(unqualifiedBrine), 0.0);
    assertEquals(carbonDioxide.exposesLegacyHenry(TEMPERATURE), carbonDioxide.exposesEffectiveHenry(traceIonTopology),
        0.0);
  }

  @Test
  void systemInitializationPublishesAndRefreshesAnalyticalDerivatives() {
    SystemPitzer system = new SystemPitzer(TEMPERATURE, 10.0);
    system.addComponent("water", 55.508);
    system.addComponent("nitrogen", 1.0e-4);
    system.setMixingRule("classic");
    system.init(2);

    PhaseInterface aqueous = system.getPhase(1);
    ComponentInterface nitrogen = aqueous.getComponent("nitrogen");
    ComponentInterface water = aqueous.getComponent("water");
    double expectedTemperatureDerivative = IapwsHenryLaw.getLnHenryCoefficientTemperatureDerivative("N2", TEMPERATURE);

    assertEquals(expectedTemperatureDerivative, nitrogen.getdfugdt(), 1.0e-12);
    assertEquals(-0.1, nitrogen.getdfugdp(), 1.0e-15);
    assertEquals(-0.1, water.getdfugdp(), 1.0e-15);
    assertEquals(nitrogen.getdfugdt(), system.getProperty("logfugdT", "nitrogen", 1), 0.0);
    assertEquals(nitrogen.getdfugdp(), system.getProperty("logfugdP", "nitrogen", 1), 0.0);
    assertEquals(nitrogen.getdfugdp(), centeredPressureDerivative((ComponentGE) nitrogen, aqueous), 2.0e-10);

    system.setPressure(25.0);
    system.setTemperature(318.15);
    system.init(2);
    aqueous = system.getPhase(1);
    nitrogen = aqueous.getComponent("nitrogen");
    assertEquals(IapwsHenryLaw.getLnHenryCoefficientTemperatureDerivative("N2", 318.15), nitrogen.getdfugdt(), 1.0e-12);
    assertEquals(-0.04, nitrogen.getdfugdp(), 1.0e-15);

    SystemPitzer cloned = system.clone();
    cloned.init(2);
    assertEquals(nitrogen.getdfugdt(), cloned.getPhase(1).getComponent("nitrogen").getdfugdt(), 0.0);
    assertEquals(nitrogen.getdfugdp(), cloned.getPhase(1).getComponent("nitrogen").getdfugdp(), 0.0);

    system.setPressure(10.0);
    system.setTemperature(TEMPERATURE);
    system.init(2);
    assertEquals(expectedTemperatureDerivative, system.getPhase(1).getComponent("nitrogen").getdfugdt(), 1.0e-12);
    assertEquals(-0.1, system.getPhase(1).getComponent("nitrogen").getdfugdp(), 1.0e-15);
  }

  private static double centeredPressureDerivative(ComponentGE component, PhaseInterface phase) {
    double pressure = phase.getPressure();
    double step = pressure * 1.0e-5;
    phase.setPressure(pressure + step);
    double plus = Math.log(component.fugcoef(phase));
    phase.setPressure(pressure - step);
    double minus = Math.log(component.fugcoef(phase));
    phase.setPressure(pressure);
    component.fugcoef(phase);
    return (plus - minus) / (2.0 * step);
  }

  private static PhasePitzer phaseAt(double temperature) {
    PhasePitzer phase = new PhasePitzer();
    phase.setTemperature(temperature);
    return phase;
  }

  private static PhasePitzer aqueousPhase(boolean includeSalt) {
    return aqueousPhaseWithSalt(includeSalt ? 1.0 : 0.0);
  }

  private static PhasePitzer aqueousPhaseWithSalt(double saltMoles) {
    SystemPitzer system = new SystemPitzer(TEMPERATURE, 1.01325);
    system.addComponent("water", 55.508);
    if (saltMoles > 0.0) {
      system.addComponent("Na+", saltMoles);
      system.addComponent("Cl-", saltMoles);
    }
    system.init(0);
    return (PhasePitzer) system.getPhase(1);
  }

  private static double finiteDifferenceLogHenry(ComponentGE component, double temperature) {
    double step = 1.0e-3;
    return (Math.log(component.getHenryCoef(temperature + step)) - Math.log(component.getHenryCoef(temperature - step)))
        / (2.0 * step);
  }

  private static final class ExposedNrtlComponent extends ComponentGeNRTL {
    private static final long serialVersionUID = 1000;

    private ExposedNrtlComponent(String name) {
      super(name, 1.0, 1.0, 0);
    }

    private void forceReferenceStateType(String referenceState) {
      referenceStateType = referenceState;
    }

    private boolean exposesUsesHenryReference(PhaseInterface phase) {
      return usesHenryReference(phase);
    }

    private double exposesEffectiveHenry(PhaseInterface phase) {
      return getEffectiveHenryCoefficient(phase);
    }

    private double exposesLnHenryDerivative(PhaseInterface phase) {
      return getLnHenryCoefficientTemperatureDerivative(phase);
    }
  }

  private static final class ExposedPitzerComponent extends ComponentGePitzer {
    private static final long serialVersionUID = 1000;

    private ExposedPitzerComponent(String name) {
      super(name, 1.0, 1.0, 0);
    }

    private double exposesEffectiveHenry(PhaseInterface phase) {
      return getEffectiveHenryCoefficient(phase);
    }

    private double exposesLnHenryDerivative(PhaseInterface phase) {
      return getLnHenryCoefficientTemperatureDerivative(phase);
    }

    private double exposesLegacyHenry(double temperature) {
      return getEffectiveHenryCoefficient(temperature);
    }

    private double exposesLegacyLnHenryDerivative(double temperature) {
      return getLnHenryCoefficientTemperatureDerivative(temperature);
    }
  }
}
