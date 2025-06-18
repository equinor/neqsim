package neqsim.process.equipment.ejector;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Ejector class represents an ejector in a process simulation. It mixes a motive stream with a
 * suction stream and calculates the resulting mixed stream.
 *
 * @author esol
 */
public class Ejector extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private StreamInterface motiveStream;
  private StreamInterface suctionStream;
  private StreamInterface mixedStream;

  private double dischargePressure;
  private double efficiencyIsentropic = 0.75; // default nozzle mixing efficiency
  private double diffuserEfficiency = 0.8; // diffuser pressure recovery efficiency
  private double throatArea = 0.001; // default throat area [m2]

  /**
   * Constructs an Ejector with the specified name, motive stream, and suction stream.
   *
   * @param name the name of the ejector
   * @param motiveStream the motive stream
   * @param suctionStream the suction stream
   */
  public Ejector(String name, StreamInterface motiveStream, StreamInterface suctionStream) {
    super(name);
    this.motiveStream = motiveStream;
    this.suctionStream = suctionStream;
    this.mixedStream = motiveStream.clone();
  }

  /**
   * <p>
   * Setter for the field <code>dischargePressure</code>.
   * </p>
   *
   * @param dischargePressure a double
   */
  public void setDischargePressure(double dischargePressure) {
    this.dischargePressure = dischargePressure;
  }

  /**
   * <p>
   * Setter for the field <code>efficiencyIsentropic</code>.
   * </p>
   *
   * @param efficiencyIsentropic a double
   */
  public void setEfficiencyIsentropic(double efficiencyIsentropic) {
    this.efficiencyIsentropic = efficiencyIsentropic;
  }

  /**
   * <p>
   * Setter for the field <code>diffuserEfficiency</code>.
   * </p>
   *
   * @param diffuserEfficiency a double
   */
  public void setDiffuserEfficiency(double diffuserEfficiency) {
    this.diffuserEfficiency = diffuserEfficiency;
  }

  /**
   * <p>
   * Setter for the field <code>throatArea</code>.
   * </p>
   *
   * @param throatArea a double
   */
  public void setThroatArea(double throatArea) {
    this.throatArea = throatArea;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    motiveStream.run();
    suctionStream.run();
    // Mixing streams at constant discharge pressure
    motiveStream.setPressure(dischargePressure);
    suctionStream.setPressure(dischargePressure);

    double hMotive = motiveStream.getFluid().getEnthalpy();
    double hSuction = suctionStream.getFluid().getEnthalpy();

    double mDotMotive = motiveStream.getFlowRate("kg/s");
    double mDotSuction = suctionStream.getFlowRate("kg/s");

    double idealMixedEnthalpy =
        calculateIdealMixedEnthalpy(hMotive, hSuction, mDotMotive, mDotSuction);
    double hMixedActual = calculateActualMixedEnthalpy(hMotive, idealMixedEnthalpy);

    mixedStream = motiveStream.clone();
    // mixedStream.add(suctionStream);
    mixedStream.getFluid().setTotalFlowRate(mDotMotive + mDotSuction, "kg/s");
    // mixedStream.getFluid().setEnthalpy(hMixedActual, "J/kg");
    mixedStream.setPressure(dischargePressure);
    // mixedStream.runPHflash();

    checkChokedFlow(mDotMotive, mDotSuction);

    double hActualDiffuserOut = calculateDiffuserOutput(hMixedActual);
    // mixedStream.getFluid().setEnthalpy(hActualDiffuserOut, "J/kg");
    // mixedStream.runPHflash();

    // Update final state
    mixedStream.setPressure(mixedStream.getFluid().getPressure());
  }

  private double calculateIdealMixedEnthalpy(double hMotive, double hSuction, double mDotMotive,
      double mDotSuction) {
    return (hMotive * mDotMotive + hSuction * mDotSuction) / (mDotMotive + mDotSuction);
  }

  private double calculateActualMixedEnthalpy(double hMotive, double idealMixedEnthalpy) {
    return hMotive + (idealMixedEnthalpy - hMotive) / efficiencyIsentropic;
  }

  private void checkChokedFlow(double mDotMotive, double mDotSuction) {
    double density = mixedStream.getFluid().getDensity("kg/m3");
    double speedOfSound = mixedStream.getFluid().getSoundSpeed();
    double velocity = (mDotMotive + mDotSuction) / (density * throatArea);
    double machNumber = velocity / speedOfSound;

    if (machNumber >= 1.0) {
      System.out.println("Choked flow detected! Mach number: " + machNumber);
    } else {
      System.out.println("Flow not choked. Mach number: " + machNumber);
    }
  }

  private double calculateDiffuserOutput(double hMixedActual) {
    double vInlet = (motiveStream.getFlowRate("kg/s") + suctionStream.getFlowRate("kg/s"))
        / (mixedStream.getFluid().getDensity("kg/m3") * throatArea);
    double hIdealDiffuserOut = hMixedActual + 0.5 * vInlet * vInlet;
    return hMixedActual + (hIdealDiffuserOut - hMixedActual) / diffuserEfficiency;
  }

  /**
   * <p>
   * getOutStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOutStream() {
    return mixedStream;
  }

  /**
   * <p>
   * getEntrainmentRatio.
   * </p>
   *
   * @return a double
   */
  public double getEntrainmentRatio() {
    return suctionStream.getFlowRate("kg/s") / motiveStream.getFlowRate("kg/s");
  }
}
