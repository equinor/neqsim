package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link CompositionalLinePackPipe}, {@link RetrogradeDewPointScanner} and
 * {@link DewPointGuardedOutletController}.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class CompositionalLinePackPipeTest {
  /** Logger. */
  private static final Logger logger = LogManager.getLogger(CompositionalLinePackPipeTest.class);

  /**
   * Rich gas (CO2-bearing, C3+ about 4.5 mol%) with a cricondenbar near 100 bara in SRK.
   *
   * @return fluid at 10 C and 130 bara
   */
  static SystemInterface richGas() {
    SystemInterface gas = new SystemSrkEos(283.15, 130.0);
    gas.addComponent("nitrogen", 0.21);
    gas.addComponent("CO2", 7.98);
    gas.addComponent("methane", 79.38);
    gas.addComponent("ethane", 7.91);
    gas.addComponent("propane", 2.82);
    gas.addComponent("i-butane", 0.44);
    gas.addComponent("n-butane", 0.53);
    gas.addComponent("i-pentane", 0.28);
    gas.addComponent("n-pentane", 0.20);
    gas.addComponent("n-hexane", 0.11);
    gas.addComponent("n-heptane", 0.09);
    gas.addComponent("n-octane", 0.05);
    gas.addComponent("n-nonane", 0.01);
    gas.setMixingRule("classic");
    return gas;
  }

  /**
   * Builds a short test pipe fed by the rich gas.
   *
   * @param sections number of sections
   * @param length length in m
   * @param flowKgPerSecond inlet mass flow in kg/s
   * @return configured pipe
   */
  static CompositionalLinePackPipe pipe(int sections, double length, double flowKgPerSecond) {
    Stream feed = new Stream("feed", richGas());
    feed.setFlowRate(flowKgPerSecond, "kg/sec");
    feed.setTemperature(10.0, "C");
    feed.setPressure(130.0, "bara");
    feed.run();
    CompositionalLinePackPipe pipe = new CompositionalLinePackPipe("line", feed);
    pipe.setLength(length);
    pipe.setDiameter(0.5);
    pipe.setPipeWallRoughness(45e-6);
    pipe.setWallThickness(0.02);
    pipe.setNumberOfSections(sections);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setConstantSurfaceTemperature(278.15);
    return pipe;
  }

  /** Upper dew point from the scanner brackets the TP-flash phase change and stays below the cricondenbar. */
  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  void scannerBracketsPhaseBoundary() {
    SystemInterface gas = richGas();
    double pDew = RetrogradeDewPointScanner.upperDewPointPressure(gas, 278.15, 150.0);
    assertTrue(pDew > 80 && pDew < 110, "upper dew point at 5 C was " + pDew);
    SystemInterface work = gas.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(work);
    assertTrue(RetrogradeDewPointScanner.isTwoPhase(work, ops, 278.15, pDew - 0.2));
    assertFalse(RetrogradeDewPointScanner.isTwoPhase(work, ops, 278.15, pDew + 0.2));
    double pLow = RetrogradeDewPointScanner.lowerDewPointPressure(gas, 278.15, pDew);
    assertTrue(pLow > 0 && pLow < 20, "lower dew point at 5 C was " + pLow);
    double[] cb = RetrogradeDewPointScanner.cricondenbar(gas, 233.15, 303.15, 150.0);
    assertTrue(cb[0] >= pDew - 1e-6, "cricondenbar " + cb[0] + " below the 5 C dew point " + pDew);
    assertEquals(130.0, gas.getPressure(), 1e-12, "caller fluid must not be modified");
  }

  /** A stagnant line with elevation settles to hydrostatic equilibrium and conserves mass and energy. */
  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  void stagnantInclinedLineIsHydrostaticAndConservative() {
    CompositionalLinePackPipe pipe = pipe(5, 5000.0, 0.0);
    pipe.setSectionElevations(new double[] {0.0, -50.0, -100.0, -150.0, -200.0});
    pipe.setHeatTransferCoefficient(0.0);
    pipe.run(UUID.randomUUID());
    double[] p0 = pipe.getPressureProfile();
    for (int i = 0; i < 6; i++) {
      pipe.runTransient(100.0, UUID.randomUUID());
    }
    double[] p = pipe.getPressureProfile();
    double rho = pipe.totalMass() / (Math.PI * 0.25 * 0.25 * 5000.0);
    double expected = rho * 9.81 * 200.0 / 1e5; // section-centre elevations 0 and -200 m
    assertEquals(expected, p[4] - p[0], 0.03 * expected, "hydrostatic head");
    assertEquals(p0[4] - p0[0], p[4] - p[0], 0.05, "initial march is already hydrostatic");
    double[] err = pipe.getBalanceErrors();
    assertTrue(Math.abs(err[0]) < 1e-10 && err[1] < 1e-9 && Math.abs(err[2]) < 1e-10,
        "balance errors " + err[0] + ", " + err[1] + ", " + err[2]);
    assertEquals(0, pipe.getNumberOfTwoPhaseSections());
  }

  /**
   * Depressurising a single-phase rich gas below its cricondenbar forms retrograde condensate, which is enriched in
   * C3+, while all balances close; the dew-point guard keeps the outlet shut while liquid is present.
   */
  @Test
  @Timeout(value = 4, unit = TimeUnit.MINUTES)
  void blowdownFormsRetrogradeLiquidAndGuardStaysShut() {
    CompositionalLinePackPipe pipe = pipe(8, 16000.0, 0.0);
    pipe.initializeUniform(130.0, 283.15);
    assertEquals(0, pipe.getNumberOfTwoPhaseSections(), "130 bara is above the cricondenbar");
    double maxLiquid = 0;
    pipe.setOutletMassFlowRate(80.0);
    for (int i = 0; i < 24; i++) {
      pipe.runTransient(150.0, UUID.randomUUID());
      maxLiquid = Math.max(maxLiquid, pipe.getLiquidInventory("kg"));
    }
    double[] p = pipe.getPressureProfile();
    logger.info("after blowdown: Pout {} bara, liquid {} kg, two-phase sections {}", p[p.length - 1],
        pipe.getLiquidInventory("kg"), pipe.getNumberOfTwoPhaseSections());
    assertTrue(maxLiquid > 1.0, "retrograde liquid expected, max " + maxLiquid + " kg");
    assertTrue(pipe.getNumberOfTwoPhaseSections() > 0);
    assertTrue(pipe.getLiquidInventory("m3") > 0);
    assertThrows(IllegalArgumentException.class, () -> pipe.getLiquidInventory("bbl/d"));
    double[] err = pipe.getBalanceErrors();
    assertTrue(Math.abs(err[0]) < 1e-10 && err[1] < 1e-9 && Math.abs(err[2]) < 1e-10,
        "balance errors " + err[0] + ", " + err[1] + ", " + err[2]);
    assertTrue(pipe.getThermodynamicClosureResidual() < 1e-4);
    assertEquals(0.0, pipe.getCumulativeOutletLiquidMass(), 0.0, "gas-only transport delivers no liquid");

    DewPointGuardedOutletController guard = new DewPointGuardedOutletController(100.0);
    pipe.setOutletMassFlowRate(0.0);
    assertEquals(0.0, guard.update(pipe, 50.0, 600.0), 0.0, "guard must stay shut while liquid is present");
    assertFalse(guard.isOpen());
  }

  /** On a packed single-phase line above the floor the guard opens and ramps delivery. */
  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  void guardOpensOnLiquidFreeLineAboveDewPoint() {
    CompositionalLinePackPipe pipe = pipe(4, 8000.0, 0.0);
    pipe.initializeUniform(130.0, 283.15);
    DewPointGuardedOutletController guard = new DewPointGuardedOutletController(103.0);
    guard.setDownstreamSections(2);
    guard.setRampTime(3600.0);
    double flow = guard.update(pipe, 40.0, 600.0);
    assertTrue(guard.isOpen());
    assertTrue(guard.getFloorBara() >= 105.0 && guard.getFloorBara() < 130.0, "floor " + guard.getFloorBara());
    assertEquals(40.0 * 600.0 / 3600.0, flow, 1e-9, "ramped, unthrottled delivery");
    assertEquals(0, guard.getTwoPhaseDeliveryCount());
  }

  /** TwoFluidPipe liquid inventory rejects unknown units instead of silently returning cubic metres. */
  @Test
  void twoFluidPipeLiquidInventoryRejectsUnknownUnit() {
    Stream feed = new Stream("feed", richGas());
    feed.setFlowRate(10.0, "kg/sec");
    feed.run();
    TwoFluidPipe tf = new TwoFluidPipe("tf", feed);
    assertThrows(IllegalArgumentException.class, () -> tf.getLiquidInventory("kg/m3"));
  }

  /** The example in docs/process/compositional-linepack-pipe.md, verbatim. */
  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void documentationExample() {
    SystemInterface gas = new SystemSrkEos(283.15, 130.0);
    gas.addComponent("methane", 79.4);
    gas.addComponent("CO2", 8.0);
    gas.addComponent("ethane", 7.9);
    gas.addComponent("propane", 2.8);
    gas.addComponent("n-butane", 1.0);
    gas.addComponent("n-pentane", 0.5);
    gas.addComponent("n-hexane", 0.2);
    gas.addComponent("n-heptane", 0.15);
    gas.addComponent("n-octane", 0.05);
    gas.setMixingRule("classic");
    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(0.0, "kg/sec");
    feed.setTemperature(15.0, "C");
    feed.setPressure(200.0, "bara");
    feed.run();

    CompositionalLinePackPipe pipe = new CompositionalLinePackPipe("export line", feed);
    pipe.setLength(20000.0);
    pipe.setDiameter(0.5);
    pipe.setWallThickness(0.02);
    pipe.setPipeWallRoughness(45e-6);
    pipe.setNumberOfSections(8);
    pipe.setHeatTransferCoefficient(5.0);
    pipe.setConstantSurfaceTemperature(278.15);
    pipe.initializeUniform(130.0, 283.15);

    // depressurise from the outlet
    pipe.setOutletMassFlowRate(80.0);
    for (int i = 0; i < 20; i++) {
      pipe.runTransient(180.0, UUID.randomUUID());
    }
    double liquidKg = pipe.getLiquidInventory("kg");

    // restart: shut outlet, supply gas, deliver only when the line is dry and above the local dew point
    double cricondenbar = RetrogradeDewPointScanner.cricondenbar(gas, 233.15, 303.15, 150.0)[0];
    DewPointGuardedOutletController guard = new DewPointGuardedOutletController(cricondenbar);
    guard.setDownstreamSections(3);
    feed.setFlowRate(60.0, "kg/sec");
    feed.run();
    for (int i = 0; i < 20; i++) {
      pipe.setOutletMassFlowRate(guard.update(pipe, 60.0, 600.0));
      pipe.runTransient(600.0, UUID.randomUUID());
    }
    double[] balanceErrors = pipe.getBalanceErrors(); // {mass, worst component, energy}, relative

    logger.info("doc example: liquid after blowdown {} kg, guard open {}, floor {} bara, two-phase deliveries {}",
        liquidKg, guard.isOpen(), guard.getFloorBara(), guard.getTwoPhaseDeliveryCount());
    assertTrue(liquidKg > 0.0, "blowdown below the cricondenbar should form condensate");
    assertTrue(Math.abs(balanceErrors[0]) < 1e-10 && balanceErrors[1] < 1e-9 && Math.abs(balanceErrors[2]) < 1e-10);
    assertEquals(0, guard.getTwoPhaseDeliveryCount(), "no delivery while the outlet section is two-phase");
    if (guard.isOpen()) {
      assertEquals(0, pipe.getNumberOfTwoPhaseSections());
    }
  }

  /**
   * Two-fluid liquid transport: in a shut-in V-shaped line the condensate drains to the low point, the liquid stops
   * moving, nothing leaves the closed ends and all balances close.
   */
  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void condensateDrainsToLowPointWithTwoFluidLiquidTransport() {
    CompositionalLinePackPipe pipe = pipe(7, 1400.0, 0.0);
    pipe.setSectionElevations(new double[] {0.0, -4.0, -8.0, -12.0, -8.0, -4.0, 0.0});
    pipe.setHeatTransferCoefficient(0.0);
    pipe.setLiquidTransportModel(CompositionalLinePackPipe.LiquidTransportModel.STRATIFIED_TWO_FLUID);
    pipe.initializeUniform(70.0, 278.15);
    double initialLiquid = pipe.getLiquidInventory("kg");
    assertTrue(initialLiquid > 1.0 && pipe.getNumberOfTwoPhaseSections() == 7, "70 bara, 5 C is inside the envelope");
    double uniformShare = pipe.getLiquidMassProfile()[3] / initialLiquid;
    for (int i = 0; i < 40; i++) {
      pipe.runTransient(60.0, UUID.randomUUID());
    }
    double[] liquid = pipe.getLiquidMassProfile();
    double total = pipe.getLiquidInventory("kg");
    double lowShare = liquid[3] / total;
    logger.info("pooling: liquid {} -> {} kg, low-point share {} -> {}, end sections {} and {} kg", initialLiquid,
        total, uniformShare, lowShare, liquid[0], liquid[6]);
    assertTrue(lowShare > 3.0 * uniformShare, "condensate must collect at the low point, share " + lowShare);
    assertTrue(liquid[0] < 0.2 * initialLiquid / 7 && liquid[6] < 0.2 * initialLiquid / 7, "high ends must drain");
    assertEquals(liquid[2], liquid[4], 0.05 * Math.max(liquid[2], 1e-9) + 1e-6, "symmetric line, symmetric pool");
    double fastest = 0.0;
    for (double u : pipe.getLiquidFaceVelocityProfile()) {
      fastest = Math.max(fastest, Math.abs(u));
    }
    logger.info("pooling: fastest remaining film velocity {} m/s", fastest);
    assertEquals(0.0, pipe.getCumulativeOutletLiquidMass(), 0.0);
    double[] err = pipe.getBalanceErrors();
    assertTrue(Math.abs(err[0]) < 1e-10 && err[1] < 1e-9 && Math.abs(err[2]) < 1e-10,
        "balance errors " + err[0] + ", " + err[1] + ", " + err[2]);
  }

  /**
   * Two-fluid liquid transport during blowdown: liquid leaves through the outlet only as the force balance allows, the
   * amount lies between the immobile and no-slip bounds, and the balances close.
   */
  @Test
  @Timeout(value = 6, unit = TimeUnit.MINUTES)
  void twoFluidLiquidDeliveryLiesBetweenBounds() {
    double[] delivered = new double[3];
    for (int mode = 0; mode < 3; mode++) {
      CompositionalLinePackPipe pipe = pipe(8, 16000.0, 0.0);
      if (mode == 1) {
        pipe.setLiquidTransportModel(CompositionalLinePackPipe.LiquidTransportModel.STRATIFIED_TWO_FLUID);
      } else if (mode == 2) {
        pipe.setLiquidSlipFactor(1.0);
      }
      pipe.initializeUniform(130.0, 283.15);
      pipe.setOutletMassFlowRate(80.0);
      for (int i = 0; i < 24; i++) {
        pipe.runTransient(150.0, UUID.randomUUID());
      }
      delivered[mode] = pipe.getCumulativeOutletLiquidMass();
      double[] err = pipe.getBalanceErrors();
      assertTrue(Math.abs(err[0]) < 1e-10 && err[1] < 1e-9 && Math.abs(err[2]) < 1e-10,
          "mode " + mode + " balance errors " + err[0] + ", " + err[1] + ", " + err[2]);
    }
    logger.info("liquid delivered in blowdown: immobile {} kg, two-fluid {} kg, no-slip {} kg", delivered[0],
        delivered[1], delivered[2]);
    assertEquals(0.0, delivered[0], 0.0);
    assertTrue(delivered[2] > 0.0, "no-slip transport must deliver liquid");
    assertTrue(delivered[1] >= 0.0 && delivered[1] <= delivered[2] * 1.0001,
        "two-fluid delivery " + delivered[1] + " must not exceed the no-slip bound " + delivered[2]);
  }
}
