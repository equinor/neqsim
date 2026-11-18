package neqsim.process.mpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Synthetic export/injection allocation with a methane splitter and assumed local pressure response.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ExportInjectionMpcExampleTest {
  private static final double SCAN_SECONDS = 15.0;
  private static final int HORIZON = 24;
  private static final double FEED_KG_H = 100.0;
  private static final double NORMAL_SUCTION_BARA = 48.0;
  private static final double EXPORT_LIMIT_BARA = 100.0;

  /** Compare preview control with a pressure feedback loop on the same load trace. */
  @Test
  void previewTradesEarlyInjectionForLowerExportPressureExcursion() {
    SystemInterface methane = new SystemSrkEos(300.0, NORMAL_SUCTION_BARA);
    methane.addComponent("methane", 1.0);
    methane.setMixingRule("classic");
    Stream feed = new Stream("Suction", methane);
    feed.setFlowRate(FEED_KG_H, "kg/hr");
    Stream header = new Stream("Export pressure measurement", methane.clone());
    header.setPressure(EXPORT_LIMIT_BARA, "bara");
    Stream nomination = new Stream("Export nomination", methane.clone());
    nomination.setFlowRate(80.0, "kg/hr");
    Splitter routing = new Splitter("Export and injection", feed, 2);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(header);
    process.add(nomination);
    process.add(routing);

    ProcessLinkedMPC controller = new ProcessLinkedMPC("Gas allocation", process);
    ManipulatedVariable suction = controller.addMV(feed.getName(), "pressure", 48.0, 54.0, 1.0).setUnit("bara")
        .setPreferredValue(NORMAL_SUCTION_BARA).setPreferredWeight(1.0).setMoveWeight(0.1);
    ControlledVariable pressure = controller.addCV(header.getName(), "pressure", EXPORT_LIMIT_BARA).setUnit("bara")
        .setWeight(0.0).setSoftConstraints(Double.NEGATIVE_INFINITY, EXPORT_LIMIT_BARA).setSoftConstraintPenalty(20.0);
    DisturbanceVariable takeaway = controller.addDV(nomination.getName(), "flowRate").setUnit("kg/hr");
    controller.setSampleTime(SCAN_SECONDS);
    controller.setPredictionHorizon(HORIZON);
    controller.setControlHorizon(4);
    controller.setHorizonResponses(response(suction, pressure, -1.0), response(takeaway, pressure, -0.5));

    double basicSuction = NORMAL_SUCTION_BARA;
    double basicPressure = EXPORT_LIMIT_BARA;
    double basicExcessBarScans = 0.0;
    double mpcExcessBarScans = 0.0;
    double earlyExtraInjectionKg = 0.0;
    for (int scan = 0; scan < 16; scan++) {
      double[][] preview = new double[HORIZON][1];
      for (int k = 0; k < HORIZON; k++) {
        // The larger loss on scan 9 becomes known on scan 10.
        preview[k][0] = nominationAt(scan + k, scan >= 10);
      }
      double before = feed.getPressure("bara");
      double proposed = controller.calculate(preview)[0];
      double predictedPressure = controller.getLastHorizonPlan().getPredictedCvs()[0][0];
      double upperSlack = controller.getLastHorizonPlan().getUpperSlack()[0][0];
      assertEquals(Math.max(0.0, predictedPressure - EXPORT_LIMIT_BARA), upperSlack, 1e-6);
      assertEquals(before, feed.getPressure("bara"), 1e-9);
      controller.applyMoves();
      assertEquals(proposed, feed.getPressure("bara"), 1e-8);
      assertTrue(proposed >= 48.0 - 1e-7 && proposed <= 54.0 + 1e-7);
      assertTrue(Math.abs(proposed - before) <= 1.0 + 1e-7);

      // The baseline uses the previous pressure measurement and identical suction/slew limits.
      basicSuction = Math.max(48.0,
          Math.min(54.0, basicSuction + Math.max(-1.0, Math.min(1.0, basicPressure - EXPORT_LIMIT_BARA))));
      double actualNomination = nominationAt(scan, true);
      double basicInjection = injectionRate(basicSuction);
      basicPressure = headerPressure(FEED_KG_H - basicInjection, actualNomination);

      double injectionFraction = injectionRate(proposed) / FEED_KG_H;
      routing.setSplitFactors(new double[] {1.0 - injectionFraction, injectionFraction});
      feed.run();
      routing.run();
      double export = routing.getSplitStream(0).getFlowRate("kg/hr");
      double injection = routing.getSplitStream(1).getFlowRate("kg/hr");
      assertEquals(FEED_KG_H, export + injection, 1e-7);
      assertEquals(injectionRate(proposed), injection, 1e-7);
      double actualPressure = headerPressure(export, actualNomination);
      header.setPressure(actualPressure, "bara");
      nomination.setFlowRate(actualNomination, "kg/hr");
      if (scan < 4) {
        earlyExtraInjectionKg += (injection - basicInjection) * SCAN_SECONDS / 3600.0;
      } else if (scan < 9) {
        basicExcessBarScans += Math.max(0.0, basicPressure - EXPORT_LIMIT_BARA);
        mpcExcessBarScans += Math.max(0.0, actualPressure - EXPORT_LIMIT_BARA);
      }
      if (scan == 9) {
        // 74 -> 64 kg/h was unannounced: the independent header law gives exactly 5 bar forecast error.
        assertEquals(5.0, actualPressure - predictedPressure, 1e-6);
      } else {
        assertEquals(actualPressure, predictedPressure, 1e-6);
      }
      if (scan == 10) {
        // Even the maximum permitted suction (54 bara) exports 68 kg/h, above the 64 kg/h nomination.
        assertTrue(upperSlack >= 2.0 - 1e-6);
      }
    }
    assertTrue(earlyExtraInjectionKg > 0.001, "Some saleable gas is diverted in advance of the known upset");
    assertTrue(mpcExcessBarScans < basicExcessBarScans / 2.0,
        "The anticipatory move cuts the planned event's integrated pressure exceedance by at least half");
  }

  /**
   * Construct a local measured step trace that reaches its stationary gain after one interval.
   *
   * @param input manipulated or disturbance variable
   * @param output export pressure measurement
   * @param gain bar per unit increase in input
   * @return one-unit step trace at the controller scan interval
   */
  private static StepResponse response(MPCVariable input, ControlledVariable output, double gain) {
    return new StepResponse(input.getName(), output.getName(), new double[] {0.0, SCAN_SECONDS},
        new double[] {EXPORT_LIMIT_BARA, EXPORT_LIMIT_BARA + gain}, 1.0, EXPORT_LIMIT_BARA, SCAN_SECONDS,
        input.getUnit(), output.getUnit());
  }

  /**
   * Synthetic nomination for the next export interval.
   *
   * @param scan zero-based interval index
   * @param includeSurprise whether the unexpected change has become known
   * @return available export takeaway in kg/h
   */
  private static double nominationAt(int scan, boolean includeSurprise) {
    if (includeSurprise && scan >= 9 && scan <= 11) {
      return 64.0;
    }
    return scan >= 4 && scan <= 11 ? 74.0 : 80.0;
  }

  /**
   * Assumed injection demand for the synthetic third-stage suction setting.
   *
   * @param suctionBara suction pressure in bara
   * @return gas routed to injection in kg/h
   */
  private static double injectionRate(double suctionBara) {
    return 20.0 + 2.0 * (suctionBara - NORMAL_SUCTION_BARA);
  }

  /**
   * Local export backpressure law evaluated independently of controller predictions.
   *
   * @param exportKgH routed export rate in kg/h
   * @param nominationKgH available takeaway in kg/h
   * @return header pressure in bara
   */
  private static double headerPressure(double exportKgH, double nominationKgH) {
    return EXPORT_LIMIT_BARA + 0.5 * (exportKgH - nominationKgH);
  }
}
