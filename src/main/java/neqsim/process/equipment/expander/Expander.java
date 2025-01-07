package neqsim.process.equipment.expander;

import java.util.UUID;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * Expander class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Expander extends Compressor implements ExpanderInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for Expander.
   *
   * @param name name of unit operation
   */
  public Expander(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Expander.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Expander(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // System.out.println("expander running..");
    thermoSystem = inStream.getThermoSystem().clone();
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
    thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoSystem.init(3);
    // double presinn = getThermoSystem().getPressure();
    double hinn = getThermoSystem().getEnthalpy();
    // double densInn = getThermoSystem().getDensity();
    double entropy = getThermoSystem().getEntropy();
    inletEnthalpy = hinn;
    double hout = hinn;
    if (usePolytropicCalc) {
      int numbersteps = 40;
      double dp = (pressure - getThermoSystem().getPressure()) / (1.0 * numbersteps);

      for (int i = 0; i < numbersteps; i++) {
        entropy = getThermoSystem().getEntropy();
        double hinn_loc = getThermoSystem().getEnthalpy();
        getThermoSystem().setPressure(getThermoSystem().getPressure() + dp);
        thermoOps.PSflash(entropy);
        hout = hinn_loc + (getThermoSystem().getEnthalpy() - hinn_loc) * polytropicEfficiency;
        thermoOps.PHflash(hout, 0);
      }

      dH = hout - hinn;
      /*
       * HYSYS method double oldPolyt = 10.5; int iter = 0; do {
       *
       * iter++; double n = Math.log(thermoSystem.getPressure() / presinn) /
       * Math.log(thermoSystem.getDensity() / densInn); double k =
       * Math.log(thermoSystem.getPressure() / presinn) / Math.log(densOutIdeal / densInn); double
       * factor = ((Math.pow(thermoSystem.getPressure() / presinn, (n - 1.0) / n) - 1.0) * (n / (n -
       * 1.0)) * (k - 1) / k) / (Math.pow(thermoSystem.getPressure() / presinn, (k - 1.0) / k) -
       * 1.0); oldPolyt = polytropicEfficiency; polytropicEfficiency = factor *
       * isentropicEfficiency; dH = thermoSystem.getEnthalpy() - hinn; hout = hinn + dH /
       * polytropicEfficiency; thermoOps.PHflash(hout, 0); System.out.println(" factor " + factor +
       * " n " + n + " k " + k + " polytropic effici " + polytropicEfficiency + " iter " + iter); }
       * while (Math.abs((oldPolyt - polytropicEfficiency) / oldPolyt) > 1e-5 && iter < 500); //
       * polytropicEfficiency = isentropicEfficiency * ();
       */
    } else {
      getThermoSystem().setPressure(pressure);

      // System.out.println("entropy inn.." + entropy);
      thermoOps.PSflash(entropy);
      // double densOutIdeal = getThermoSystem().getDensity();
      if (!powerSet) {
        dH = (getThermoSystem().getEnthalpy() - hinn) * isentropicEfficiency;
      }
      hout = hinn + dH;
      isentropicEfficiency = dH / (getThermoSystem().getEnthalpy() - hinn);
      // System.out.println("isentropicEfficiency.. " + isentropicEfficiency);
      dH = hout - hinn;
      thermoOps.PHflash(hout, 0);
    }
    if (isSetEnergyStream()) {
      energyStream.setDuty(-dH);
    }
    // thermoSystem.display();
    outStream.setThermoSystem(getThermoSystem());
    setCalculationIdentifier(id);
  }
}
