package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;
import neqsim.util.validation.ValidationResult;

/**
 * Pipeline driven by a qualified VFPPROD table, with explicit thermal and surface-volume bases. Forward mode inverts
 * for arrival THP; backward mode reports required inlet BHP without changing the connected inlet stream. This unit does
 * not balance networks or predict temperature.
 *
 * @author ESOL
 * @version 1.0
 */
public class VfpPipeline extends TwoPortEquipment {
  private static final long serialVersionUID = 1L;
  private VfpTable table;
  private VfpTableArguments argumentProvider;
  private int numberOfParallelLines = 1;
  private double outletTemperatureK = Double.NaN;
  private double specifiedOutletPressureBara = Double.NaN;
  private double requiredInletPressureBara = Double.NaN;
  private double[] tableArguments;
  private double tableGasRatio = Double.NaN;
  private boolean allowZeroFlow;

  /**
   * Creates a disconnected pipeline. Connect an inlet before running.
   *
   * @param name equipment name
   */
  public VfpPipeline(String name) {
    super(name);
  }

  /**
   * Creates a pipeline connected to an inlet stream.
   *
   * @param name equipment name
   * @param inlet inlet stream, whose composition and total flow are preserved
   */
  public VfpPipeline(String name, StreamInterface inlet) {
    super(name, inlet);
  }

  /**
   * Selects the qualified hydraulic table.
   *
   * @param value table with rates in Sm3/day and pressures in bara
   */
  public void setVfpTable(VfpTable value) {
    if (value == null) {
      throw new IllegalArgumentException("VFP table is required");
    }
    table = value;
  }

  /**
   * Defines how total surface gas rate, WGR and GOR are obtained.
   *
   * @param provider explicit table-provider-compatible surface separation definition
   */
  public void setTableArguments(VfpTableArguments provider) {
    if (provider == null) {
      throw new IllegalArgumentException("Table-argument provision is required");
    }
    argumentProvider = provider;
  }

  /**
   * Gets the arguments from the last successful run (no implicit re-flash).
   *
   * @return copy of {gas rate per line [Sm3/day], WGR [Sm3/Sm3], GOR [Sm3/Sm3]}, or null before a successful
   * nonzero-flow run
   */
  public double[] getTableArguments() {
    return tableArguments == null ? null : tableArguments.clone();
  }

  /** @return last successful table gas-ratio coordinate in declared GOR or OGR [Sm3/Sm3], or NaN */
  public double getTableGasRatio() {
    return tableGasRatio;
  }

  /**
   * Splits total gas flow over identical parallel lines; ratios are unchanged.
   *
   * @param count positive number of identical lines, dimensionless
   */
  public void setNumberOfParallelLines(int count) {
    if (count < 1) {
      throw new IllegalArgumentException("Number of parallel lines must be positive");
    }
    numberOfParallelLines = count;
  }

  /**
   * Specifies the required outlet temperature in kelvin.
   *
   * @param temperature outlet temperature in K
   */
  @Override
  public void setOutletTemperature(double temperature) {
    setOutletTemperature(temperature, "K");
  }

  /**
   * Specifies the thermal boundary independently of the hydraulic table.
   *
   * @param temperature outlet temperature in unit
   * @param unit K, C, F or R
   */
  @Override
  public void setOutletTemperature(double temperature, String unit) {
    double value = new TemperatureUnit(temperature, unit).getValue("K");
    if (!Double.isFinite(value) || value <= 0) {
      throw new IllegalArgumentException("Outlet temperature must be finite and positive in K");
    }
    outletTemperatureK = value;
  }

  /**
   * Selects backward mode at the specified arrival pressure in bara.
   *
   * @param pressure absolute outlet pressure in bara
   */
  @Override
  public void setOutletPressure(double pressure) {
    setOutletPressure(pressure, "bara");
  }

  /**
   * Selects backward mode with a fixed arrival pressure.
   *
   * @param pressure arrival pressure in unit
   * @param unit pressure unit, e.g. bara or Pa
   */
  @Override
  public void setOutletPressure(double pressure, String unit) {
    double value = new PressureUnit(pressure, unit).getValue("bara");
    if (!Double.isFinite(value) || value <= 0) {
      throw new IllegalArgumentException("Outlet pressure must be finite positive absolute pressure");
    }
    specifiedOutletPressureBara = value;
  }

