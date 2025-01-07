/*
 * InterfaceProperties.java
 *
 * Created on 13. august 2001, 13:12
 */

package neqsim.physicalproperties.interfaceproperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.AdsorptionInterface;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.PotentialTheoryAdsorption;
import neqsim.physicalproperties.interfaceproperties.surfacetension.FirozabadiRamleyInterfaceTension;
import neqsim.physicalproperties.interfaceproperties.surfacetension.GTSurfaceTension;
import neqsim.physicalproperties.interfaceproperties.surfacetension.GTSurfaceTensionSimple;
import neqsim.physicalproperties.interfaceproperties.surfacetension.LGTSurfaceTension;
import neqsim.physicalproperties.interfaceproperties.surfacetension.ParachorSurfaceTension;
import neqsim.physicalproperties.interfaceproperties.surfacetension.SurfaceTensionInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * InterfaceProperties class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterfaceProperties implements InterphasePropertiesInterface, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(InterfaceProperties.class);

  SystemInterface system;
  SurfaceTensionInterface gasLiquidSurfaceTensionCalc = null;
  SurfaceTensionInterface gasAqueousSurfaceTensionCalc = null;
  SurfaceTensionInterface liquidLiquidSurfaceTensionCalc = null;
  private AdsorptionInterface[] adsorptionCalc;
  double[] surfaceTension;
  int numberOfInterfaces = 1;
  private int interfacialTensionModel = 0;

  /**
   * <p>
   * Constructor for InterfaceProperties.
   * </p>
   */
  public InterfaceProperties() {}

  /**
   * <p>
   * Constructor for InterfaceProperties.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public InterfaceProperties(SystemInterface system) {
    numberOfInterfaces = system.getNumberOfPhases() - 1;
    this.system = system;
    // gasLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
    // gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
    // liquidLiquidSurfaceTensionCalc = new
    // FirozabadiRamleyInterfaceTension(system);
  }

  /** {@inheritDoc} */
  @Override
  public InterfaceProperties clone() {
    InterfaceProperties clonedSystem = null;
    try {
      // clonedSystem = (InterfaceProperties) suclone();
      // clonedSystem.chemicalReactionOperations = (ChemicalReactionOperations)
      // chemicalReactionOperations.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    // clonedSystem.system = system;
    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void init(SystemInterface system) {
    this.system = system;
    init();
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    numberOfInterfaces = system.getNumberOfPhases() - 1;
    surfaceTension = new double[numberOfInterfaces + 1];
    if (gasLiquidSurfaceTensionCalc == null || gasAqueousSurfaceTensionCalc == null
        || liquidLiquidSurfaceTensionCalc == null) {
      setInterfacialTensionModel(interfacialTensionModel);
    }
    // surfaceTensionCalc[i] = new LGTSurfaceTension(system);
    // surfaceTension[i] = surfaceTensionCalc[i].calcSurfaceTension(i, i
    // + 1);
  }

  /** {@inheritDoc} */
  @Override
  public void initAdsorption() {
    setAdsorptionCalc(new AdsorptionInterface[system.getNumberOfPhases()]);

    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      getAdsorptionCalc()[i] = new PotentialTheoryAdsorption(system);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setSolidAdsorbentMaterial(String material) {
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      getAdsorptionCalc()[i].setSolidMaterial(material);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcAdsorption() {
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      getAdsorptionCalc()[i].calcAdsorption(i);
    }
  }

  /*
   * public double getSurfaceTension(int numb) { if (numb >= numberOfInterfaces) { return 0.0; }
   * else { return surfaceTension[numb]; } }
   */

  /** {@inheritDoc} */
  @Override
  public double getSurfaceTension(int numb1, int numb2) {
    if (system.getPhase(numb1).getType() == PhaseType.GAS
        && system.getPhase(numb2).getType() == PhaseType.OIL) {
      return gasLiquidSurfaceTensionCalc.calcSurfaceTension(numb1, numb2);
    } else if (system.getPhase(numb1).getType() == PhaseType.GAS
        && system.getPhase(numb2).getType() == PhaseType.AQUEOUS) {
      return gasAqueousSurfaceTensionCalc.calcSurfaceTension(numb1, numb2);
    } else {
      return liquidLiquidSurfaceTensionCalc.calcSurfaceTension(numb1, numb2);
    }
  }

  // TODO: add unit conversion implementation to interfacial tension
  /** {@inheritDoc} */
  @Override
  public double getSurfaceTension(int numb1, int numb2, String unit) {
    double val = getSurfaceTension(numb1, numb2);
    // ...conversion methods
    return val;
  }

  /** {@inheritDoc} */
  @Override
  public SurfaceTensionInterface getSurfaceTensionModel(int i) {
    if (system.getPhase(i).getType() == PhaseType.GAS) {
      return gasLiquidSurfaceTensionCalc;
    } else {
      return gasLiquidSurfaceTensionCalc;
    }
  }

  /** {@inheritDoc} */
  @Override
  public int getInterfacialTensionModel() {
    return interfacialTensionModel;
  }

  /** {@inheritDoc} */
  @Override
  public void setInterfacialTensionModel(String phase1, String phase2, String model) {
    SurfaceTensionInterface surfTensModel = null;
    if ("Linear Gradient Theory".equals(model)) {
      surfTensModel = new LGTSurfaceTension(system);
    } else if ("Simple Gradient Theory".equals(model)) {
      surfTensModel = new GTSurfaceTensionSimple(system);
    } else if ("Full Gradient Theory".equals(model)) {
      surfTensModel = new GTSurfaceTension(system);
    } else if ("Firozabadi Ramley".equals(model)) {
      surfTensModel = new FirozabadiRamleyInterfaceTension(system);
    } else if ("Parachor".equals(model) || "Weinaug-Katz".equals(model)) {
      surfTensModel = new ParachorSurfaceTension(system);
    } else {
      surfTensModel = new ParachorSurfaceTension(system);
    }

    if (phase1.equals("gas") && phase2.equals("oil")) {
      gasLiquidSurfaceTensionCalc = surfTensModel;
    } else if (phase1.equals("gas") && phase2.equals("aqueous")) {
      gasAqueousSurfaceTensionCalc = surfTensModel;
    } else if (phase1.equals("oil") && phase2.equals("aqueous")) {
      liquidLiquidSurfaceTensionCalc = surfTensModel;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setInterfacialTensionModel(int interfacialTensionModel) {
    this.interfacialTensionModel = interfacialTensionModel;
    if (interfacialTensionModel == 0) {
      gasLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
      gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
      liquidLiquidSurfaceTensionCalc = new FirozabadiRamleyInterfaceTension(system);
    } else if (interfacialTensionModel == 1) {
      gasLiquidSurfaceTensionCalc = new GTSurfaceTension(system); // Sintef method
      // liquidLiquidSurfaceTensionCalc = new GTSurfaceTension(system); //Sintef
      // method
      gasAqueousSurfaceTensionCalc = new GTSurfaceTensionSimple(system);
      // gasLiquidSurfaceTensionCalc = new GTSurfaceTensionSimple(system);
      liquidLiquidSurfaceTensionCalc = new GTSurfaceTensionSimple(system);
    } else if (interfacialTensionModel == 2) {
      gasLiquidSurfaceTensionCalc = new LGTSurfaceTension(system);
      liquidLiquidSurfaceTensionCalc = new LGTSurfaceTension(system);
      gasAqueousSurfaceTensionCalc = new LGTSurfaceTension(system);
    } else if (interfacialTensionModel == 3) {
      gasLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
      gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
      liquidLiquidSurfaceTensionCalc = new FirozabadiRamleyInterfaceTension(system);
    } else if (interfacialTensionModel == 4) {
      gasLiquidSurfaceTensionCalc = new GTSurfaceTensionSimple(system);
      liquidLiquidSurfaceTensionCalc = new LGTSurfaceTension(system);
      gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
    } else if (interfacialTensionModel == 5) {
      gasLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
      gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
      liquidLiquidSurfaceTensionCalc = new FirozabadiRamleyInterfaceTension(system);
    } else {
      gasLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
      gasAqueousSurfaceTensionCalc = new ParachorSurfaceTension(system);
      liquidLiquidSurfaceTensionCalc = new ParachorSurfaceTension(system);
    }
  }

  /** {@inheritDoc} */
  @Override
  public AdsorptionInterface[] getAdsorptionCalc() {
    return adsorptionCalc;
  }

  /** {@inheritDoc} */
  @Override
  public AdsorptionInterface getAdsorptionCalc(String phaseName) {
    return adsorptionCalc[system.getPhaseNumberOfPhase(phaseName)];
  }

  /** {@inheritDoc} */
  @Override
  public void setAdsorptionCalc(AdsorptionInterface[] adsorptionCalc) {
    this.adsorptionCalc = adsorptionCalc;
  }
}
