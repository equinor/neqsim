package neqsim.process.mechanicaldesign.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.heatexchanger.PlateHeatExchangerDesignCalculator.ChannelResult;
import neqsim.process.mechanicaldesign.heatexchanger.PlateHeatExchangerDesignCalculator.Rating;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/** Tests correlation references, physical balances and the executable plate-cooler guide. */
class PlateHeatExchangerDesignCalculatorTest extends NeqSimTest {
  private static final Logger logger = LogManager.getLogger(PlateHeatExchangerDesignCalculatorTest.class);

  /** Representative assumptions, not a reconstruction of an undisclosed vendor plate pattern. */
  private PlateHeatExchangerDesignCalculator cooler() {
    PlateHeatExchangerDesignCalculator calc = new PlateHeatExchangerDesignCalculator();
    calc.setNumberOfPlates(721);
    calc.setFrameCapacityPlates(869);
    calc.setBoltedCapacityPlates(721);
    calc.setPlateArea(1.49);
    calc.setEffectiveFlowWidth(0.65);
    calc.setPlateSpacing(0.003);
    calc.setSurfaceEnlargementFactor(1.18);
    calc.setChevronAngle(60.0);
    calc.setPlateThickness(0.0006);
    calc.setPlateConductivity(16.0);
    calc.setHotPortDiameter(0.20);
    calc.setColdPortDiameter(0.20);
    calc.setPortLossCoefficient(1.5);
    calc.setHotSide(844230.0 / 3600.0, 37.0, 1055.0, 0.0025, 3560.0, 0.45);
    calc.setColdSide(765000.0 / 3600.0, 10.0, 1025.0, 0.00108, 4020.0, 0.60);
    return calc;
  }

  @Test
  void martinMatchesIndependentPublishedImplementations() {
    // fluids.friction documentation and ht.conv_plate documentation, Martin 1999 variant.
    assertEquals(0.7818916308365043, PlateHeatExchangerDesignCalculator.martinDarcyFrictionFactor(20000.0, 45.0),
        1e-12);
    assertEquals(30.427601053757, PlateHeatExchangerDesignCalculator.martinNusseltNumber(2000.0, 0.7, 45.0), 1e-10);
    // Independent appendix evaluation on the laminar branch (Re < 2000).
    assertEquals(0.9120699098338262, PlateHeatExchangerDesignCalculator.martinDarcyFrictionFactor(1000.0, 45.0), 1e-12);
  }

  @Test
  void representativeCoolerMeetsIssueThermalTargetsAndConservesEnergy() {
    PlateHeatExchangerDesignCalculator calc = cooler();
    Rating r = calc.calculate();
    assertEquals(17520000.0, r.getDuty(), 0.05 * 17520000.0);
    assertEquals(2589.0, r.getOverallU(), 0.10 * 2589.0);
    assertEquals(16.0, r.getHotOutletTemperature(), 0.3);
    assertEquals(30.5, r.getColdOutletTemperature(), 0.3);
    assertEquals(r.getDuty(), 844230.0 / 3600.0 * 3560.0 * (37.0 - r.getHotOutletTemperature()), 1e-7);
    assertEquals(r.getDuty(), 765000.0 / 3600.0 * 4020.0 * (r.getColdOutletTemperature() - 10.0), 1e-7);
    assertEquals(719 * 1.49, r.getArea(), 1e-10);
    assertEquals(r.getArea() * r.getOverallU(), r.getUA(), 1e-9);
    assertEquals(360, r.getHotSide().getChannelsPerPass());
    assertEquals(360, r.getColdSide().getChannelsPerPass());
    assertTrue(r.isWithinCorrelationRange());
    // A plausibility screen only: vendor ports, angles and gaps were not supplied in #3763.
    assertEquals(89200.0, r.getHotSide().getTotalPressureDrop(), 0.15 * 89200.0);
    assertEquals(67100.0, r.getColdSide().getTotalPressureDrop(), 0.15 * 67100.0);
    logger.info("Representative plate cooler: Q={} W, U={} W/(m2 K), hot/cold outlets={} / {} C, dp={} / {} Pa",
        r.getDuty(), r.getOverallU(), r.getHotOutletTemperature(), r.getColdOutletTemperature(),
        r.getHotSide().getTotalPressureDrop(), r.getColdSide().getTotalPressureDrop());
  }

