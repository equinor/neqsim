package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyAllocation;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyNetworkReport;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.processmodel.ProcessSystem;

/** Reproducible 24-hour offshore electrical-system reference case. */
public final class OffshoreEnergyReferenceCase {
  private static final String WIND_PARTICIPANT = "offshore wind.power";
  private static final String GAS_PARTICIPANT = "gas turbine generation.power";

  private OffshoreEnergyReferenceCase() {
  }

  public static EnergyTimeSeriesResult run24HourCase() {
    EnergyBus electricalBus = new EnergyBus("offshore electrical bus", EnergyType.ELECTRICAL);

    EnergyPort wind = port("offshore wind", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, electricalBus);
    wind.setPriority(0);
    wind.setEnergyPricePerMWh(0.0);
    wind.setEmissionFactorKgPerMWh(0.0);

    EnergyPort gasTurbine = port("gas turbine generation", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED,
        electricalBus);
    gasTurbine.setPriority(10);
    gasTurbine.setEnergyPricePerMWh(90.0);
    gasTurbine.setEmissionFactorKgPerMWh(450.0);

    EnergyPort criticalLoad = port("critical process load", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION,
        electricalBus);
    criticalLoad.setPriority(0);

    EnergyPort flexibleLoad = port("flexible process load", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION,
        electricalBus);
    flexibleLoad.setPriority(20);

    ProcessSystem process = new ProcessSystem();
    EnergyTimeSeriesSimulator simulator = new EnergyTimeSeriesSimulator(process);
    simulator.addEnergyBus(electricalBus);
    simulator.setIntervalSeconds(4.0 * 3600.0);
    simulator.setDurationSeconds(24.0 * 3600.0);

    double[] times = new double[] { 0.0, 4.0 * 3600.0, 8.0 * 3600.0, 12.0 * 3600.0, 16.0 * 3600.0, 20.0 * 3600.0 };
    simulator.addProfile(
        EnergyTimeSeriesProfile.step("wind availability", times, megawatts(2.0, 6.0, 16.0, 8.0, 4.0, 1.0)),
        value -> wind.setDuty(value));
    simulator.addProfile(
        EnergyTimeSeriesProfile.step("gas availability", times, megawatts(15.0, 15.0, 15.0, 15.0, 15.0, 15.0)),
        value -> gasTurbine.setDuty(value));
    simulator.addProfile(
        EnergyTimeSeriesProfile.step("critical demand", times, megawatts(8.0, 9.0, 10.0, 11.0, 10.0, 9.0)),
        value -> criticalLoad.setRequestedPower(value));
    simulator.addProfile(
        EnergyTimeSeriesProfile.step("flexible demand", times, megawatts(3.0, 4.0, 5.0, 4.0, 3.0, 2.0)),
        value -> flexibleLoad.setRequestedPower(value));

    return simulator.run();
  }

  public static double getWindGeneratedEnergyMWh(EnergyTimeSeriesResult result) {
    return getParticipantEnergyMWh(result, WIND_PARTICIPANT, false);
  }

  public static double getGasGeneratedEnergyMWh(EnergyTimeSeriesResult result) {
    return getParticipantEnergyMWh(result, GAS_PARTICIPANT, false);
  }

  public static double getWindCurtailedEnergyMWh(EnergyTimeSeriesResult result) {
    return getParticipantEnergyMWh(result, WIND_PARTICIPANT, true);
  }

  public static void requireAcceptanceCriteria(EnergyTimeSeriesResult result) {
    if (result == null) {
      throw new IllegalArgumentException("Reference-case result is required");
    }
    requireClose(result.getServedEnergyMWh(), 312.0, 1.0e-9, "served energy");
    requireClose(result.getUnmetEnergyMWh(), 0.0, 1.0e-9, "unmet energy");
    requireClose(result.getCurtailedEnergyMWh(), 196.0, 1.0e-9, "total curtailed supply energy");
    requireClose(getWindCurtailedEnergyMWh(result), 4.0, 1.0e-9, "curtailed wind energy");
    requireClose(getWindGeneratedEnergyMWh(result), 144.0, 1.0e-9, "accepted wind energy");
    requireClose(getGasGeneratedEnergyMWh(result), 168.0, 1.0e-9, "accepted gas energy");
    requireClose(result.getOperatingCost(), 15120.0, 1.0e-6, "operating cost");
    requireClose(result.getCo2EmissionsKg(), 75600.0, 1.0e-6, "CO2 emissions");
  }

  private static double getParticipantEnergyMWh(EnergyTimeSeriesResult result, String participantName,
      boolean curtailed) {
    if (result == null) {
      throw new IllegalArgumentException("Reference-case result is required");
    }
    double energyMWh = 0.0;
    for (EnergyTimeSeriesResult.IntervalResult interval : result.getIntervals()) {
      double hours = interval.getDurationSeconds() / 3600.0;
      for (EnergyNetworkReport report : interval.getNetworkReports()) {
        for (EnergyAllocation allocation : report.getAllocations()) {
          if (participantName.equals(allocation.getParticipantName())) {
            double power = curtailed ? allocation.getCurtailedPower() : allocation.getAllocatedPower();
            energyMWh += power * hours / 1.0e6;
          }
        }
      }
    }
    return energyMWh;
  }

  private static EnergyPort port(String owner, EnergyPortDirection direction, EnergyPortMode mode, EnergyBus bus) {
    EnergyPort port = new EnergyPort("power", EnergyType.ELECTRICAL, direction, mode);
    port.setOwnerName(owner);
    port.connect(bus);
    return port;
  }

  private static double[] megawatts(double... values) {
    double[] watts = values.clone();
    for (int index = 0; index < watts.length; index++) {
      watts[index] *= 1.0e6;
    }
    return watts;
  }

  private static void requireClose(double actual, double expected, double tolerance, String name) {
    if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
      throw new IllegalStateException(
          "Offshore reference-case " + name + " expected " + expected + " but was " + actual);
    }
  }
}
