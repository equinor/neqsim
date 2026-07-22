package neqsim.process.safety.settleout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.settleout.SettleOutPressureAnalyzer.SettleOutVerdict;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link SettleOutPressureAnalyzer}.
 *
 * @author ESOL
 */
public class SettleOutPressureAnalyzerTest {

  /**
   * The settle-out pressure of a low-pressure suction volume mixed with a smaller high-pressure discharge volume must
   * lie between the two compartment pressures.
   */
  @Test
  void settleOutPressureLiesBetweenCompartmentPressures() {
    SettleOutPressureAnalyzer analyzer = new SettleOutPressureAnalyzer();
    analyzer.addVolume("suction", 8.0, "m3", 20.0, "bara", 30.0, "C");
    analyzer.addVolume("discharge", 2.0, "m3", 90.0, "bara", 110.0, "C");
    SettleOutPressureResult result = analyzer.analyze();

    assertTrue(result.getSettleOutPressureBara() > result.getMinCompartmentPressureBara(),
        "settle-out must exceed the lowest compartment pressure");
    assertTrue(result.getSettleOutPressureBara() < result.getMaxCompartmentPressureBara(),
        "settle-out must stay below the highest compartment pressure");
    assertEquals(10.0, result.getTotalVolumeM3(), 1.0e-9);
    assertEquals(2, result.getCompartments().size());
  }

  /**
   * With an ideal-gas basis and equal temperatures, the settle-out pressure equals the volume-weighted pressure average
   * so it can be checked in closed form.
   */
  @Test
  void idealGasIsothermalMatchesVolumeWeightedAverage() {
    SettleOutPressureAnalyzer analyzer = new SettleOutPressureAnalyzer();
    analyzer.addVolume("a", 6.0, "m3", 10.0, "bara", 25.0, "C");
    analyzer.addVolume("b", 4.0, "m3", 60.0, "bara", 25.0, "C");
    SettleOutPressureResult result = analyzer.analyze();

    double expected = (10.0 * 6.0 + 60.0 * 4.0) / 10.0;
    assertEquals(expected, result.getSettleOutPressureBara(), 1.0e-6);
    assertEquals(SettleOutVerdict.NO_RATING, result.getVerdict());
  }

  /**
   * When the settle-out pressure rises above the protected suction-system rating the verdict must flag an exceedance.
   */
  @Test
  void settleOutAboveProtectedRatingFlagsExceedance() {
    SettleOutPressureAnalyzer analyzer = new SettleOutPressureAnalyzer();
    analyzer.addVolume("suction", 5.0, "m3", 25.0, "bara", 40.0, "C");
    analyzer.addVolume("discharge", 5.0, "m3", 120.0, "bara", 120.0, "C");
    analyzer.setProtectedPressureRating(50.0, "bara");
    SettleOutPressureResult result = analyzer.analyze();

    assertTrue(result.isExceedsRating(), "settle-out should exceed the 50 bara protected rating");
    assertEquals(SettleOutVerdict.EXCEEDS_RATING, result.getVerdict());
    assertTrue(result.getMarginToRatingBar() < 0.0);
  }

  /**
   * Supplying a real-gas system must still produce a finite settle-out pressure between the compartment pressures.
   */
  @Test
  void realGasSystemProducesFiniteSettleOut() {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");

    SettleOutPressureAnalyzer analyzer = new SettleOutPressureAnalyzer();
    analyzer.setGas(gas);
    analyzer.addVolume("suction", 8.0, "m3", 20.0, "bara", 30.0, "C");
    analyzer.addVolume("discharge", 2.0, "m3", 90.0, "bara", 110.0, "C");
    SettleOutPressureResult result = analyzer.analyze();

    assertTrue(result.getSettleOutPressureBara() > 20.0);
    assertTrue(result.getSettleOutPressureBara() < 90.0);
    assertTrue(result.getSettleOutZFactor() > 0.0);
    assertTrue(result.toJson().contains("settleOutPressureBara"));
  }

  /**
   * A single compartment is insufficient for a settle-out balance.
   */
  @Test
  void rejectsFewerThanTwoCompartments() {
    SettleOutPressureAnalyzer analyzer = new SettleOutPressureAnalyzer();
    analyzer.addVolume("only", 5.0, "m3", 30.0, "bara", 40.0, "C");
    assertThrows(IllegalStateException.class, analyzer::analyze);
  }

  /**
   * Non-physical compartment inputs are rejected at entry.
   */
  @Test
  void rejectsNonPhysicalCompartment() {
    SettleOutPressureAnalyzer analyzer = new SettleOutPressureAnalyzer();
    assertThrows(IllegalArgumentException.class, () -> analyzer.addVolume("bad", -1.0, "m3", 30.0, "bara", 40.0, "C"));
    assertFalse(analyzer.toString().isEmpty());
  }
}