  @Test
  void channelAndPortPressureDropUseDarcyAndTotalSideFlow() {
    PlateHeatExchangerDesignCalculator calc = cooler();
    ChannelResult r = calc.calculateHydraulics(true);
    double expectedVelocity = (844230.0 / 3600.0) / (1055.0 * 360 * 0.003 * 0.65);
    assertEquals(expectedVelocity, r.getVelocity(), 1e-12);
    assertEquals(2 * 0.003 / 1.18, calc.getHydraulicDiameter(), 1e-12);
    assertEquals(1.49 / (0.65 * 1.18), calc.getEffectiveFlowLength(), 1e-12);
    double expectedChannelDrop = r.getDarcyFrictionFactor() * calc.getEffectiveFlowLength()
        / calc.getHydraulicDiameter() * 1055.0 * expectedVelocity * expectedVelocity / 2.0;
    assertEquals(expectedChannelDrop, r.getChannelPressureDrop(), 1e-8);
    assertEquals(1.5 * 1055.0 * r.getPortVelocity() * r.getPortVelocity() / 2.0, r.getPortPressureDrop(), 1e-8);
    calc.setHotPortDiameter(0.4);
    ChannelResult enlargedPort = calc.calculateHydraulics(true);
    assertEquals(r.getPortPressureDrop() / 16, enlargedPort.getPortPressureDrop(), 1e-8);
    assertEquals(r.getChannelPressureDrop(), enlargedPort.getChannelPressureDrop(), 1e-8);
    assertEquals(r.getHeatTransferCoefficient(), enlargedPort.getHeatTransferCoefficient(), 1e-8);
    double diameter = PlateHeatExchangerDesignCalculator.sizePortDiameter(844230.0 / 3600.0, 1055, 3.0);
    calc.setHotPortDiameter(diameter);
    assertEquals(3.0, calc.calculateHydraulics(true).getPortVelocity(), 1e-12);
  }

  @Test
  void frameAndBoltsAreIndependentAndRefillRecalculatesFilmCoefficients() {
    PlateHeatExchangerDesignCalculator calc = cooler();
    Rating installed = calc.calculate();
    assertEquals(148, calc.getFrameSparePlates());
    assertEquals(148 * 1.49, calc.getFrameAdditionalArea(), 1e-10);
    assertEquals(0, calc.getAdditionalPlatesWithoutBoltReplacement());
    calc.setNumberOfPlates(869);
    assertThrows(IllegalArgumentException.class, calc::calculate);
    calc.setBoltedCapacityPlates(869);
    Rating expanded = calc.calculate();
    assertEquals(0, calc.getFrameSparePlates());
    assertEquals(0, calc.getAdditionalPlatesWithoutBoltReplacement());
    assertEquals(867.0 / 719.0, expanded.getArea() / installed.getArea(), 1e-12);
    assertTrue(expanded.getOverallU() < installed.getOverallU());
    assertTrue(expanded.getDuty() > installed.getDuty());
    assertTrue(expanded.getHotSide().getChannelPressureDrop() < installed.getHotSide().getChannelPressureDrop());
    assertEquals(installed.getHotSide().getPortPressureDrop(), expanded.getHotSide().getPortPressureDrop(), 1e-8);
    assertEquals(719 * 1.49, installed.getArea(), 1e-10); // earlier snapshot is unchanged
  }

  @Test
  void unknownCapacityIsExplicitAndNeverUnlimited() {
    PlateHeatExchangerDesignCalculator calc = cooler();
    calc.setFrameCapacityPlates(0);
    calc.setBoltedCapacityPlates(0);
    assertThrows(IllegalStateException.class, calc::getFrameSparePlates);
    assertThrows(IllegalStateException.class, calc::getAdditionalPlatesWithoutBoltReplacement);
    assertNull(calc.toMap().get("frameSparePlates"));
    assertTrue(calc.toJson().contains("\"additionalPlatesWithoutBoltReplacement\": null"));
  }