  /** Selects forward mode: arrival pressure is solved from inlet pressure, in bara internally. */
  public void setForwardMode() {
    specifiedOutletPressureBara = Double.NaN;
  }

  /**
   * Reads the required inlet pressure from the last successful run.
   *
   * @param unit pressure unit, e.g. bara
   * @return required inlet pressure in unit; NaN before a successful run
   */
  public double getRequiredInletPressure(String unit) {
    return new PressureUnit(requiredInletPressureBara, "bara").getValue(unit);
  }

  /**
   * Enables explicit zero-flow bypass, including in backward mode.
   *
   * @param allow true to copy inlet pressure at zero flow; false (default) to reject zero flow
   */
  public void setAllowZeroFlow(boolean allow) {
    allowZeroFlow = allow;
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = super.validateSetup();
    if (table == null) {
      result.addError("VFP table", "Missing VFP table", "Call setVfpTable(VfpTable.fromString(...))");
    }
    if (Double.isNaN(outletTemperatureK)) {
      result.addError("outlet temperature", "Missing outlet temperature", "Call setOutletTemperature(value, unit)");
    }
    if (argumentProvider == null) {
      result.addError("table arguments", "Missing table-argument provision", "Call setTableArguments(provider)");
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inStream == null || inStream.getThermoSystem() == null) {
      throw new IllegalStateException("VfpPipeline requires an inlet stream with a fluid");
    }
    if (table == null) {
      throw new IllegalStateException("VfpPipeline requires a VFP table");
    }
    if (Double.isNaN(outletTemperatureK)) {
      throw new IllegalStateException("VfpPipeline requires outlet temperature; VFP carries no thermal model");
    }
    if (argumentProvider == null) {
      throw new IllegalStateException("VfpPipeline requires a table-argument provision");
    }
    double flow = inStream.getFlowRate("kg/sec");
    double inletPressure = inStream.getPressure("bara");
    if (!Double.isFinite(flow) || flow < 0 || !Double.isFinite(inletPressure) || inletPressure <= 0) {
      throw new IllegalArgumentException("VfpPipeline requires nonnegative finite flow and positive inlet pressure");
    }
    double outletPressure = inletPressure;
    double requiredPressure = inletPressure;
    double[] arguments = null;
    double ratio = Double.NaN;
    if (flow == 0) {
      if (!allowZeroFlow) {
        throw new IllegalArgumentException("Zero flow requires explicit setAllowZeroFlow(true)");
      }
    } else {
      double[] supplied = argumentProvider.calculate(inStream);
      if (supplied == null || supplied.length != 3) {
        throw new IllegalArgumentException("Table arguments must be {gas rate, WGR, GOR}");
      }
      arguments = supplied.clone();
      arguments[0] /= numberOfParallelLines;
      if (!Double.isFinite(arguments[0]) || arguments[0] <= 0 || !Double.isFinite(arguments[1]) || arguments[1] < 0
          || Double.isNaN(arguments[2]) || arguments[2] <= 0) {
        throw new IllegalArgumentException("Table arguments require positive gas rate and GOR, nonnegative WGR");
      }
      ratio = "OGR".equals(table.getGasRatioType()) ? 1 / arguments[2] : arguments[2];
      if (Double.isNaN(specifiedOutletPressureBara)) {
        outletPressure = table.outletPressure(arguments[0], inletPressure, arguments[1], ratio, table.getAlq());
      } else {
        outletPressure = specifiedOutletPressureBara;
        requiredPressure = table.inletPressure(arguments[0], outletPressure, arguments[1], ratio, table.getAlq());
      }
    }
    SystemInterface fluid = inStream.getThermoSystem().clone();
    fluid.setPressure(outletPressure, "bara");
    fluid.setTemperature(outletTemperatureK, "K");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    if (flow == 0) {
      // TPflash seeds empty fluids with a tiny inventory; preserve the explicit zero-flow contract.
      fluid.setTotalNumberOfMoles(0);
    }
    outStream.setThermoSystem(fluid);
    outStream.setCalculationIdentifier(id);
    requiredInletPressureBara = requiredPressure;
    tableArguments = arguments;
    tableGasRatio = ratio;
    setCalculationIdentifier(id);
  }
}
