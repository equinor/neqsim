package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.valve.TabulatedValveCharacteristic;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks that a tabulated opening curve gives the same valve behaviour in the Kv-based sizing path and in the
 * multiphase choke path.
 *
 * @author NeqSim
 * @version 1.0
 */
public class ThrottlingValveTabulatedCharacteristicTest {
  /** Quick-opening style choke curve: Cv in percent of the full-open value. */
  private static final double[] OPENING = {10.0, 25.0, 50.0, 75.0, 100.0};
  /** Flow coefficient at the openings. */
  private static final double[] CV = {8.0, 30.0, 65.0, 88.0, 100.0};

  private Stream gasOilStream;

  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(320.0, 100.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.10);
    fluid.addComponent("nC10", 0.05);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();
    gasOilStream = new Stream("inlet", fluid);
    gasOilStream.setFlowRate(10000.0, "kg/hr");
    gasOilStream.run();
  }

  /**
   * Transient choke flow at an opening.
   *
   * @param opening valve opening [percent]
   * @param table characteristic to use, or null for the default linear one
   * @return mass flow [kg/hr]
   */
  private double chokeFlow(double opening, TabulatedValveCharacteristic table) {
    ThrottlingValve choke = new ThrottlingValve("choke", gasOilStream);
    choke.setOutletPressure(50.0, "bara");
    choke.setCalculateSteadyState(false);
    choke.setPercentValveOpening(opening);
    ValveMechanicalDesign design = choke.getMechanicalDesign();
    design.setValveSizingStandard("Sachdeva");
    design.setChokeDiameter(1.0, "in");
    if (table != null) {
      design.setValveCharacterizationMethod(table);
    }
    choke.runTransient(0.1);
    return choke.getOutletStream().getFlowRate("kg/hr");
  }

  /** A table that equals the linear curve reproduces the default choke flow exactly. */
  @Test
  void linearTableMatchesDefaultChoke() {
    TabulatedValveCharacteristic linear = new TabulatedValveCharacteristic(new double[] {0.0, 50.0, 100.0},
        new double[] {0.0, 50.0, 100.0});
    for (double opening : new double[] {20.0, 45.0, 80.0, 100.0}) {
      assertEquals(chokeFlow(opening, null), chokeFlow(opening, linear), 1.0e-6 * chokeFlow(opening, null),
          "opening " + opening);
    }
  }

  /** The choke flow follows the area fraction of the curve, so the multiphase path honours the characteristic. */
  @Test
  void chokeFlowFollowsTheCharacteristic() {
    TabulatedValveCharacteristic table = new TabulatedValveCharacteristic(OPENING, CV);
    double full = chokeFlow(100.0, table);
    double previous = 0.0;
    for (double opening : new double[] {10.0, 25.0, 50.0, 75.0, 100.0}) {
      double q = chokeFlow(opening, table);
      assertTrue(q > previous, "flow must rise with opening");
      assertEquals(table.getOpeningFactor(opening), q / full, 0.03, "flow ratio at " + opening);
      previous = q;
    }
    // table factor 0.30 against 0.25 for the default linear curve
    assertEquals(table.getOpeningFactor(25.0) / 0.25, chokeFlow(25.0, table) / chokeFlow(25.0, null), 0.05);
  }

  /** Reverse mode of the Kv path: pressure drop times the squared curve factor is constant for a liquid. */
  @Test
  void liquidPressureDropScalesWithTheCurve() {
    SystemInterface water = new SystemSrkEos(293.15, 12.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(2);
    new ThermodynamicOperations(water).TPflash();
    Stream feed = new Stream("water", water);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.run();
    TabulatedValveCharacteristic table = new TabulatedValveCharacteristic(OPENING, CV);
    double[] openings = {40.0, 60.0, 90.0};
    double[] scaled = new double[openings.length];
    for (int i = 0; i < openings.length; i++) {
      ThrottlingValve valve = new ThrottlingValve("v" + i, feed);
      valve.setOutletPressure(8.0, "bara");
      valve.getMechanicalDesign().setValveCharacterizationMethod(table);
      valve.setPercentValveOpening(100.0);
      valve.run();
      valve.setIsCalcOutPressure(true);
      valve.setPercentValveOpening(openings[i]);
      valve.run();
      double dp = 12.0 - valve.getOutletStream().getPressure("bara");
      assertTrue(dp > 0.0, "positive pressure drop at " + openings[i]);
      scaled[i] = dp * Math.pow(table.getOpeningFactor(openings[i]), 2.0);
    }
    assertEquals(scaled[0], scaled[1], 0.05 * scaled[0]);
    assertEquals(scaled[0], scaled[2], 0.05 * scaled[0]);
  }
}
