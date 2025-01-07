package neqsim.process.equipment.heatexchanger;

import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.HeaterResponse;

/**
 * <p>
 * Cooler class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Cooler extends Heater {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for Cooler.
   *
   * @param name name of cooler
   */
  public Cooler(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Cooler.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Cooler(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    UUID id = UUID.randomUUID();
    inStream.run(id);
    inStream.getFluid().init(3);
    getOutletStream().run(id);
    getOutletStream().getFluid().init(3);

    double heatTransferEntropyProd = coolingMediumTemperature * getDuty();
    System.out.println("heat entropy " + heatTransferEntropyProd);
    double entrop = getOutletStream().getThermoSystem().getEntropy(unit)
        - inStream.getThermoSystem().getEntropy(unit);

    return entrop;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new HeaterResponse(this));
  }
}