  @Test
  void foulingReadsCurrentResistanceWithoutAdvancingTime() {
    PlateHeatExchangerDesignCalculator calc = cooler();
    Rating clean = calc.calculate();
    FoulingModel model = FoulingModel.createCoolingWaterModel(0.0002, 720.0);
    model.advanceTime(720.0);
    calc.setColdFoulingModel(model);
    FoulingModel fixed = new FoulingModel();
    fixed.setFixedFoulingResistance(0.00005);
    calc.setHotFoulingModel(fixed);
    Rating fouled = calc.calculate();
    assertEquals(1.0 / clean.getOverallU() + 0.0002 * (1 - Math.exp(-1)) + 0.00005, 1.0 / fouled.getOverallU(), 1e-12);
    assertEquals(clean.getOverallU(), fouled.getCleanU(), 1e-10);
    assertTrue(fouled.getDuty() < clean.getDuty());
    assertEquals(fouled.getDuty(), calc.calculate().getDuty(), 1e-10);
    assertEquals(720.0, model.getOperatingTimeHours(), 1e-10);
    model.advanceTime(720.0);
    assertTrue(calc.calculate().getOverallU() < fouled.getOverallU());
    fixed.setFixedFoulingResistance(-1.0);
    assertThrows(IllegalArgumentException.class, calc::calculate);
  }

  @Test
  void ntuLimitsAndNearEqualCapacityRatesRemainStable() {
    assertEquals(0, PlateHeatExchangerDesignCalculator.counterflowEffectiveness(0, 1));
    assertEquals(0.5, PlateHeatExchangerDesignCalculator.counterflowEffectiveness(1, 1), 1e-15);
    assertEquals(0.5, PlateHeatExchangerDesignCalculator.counterflowEffectiveness(1, 1 - 1e-14), 1e-14);
    assertEquals(-Math.expm1(-2), PlateHeatExchangerDesignCalculator.counterflowEffectiveness(2, 0), 1e-15);
    assertEquals(1.0, PlateHeatExchangerDesignCalculator.counterflowEffectiveness(1e12, 0.5), 1e-12);
    assertEquals(1e-15, PlateHeatExchangerDesignCalculator.counterflowEffectiveness(1e-15, 0.9), 1e-29);
  }

  @Test
  void zeroDrivingTemperatureAndZeroFlowReturnZeroDuty() {
    PlateHeatExchangerDesignCalculator calc = cooler();
    calc.setHotSide(100, 10, 1000, 0.001, 4200, 0.6);
    Rating equal = calc.calculate();
    assertEquals(0, equal.getDuty());
    assertEquals(10, equal.getHotOutletTemperature());
    assertEquals(10, equal.getColdOutletTemperature());
    calc.setHotSide(0, 37, 1000, 0.001, 4200, 0.6);
    Rating stopped = calc.calculate();
    assertEquals(0, stopped.getDuty());
    assertEquals(0, stopped.getUA());
    assertEquals(0, stopped.getHotSide().getTotalPressureDrop());
    assertEquals(37, stopped.getHotOutletTemperature());
    assertEquals(10, stopped.getColdOutletTemperature());
    assertFalse(stopped.isWithinCorrelationRange());
    assertEquals(0, PlateHeatExchangerDesignCalculator.sizePortDiameter(0, 1000, 3));
  }

  @Test
  void tinyAvailableTemperatureDifferenceHasFiniteDutyAndOutlets() {
    PlateHeatExchangerDesignCalculator calc = cooler();
    calc.setHotSide(844230.0 / 3600.0, 10.0 + 1e-10, 1055, 0.0025, 3560, 0.45);
    Rating r = calc.calculate();
    assertTrue(r.getDuty() > 0 && r.getDuty() < 1e-3);
    assertTrue(r.getHotOutletTemperature() >= 10);
    assertTrue(r.getColdOutletTemperature() <= 10 + 1e-10);
  }

  @Test
  void multiplePassesHaveIntegralChannelsAndDoNotPretendToBeCounterflow() {
    PlateHeatExchangerDesignCalculator calc = cooler();
    ChannelResult single = calc.calculateHydraulics(true);
    calc.setHotPasses(2);
    calc.setColdPasses(3);
    ChannelResult multi = calc.calculateHydraulics(true);
    assertEquals(180, multi.getChannelsPerPass());
    assertEquals(2 * single.getVelocity(), multi.getVelocity(), 1e-12);
    assertEquals(2 * single.getPortPressureDrop(), multi.getPortPressureDrop(), 1e-8);
    assertEquals(120, calc.calculateHydraulics(false).getChannelsPerPass());
    assertThrows(UnsupportedOperationException.class, calc::calculate);
    calc.setHotPasses(7);
    assertThrows(IllegalArgumentException.class, () -> calc.calculateHydraulics(true));
  }

