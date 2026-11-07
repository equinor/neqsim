package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.driver.ElectricMotorDriver;
import neqsim.process.equipment.energy.CompressorPowerTrain;
import neqsim.process.equipment.energy.ElectricMotor;
import neqsim.process.equipment.energy.Generator;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineCatalog;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineUnit;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.CompressorShaftCalculator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Real-fluid compressor, gearbox and supply integration checks.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class CompressorShaftPowerTrainTest {
  /**
   * Creates a methane/ethane gas stream.
   *
   * @return gas stream
   */
  private Stream gas() {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 10.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream stream = new Stream("gas", fluid);
    stream.setFlowRate(20000.0, "kg/hr");
    stream.run();
    return stream;
  }

  /**
   * Creates and characterizes a real-fluid compressor body.
   *
   * @param name body name
   * @param inlet inlet stream
   * @param outletPressureBara design discharge pressure
   * @return body with a generated chart, suitable only for synthetic integration tests
   */
  private Compressor body(String name, Stream inlet, double outletPressureBara) {
    Compressor compressor = new Compressor(name, inlet);
    compressor.setOutletPressure(outletPressureBara);
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.78);
    compressor.run();
    compressor.generateCompressorChart("normal curves", 5);
    return compressor;
  }

  /** Actual body powers and gearbox loss set the external motor electrical demand. */
  @Test
  void twoBodyElectricTrainUsesOneSpeedAndActualDuty() {
    Stream feed = gas();
    Compressor first = body("first", feed, 20.0);
    Compressor second = body("second", (Stream) first.getOutletStream(), 40.0);
    CompressorShaft shaft = new CompressorShaft("train");
    shaft.addCompressor(first);
    shaft.addCompressor(second);
    shaft.setMechanicalEfficiency(0.98);
    shaft.setPowerTrain(CompressorPowerTrain.electricDrive(new ElectricMotor("motor", 0.95)));
    shaft.runAtFixedSpeed(first.getSpeed(), null);
    CompressorPowerTrain.Result result = shaft.getPowerTrainResult();
    assertNotNull(result);
    assertTrue(result.isFeasible());
    assertEquals(first.getSpeed(), second.getSpeed(), 1.0e-9);
    assertEquals((first.getPower() + second.getPower()) / 0.98, result.getRequiredPowerW(), 1.0e-6);
    assertEquals(result.getRequiredPowerW() / 0.95, result.getElectricalInputW(), 1.0e-6);
    assertEquals(feed.getFlowRate("kg/hr"), second.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(first.getPower(),
        first.getOutletStream().getFluid().getEnthalpy() - first.getInletStream().getFluid().getEnthalpy(),
        first.getPower() * 1.0e-5);
    assertTrue(shaft.getOperatingReport().toJson().contains("\"powerTrain\":{\"mode\":\"ELECTRIC_MOTOR\""));
    double original = result.getElectricalInputW();
    shaft.runAtFixedSpeed(first.getSpeed(), null);
    assertEquals(original, shaft.getPowerTrainResult().getElectricalInputW(), original * 1.0e-8);
  }

  /** Fixed-speed turbine fuel accounting refreshes automatically, including shutdown of all bodies. */
  @Test
  void fixedSpeedRefreshesTurbineFuelAndIdleResetsIt() {
    Compressor compressor = body("compressor", gas(), 20.0);
    CompressorShaft shaft = new CompressorShaft("train");
    shaft.addCompressor(compressor);
    GasTurbineUnit turbine = new GasTurbineUnit("GT", gas(), GasTurbineCatalog.get("LM2500"));
    shaft.setGasTurbineUnitDriver(turbine);
    shaft.runAtFixedSpeed(compressor.getSpeed(), null);
    assertTrue(turbine.getFuelMassFlowKgPerS() > 0.0);
    assertEquals(shaft.getRequiredDriverPower("W"), turbine.getShaftPowerW(), 1.0e-6);
    shaft.setPowerTrain(CompressorPowerTrain.directDrive(turbine));
    shaft.setMemberActive(compressor, false);
    shaft.solveSpeed(compressor, 20.0, "bara", null);
    assertEquals(0.0, shaft.getPowerTrainResult().getFuelMassKgPerS());
    assertEquals(0.0, shaft.getOperatingReport().getDriverFuelMassFlowKgPerHr());
    assertTrue(shaft.getOperatingReport().isFeasible());
  }

  /** An invalid fixed-speed motor point has zero capability and cannot fall back to a supply cap. */
  @Test
  void zeroDriverCapacityIsNotUnlimitedOrReplacedByElectricalSupply() {
    Compressor compressor = body("compressor", gas(), 20.0);
    CompressorShaft shaft = new CompressorShaft("train");
    shaft.addCompressor(compressor);
    double designSpeed = compressor.getSpeed();
    ElectricMotorDriver fixed = new ElectricMotorDriver(5000.0, 0.5 * designSpeed, 0.96);
    shaft.setDriverCurve(fixed);
    shaft.runAtFixedSpeed(designSpeed, null);
    assertTrue(compressor.getPower() > 0.0);
    assertEquals(0.0, shaft.getAvailableShaftPower("W"));
    assertTrue(shaft.isOverPower());
    assertFalse(shaft.getOperatingReport().isFeasible());
    shaft.setElectricSupplyLimit(10.0, "MW", 0.95);
    assertEquals(0.0, shaft.getAvailableShaftPower("W"));
    assertTrue(shaft.isOverPower());
  }

  /** A supply shortage stays distinct from the compressor thermodynamic demand. */
  @Test
  void shortageAndInstalledCapReachStructuredReport() {
    Compressor compressor = body("compressor", gas(), 20.0);
    CompressorShaft shaft = new CompressorShaft("train");
    shaft.addCompressor(compressor);
    ElectricMotor motor = new ElectricMotor("motor", 0.95);
    CompressorPowerTrain train = CompressorPowerTrain.electricDrive(motor);
    train.setElectricalSupplyLimitW(0.0);
    shaft.setPowerTrain(train);
    shaft.runAtFixedSpeed(compressor.getSpeed(), null);
    assertTrue(compressor.getPower() > 0.0);
    assertTrue(shaft.isOverPower());
    assertFalse(shaft.getOperatingReport().isFeasible());
    assertEquals(0.0, shaft.getPowerTrainResult().getDeliveredPowerW());
    train.setElectricalSupplyLimitW(Double.POSITIVE_INFINITY);
    shaft.setMaxShaftPower(0.5 * compressor.getPower(), "W");
    shaft.updateDriverOperatingPoint();
    assertEquals(shaft.getMaxShaftPower("W"), shaft.getPowerTrainResult().getDeliveredPowerW(), 1.0e-6);
    assertFalse(shaft.getOperatingReport().getPowerTrain().isFeasible());
  }

  /**
   * Serialized connections retain turbine identity and observe subsequent changes through the same live graph.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void serializationPreservesLiveTurbineAndSupplyConnections() throws Exception {
    Compressor compressor = body("compressor", gas(), 20.0);
    CompressorShaft shaft = new CompressorShaft("train");
    shaft.addCompressor(compressor);
    GasTurbineUnit turbine = new GasTurbineUnit("GT", gas(), GasTurbineCatalog.get("LM2500"));
    Generator generator = new Generator("generator", 0.97);
    ElectricMotor motor = new ElectricMotor("motor", 0.95);
    CompressorPowerTrain train = CompressorPowerTrain.gasElectricDrive(turbine, generator, motor);
    shaft.setPowerTrain(train);
    assertSame(turbine, train.getTurbine());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(new Object[] {shaft, train, turbine, motor});
    }
    Object[] restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (Object[]) input.readObject();
    }
    CompressorShaft restoredShaft = (CompressorShaft) restored[0];
    CompressorPowerTrain restoredTrain = (CompressorPowerTrain) restored[1];
    GasTurbineUnit restoredTurbine = (GasTurbineUnit) restored[2];
    ElectricMotor restoredMotor = (ElectricMotor) restored[3];
    assertSame(restoredTurbine, restoredTrain.getTurbine());
    double originalPower = restoredShaft.getAvailableShaftPower("W");
    restoredTurbine.setAmbientPressureBara(0.8);
    assertTrue(restoredShaft.getAvailableShaftPower("W") < originalPower);
    restoredMotor.setTripped(true);
    assertEquals(0.0, restoredShaft.getAvailableShaftPower("W"));
    restoredMotor.setTripped(false);
    restoredTrain.setElectricalSupplyLimitW(0.0);
    assertEquals(0.0, restoredShaft.getAvailableShaftPower("W"));
    assertTrue(shaft.getAvailableShaftPower("W") > 0.0);
  }

  /** Selecting an integrated electric supply does not report fuel from a previously selected legacy turbine. */
  @Test
  void switchingFromLegacyTurbineToElectricSupplyClearsFuelReporting() {
    Compressor compressor = body("compressor", gas(), 20.0);
    CompressorShaft shaft = new CompressorShaft("train");
    shaft.addCompressor(compressor);
    GasTurbineUnit legacy = new GasTurbineUnit("legacy GT", gas(), GasTurbineCatalog.get("LM2500"));
    shaft.setGasTurbineUnitDriver(legacy);
    shaft.runAtFixedSpeed(compressor.getSpeed(), null);
    assertTrue(shaft.getOperatingReport().getDriverFuelMassFlowKgPerHr() > 0.0);
    shaft.setPowerTrain(CompressorPowerTrain.electricDrive(new ElectricMotor("motor", 0.95)));
    shaft.runAtFixedSpeed(compressor.getSpeed(), null);
    assertEquals(0.0, shaft.getOperatingReport().getDriverFuelMassFlowKgPerHr());
    assertTrue(Double.isNaN(shaft.getOperatingReport().getDriverExhaustTemperatureK()));
  }

  /** Normal ProcessSystem execution refreshes fuel matching and rejects a below-minimum turbine. */
  @Test
  void processCalculatorRefreshesSupplyAndRejectsMinimumLoadViolation() {
    Stream feed = gas();
    Compressor compressor = body("compressor", feed, 20.0);
    CompressorShaft shaft = new CompressorShaft("train");
    shaft.addCompressor(compressor);
    shaft.setSpeed(compressor.getSpeed());
    GasTurbineUnit turbine = new GasTurbineUnit("GT", gas(), GasTurbineCatalog.get("LM2500"));
    shaft.setPowerTrain(CompressorPowerTrain.directDrive(turbine));
    CompressorShaftCalculator calculator = new CompressorShaftCalculator("shaft control", shaft, compressor, 20.0,
        "bara");
    calculator.setFixedSpeedMode(true);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.add(calculator);
    process.run();
    assertNotNull(shaft.getPowerTrainResult());
    assertEquals(shaft.getRequiredDriverPower("W"), turbine.getShaftPowerW(), 1.0e-6);
    assertTrue(turbine.getFuelMassFlowKgPerS() > 0.0);
    assertFalse(calculator.isFeasible());
    assertEquals(CompressorShaft.SolveStatus.DRIVER_BELOW_MINIMUM_LOAD, calculator.getLastSolveResult().getStatus());
  }

  /** Reaching an exact speed-bound pressure never bypasses a driver power limit. */
  @Test
  void exactSpeedBoundStillChecksPower() {
    Compressor compressor = body("compressor", gas(), 20.0);
    CompressorShaft shaft = new CompressorShaft("train");
    shaft.addCompressor(compressor);
    double design = compressor.getSpeed();
    shaft.setSpeedBounds(design, design * 1.1);
    shaft.runAtFixedSpeed(design, null);
    double target = compressor.getOutletStream().getPressure("bara");
    shaft.setMaxShaftPower(0.1 * compressor.getPower(), "W");
    shaft.solveSpeed(compressor, target, "bara", null);
    assertFalse(shaft.getLastSolveResult().isFeasible());
    assertEquals(CompressorShaft.SolveStatus.OVER_POWER, shaft.getLastSolveResult().getStatus());
  }
}
