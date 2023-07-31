package neqsim.processSimulation.processEquipment.distillation;

import java.util.UUID;
import neqsim.processSimulation.processEquipment.splitter.Splitter;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Condenser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Condenser extends SimpleTray {
  private static final long serialVersionUID = 1000;

  private double refluxRatio = 0.1;
  boolean refluxIsSet = false;
  double duty = 0.0;
  boolean totalCondenser = false;
  Splitter mixedStreamSplitter = null;

  public void setTotalCondenser(boolean isTotalCondenser) {
    this.totalCondenser = isTotalCondenser;
  }

  /**
   * {@inheritDoc}
   *
   * @param name a {@link java.lang.String} object
   */
  public Condenser(String name) {
    super(name);
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
   * getGasOutStream.
   * </p>
   *
   * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
   */
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
   * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
   */
  public StreamInterface getProductOutStream() {
    return super.getGasOutStream();
  }

  /**
   * <p>
   * getLiquidOutStream.
   * </p>
   *
   * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
   */
  public StreamInterface getLiquidOutStream() {
    if (totalCondenser) {
      return new Stream("", mixedStreamSplitter.getSplitStream(0));
    } else {
      return super.getLiquidOutStream();
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
        e.printStackTrace();
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
