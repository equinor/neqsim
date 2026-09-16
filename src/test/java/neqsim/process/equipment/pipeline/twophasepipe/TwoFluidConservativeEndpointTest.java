package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Contracts for conservative endpoint recovery; legacy recovery remains a separate path. */
class TwoFluidConservativeEndpointTest {
  @Test
  void recoversIndependentPhaseVelocitiesWithoutLegacyMagnitudeCaps() {
    TwoFluidSection section = section();
    double[] state = state(section, 0.6, 0.25, 0.15, 120.0, -70.0, 80.0);

    section.setConservativeEndpoint(state, 1.0e-12);

    assertArrayEquals(state, section.getStateVector(), 0.0);
    assertEquals(120.0, section.getGasVelocity(), 1.0e-12);
    assertEquals(-70.0, section.getOilVelocity(), 1.0e-12);
    assertEquals(80.0, section.getWaterVelocity(), 1.0e-12);
    assertEquals((state[4] + state[5]) / (state[1] + state[2]), section.getLiquidVelocity(), 0.0);
    assertEquals(0.25, section.getOilHoldup(), 1.0e-15);
    assertEquals(0.15, section.getWaterHoldup(), 1.0e-15);
    assertEquals(0.15 / 0.4, section.getWaterCut(), 1.0e-15);
    assertEquals(0.25 * -70.0 + 0.15 * 80.0, section.getSuperficialLiquidVelocity(), 1.0e-12);
    assertEquals(0.6 * 120.0 + 0.25 * -70.0 + 0.15 * 80.0, section.getMixtureVelocity(), 1.0e-12);
    assertEquals((state[0] + state[1] + state[2]) / section.getArea(), section.getMixtureDensity(), 1.0e-12);
  }

  @Test
  void acceptsPureGasWithoutDependingOnStaleBulkLiquidProperties() {
    TwoFluidSection section = section();
    section.setLiquidDensity(0.0);
    double[] state = state(section, 1.0, 0.0, 0.0, 3.0, 0.0, 0.0);

    section.setConservativeEndpoint(state, 1.0e-12);

    assertArrayEquals(state, section.getStateVector(), 0.0);
    assertEquals(0.0, section.getLiquidVelocity(), 0.0);
    assertEquals(0.0, section.getSuperficialLiquidVelocity(), 0.0);
    assertEquals(section.getOilDensity(), section.getLiquidDensity(), 0.0);
    assertEquals(section.getGasDensity(), section.getMixtureDensity(), 0.0);
  }

  @Test
  void preservesPermittedVolumeResidualInsteadOfRenormalizingHoldup() {
    TwoFluidSection section = section();
    double[] state = state(section, 0.6 + 2.0e-8, 0.25, 0.15, 1.0, 0.5, 0.3);

    section.setConservativeEndpoint(state, 1.0e-7);

    assertArrayEquals(state, section.getStateVector(), 0.0);
    assertEquals(1.0 + 2.0e-8, section.getGasHoldup() + section.getLiquidHoldup(), 1.0e-15);
    assertEquals(state[0] / section.getGasDensity() / section.getArea(), section.getGasHoldup(), 0.0);
    PipeSection legacy = new PipeSection(0.0, 10.0, 0.1, 0.0);
    legacy.setGasHoldup(section.getGasHoldup());
    legacy.setLiquidHoldup(section.getLiquidHoldup());
    legacy.updateDerivedQuantities();
    assertEquals(1.0, legacy.getGasHoldup() + legacy.getLiquidHoldup(), 1.0e-15,
        "The existing public primitive update must retain its normalization behavior");
  }

  @Test
  void recoversPositiveTraceAndExactlyAbsentPhasesWithoutStaleVelocity() {
    TwoFluidSection section = section();
    double oilMass = 1.0e-15;
    double oilHoldup = oilMass / section.getOilDensity() / section.getArea();
    double[] state = state(section, 1.0 - oilHoldup, oilHoldup, 0.0, 2.0, -17.0, 0.0);
    section.setWaterVelocity(12.0);
    section.setOilVelocity(23.0);

    section.setConservativeEndpoint(state, 1.0e-12);

    assertTrue(section.getOilMassPerLength() > 0.0);
    assertTrue(section.getOilMassPerLength() < 1.0e-12);
    assertEquals(-17.0, section.getOilVelocity(), 1.0e-12);
    assertEquals(0.0, section.getWaterVelocity(), 0.0);
    assertArrayEquals(state, section.getStateVector(), 0.0);
  }

  @Test
  void rejectsInvalidEndpointsBeforeMutatingAnyRecoveredState() {
    TwoFluidSection section = section();
    double[] valid = state(section, 0.6, 0.25, 0.15, 2.0, 0.5, -0.1);
    section.setConservativeEndpoint(valid, 1.0e-12);
    double[] primitives = primitives(section);
    double[][] invalid = { valid.clone(), valid.clone(), valid.clone(), valid.clone(), valid.clone() };
    invalid[0][0] = -1.0;
    invalid[1][1] *= 2.0;
    invalid[2][2] = 0.0;
    invalid[3][3] = Double.POSITIVE_INFINITY;
    invalid[4][6] = Double.NaN;
    for (double[] trial : invalid) {
      assertThrows(IllegalArgumentException.class, () -> section.setConservativeEndpoint(trial, 1.0e-12));
      assertArrayEquals(valid, section.getStateVector(), 0.0);
      assertArrayEquals(primitives, primitives(section), 0.0);
    }
    assertThrows(IllegalArgumentException.class, () -> section.setConservativeEndpoint(valid, 0.0));
    assertThrows(IllegalArgumentException.class, () -> section.setConservativeEndpoint(new double[6], 1.0e-12));
  }

  private static TwoFluidSection section() {
    TwoFluidSection section = new TwoFluidSection(0.0, 10.0, 0.1, 0.0);
    section.setPressure(5.0e6);
    section.setGasDensity(40.0);
    section.setOilDensity(700.0);
    section.setWaterDensity(1000.0);
    section.setLiquidDensity(850.0);
    return section;
  }

  private static double[] state(TwoFluidSection section, double gas, double oil, double water, double gasVelocity,
      double oilVelocity, double waterVelocity) {
    double area = section.getArea();
    double gasMass = gas * section.getGasDensity() * area;
    double oilMass = oil * section.getOilDensity() * area;
    double waterMass = water * section.getWaterDensity() * area;
    return new double[] { gasMass, oilMass, waterMass, gasMass * gasVelocity, oilMass * oilVelocity,
        waterMass * waterVelocity, 1234.0 };
  }

  private static double[] primitives(TwoFluidSection section) {
    return new double[] { section.getGasHoldup(), section.getOilHoldup(), section.getWaterHoldup(),
        section.getLiquidHoldup(), section.getGasVelocity(), section.getOilVelocity(), section.getWaterVelocity(),
        section.getLiquidVelocity(), section.getLiquidDensity(), section.getMixtureDensity(), section.getWaterCut() };
  }
}
