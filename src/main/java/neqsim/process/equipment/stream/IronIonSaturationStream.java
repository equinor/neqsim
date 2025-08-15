/*
 * IronIonSaturationStream.java
 *
 * Created on 12. mars 2001, 13:11
 */

package neqsim.process.equipment.stream;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.exception.InvalidInputException;

/**
 * <p>
 * IronIonSaturationStream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class IronIonSaturationStream extends Stream {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(IronIonSaturationStream.class);

  protected SystemInterface reactiveThermoSystem;

  /**
   * Constructor for IronIonSaturationStream.
   *
   * @param name name of stream
   */
  public IronIonSaturationStream(String name) {
    super(name);
  }

  /**
   * Constructor for IronIonSaturationStream.
   *
   * @param name name of stream
   * @param stream input stream
   */
  public IronIonSaturationStream(String name, StreamInterface stream) {
    super(name, stream);
  }

  /**
   * <p>
   * Constructor for IronIonSaturationStream.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public IronIonSaturationStream(String name, SystemInterface thermoSystem) {
    super(name, thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public IronIonSaturationStream clone() {
    IronIonSaturationStream clonedSystem = null;
    try {
      clonedSystem = (IronIonSaturationStream) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public IronIonSaturationStream clone(String name) {
    if (this.getName() == name) {
      throw new RuntimeException(
          new InvalidInputException(this, "clone", "name", "- Same name as in original object"));
    }
    IronIonSaturationStream s = this.clone();
    s.setName(name);
    return s;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    logger.info("start flashing stream... " + streamNumber);
    if (stream != null) {
      thermoSystem = this.stream.getThermoSystem().clone();
    }
    if (stream != null) {
      reactiveThermoSystem = this.stream.getThermoSystem().setModel("Electrolyte-CPA-EOS-statoil");
    }
    reactiveThermoSystem.addComponent("Fe++", 1e-6);
    // reactiveThermoSystem.chemicalReactionInit();
    // reactiveThermoSystem.createDatabase(true);
    // reactiveThermoSystem.addComponent("water",
    // reactiveThermoSystem.getPhase(0).getComponent("MEG").getNumberOfmoles());
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
    thermoOps.TPflash();
    reactiveThermoSystem.initProperties();
    // reactiveThermoSystem.display();
    try {
      logger.info("aqueous phase number " + reactiveThermoSystem.getPhaseNumberOfPhase("aqueous"));
      thermoOps.addIonToScaleSaturation(reactiveThermoSystem.getPhaseNumberOfPhase("aqueous"),
          "FeCO3", "Fe++");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // reactiveThermoSystem.display();
    logger.info("number of phases: " + reactiveThermoSystem.getNumberOfPhases());
    logger.info("beta: " + reactiveThermoSystem.getBeta());
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    reactiveThermoSystem.display(name);
  }
}
