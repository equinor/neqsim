package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhasePitzer;

/** Tests the attributed IAPWS G7-04 common-gas Henry correlation and GE integration. */
class IapwsHenryLawTest {
  private static final String[] GASES = { "He", "Ne", "Ar", "Kr", "Xe", "H2", "N2", "O2", "CO", "CO2", "H2S", "CH4",
      "C2H6", "SF6" };

  private static final double[][] EXPECTED_LOG_KH_GPA = { { 2.6576, 2.1660, 1.1973, -0.1993 },
      { 2.5134, 2.3512, 1.5952, 0.4659 }, { 1.4061, 1.8079, 1.1536, 0.0423 }, { 0.8210, 1.4902, 0.9798, 0.0006 },
      { 0.2792, 1.1430, 0.5033, -0.7081 }, { 1.9702, 1.8464, 1.0513, -0.1848 }, { 2.1716, 2.3509, 1.4842, 0.1647 },
      { 1.5024, 1.8832, 1.1630, -0.0276 }, { 1.7652, 1.9939, 1.1250, -0.2382 }, { -1.7508, -0.5450, -0.6524, -1.3489 },
      { -2.8784, -1.7083, -1.6074, -2.1319 }, { 1.4034, 1.7946, 1.0342, -0.2209 }, { 1.1418, 1.8495, 0.8274, -0.8141 },
      { 3.1445, 3.6919, 2.6749, 1.2402 } };

  @Test
  void reproducesAllPublishedGuidelineCheckValues() {
    double[] temperatures = { 300.0, 400.0, 500.0, 600.0 };
    for (int gas = 0; gas < GASES.length; gas++) {
      for (int point = 0; point < temperatures.length; point++) {
        double logKhGpa = Math
            .log(IapwsHenryLaw.getHenryCoefficientBarAllowExtrapolation(GASES[gas], temperatures[point]) / 10000.0);
        assertEquals(EXPECTED_LOG_KH_GPA[gas][point], logKhGpa, 5.1e-5,
            GASES[gas] + " at " + temperatures[point] + " K");
      }
    }
  }

  @Test
  void analyticalDerivativeMatchesCenteredFiniteDifference() {
    for (String gas : GASES) {
      double temperature = 400.0;
      double step = 1.0e-3;
      double expected = (Math.log(IapwsHenryLaw.getHenryCoefficientBar(gas, temperature + step))
          - Math.log(IapwsHenryLaw.getHenryCoefficientBar(gas, temperature - step))) / (2.0 * step);
      assertEquals(expected, IapwsHenryLaw.getLnHenryCoefficientTemperatureDerivative(gas, temperature), 1.0e-9, gas);
    }
  }

  @Test
  void assessmentSeparatesFitExtrapolationAndUnsupportedStates() {
    IapwsHenryLaw.Assessment fitted = IapwsHenryLaw.assess("methane", 300.0);
    assertEquals(IapwsHenryLaw.Status.WITHIN_FITTED_RANGE, fitted.getStatus());
    assertEquals("CH4", fitted.getCanonicalGasName());
    assertTrue(fitted.isUsable());
    assertEquals(IapwsHenryLaw.DATASET_ID, fitted.getDatasetId());
    assertEquals(-10.44708, fitted.getCoefficientA(), 0.0);
    assertEquals(4.66491, fitted.getCoefficientB(), 0.0);
    assertEquals(12.12986, fitted.getCoefficientC(), 0.0);
    assertEquals(0.0386, fitted.getRmsLogHenryResidual(), 0.0);

    IapwsHenryLaw.Assessment extrapolated = IapwsHenryLaw.assess("ethane", 600.0);
    assertEquals(IapwsHenryLaw.Status.GUIDELINE_EXTRAPOLATION, extrapolated.getStatus());
    assertFalse(extrapolated.isUsable());
    assertThrows(IllegalArgumentException.class, () -> IapwsHenryLaw.getHenryCoefficientBar("ethane", 600.0));
    assertTrue(IapwsHenryLaw.getHenryCoefficientBarAllowExtrapolation("ethane", 600.0) > 0.0);

    IapwsHenryLaw.Assessment outside = IapwsHenryLaw.assess("CH4", 700.0);
    assertEquals(IapwsHenryLaw.Status.OUTSIDE_CORRELATION_DOMAIN, outside.getStatus());
    assertFalse(outside.isUsable());
    assertThrows(IllegalArgumentException.class, () -> IapwsHenryLaw.getHenryCoefficientBar("CH4", 700.0));

    IapwsHenryLaw.Assessment unsupported = IapwsHenryLaw.assess("propane", 300.0);
    assertEquals(IapwsHenryLaw.Status.UNSUPPORTED_SPECIES, unsupported.getStatus());
    assertFalse(unsupported.isSupportedSpecies());
  }

  @Test
  void genericAndPitzerPathsUseExplicitStandardStateMapping() {
    PhasePitzer aqueousPhase = new PhasePitzer();
    aqueousPhase.setTemperature(300.0);
    aqueousPhase.addComponent("water", 55.508, 55.508, 0);

    ComponentGeNRTL generic = new ComponentGeNRTL("methane", 1.0e-6, 1.0e-6, 1);
    ComponentGePitzer pitzer = new ComponentGePitzer("methane", 1.0e-6, 1.0e-6, 1);
    double moleFractionHenry = IapwsHenryLaw.getHenryCoefficientBar("CH4", 300.0);

    assertEquals(moleFractionHenry, generic.getEffectiveHenryCoefficient(aqueousPhase), 1.0e-10);
    assertEquals(moleFractionHenry * IapwsHenryLaw.WATER_MOLAR_MASS_KG_PER_MOL,
        pitzer.getEffectiveHenryCoefficient(aqueousPhase), 1.0e-10);
    assertEquals(IapwsHenryLaw.getLnHenryCoefficientTemperatureDerivative("CH4", 300.0),
        generic.getLnHenryCoefficientTemperatureDerivative(aqueousPhase), 1.0e-12);
  }

  @Test
  void supportedSpeciesOutsideWaterDomainFailsClosedInIntegratedPath() {
    PhasePitzer aqueousPhase = new PhasePitzer();
    aqueousPhase.setTemperature(700.0);
    aqueousPhase.addComponent("water", 55.508, 55.508, 0);
    ComponentGeNRTL methane = new ComponentGeNRTL("methane", 1.0e-6, 1.0e-6, 1);

    assertEquals(ComponentGE.INSOLUBLE_HENRY_COEFFICIENT, methane.getEffectiveHenryCoefficient(aqueousPhase), 0.0);
    assertEquals(0.0, methane.getLnHenryCoefficientTemperatureDerivative(aqueousPhase), 0.0);
  }

  @Test
  void supportedSpeciesOutsideFittedRangeFailsClosedInRuntimePath() {
    PhasePitzer aqueousPhase = new PhasePitzer();
    aqueousPhase.setTemperature(500.0);
    aqueousPhase.addComponent("water", 55.508, 55.508, 0);
    ComponentGeNRTL ethane = new ComponentGeNRTL("ethane", 1.0e-6, 1.0e-6, 1);

    assertEquals(IapwsHenryLaw.Status.GUIDELINE_EXTRAPOLATION,
        IapwsHenryLaw.assess("ethane", aqueousPhase.getTemperature()).getStatus());
    assertEquals(ComponentGE.INSOLUBLE_HENRY_COEFFICIENT, ethane.getEffectiveHenryCoefficient(aqueousPhase), 0.0);
    assertEquals(0.0, ethane.getLnHenryCoefficientTemperatureDerivative(aqueousPhase), 0.0);
  }
}
