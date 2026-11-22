package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for the calibrated solid asphaltene model.
 *
 * @author Even Solbraa
 */
public class AsphalteneSolidModelTest {
  private static final double T = 363.15;

  /**
   * Synthetic live oil blended with a gas condensate.
   *
   * @param condensate mole fraction of condensate in the blend
   * @return fluid with an asphaltene pseudo-component
   */
  static SystemInterface fluid(double condensate) {
    SystemInterface s = new SystemSrkEos(T, 300.0);
    String[] names = {"methane", "ethane", "propane", "n-butane", "n-hexane"};
    double[] host = {30, 7, 5, 3, 3};
    double[] cond = {72, 9, 6, 3, 2};
    for (int i = 0; i < names.length; i++) {
      s.addComponent(names[i], (1 - condensate) * host[i] + condensate * cond[i]);
    }
    s.addTBPfraction("C10", (1 - condensate) * 15 + condensate * 2, 0.14, 0.79);
    s.addTBPfraction("C20", (1 - condensate) * 15 + 1e-6, 0.28, 0.86);
    s.addTBPfraction("C40", (1 - condensate) * 12 + 1e-6, 0.56, 0.93);
    s.addTBPfraction("Asphaltene", (1 - condensate) * 0.3, 1.0, 1.15, 1015.0, 15.7, 1.5);
    s.setMixingRule("classic");
    return s;
  }

  private static AsphalteneSolidModel calibratedHost() {
    AsphalteneSolidModel model = new AsphalteneSolidModel(fluid(0.0));
    model.calibrate(T, 250.0);
    return model;
  }

  @Test
  void calibrationReproducesMeasuredOnset() {
    AsphalteneSolidModel model = calibratedHost();
    assertEquals(0.0, model.supersaturation(T, 250.0), 1e-9);
    AsphalteneSolidModel.OnsetResult result = model.onsetPressures(T, 500.0, 20.0, 10.0);
    assertFalse(result.isUnstableAtMaximumPressure());
    assertEquals(250.0, result.getUpperOnsetPressure(), 0.2);
    assertFalse(model.isAsphalteneLiquidSplitDetected());
  }

  @Test
  void interactionParametersFollowSizeAsymmetry() {
    AsphalteneSolidModel model = calibratedHost();
    double methane = model.getInteractionParameter("methane");
    double hexane = model.getInteractionParameter("n-hexane");
    double heavy = model.getInteractionParameter("C40_PC");
    assertTrue(methane > 0.05, "methane kij " + methane);
    assertTrue(methane > hexane && hexane > heavy);
    assertEquals(0.0, heavy, 0.01);
    assertEquals(0.0, AsphalteneSolidModel.sizeAsymmetryKij(10.0, 10.0, 1.0), 1e-12);
  }

  @Test
  void condensateRaisesOnsetPressure() {
    AsphalteneSolidModel model = calibratedHost();
    double host = model.onsetPressures(T, 500.0, 20.0, 10.0).getUpperOnsetPressure();
    double blend10 = model.onsetPressures(fluid(0.1), T, 600.0, 20.0, 10.0).getUpperOnsetPressure();
    double blend30 = model.onsetPressures(fluid(0.3), T, 600.0, 20.0, 10.0).getUpperOnsetPressure();
    assertTrue(blend10 > host + 2.0, "10 % condensate onset " + blend10);
    assertTrue(blend30 > blend10 + 10.0, "30 % condensate onset " + blend30);
  }

  @Test
  void precipitatedFractionGrowsBelowOnset() {
    AsphalteneSolidModel model = calibratedHost();
    SystemInterface host = fluid(0.0);
    assertEquals(0.0, model.precipitatedFraction(host, T, 260.0), 0.0);
    double at240 = model.precipitatedFraction(host, T, 240.0);
    double at200 = model.precipitatedFraction(host, T, 200.0);
    assertTrue(at240 > 0.0 && at240 < at200 && at200 < 1.0, at240 + " " + at200);
  }

  @Test
  void lowerOnsetCalibrationReproducesEnvelope() throws Exception {
    SystemInterface host = fluid(0.0);
    host.setPressure(200.0);
    new ThermodynamicOperations(host).bubblePointPressureFlash(false);
    double bubble = host.getPressure();
    AsphalteneSolidModel model = calibratedHost();
    model.calibrateLowerOnset(80.0);
    assertTrue(model.getSolidMolarVolume() > 0.0);
    AsphalteneSolidModel.OnsetResult result = model.onsetPressures(T, 500.0, 20.0, 5.0);
    assertEquals(250.0, result.getUpperOnsetPressure(), 0.2);
    assertEquals(80.0, result.getLowerOnsetPressure(), 0.2);
    assertTrue(result.getLowerOnsetPressure() < bubble && bubble < result.getUpperOnsetPressure());
  }

