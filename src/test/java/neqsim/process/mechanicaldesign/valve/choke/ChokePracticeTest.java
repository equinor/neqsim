package neqsim.process.mechanicaldesign.valve.choke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.valve.ControlValveSizing_MultiphaseChoke;
import neqsim.process.mechanicaldesign.valve.LinearCharacteristic;
import neqsim.process.mechanicaldesign.valve.ValveCharacteristic;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests of the choke flow-coefficient conversions, operating-envelope screening and condition monitoring.
 *
 * @author NeqSim
 * @version 1.0
 */
public class ChokePracticeTest {

  /** The snippets of the choke practice documentation page. */
  @Test
  void documentationExamples() {
    double d = ChokeFlowCoefficient.orificeDiameterFromCv(100.0, 1.0);
    assertEquals(100.0, ChokeFlowCoefficient.cvFromOrificeDiameter(d, 1.0), 1.0e-9);
    assertEquals(1.0, ChokeFlowCoefficient.dischargeCoefficientFromCv(100.0, d), 1.0e-9);

    ChokeOperatingEnvelope.Result result = new ChokeOperatingEnvelope()
        .setTrimType(ChokeOperatingEnvelope.TrimType.PLUG_AND_CAGE)
        .setOperatingPoint(500.0, 15.0, new LinearCharacteristic()).setCleanUpSizes(4.0, 14.0)
        .setCollapseCapacity(30.0, 40.0).setOpeningTimes(120.0, 60.0).evaluate();
    assertEquals(ChokeOperatingEnvelope.Status.WARN, result.getOverallStatus());
    assertTrue(result.get("operating range").getMessage().contains("15.0"));

    double observed = ChokeFlowCoefficient.observedCvFromMassFlow(20.0, 800.0, 20.0e5);
    double theoretical = ChokeFlowCoefficient.cvAtOpening(500.0, 40.0, new LinearCharacteristic());
    ChokeConditionMonitor.Assessment a = new ChokeConditionMonitor().setDeviationTolerance(0.10).assessCv(observed,
        theoretical);
    assertEquals(observed / theoretical, a.getCapacityRatio(), 1.0e-12);

    double velocity = ChokeFlowCoefficient.throttlingVelocity(0.05, 100.0, 0.84);
    assertEquals(0.05 / ChokeFlowCoefficient.orificeAreaFromCv(100.0, 0.84), velocity, 1.0e-12);
  }

  /** A Cv of one corresponds to an orifice of about 4.65 mm, and Cv = 100 to about 46.5 mm. */
  @Test
  void cvToOrificeDiameterFollowsTheDefinition() {
    assertEquals(4.65e-3, ChokeFlowCoefficient.orificeDiameterFromCv(1.0, 1.0), 0.01e-3);
    assertEquals(46.5e-3, ChokeFlowCoefficient.orificeDiameterFromCv(100.0, 1.0), 0.05e-3);
    double d = ChokeFlowCoefficient.orificeDiameterFromCv(240.0, 0.84);
    assertEquals(240.0, ChokeFlowCoefficient.cvFromOrificeDiameter(d, 0.84), 1.0e-9);
    assertEquals(0.84, ChokeFlowCoefficient.dischargeCoefficientFromCv(240.0, d), 1.0e-12);
    // Cv = 46 224 Cd D^2 with D in metres
    assertEquals(46224.0, ChokeFlowCoefficient.cvFromOrificeDiameter(1.0, 1.0), 10.0);
  }

  /** One US gallon per minute of water at one psi is a Cv of one. */
  @Test
  void observedCvReproducesTheDefinition() {
    double cv = ChokeFlowCoefficient.observedCv(ChokeFlowCoefficient.CV1_FLOW_M3_PER_S, 1000.0,
        ChokeFlowCoefficient.PSI_PA);
    assertEquals(1.0, cv, 1.0e-9);
    assertEquals(2.0, ChokeFlowCoefficient.observedCv(2.0 * ChokeFlowCoefficient.CV1_FLOW_M3_PER_S, 1000.0,
        ChokeFlowCoefficient.PSI_PA), 1.0e-9);
  }

