package neqsim.process.equipment.pipeline;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

/**
 * Single equilibrium TP flash to explicitly specified surface reference conditions. Phase volumes use mass flow divided
 * by phase density at those conditions, rather than ideal-gas total feed volume.
 *
 * @author ESOL
 * @version 1.0
 */
public final class StandardConditionsFlash implements VfpTableArguments {
  private static final long serialVersionUID = 1L;
  private final double temperatureK;
  private final double pressureBara;

  /**
   * Defines the standard-state separation used by the table provider.
   *
   * @param temperature reference temperature in temperatureUnit
   * @param temperatureUnit K, C, F or R
   * @param pressure reference absolute pressure in pressureUnit
   * @param pressureUnit a NeqSim pressure unit, e.g. bara or Pa
   */
  public StandardConditionsFlash(double temperature, String temperatureUnit, double pressure, String pressureUnit) {
    temperatureK = new TemperatureUnit(temperature, temperatureUnit).getValue("K");
    pressureBara = new PressureUnit(pressure, pressureUnit).getValue("bara");
    if (!Double.isFinite(temperatureK) || temperatureK <= 0 || !Double.isFinite(pressureBara) || pressureBara <= 0) {
      throw new IllegalArgumentException("Standard temperature and absolute pressure must be finite and positive");
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[] calculate(StreamInterface stream) {
    if (stream == null || stream.getThermoSystem() == null) {
      throw new IllegalArgumentException("StandardConditionsFlash requires an inlet fluid");
    }
    SystemInterface fluid = stream.getThermoSystem().clone();
    fluid.setTemperature(temperatureK, "K");
    fluid.setPressure(pressureBara, "bara");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(1);
    double gas = 0;
    double water = 0;
    double oil = 0;
    for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
      double volume = fluid.getPhase(p).getFlowRate("m3/sec") * 86400;
      if (!Double.isFinite(volume) || volume < 0) {
        throw new IllegalArgumentException("Invalid standard phase volume");
      }
      PhaseType type = fluid.getPhase(p).getType();
      if (type == PhaseType.GAS) {
        gas += volume;
      } else if (type == PhaseType.AQUEOUS) {
        water += volume;
      } else if (type == PhaseType.OIL || type == PhaseType.LIQUID) {
        oil += volume;
      } else if (volume > 0) {
        throw new IllegalArgumentException("Unsupported standard phase " + type);
      }
    }
    if (gas <= 0) {
      throw new IllegalArgumentException("Standard gas rate must be positive for GAS/WGR VFP arguments");
    }
    return new double[] {gas, water / gas, oil == 0 ? Double.POSITIVE_INFINITY : gas / oil};
  }
}