  @Test
  void temperatureCalibrationKeepsFirstPoint() {
    AsphalteneSolidModel model = calibratedHost();
    model.calibrateTemperature(T + 30.0, 220.0);
    assertEquals(220.0, model.onsetPressures(T + 30.0, 500.0, 20.0, 5.0).getUpperOnsetPressure(), 0.2);
    assertEquals(250.0, model.onsetPressures(T, 500.0, 20.0, 5.0).getUpperOnsetPressure(), 0.2);
  }

  @Test
  void fitKijExponentRecoversBlendOnset() {
    AsphalteneSolidModel reference = new AsphalteneSolidModel(fluid(0.0));
    reference.setKijExponent(1.6);
    reference.calibrate(T, 250.0);
    double measured = reference.onsetPressures(fluid(0.2), T, 600.0, 20.0, 5.0).getUpperOnsetPressure();
    AsphalteneSolidModel model = calibratedHost();
    double exponent = model.fitKijExponent(fluid(0.2), T, measured, 600.0, 20.0);
    assertEquals(1.6, exponent, 0.05);
    assertEquals(250.0, model.onsetPressures(T, 500.0, 20.0, 10.0).getUpperOnsetPressure(), 0.2);
  }

  @Test
  void invalidUseIsRejected() {
    SystemInterface noAsphaltene = new SystemSrkEos(T, 10.0);
    noAsphaltene.addComponent("methane", 1.0);
    noAsphaltene.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> new AsphalteneSolidModel(noAsphaltene));
    AsphalteneSolidModel model = new AsphalteneSolidModel(fluid(0.0));
    assertThrows(IllegalStateException.class, () -> model.onsetPressures(T, 500.0, 20.0, 10.0));
    model.calibrate(T, 250.0);
    assertThrows(IllegalArgumentException.class, () -> model.calibrateLowerOnset(300.0));
    assertThrows(IllegalArgumentException.class, () -> model.calibrateTemperature(T, 240.0));
  }

  @Test
  void databaseAsphalteneComponentIsAccepted() {
    SystemInterface s = new SystemSrkEos(T, 100.0);
    s.addComponent("methane", 1.0);
    s.addComponent("asphaltene", 0.01);
    s.setMixingRule("classic");
    assertEquals("asphaltene", new AsphalteneSolidModel(s).getAsphalteneName());
  }

  /** The snippet in docs/pvtsimulation/flowassurance/asphaltene_modeling.md, section 9. */
  @Test
  void documentationExample() {
    SystemInterface host = new SystemSrkEos(363.15, 300.0);
    host.addComponent("methane", 30.0);
    host.addComponent("ethane", 7.0);
    host.addComponent("propane", 5.0);
    host.addComponent("n-butane", 3.0);
    host.addComponent("n-hexane", 3.0);
    host.addTBPfraction("C10", 15.0, 0.14, 0.79);
    host.addTBPfraction("C20", 15.0, 0.28, 0.86);
    host.addTBPfraction("C40", 12.0, 0.56, 0.93);
    host.addTBPfraction("Asphaltene", 0.3, 1.0, 1.15, 1015.0, 15.7, 1.5);
    host.setMixingRule("classic");

    AsphalteneSolidModel model = new AsphalteneSolidModel(host);
    model.calibrate(363.15, 250.0);
    model.calibrateLowerOnset(80.0);
    AsphalteneSolidModel.OnsetResult envelope = model.onsetPressures(363.15, 500.0, 20.0, 5.0);
    assertEquals(250.0, envelope.getUpperOnsetPressure(), 0.2);
    assertEquals(80.0, envelope.getLowerOnsetPressure(), 0.2);
    double blendOnset = model.onsetPressures(fluid(0.1), 363.15, 600.0, 20.0, 5.0).getUpperOnsetPressure();
    assertTrue(blendOnset > 250.0);
    double precipitated = model.precipitatedFraction(host, 363.15, 200.0);
    assertTrue(precipitated > 0.0 && precipitated < 1.0);
    assertEquals(0.0964, model.getInteractionParameter("methane"), 0.001);
    assertEquals(0.0247, model.getInteractionParameter("n-hexane"), 0.001);
  }
}