  @Test
  void evenPlateCountAllocatesExtraChannelToColdSide() {
    PlateHeatExchangerDesignCalculator calc = cooler();
    calc.setNumberOfPlates(720);
    assertEquals(359, calc.getHotChannelCount());
    assertEquals(360, calc.getColdChannelCount());
    assertEquals(718 * 1.49, calc.calculate().getArea(), 1e-10);
  }

  @Test
  void flagsExtrapolationAndRespondsToChevronAngle() {
    PlateHeatExchangerDesignCalculator calc = cooler();
    Rating sixty = calc.calculate();
    calc.setChevronAngle(45);
    Rating fortyFive = calc.calculate();
    assertTrue(fortyFive.getOverallU() < sixty.getOverallU());
    assertTrue(fortyFive.getHotSide().getChannelPressureDrop() < sixty.getHotSide().getChannelPressureDrop());
    calc.setHotSide(1, 37, 1055, 0.0025, 3560, 0.45);
    assertFalse(calc.calculate().isWithinCorrelationRange());
  }

  @Test
  void invalidInputsFailBeforeReturningAnyRating() {
    assertThrows(IllegalStateException.class, () -> new PlateHeatExchangerDesignCalculator().calculate());
    PlateHeatExchangerDesignCalculator calc = cooler();
    assertThrows(IllegalArgumentException.class, () -> calc.setHotSide(-1, 37, 1000, 0.001, 4000, 0.6));
    assertThrows(IllegalArgumentException.class, () -> calc.setHotSide(1, Double.NaN, 1000, 0.001, 4000, 0.6));
    assertThrows(IllegalArgumentException.class, () -> calc.setHotSide(1, 37, 1000, 0, 4000, 0.6));
    assertThrows(IllegalArgumentException.class,
        () -> calc.setHotSide(1, 37, 1000, 0.001, Double.POSITIVE_INFINITY, 0.6));
    calc.setPlateSpacing(0);
    assertThrows(IllegalArgumentException.class, calc::calculate);
    calc.setPlateSpacing(0.003);
    calc.setNumberOfPlates(2);
    assertThrows(IllegalArgumentException.class, calc::calculate);
    calc.setNumberOfPlates(721);
    calc.setFrameCapacityPlates(720);
    assertThrows(IllegalArgumentException.class, calc::calculate);
    calc.setFrameCapacityPlates(869);
    calc.setColdPortDiameter(Double.NaN);
    assertThrows(IllegalArgumentException.class, calc::calculate);
    assertThrows(IllegalArgumentException.class,
        () -> PlateHeatExchangerDesignCalculator.martinDarcyFrictionFactor(0, 45));
    assertThrows(IllegalArgumentException.class,
        () -> PlateHeatExchangerDesignCalculator.martinNusseltNumber(1000, 7, 90));
    assertThrows(IllegalArgumentException.class,
        () -> PlateHeatExchangerDesignCalculator.counterflowEffectiveness(1, 1.1));
  }

  @Test
  void streamSnapshotAndSerializationPreserveRatingWithoutMutatingFeed() throws Exception {
    SystemSrkCPAstatoil water = new SystemSrkCPAstatoil(310.15, 5.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    Stream hot = new Stream("hot water", water);
    hot.setFlowRate(10, "kg/sec");
    hot.run();
    Stream cold = hot.clone("cold water");
    cold.setTemperature(283.15);
    cold.run();
    PlateHeatExchangerDesignCalculator calc = new PlateHeatExchangerDesignCalculator();
    calc.setHotStream(hot);
    calc.setColdStream(cold);
    Rating r = calc.calculate();
    assertTrue(r.getDuty() > 0);
    assertEquals(310.15, hot.getTemperature(), 1e-10);
    assertEquals(283.15, cold.getTemperature(), 1e-10);
    hot.setTemperature(320.15);
    assertEquals(r.getDuty(), calc.calculate().getDuty(), 1e-8); // snapshot is independent
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(calc);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      PlateHeatExchangerDesignCalculator copy = (PlateHeatExchangerDesignCalculator) in.readObject();
      assertEquals(r.getDuty(), copy.calculate().getDuty(), 1e-8);
      assertNotNull(copy.toMap().get("hotSide"));
    }
    assertThrows(IllegalArgumentException.class, () -> calc.setColdStream(null));
  }
}