  /** Invalid inputs are rejected. */
  @Test
  void invalidInputsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> ChokeFlowCoefficient.orificeDiameterFromCv(0.0, 0.8));
    assertThrows(IllegalArgumentException.class, () -> ChokeFlowCoefficient.orificeDiameterFromCv(10.0, 1.2));
    assertThrows(IllegalArgumentException.class, () -> ChokeFlowCoefficient.observedCv(1.0, 1000.0, 0.0));
  }

  /** The liquid branch of the flow model has the capacity of the data-sheet Cv for the same discharge coefficient. */
  @Test
  void flowModelCapacityEqualsDataSheetCv() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("n-heptane", 1.0);
    fluid.setMixingRule(2);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    double cd = 0.84;
    SachdevaChokeFlow model = new SachdevaChokeFlow();
    model.setDischargeCoefficient(cd);
    model.setChokeDiameter(ChokeFlowCoefficient.orificeDiameterFromCv(100.0, cd));
    double massFlow = model.calculateMassFlowRate(fluid, 50.0e5, 30.0e5);
    double density = fluid.getDensity("kg/m3");
    double cv = ChokeFlowCoefficient.observedCvFromMassFlow(massFlow, density, 20.0e5);
    assertEquals(100.0, cv, 0.01);
  }

  /** The valve mechanical design converts Cv and Cd to a diameter and reports the equivalent Cv at an opening. */
  @Test
  void valveDesignExposesCvOfTheChoke() {
    SystemInterface fluid = new SystemSrkEos(320.0, 100.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-heptane", 0.3);
    fluid.setMixingRule(2);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    ThrottlingValve choke = new ThrottlingValve("choke", feed);
    ValveMechanicalDesign design = choke.getMechanicalDesign();
    design.setValveSizingStandard("Sachdeva");
    design.setChokeFlowCoefficient(100.0, 0.84);
    assertEquals(0.84, ChokeFlowCoefficient.dischargeCoefficientFromCv(100.0, design.getChokeDiameter()), 1.0e-9);
    assertEquals(100.0, design.getChokeEquivalentCv(100.0), 1.0e-9);
    assertEquals(50.0, design.getChokeEquivalentCv(50.0), 1.0e-9);

    // An eroded choke passing 30 % more than its curve is modelled with the observed Cv.
    choke.setOutletPressure(50.0, "bara");
    ControlValveSizing_MultiphaseChoke method = (ControlValveSizing_MultiphaseChoke) design.getValveSizingMethod();
    double clean = (Double) method.calcValveSize(100.0).get("massFlowRate");
    design.setChokeFlowCoefficient(130.0, 0.84);
    double eroded = (Double) method.calcValveSize(100.0).get("massFlowRate");
    assertEquals(1.3, eroded / clean, 1.0e-6);
  }

  /** Trim-specific minimum Cv fractions drive the operating-range finding. */
  @Test
  void operatingRangeDependsOnTrim() {
    ValveCharacteristic linear = new LinearCharacteristic();
    ChokeOperatingEnvelope.Result plugLow = new ChokeOperatingEnvelope()
        .setTrimType(ChokeOperatingEnvelope.TrimType.PLUG_AND_CAGE).setOperatingPoint(500.0, 15.0, linear).evaluate();
    assertEquals(ChokeOperatingEnvelope.Status.WARN, plugLow.get("operating range").getStatus());
    ChokeOperatingEnvelope.Result plugOk = new ChokeOperatingEnvelope()
        .setTrimType(ChokeOperatingEnvelope.TrimType.PLUG_AND_CAGE).setOperatingPoint(500.0, 25.0, linear).evaluate();
    assertEquals(ChokeOperatingEnvelope.Status.PASS, plugOk.get("operating range").getStatus());
    ChokeOperatingEnvelope.Result disc = new ChokeOperatingEnvelope()
        .setTrimType(ChokeOperatingEnvelope.TrimType.ROTATING_DISC).setCv(500.0, 250.0).evaluate();
    assertEquals(ChokeOperatingEnvelope.Status.WARN, disc.get("operating range").getStatus());
    assertEquals(ChokeOperatingEnvelope.Status.PASS,
        new ChokeOperatingEnvelope().setTrimType(ChokeOperatingEnvelope.TrimType.ROTATING_DISC).setCv(500.0, 350.0)
            .evaluate().get("operating range").getStatus());
  }

  /** A calculated sand-service minimum is a hard criterion for plug-and-cage trims. */
  @Test
  void sandServiceMinimumIsHard() {
    ChokeOperatingEnvelope.Result result = new ChokeOperatingEnvelope().setCv(500.0, 125.0).setSandService(0.30)
        .evaluate();
    assertEquals(ChokeOperatingEnvelope.Status.FAIL, result.get("operating range").getStatus());
    assertEquals(ChokeOperatingEnvelope.Status.FAIL, result.getOverallStatus());
  }

  /** Clean-up port size, collapse capacity, noise and opening speed checks. */
  @Test
  void otherChecksAndOverallStatus() {
    ChokeOperatingEnvelope envelope = new ChokeOperatingEnvelope();
    assertEquals(ChokeOperatingEnvelope.Status.NOT_EVALUATED, envelope.evaluate().getOverallStatus());
    envelope.setCleanUpSizes(4.0, 10.0).setCollapseCapacity(30.0, 40.0).setNoise(95.0, 85.0).setOpeningTimes(120.0,
        60.0);
    ChokeOperatingEnvelope.Result result = envelope.evaluate();
    assertEquals(ChokeOperatingEnvelope.Status.FAIL, result.get("clean-up port size").getStatus());
    assertEquals(ChokeOperatingEnvelope.Status.PASS, result.get("collapse capacity").getStatus());
    assertEquals(ChokeOperatingEnvelope.Status.WARN, result.get("noise").getStatus());
    assertEquals(ChokeOperatingEnvelope.Status.PASS, result.get("opening speed").getStatus());
    assertEquals(ChokeOperatingEnvelope.Status.NOT_EVALUATED, result.get("operating range").getStatus());
    envelope.setCollapseCapacity(50.0, 40.0).setOpeningTimes(30.0, 60.0);
    result = envelope.evaluate();
    assertEquals(ChokeOperatingEnvelope.Status.FAIL, result.get("collapse capacity").getStatus());
    assertEquals(ChokeOperatingEnvelope.Status.FAIL, result.get("opening speed").getStatus());
  }

  /** Observed Cv above theory means erosion, below means plugging; the ratio scales the collapse flow. */
  @Test
  void cvDeviationClassifiesErosionAndPlugging() {
    ChokeConditionMonitor monitor = new ChokeConditionMonitor();
    ChokeConditionMonitor.Assessment eroded = monitor.assessCv(130.0, 100.0);
    assertEquals(ChokeConditionMonitor.Condition.EROSION_SUSPECTED, eroded.getCondition());
    assertEquals(1.3, eroded.getCapacityRatio(), 1.0e-12);
    assertEquals(0.3, eroded.getDeviation(), 1.0e-12);
    assertEquals(ChokeConditionMonitor.Condition.PLUGGING_SUSPECTED, monitor.assessCv(70.0, 100.0).getCondition());
    assertEquals(ChokeConditionMonitor.Condition.NORMAL, monitor.assessCv(104.0, 100.0).getCondition());
    assertEquals(ChokeConditionMonitor.Condition.NORMAL,
        monitor.setDeviationTolerance(0.4).assessCv(130.0, 100.0).getCondition());
  }

  /** Flow in the closed position flags seat leakage. */
  @Test
  void closedPositionLeakIsDetected() {
    ChokeConditionMonitor monitor = new ChokeConditionMonitor();
    assertEquals(ChokeConditionMonitor.Condition.CLOSED_LEAK_SUSPECTED,
        monitor.assessClosedPosition(5.0, 500.0).getCondition());
    assertEquals(ChokeConditionMonitor.Condition.NORMAL, monitor.assessClosedPosition(0.0, 500.0).getCondition());
  }

  /** A series is judged by its median deviation and by whether the Cv follows the opening. */
  @Test
  void seriesAssessmentDetectsErosionAndStuckStem() {
    ValveCharacteristic linear = new LinearCharacteristic();
    double[] opening = {20.0, 30.0, 40.0, 50.0, 60.0};
    double[] eroded = new double[opening.length];
    double[] stuck = new double[opening.length];
    for (int i = 0; i < opening.length; i++) {
      eroded[i] = 1.25 * 5.0 * opening[i];
      stuck[i] = 150.0 + (i % 2);
    }
    ChokeConditionMonitor monitor = new ChokeConditionMonitor();
    ChokeConditionMonitor.Assessment erosion = monitor.assessSeries(opening, eroded, 500.0, linear);
    assertEquals(ChokeConditionMonitor.Condition.EROSION_SUSPECTED, erosion.getCondition());
    assertEquals(1.25, erosion.getCapacityRatio(), 1.0e-9);
    assertEquals(ChokeConditionMonitor.Condition.NOT_RESPONDING,
        monitor.assessSeries(opening, stuck, 500.0, linear).getCondition());
    assertEquals(ChokeConditionMonitor.Condition.INSUFFICIENT_DATA,
        monitor.assessSeries(new double[] {50.0}, new double[] {250.0}, 500.0, linear).getCondition());
  }
}
