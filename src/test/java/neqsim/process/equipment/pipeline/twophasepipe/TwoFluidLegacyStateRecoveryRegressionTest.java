package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class TwoFluidLegacyStateRecoveryRegressionTest {
  @Test
  void fiveVariableInputReplacesEveryLiquidInventory() {
    TwoFluidSection section = section();
    section.setWaterCut(0.25);
    section.setStateVector(new double[] { 1.0, 8.0, 16.0, 2.0, 16.0, 48.0, 100.0 });

    section.setStateVector(new double[] { 2.0, 12.0, 6.0, 24.0, 500.0 });

    assertEquals(12.0, section.getOilMassPerLength() + section.getWaterMassPerLength(), 1.0e-12);
    assertEquals(24.0, section.getOilMomentumPerLength() + section.getWaterMomentumPerLength(), 1.0e-12);
    double oilVolume = section.getOilMassPerLength() / 800.0;
    double waterVolume = section.getWaterMassPerLength() / 1000.0;
    assertEquals(0.25, waterVolume / (oilVolume + waterVolume), 1.0e-12);
    section.extractPrimitiveVariables();
    assertEquals(12.0, section.getLiquidMassPerLength(), 1.0e-12);
    assertEquals(2.0, section.getLiquidVelocity(), 1.0e-12);
    assertEquals(500.0, section.getEnergyPerLength(), 0.0);
  }

  @Test
  void sixVariableGasOnlyInputClearsOldLiquidMomenta() {
    TwoFluidSection section = section();
    section.setStateVector(new double[] { 1.0, 8.0, 16.0, 2.0, 16.0, 48.0, 100.0 });

    section.setStateVector(new double[] { 2.0, 0.0, 0.0, 6.0, 0.0, 500.0 });

    assertEquals(0.0, section.getOilMomentumPerLength(), 0.0);
    assertEquals(0.0, section.getWaterMomentumPerLength(), 0.0);
  }

  @Test
  void sixVariableTraceLiquidInputPreservesItsSpecifiedMomentum() {
    TwoFluidSection section = section();
    section.setStateVector(new double[] { 1.0, 8.0, 16.0, 2.0, 16.0, 48.0, 100.0 });

    section.setStateVector(new double[] { 2.0, 4.0e-13, 6.0e-13, 6.0, 3.0e-12, 500.0 });

    assertEquals(1.2e-12, section.getOilMomentumPerLength(), 1.0e-26);
    assertEquals(1.8e-12, section.getWaterMomentumPerLength(), 1.0e-26);
  }

  private static TwoFluidSection section() {
    TwoFluidSection section = new TwoFluidSection(0.5, 1.0, 1.0, 0.0);
    section.setGasDensity(10.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(850.0);
    return section;
  }
}
