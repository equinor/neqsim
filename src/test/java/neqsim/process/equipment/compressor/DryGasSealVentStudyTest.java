package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.process.equipment.compressor.DryGasSealVentStudy.Result;
import neqsim.process.equipment.compressor.DryGasSealVentStudy.Vent;

/**
 * Independent junction balances and off-design cases for a synthetic tandem dry gas seal.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class DryGasSealVentStudyTest {
  private static final double EPS = 1e-9;

  /**
   * Creates a vent with 100 bar per (kg/s) installed resistance.
   *
   * @param receiverBara zero-flow pressure in bara
   * @return synthetic rating
   */
  private Vent line(double receiverBara) {
    return new Vent(new double[] {receiverBara, receiverBara + 3}, new double[] {0, 0.030});
  }

  /**
   * Creates a signed secondary-seal rating with a linear analytical reference.
   *
   * @param primary primary vent rating
   * @param secondary secondary assembly rating
   * @param resistance bar per (kg/s)
   * @return hydraulic configuration
   */
  private DryGasSealVentStudy study(Vent primary, Vent secondary, double resistance) {
    return new DryGasSealVentStudy(primary, secondary, new double[] {-0.02, 0, 0.02},
        new double[] {-0.02 * resistance, 0, 0.02 * resistance});
  }

  /** Checks the documented example, all four intact/failed face combinations and both component balances. */
  @Test
  void faceFailuresAndContainmentBalance() {
    Vent flare = new Vent(new double[] {1.1, 4.1}, new double[] {0, 0.030});
    Vent atmosphere = new Vent(new double[] {1.0, 4.0}, new double[] {0, 0.030});
    DryGasSealVentStudy failed = new DryGasSealVentStudy(flare, atmosphere, new double[] {-0.020, 0, 0.020},
        new double[] {-0.4, 0, 0.4});
    Result combined = failed.evaluate(50, 0.010, 0.0002, 0.0003);
    assertEquals(0.00495454545, combined.transferKgS, EPS);
    for (double resistance : new double[] {2000, 20}) {
      for (double leak : new double[] {0.0008, 0.010}) {
        double primaryLoad = leak + 0.0002;
        double q = (0.1 + 100 * (primaryLoad - 0.0003)) / (resistance + 200);
        Result result = study(flare, atmosphere, resistance).evaluate(50, leak, 0.0002, 0.0003);
        assertEquals(q, result.transferKgS, EPS);
        assertEquals(1.1 + 100 * (primaryLoad - q), result.primaryBara, EPS);
        assertEquals(1.0 + 100 * (0.0003 + q), result.secondaryBara, EPS);
        assertEquals(primaryLoad + 0.0003, result.flareKgS + result.atmosphereKgS + result.bearingKgS, EPS);
        assertEquals(leak, result.flareProcessKgS + result.atmosphereProcessKgS + result.bearingProcessKgS, EPS);
        assertEquals(q * leak / primaryLoad, result.atmosphereProcessKgS, EPS);
      }
    }
    assertEquals(1.5 - combined.secondaryBara, combined.minimumPressureMarginBar(4, 1.5, 1, 1), EPS);
  }

  /** Checks separate process gas at the bearing after a failed separation seal and loss of purge. */
  @Test
  void separationFailureAndPurgeLoss() {
    Vent bearingLeak = new Vent(new double[] {1, 2, 3}, new double[] {0, 0.005, 0.010}, new double[] {0, 0.002, 0.010});
    DryGasSealVentStudy caseStudy = study(Vent.blocked(), bearingLeak, 20);
    Result result = caseStudy.evaluate(50, 0.006, 0.001, 0);
    assertEquals(2, result.secondaryBara, EPS);
    assertEquals(2.14, result.primaryBara, EPS);
    assertEquals(0, result.flareKgS, EPS);
    assertEquals(0.005, result.atmosphereKgS, EPS);
    assertEquals(0.002, result.bearingKgS, EPS);
    assertEquals(0.006 * 2 / 7, result.bearingProcessKgS, EPS);
    assertEquals(0.006 * 5 / 7, result.atmosphereProcessKgS, EPS);
    Result lostSupply = caseStudy.evaluate(50, 0.007, 0, 0);
    assertEquals(lostSupply.bearingKgS, lostSupply.bearingProcessKgS, EPS);
  }

  /** Checks reverse flow when the secondary vent is blocked and when only outlet backpressures differ. */
  @Test
  void reverseSecondarySealLoading() {
    Result blocked = study(line(1), Vent.blocked(), 20).evaluate(50, 0.005, 0, 0.003);
    assertEquals(-0.003, blocked.transferKgS, EPS);
    assertEquals(1.8, blocked.primaryBara, EPS);
    assertEquals(1.86, blocked.secondaryBara, EPS);
    assertEquals(0.005, blocked.flareProcessKgS, EPS);
    assertEquals(0, blocked.atmosphereKgS, EPS);
    assertEquals(-0.01, blocked.minimumPressureMarginBar(3, 3, 2, 0.05), EPS);
    Result reverse = study(line(1), line(1.5), 100).evaluate(50, 0.002, 0, 0.004);
    assertEquals(-0.7 / 300, reverse.transferKgS, EPS);
    assertEquals(0, reverse.atmosphereProcessKgS, EPS);
    assertEquals(0.002, reverse.flareProcessKgS, EPS);
  }

  /** Verifies closure and table ownership at interior breakpoints and with zero loads. */
  @Test
  void tightStageTableOwnershipAndZeroLoad() {
    double[] pressure = {1, 1.5, 3};
    double[] flow = {0, 0.005, 0.01};
    Vent vent = new Vent(pressure, flow);
    DryGasSealVentStudy tight = new DryGasSealVentStudy(vent, line(1), new double[] {0}, new double[] {0});
    pressure[1] = 100;
    flow[1] = 0;
    Result result = tight.evaluate(10, 0.005, 0, 0.003);
    assertEquals(1.5, result.primaryBara, EPS);
    assertEquals(1.3, result.secondaryBara, EPS);
    assertEquals(0, result.transferKgS, EPS);
    Result idle = study(vent, line(1), 20).evaluate(1, 0, 0, 0);
    assertEquals(1, idle.primaryBara, EPS);
    assertEquals(0, idle.flareProcessKgS, EPS);
  }

  /** Rejects unrated capacities, isolated cavities, unsupported backflow and incompatible source pressure. */
  @Test
  void unresolvedCasesCannotPass() {
    Vent vent = line(1);
    assertThrows(IllegalStateException.class, () -> study(vent, vent, 20).evaluate(50, 0.1, 0, 0));
    assertThrows(IllegalStateException.class, () -> study(vent, vent, 20).evaluate(1, 0.01, 0, 0));
    assertThrows(IllegalStateException.class, () -> study(Vent.blocked(), Vent.blocked(), 20).evaluate(50, 0, 0, 0));
    assertThrows(IllegalStateException.class, () -> study(line(4), vent, 20).evaluate(50, 0, 0, 0));
    DryGasSealVentStudy isolated = new DryGasSealVentStudy(vent, Vent.blocked(), new double[] {0}, new double[] {0});
    assertThrows(IllegalStateException.class, () -> isolated.evaluate(10, 0.003, 0, 0));
    DryGasSealVentStudy forwardOnly = new DryGasSealVentStudy(vent, line(1.5), new double[] {0, 0.03},
        new double[] {0, 1});
    assertThrows(IllegalStateException.class, () -> forwardOnly.evaluate(50, 0.002, 0, 0.004));
  }

  /** Checks a NeqSim nitrogen pipe rating against the integrated compressible laminar Poiseuille equation. */
  @Test
  void pipeRatingAndLargerBore() {
    SystemInterface gas = new SystemSrkEos(300, 2);
    gas.addComponent("nitrogen", 1.0);
    gas.setMixingRule("classic");
    double rate = 0.00001;
    double diameter = 0.012;
    double length = 200;
    Vent pipe = Vent.fromGasPipe(gas, 1, length, diameter, 1e-5, new double[] {0, rate, 2 * rate});
    Vent larger = Vent.fromGasPipe(gas, 1, length, 2 * diameter, 1e-5, new double[] {0, rate, 2 * rate});
    assertEquals(2, gas.getPressure(), EPS);
    gas.setPressure(1);
    new ThermodynamicOperations(gas).TPflash();
    gas.initProperties();
    double expected = Math.sqrt(1e10 + 256 * gas.getViscosity("Pas") * length * rate * 1e5
        / (Math.PI * Math.pow(diameter, 4) * gas.getDensity("kg/m3"))) / 1e5;
    Result base = new DryGasSealVentStudy(pipe, line(1), new double[] {0}, new double[] {0}).evaluate(20, rate, 0, 0);
    Result enlarged = new DryGasSealVentStudy(larger, line(1), new double[] {0}, new double[] {0}).evaluate(20, rate, 0,
        0);
    assertEquals(expected, base.primaryBara, EPS);
    assertTrue(enlarged.primaryBara < base.primaryBara);
    assertThrows(IllegalArgumentException.class,
        () -> Vent.fromGasPipe(gas, 1, length, diameter, 1e-5, new double[] {0, 1}));
    SystemInterface water = new SystemSrkEos(290, 1);
    water.addComponent("water", 1);
    water.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class,
        () -> Vent.fromGasPipe(water, 1, length, diameter, 1e-5, new double[] {0, rate}));
  }

  /** Verifies rejected input tables and use of absolute pressure. */
  @Test
  void rejectsInvalidRatings() {
    assertThrows(IllegalArgumentException.class, () -> new Vent(new double[] {1, 0}, new double[] {0, 1}));
    assertThrows(IllegalArgumentException.class, () -> new Vent(new double[] {1, 2}, new double[] {0, Double.NaN}));
    assertThrows(IllegalArgumentException.class, () -> new Vent(new double[] {1, 2}, new double[] {0, 0}));
    assertThrows(IllegalArgumentException.class,
        () -> new Vent(new double[] {1, 2}, new double[] {0.1, 0}, new double[] {0, 1}));
    assertThrows(IllegalArgumentException.class,
        () -> new DryGasSealVentStudy(line(1), line(1), new double[] {-1, 1}, new double[] {-1, 1}));
    assertThrows(IllegalArgumentException.class, () -> study(line(1), line(1), 2).evaluate(Double.NaN, 1, 0, 0));
  }
}
