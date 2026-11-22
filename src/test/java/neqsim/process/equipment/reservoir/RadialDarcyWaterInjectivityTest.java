package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity.Viscosity;
import neqsim.thermo.util.steam.Iapws_if97;

/**
 * Analytical and property-boundary checks for the shared radial-Darcy water law.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class RadialDarcyWaterInjectivityTest {
  /** The configured basis reproduces the radial-Darcy unit conversion exactly. */
  @Test
  void matchesConfiguredAnalyticalRate() {
    RadialDarcyWaterInjectivity law = RadialDarcyWaterInjectivity.configured(100.0, 20.0, 2.0, 500.0, 0.1, 0.5, 1.02);
    RadialDarcyWaterInjectivity.Result result = law.evaluate(100.0, 150.0);
    double expectedIndex = 2.0 * Math.PI * 100.0 * 9.869e-16 * 20.0 / (0.5e-3 * 1.02 * (Math.log(500.0 / 0.1) + 2.0))
        * 86400.0 * 1.0e5;
    assertEquals(expectedIndex, result.getInjectivitySm3PerDayPerBar(), 1.0e-12);
    assertEquals(expectedIndex * 50.0, result.getStandardVolumeRateSm3PerDay(), 1.0e-10);
    assertTrue(Double.isNaN(result.getStandardDensityKgPerSm3()));
    assertEquals(0.0, law.evaluate(100.0, 90.0).getStandardVolumeRateSm3PerDay(), 0.0);
  }

  /** The ordinary-water basis exposes the exact IF97/viscosity evidence used by the rate law. */
  @Test
  void exposesIapwsPropertyEvidence() {
    RadialDarcyWaterInjectivity law = RadialDarcyWaterInjectivity.iapwsPureWater(75.0, 15.0, 1.0, 400.0, 0.1, 300.0);
    RadialDarcyWaterInjectivity.Result result = law.evaluate(100.0, 160.0);
    double standardVolume = Iapws_if97.v_pt(1.01325 / 10.0, 288.15);
    double reservoirVolume = Iapws_if97.v_pt(100.0 / 10.0, 300.0);
    assertEquals(Viscosity.calcWaterViscosity(300.0, 100.0), result.getViscosityCp(), 0.0);
    assertEquals(reservoirVolume / standardVolume, result.getFormationVolumeFactor(), 1.0e-15);
    assertEquals(1.0 / standardVolume, result.getStandardDensityKgPerSm3(), 1.0e-10);
    assertEquals(RadialDarcyWaterInjectivity.PvtMode.IAPWS_IF97_PURE_WATER, result.getPvtMode());
    assertEquals(300.0, result.getTemperatureK(), 0.0);
    assertEquals(100.0, result.getReservoirPressureBara(), 0.0);
    assertEquals(160.0, result.getBottomholePressureBara(), 0.0);
  }

  /** Invalid geometry, PVT and non-liquid IF97 states fail before returning rate evidence. */
  @Test
  void rejectsUnqualifiedStates() {
    assertThrows(IllegalArgumentException.class,
        () -> RadialDarcyWaterInjectivity.configured(100.0, 20.0, 0.0, 0.1, 0.1, 0.5, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> RadialDarcyWaterInjectivity.configured(100.0, 20.0, 0.0, 500.0, 0.1, 0.0, 1.0));
    assertThrows(IllegalArgumentException.class,
        () -> RadialDarcyWaterInjectivity.iapwsPureWater(100.0, 20.0, 0.0, 500.0, 0.1, 274.0));
    RadialDarcyWaterInjectivity law = RadialDarcyWaterInjectivity.iapwsPureWater(100.0, 20.0, 0.0, 500.0, 0.1, 300.0);
    assertThrows(IllegalArgumentException.class, () -> law.evaluate(0.0, 150.0));
    assertThrows(IllegalArgumentException.class, () -> law.evaluate(701.0, 750.0));
    RadialDarcyWaterInjectivity vaporState = RadialDarcyWaterInjectivity.iapwsPureWater(100.0, 20.0, 0.0, 500.0, 0.1,
        500.0);
    assertThrows(IllegalArgumentException.class, () -> vaporState.evaluate(10.0, 150.0));
  }
}
