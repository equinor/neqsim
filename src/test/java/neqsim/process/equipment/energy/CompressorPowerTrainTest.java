package neqsim.process.equipment.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.driver.ElectricMotorDriver;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineSpec;
import neqsim.process.equipment.powergeneration.gasturbine.GasTurbineUnit;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Analytical power, loss, fuel-carbon and capability checks for integrated compression supplies.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class CompressorPowerTrainTest {
  /**
   * Builds a synthetic 10 MW, 40 percent LHV-efficiency turbine with methane fuel.
   *
   * @return configured turbine
   */
  private GasTurbineUnit turbine() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream fuel = new Stream("fuel", fluid);
    fuel.setFlowRate(1.0, "kg/sec");
    fuel.run();
    GasTurbineSpec spec = new GasTurbineSpec("synthetic", GasTurbineSpec.TurbineType.AERODERIVATIVE, 10.0e6, 9000.0,
        30.0, 750.0, 15.0, 10.0, "Analytical test, not OEM data");
    GasTurbineUnit turbine = new GasTurbineUnit("GT", fuel, spec);
    turbine.getPerformanceMap().setHeatRatePolynomial(1.0, 0.0, 0.0);
    return turbine;
  }

  /** Direct fuel-to-shaft power and methane carbon balances close independently. */
  @Test
  void directDriveClosesFuelAndCarbonBalances() {
    GasTurbineUnit turbine = turbine();
    CompressorPowerTrain train = CompressorPowerTrain.directDrive(turbine);
    CompressorPowerTrain.Result result = train.evaluate(5.0e6, 10000.0, 288.15);
    assertTrue(result.isFeasible());
    assertEquals(5.0e6, result.getTurbineShaftW(), 1.0e-6);
    assertEquals(12.5e6, result.getFuelEnergyW(), 1.0e-6);
    assertEquals(0.0, result.getConversionLossW(), 1.0e-6);
    assertEquals(0.0, result.getEnergyBalanceResidualW(), 1.0e-6);
    assertEquals(0.0, result.getElectricalInputW());
    double methaneMoles = result.getFuelMassKgPerS() / turbine.getInletStream().getFluid().getMolarMass();
    assertEquals(methaneMoles * 0.04401, result.getCO2KgPerS(), 1.0e-3);
  }

  /** Motor idle losses and auxiliaries precede the mechanical output and supply cap. */
  @Test
  void electricDriveMatchesAnalyticalSupplyLimitAndLosses() {
    ElectricMotor motor = new ElectricMotor("motor", 0.95);
    motor.setIdleLoss(10000.0);
    CompressorPowerTrain train = CompressorPowerTrain.electricDrive(motor);
    train.setElectricalAuxiliariesW(50000.0);
    train.setElectricalSupplyLimitW(1.0e6);
    CompressorPowerTrain.Result result = train.evaluate(1.0e6, 3000.0, 288.15);
    double expected = (1.0e6 - 50000.0 - 10000.0) * 0.95;
    assertEquals(expected, result.getDeliveredPowerW(), 1.0e-5);
    assertEquals(1.0e6, result.getElectricalInputW(), 1.0e-5);
    assertEquals(1.0e6 - expected, result.getConversionLossW(), 1.0e-5);
    assertFalse(result.isPowerFeasible());
    assertEquals(0.0, result.getEnergyBalanceResidualW(), 1.0e-6);
    assertEquals(0.0, result.getFuelEnergyW());
  }

  /** Every conversion stage and the auxiliary offtake enter the fuel demand. */
  @Test
  void gasElectricDriveMatchesAnalyticalEfficienciesAndGeneratorRating() {
    GasTurbineUnit turbine = turbine();
    Generator generator = new Generator("generator", 0.97);
    generator.setRatedOutputPower(8.0e6);
    ElectricMotor motor = new ElectricMotor("motor", 0.95);
    CompressorPowerTrain train = CompressorPowerTrain.gasElectricDrive(turbine, generator, motor);
    train.setElectricalAuxiliariesW(100000.0);
    CompressorPowerTrain.Result result = train.evaluate(5.0e6, 3000.0, 288.15);
    double electrical = 5.0e6 / 0.95 + 100000.0;
    double turbineShaft = electrical / 0.97;
    assertTrue(result.isFeasible());
    assertEquals(electrical, result.getElectricalInputW(), 1.0e-6);
    assertEquals(turbineShaft, result.getTurbineShaftW(), 1.0e-6);
    assertEquals(turbineShaft / 0.40, result.getFuelEnergyW(), 1.0e-6);
    assertEquals(turbineShaft - 5.0e6, result.getConversionLossW(), 1.0e-6);
    assertEquals((8.0e6 - 100000.0) * 0.95, result.getAvailablePowerW(), 1.0e-4);
    assertEquals(0.0, result.getEnergyBalanceResidualW(), 1.0e-6);
    assertEquals(result.getFuelEnergyW(), turbine.getFuelEnergyRateW());
  }

  /** The inverse generator map uses electrical output load rather than turbine or motor load. */
  @Test
  void generatorPartLoadMapAndInputLimitAreRespected() {
    Generator generator = new Generator("mapped generator");
    generator.setRatedOutputPower(8.0e6);
    generator.setLoadEfficiencyCurve(new LoadEfficiencyCurve(new double[] {0.5, 1.0}, new double[] {0.90, 0.96}));
    generator.setMaximumInputPower(6.0e6);
    CompressorPowerTrain train = CompressorPowerTrain.gasElectricDrive(turbine(), generator,
        new ElectricMotor("motor", 0.95));
    CompressorPowerTrain.Result result = train.evaluate(3.8e6, 3000.0, 288.15);
    assertEquals(4.0e6 / 0.90, result.getTurbineShaftW(), 1.0e-6);
    CompressorPowerTrain.Result limited = train.evaluate(8.0e6, 3000.0, 288.15);
    assertFalse(limited.isPowerFeasible());
    assertEquals(6.0e6, limited.getTurbineShaftW(), 1.0e-4);
  }

  /** A VFD torque region, field weakening, motor efficiency and ambient derating all enter matching. */
  @Test
  void vfdCapabilityAndPartLoadEfficiencyFollowSpeed() {
    ElectricMotorDriver model = new ElectricMotorDriver(1000.0, 3000.0, 0.96);
    model.setHasVFD(true);
    model.setMinSpeedRatio(0.3);
    model.setMaxSpeedRatio(1.2);
    ElectricMotor motor = new ElectricMotor("VFD motor");
    motor.setPerformanceModel(model);
    CompressorPowerTrain train = CompressorPowerTrain.electricDrive(motor);
    assertEquals(500000.0, train.getAvailablePowerW(1500.0, 288.15), 1.0e-6);
    assertEquals(1000000.0, train.getAvailablePowerW(3300.0, 288.15), 1.0e-6);
    CompressorPowerTrain.Result result = train.evaluate(250000.0, 1500.0, 288.15);
    assertEquals(250000.0 / (0.96 * 0.95 * 0.97), result.getElectricalInputW(), 1.0e-6);
    model.setAmbientTemperature(60.0);
    assertTrue(train.getAvailablePowerW(1500.0, 288.15) < 500000.0);
    assertEquals(0.0, train.getAvailablePowerW(4000.0, 288.15));
    assertFalse(train.evaluate(100000.0, 4000.0, 288.15).isPowerFeasible());
  }

  /** Zero supply, trips and idle operation produce zero output without stale fuel or auxiliary demand. */
  @Test
  void blackoutTripAndOffResetActualDelivery() {
    GasTurbineUnit turbine = turbine();
    ElectricMotor motor = new ElectricMotor("motor", 0.95);
    Generator generator = new Generator("generator", 0.97);
    CompressorPowerTrain train = CompressorPowerTrain.gasElectricDrive(turbine, generator, motor);
    train.evaluate(5.0e6, 3000.0, 288.15);
    train.setElectricalSupplyLimitW(0.0);
    CompressorPowerTrain.Result blackout = train.evaluate(5.0e6, 3000.0, 288.15);
    assertFalse(blackout.isFeasible());
    assertEquals(0.0, blackout.getDeliveredPowerW());
    assertEquals(0.0, blackout.getFuelMassKgPerS());
    train.setElectricalSupplyLimitW(Double.POSITIVE_INFINITY);
    motor.setTripped(true);
    assertEquals(0.0, train.getAvailablePowerW(3000.0, 288.15));
    motor.setTripped(false);
    generator.setTripped(true);
    assertEquals(0.0, train.getAvailablePowerW(3000.0, 288.15));
    generator.setTripped(false);
    CompressorPowerTrain.Result off = train.evaluate(0.0, 3000.0, 288.15);
    assertTrue(off.isFeasible());
    assertEquals(0.0, off.getElectricalInputW());
    assertEquals(0.0, off.getFuelEnergyW());
    assertEquals(0.0, off.getCO2KgPerS());
  }

  /** Turbine capacity moves with ambient while minimum stable load remains a separate feasibility check. */
  @Test
  void ambientAndMinimumLoadAreNotHiddenByMatching() {
    CompressorPowerTrain train = CompressorPowerTrain.directDrive(turbine());
    double cold = train.getAvailablePowerW(10000.0, 278.15);
    double hot = train.getAvailablePowerW(10000.0, 303.15);
    assertTrue(cold > hot);
    CompressorPowerTrain.Result low = train.evaluate(1.0e6, 10000.0, 288.15);
    assertTrue(low.isPowerFeasible());
    assertTrue(low.isBelowMinimumLoad());
    assertFalse(low.isFeasible());
  }

  /** An added shaft installation cap also caps actual fuel matching. */
  @Test
  void additionalMechanicalLimitCapsActualFuel() {
    CompressorPowerTrain train = CompressorPowerTrain.directDrive(turbine());
    CompressorPowerTrain.Result result = train.evaluate(8.0e6, 10000.0, 288.15, 6.0e6);
    assertEquals(6.0e6, result.getDeliveredPowerW(), 1.0e-6);
    assertEquals(2.0e6, result.getPowerShortfallW(), 1.0e-6);
    assertEquals(15.0e6, result.getFuelEnergyW(), 1.0e-6);
  }

  /**
   * A repeated solve is deterministic and does not mutate prior result snapshots.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void repeatedEvaluationAndSerializationPreserveResults() throws Exception {
    CompressorPowerTrain train = CompressorPowerTrain.electricDrive(new ElectricMotor("motor", 0.95));
    CompressorPowerTrain.Result first = train.evaluate(1.0e6, 3000.0, 288.15);
    assertEquals(first.toJson(), train.evaluate(1.0e6, 3000.0, 288.15).toJson());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(train);
    }
    CompressorPowerTrain copy;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (CompressorPowerTrain) input.readObject();
    }
    assertEquals(first.toJson(), copy.evaluate(1.0e6, 3000.0, 288.15).toJson());
    train.setElectricalSupplyLimitW(0.0);
    train.evaluate(1.0e6, 3000.0, 288.15);
    assertEquals(1.0e6, first.getDeliveredPowerW(), 1.0e-6);
    assertFalse(first.toJson().contains("Infinity"));
    assertFalse(first.toJson().contains("NaN"));
    assertTrue(first.toJson().contains("\"availablePowerW\":null"));
    assertNotNull(first.toMap().get("mode"));
  }

  /** Invalid powers and operating conditions cannot enter the model. */
  @Test
  void rejectsNonFiniteInputsAndInvalidTopologySettings() {
    CompressorPowerTrain train = CompressorPowerTrain.electricDrive(new ElectricMotor("motor"));
    assertThrows(IllegalArgumentException.class, () -> train.evaluate(Double.NaN, 3000.0, 288.15));
    assertThrows(IllegalArgumentException.class, () -> train.evaluate(1.0, -1.0, 288.15));
    assertThrows(IllegalArgumentException.class, () -> train.evaluate(1.0, 3000.0, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> train.setElectricalSupplyLimitW(-1.0));
    assertThrows(IllegalArgumentException.class, () -> train.setElectricalAuxiliariesW(Double.POSITIVE_INFINITY));
    assertThrows(IllegalStateException.class,
        () -> CompressorPowerTrain.directDrive(turbine()).setElectricalSupplyLimitW(1.0));
  }
}
