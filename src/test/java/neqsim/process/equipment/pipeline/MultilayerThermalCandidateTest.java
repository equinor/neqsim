package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Acceptance and identity checks for a caller-supplied radial thermal model. */
class MultilayerThermalCandidateTest {
  @Test
  void candidateAcceptancePreservesLayerReferencesAndBothTemperatureLevels() {
    MultilayerThermalCalculator calculator = calculator();
    RadialThermalLayer steel = calculator.getLayers().get(0);
    RadialThermalLayer insulation = calculator.getLayers().get(1);
    MultilayerThermalCalculator candidate = SerializationUtils.clone(calculator);
    candidate.setFluidTemperature(320.0);
    candidate.setAmbientTemperature(275.0);
    candidate.setInnerHTC(70.0);
    candidate.calculateOverallUValue();
    candidate.updateTransient(1.0e-3);
    byte[] before = SerializationUtils.serialize(calculator);

    calculator.validateCandidateConfiguration(candidate);
    assertArrayEquals(before, SerializationUtils.serialize(calculator));
    calculator.acceptCandidateState(candidate);

    assertSame(steel, calculator.getLayers().get(0));
    assertSame(insulation, calculator.getLayers().get(1));
    assertThermalStateEquals(candidate, calculator);
    assertTrue(calculator.getLastFluidHeatTransferPerLength() > 0.0);
    assertEquals(290.0, steel.getPreviousTemperature(), 0.0);
    assertTrue(steel.getTemperature() > steel.getPreviousTemperature());
    candidate.updateTransient(2.0e-3);
    calculator.updateTransient(2.0e-3);
    assertThermalStateEquals(candidate, calculator);
  }

  @Test
  void configurationOrNonfiniteStateMismatchRejectsWithoutChangingConnectedLayers() {
    MultilayerThermalCalculator calculator = calculator();
    byte[] before = SerializationUtils.serialize(calculator);
    MultilayerThermalCalculator changedOuterBoundary = SerializationUtils.clone(calculator);
    changedOuterBoundary.setOuterHTC(51.0);
    MultilayerThermalCalculator changedLayer = SerializationUtils.clone(calculator);
    changedLayer.getLayers().get(1).setThermalConductivity(0.12);
    MultilayerThermalCalculator changedGeometry = SerializationUtils.clone(calculator);
    changedGeometry.setInnerRadius(0.11);
    MultilayerThermalCalculator invalidTemperature = SerializationUtils.clone(calculator);
    invalidTemperature.getLayers().get(0).setTemperature(Double.NaN);
    for (MultilayerThermalCalculator candidate : new MultilayerThermalCalculator[] { null, changedOuterBoundary,
        changedLayer, changedGeometry, invalidTemperature }) {
      assertThrows(IllegalStateException.class, () -> calculator.validateCandidateConfiguration(candidate));
      assertArrayEquals(before, SerializationUtils.serialize(calculator));
    }
  }

  @Test
  void completePipeIntervalUpdatesSuppliedCalculatorAndRejectedIntervalPreservesIt() {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("thermal transaction feed", fluid);
    inlet.setFlowRate(6.0, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe("thermal transaction pipe", inlet);
    pipe.setLength(40.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(4);
    pipe.setElevationProfile(new double[4]);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableJouleThomson(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    pipe.closeInlet();
    pipe.closeOutlet();
    pipe.setSurfaceTemperature(280.0, "K");
    MultilayerThermalCalculator calculator = calculator();
    pipe.setThermalCalculator(calculator);
    RadialThermalLayer steel = calculator.getLayers().get(0);
    TwoFluidPipe legacy = SerializationUtils.clone(pipe);
    pipe.setTransactionalTransientEnabled(true);
    UUID id = UUID.randomUUID();

    legacy.runTransient(1.0e-3, id);
    pipe.runTransient(1.0e-3, id);

    assertSame(calculator, pipe.getThermalCalculator());
    assertSame(steel, calculator.getLayers().get(0));
    assertThermalStateEquals(legacy.getThermalCalculator(), calculator);
    assertArrayEquals(legacy.getTemperatureProfile(), pipe.getTemperatureProfile(), 0.0);
    assertArrayEquals(SerializationUtils.serialize(legacy.getLastThermalEnergyBalanceReport()),
        SerializationUtils.serialize(pipe.getLastThermalEnergyBalanceReport()));
    assertTrue(pipe.getLastThermalEnergyBalanceReport().isWithinTolerance(1.0e-5, 1.0e-10));

    pipe.setMaximumTransientSubsteps(1);
    byte[] before = SerializationUtils.serialize(pipe);
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertSame(calculator, pipe.getThermalCalculator());
    assertSame(steel, calculator.getLayers().get(0));
    assertArrayEquals(before, SerializationUtils.serialize(pipe));
  }

  /** Compare physical state; serialization also records incidental sharing of layer names with enum names. */
  private static void assertThermalStateEquals(MultilayerThermalCalculator expected,
      MultilayerThermalCalculator actual) {
    assertEquals(expected.getInnerRadius(), actual.getInnerRadius(), 0.0);
    assertEquals(expected.getInnerHTC(), actual.getInnerHTC(), 0.0);
    assertEquals(expected.getOuterHTC(), actual.getOuterHTC(), 0.0);
    assertEquals(expected.getFluidTemperature(), actual.getFluidTemperature(), 0.0);
    assertEquals(expected.getAmbientTemperature(), actual.getAmbientTemperature(), 0.0);
    assertEquals(expected.isEnableThermalMass(), actual.isEnableThermalMass());
    assertEquals(expected.getLastFluidHeatTransferPerLength(), actual.getLastFluidHeatTransferPerLength(), 0.0);
    assertEquals(expected.getLastAmbientHeatTransferPerLength(), actual.getLastAmbientHeatTransferPerLength(), 0.0);
    assertEquals(expected.calculateOverallUValue(), actual.calculateOverallUValue(), 0.0);
    assertEquals(expected.getNumberOfLayers(), actual.getNumberOfLayers());
    for (int layer = 0; layer < expected.getNumberOfLayers(); layer++) {
      RadialThermalLayer expectedLayer = expected.getLayers().get(layer);
      RadialThermalLayer actualLayer = actual.getLayers().get(layer);
      assertEquals(expectedLayer.getName(), actualLayer.getName());
      assertEquals(expectedLayer.getMaterialType(), actualLayer.getMaterialType());
      assertEquals(expectedLayer.getInnerRadius(), actualLayer.getInnerRadius(), 0.0);
      assertEquals(expectedLayer.getOuterRadius(), actualLayer.getOuterRadius(), 0.0);
      assertEquals(expectedLayer.getThermalConductivity(), actualLayer.getThermalConductivity(), 0.0);
      assertEquals(expectedLayer.getDensity(), actualLayer.getDensity(), 0.0);
      assertEquals(expectedLayer.getSpecificHeat(), actualLayer.getSpecificHeat(), 0.0);
      assertEquals(expectedLayer.getTemperature(), actualLayer.getTemperature(), 0.0);
      assertEquals(expectedLayer.getPreviousTemperature(), actualLayer.getPreviousTemperature(), 0.0);
    }
  }

  private static MultilayerThermalCalculator calculator() {
    MultilayerThermalCalculator calculator = new MultilayerThermalCalculator(0.1);
    calculator.addLayer(0.01, RadialThermalLayer.MaterialType.CARBON_STEEL);
    calculator.addLayer(0.02, RadialThermalLayer.MaterialType.PU_FOAM);
    calculator.initializeLayerTemperatures(290.0);
    return calculator;
  }
}
