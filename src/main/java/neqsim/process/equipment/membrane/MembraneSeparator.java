package neqsim.process.equipment.membrane;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Simple membrane separation unit with one inlet stream and two outlet streams
 * (retentate and permeate). Each component can be assigned a permeate fraction
 * representing the fraction of that component transported to the permeate side.
 */
public class MembraneSeparator extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(MembraneSeparator.class);

  private StreamInterface inletStream;
  private StreamInterface permeateStream;
  private StreamInterface retentateStream;
  private final Map<String, Double> permeateFractions = new HashMap<>();
  private final Map<String, Double> permeabilities = new HashMap<>();
  private double defaultPermeateFraction = 0.0;
  private double membraneArea = 0.0; // m2

  public MembraneSeparator(String name) {
    super(name);
  }

  public MembraneSeparator(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;
    this.permeateStream = new Stream(getName() + " permeate", inletStream.getThermoSystem().clone());
    this.retentateStream = new Stream(getName() + " retentate", inletStream.getThermoSystem().clone());
  }

  public StreamInterface getPermeateStream() {
    return permeateStream;
  }

  public StreamInterface getRetentateStream() {
    return retentateStream;
  }

  /**
   * Set membrane area used for permeability calculations.
   *
   * @param area membrane area in m^2
   */
  public void setMembraneArea(double area) {
    this.membraneArea = Math.max(0.0, area);
  }

  /**
   * Specify permeability coefficient for a component.
   *
   * @param component component name
   * @param permeability permeability in mol/(m2*s*Pa)
   */
  public void setPermeability(String component, double permeability) {
    permeabilities.put(component, Math.max(0.0, permeability));
  }

  /**
   * Remove any permeate fractions set previously.
   */
  public void clearPermeateFractions() {
    permeateFractions.clear();
    defaultPermeateFraction = 0.0;
  }

  /**
   * Set a global permeate fraction used for all components not explicitly set.
   *
   * @param fraction permeate fraction (0-1)
   */
  public void setDefaultPermeateFraction(double fraction) {
    this.defaultPermeateFraction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Specify permeate fraction for a component.
   *
   * @param component component name
   * @param fraction permeate fraction (0-1)
   */
  public void setPermeateFraction(String component, double fraction) {
    permeateFractions.put(component, Math.max(0.0, Math.min(1.0, fraction)));
  }

  @Override
  public boolean needRecalculation() {
    return true;
  }

  @Override
  public void run(UUID id) {
    try {
      retentateStream.setThermoSystem(inletStream.getThermoSystem().clone());
      permeateStream.setThermoSystem(inletStream.getThermoSystem().clone());

      int comps = inletStream.getThermoSystem().getPhase(0).getNumberOfComponents();
      for (int i = 0; i < comps; i++) {
        String name = inletStream.getThermoSystem().getPhase(0).getComponent(i).getName();
        double moles = inletStream.getThermoSystem().getPhase(0).getComponent(i).getNumberOfmoles();

        double molesPerm = 0.0;
        if (!permeabilities.isEmpty() && membraneArea > 0.0) {
          double perm = permeabilities.getOrDefault(name, 0.0);
          double xFeed = inletStream.getThermoSystem().getPhase(0).getComponent(i).getx();
          double partialFeed = xFeed * inletStream.getThermoSystem().getPressure();
          molesPerm = perm * membraneArea * partialFeed;
          if (molesPerm > moles) {
            molesPerm = moles;
          }
        } else {
          double frac = permeateFractions.getOrDefault(name, defaultPermeateFraction);
          molesPerm = moles * frac;
        }

        retentateStream.getThermoSystem().addComponent(name, -molesPerm);
        permeateStream.getThermoSystem().addComponent(name, molesPerm);
      }

      ThermodynamicOperations opsRet = new ThermodynamicOperations(retentateStream.getThermoSystem());
      opsRet.TPflash();
      ThermodynamicOperations opsPerm = new ThermodynamicOperations(permeateStream.getThermoSystem());
      opsPerm.TPflash();

      retentateStream.setCalculationIdentifier(id);
      permeateStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
    } catch (Exception ex) {
      logger.error("Error in membrane separator", ex);
    }
  }

  @Override
  public void runTransient(double dt, UUID id) {
    run(id);
    increaseTime(dt);
  }
}
