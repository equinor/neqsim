package neqsim.process.equipment.distillation;

import java.util.UUID;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * Condenser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Condenser extends SimpleTray {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double refluxRatio = 0.1;
  boolean refluxIsSet = false;
  double duty = 0.0;
  boolean totalCondenser = false;
  Splitter mixedStreamSplitter = null;
  private boolean separation_with_liquid_reflux = false;
  private double reflux_value;
  private String reflux_unit;

  /**
   * Constructor for the Condenser class.
   *
   * @param name a {@link java.lang.String} object
   */
  public Condenser(String name) {
    super(name);
  }

  /**
   * Checks if the separation process involves liquid reflux.
   *
   * @return {@code true} if the separation process involves liquid reflux, {@code false} otherwise.
   */
  public boolean isSeparation_with_liquid_reflux() {
    return separation_with_liquid_reflux;
  }

  /**
   * Sets the separation with liquid reflux parameters.
   *
   * @param separation_with_liquid_reflux a boolean indicating if separation with liquid reflux is
   *        set
   * @param value the value of the reflux
   * @param unit the unit of the reflux value
   */
  public void setSeparation_with_liquid_reflux(boolean separation_with_liquid_reflux, double value,
      String unit) {
    this.refluxIsSet = separation_with_liquid_reflux;
    this.separation_with_liquid_reflux = separation_with_liquid_reflux;
    this.reflux_value = value;
    this.reflux_unit = unit;
  }

  /**
   * <p>
   * Setter for the field <code>totalCondenser</code>.
   * </p>
   *
   * @param isTotalCondenser a boolean
   */
  public void setTotalCondenser(boolean isTotalCondenser) {
    this.totalCondenser = isTotalCondenser;
  }

  /**
   * <p>
   * Getter for the field <code>refluxRatio</code>.
   * </p>
   *
   * @return the refluxRatio
   */
  public double getRefluxRatio() {
    return refluxRatio;
  }

  /**
   * <p>
   * Setter for the field <code>refluxRatio</code>.
   * </p>
   *
   * @param refluxRatio the refluxRatio to set
   */
  public void setRefluxRatio(double refluxRatio) {
    this.refluxRatio = refluxRatio;
    refluxIsSet = true;
  }

  /**
   * <p>
   * Getter for the field <code>duty</code>.
   * </p>
   *
   * @return a double
   */
  public double getDuty() {
    // return calcMixStreamEnthalpy();
    return duty;
  }

  /**
   * <p>
   * getDuty.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getDuty(String unit) {
    neqsim.util.unit.PowerUnit powerUnit = new neqsim.util.unit.PowerUnit(duty, "W");
    return powerUnit.getValue(unit);
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getGasOutStream() {
    if (totalCondenser) {
      return new Stream("", mixedStreamSplitter.getSplitStream(1));
    } else {
      return super.getGasOutStream();
    }
  }

  /**
   * <p>
   * getProductOutStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getProductOutStream() {
    return getGasOutStream();
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getLiquidOutStream() {
    if (totalCondenser || separation_with_liquid_reflux) {
      return mixedStreamSplitter.getSplitStream(0);
    } else {
      return super.getLiquidOutStream();
    }
  }

  /**
   * Get the liquid product stream from the condenser.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getLiquidProductStream() {
    if (separation_with_liquid_reflux) {
      return mixedStreamSplitter.getSplitStream(1);
    } else {
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // System.out.println("guess temperature " + getTemperature());
    if (refluxIsSet && totalCondenser) {
      UUID oldID = getCalculationIdentifier();
      SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
      mixedStream.setThermoSystem(thermoSystem2);
      ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
      if (streams.size() > 0) {
        mixedStream.getThermoSystem().setNumberOfPhases(2);
        mixedStream.getThermoSystem().init(0);
        mixStream();
      }
      double enthalpy = calcMixStreamEnthalpy();
      try {
        testOps.bubblePointTemperatureFlash();
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
      mixedStream.getThermoSystem().init(3);
      // mixedStream.getThermoSystem().prettyPrint();

      mixedStreamSplitter = new Splitter("splitter", mixedStream, 2);
      mixedStreamSplitter.setSplitFactors(new double[] {refluxRatio, 1.0 - refluxRatio});
      mixedStreamSplitter.run();
    } else if (!refluxIsSet) {
      UUID oldID = getCalculationIdentifier();
      super.run(id);
      setCalculationIdentifier(oldID);
    } else if (separation_with_liquid_reflux) {
      super.run(id);
      Stream liquidstream = new Stream("temp liq stream", mixedStream.getFluid().phaseToSystem(1));
      liquidstream.run();
      if (liquidstream.getFlowRate("kg/hr") < this.reflux_value) {
        liquidstream.setFlowRate(this.reflux_value + 1, this.reflux_unit);
        liquidstream.run();
      }
      mixedStreamSplitter = new Splitter("splitter", liquidstream, 2);
      mixedStreamSplitter.setFlowRates(new double[] {this.reflux_value, -1}, this.reflux_unit);
      mixedStreamSplitter.run();
    } else {
      SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
      // System.out.println("total number of moles " +
      // thermoSystem2.getTotalNumberOfMoles());
      mixedStream.setThermoSystem(thermoSystem2);
      ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
      testOps.PVrefluxFlash(refluxRatio, 0);
    }
    // System.out.println("enthalpy: " +
    // mixedStream.getThermoSystem().getEnthalpy());
    // System.out.println("enthalpy: " + enthalpy);
    // System.out.println("temperature: " +
    // mixedStream.getThermoSystem().getTemperature());
    duty = mixedStream.getFluid().getEnthalpy() - calcMixStreamEnthalpy0();
    energyStream.setDuty(duty);
    // System.out.println("beta " + mixedStream.getThermoSystem().getBeta())

    setCalculationIdentifier(id);
  }
}
