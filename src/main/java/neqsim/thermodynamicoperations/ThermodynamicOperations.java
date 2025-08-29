package neqsim.thermodynamicoperations;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.Arrays;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.api.ioc.CalculationResult;
import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemProperties;
import neqsim.thermodynamicoperations.flashops.CalcIonicComposition;
import neqsim.thermodynamicoperations.flashops.CriticalPointFlash;
import neqsim.thermodynamicoperations.flashops.PHflash;
import neqsim.thermodynamicoperations.flashops.PHflashSingleComp;
import neqsim.thermodynamicoperations.flashops.PHsolidFlash;
import neqsim.thermodynamicoperations.flashops.PSFlash;
import neqsim.thermodynamicoperations.flashops.PSFlashGERG2008;
import neqsim.thermodynamicoperations.flashops.PSFlashLeachman;
import neqsim.thermodynamicoperations.flashops.PSFlashVega;
import neqsim.thermodynamicoperations.flashops.PSflashSingleComp;
import neqsim.thermodynamicoperations.flashops.PVrefluxflash;
import neqsim.thermodynamicoperations.flashops.SaturateWithWater;
import neqsim.thermodynamicoperations.flashops.SolidFlash1;
import neqsim.thermodynamicoperations.flashops.TPgradientFlash;
import neqsim.thermodynamicoperations.flashops.TSFlash;
import neqsim.thermodynamicoperations.flashops.TVflash;
import neqsim.thermodynamicoperations.flashops.VHflashQfunc;
import neqsim.thermodynamicoperations.flashops.VUflashQfunc;
import neqsim.thermodynamicoperations.flashops.dTPflash;
import neqsim.thermodynamicoperations.flashops.saturationops.AddIonToScaleSaturation;
import neqsim.thermodynamicoperations.flashops.saturationops.BubblePointPressureFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.BubblePointPressureFlashDer;
import neqsim.thermodynamicoperations.flashops.saturationops.BubblePointTemperatureNoDer;
import neqsim.thermodynamicoperations.flashops.saturationops.CalcSaltSatauration;
import neqsim.thermodynamicoperations.flashops.saturationops.CheckScalePotential;
import neqsim.thermodynamicoperations.flashops.saturationops.ConstantDutyFlashInterface;
import neqsim.thermodynamicoperations.flashops.saturationops.ConstantDutyPressureFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.ConstantDutyTemperatureFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.CricondebarFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.DewPointPressureFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.DewPointTemperatureFlashDer;
import neqsim.thermodynamicoperations.flashops.saturationops.FreezingPointTemperatureFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.HCdewPointPressureFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.HydrateEquilibriumLine;
import neqsim.thermodynamicoperations.flashops.saturationops.HydrateFormationPressureFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.HydrateFormationTemperatureFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.HydrateInhibitorConcentrationFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.HydrateInhibitorwtFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.SolidComplexTemperatureCalc;
import neqsim.thermodynamicoperations.flashops.saturationops.WATcalc;
import neqsim.thermodynamicoperations.flashops.saturationops.WaterDewPointEquilibriumLine;
import neqsim.thermodynamicoperations.flashops.saturationops.WaterDewPointTemperatureFlash;
import neqsim.thermodynamicoperations.flashops.saturationops.WaterDewPointTemperatureMultiphaseFlash;
import neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops.CricondenBarFlash;
import neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops.CricondenThermFlash;
import neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops.HPTphaseEnvelope;
import neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops.PTphaseEnvelope;
import neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops.PTphaseEnvelopeNew2;
import neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops.PTphaseEnvelopeNew3;
import neqsim.thermodynamicoperations.phaseenvelopeops.reactivecurves.PloadingCurve2;
import neqsim.thermodynamicoperations.propertygenerator.OLGApropertyTableGeneratorWaterKeywordFormat;
import neqsim.thermodynamicoperations.propertygenerator.OLGApropertyTableGeneratorWaterStudents;
import neqsim.thermodynamicoperations.propertygenerator.OLGApropertyTableGeneratorWaterStudentsPH;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.exception.IsNaNException;

/**
 * <p>
 * ThermodynamicOperations class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ThermodynamicOperations implements java.io.Serializable, Cloneable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ThermodynamicOperations.class);

  private Thread thermoOperationThread = new Thread();
  private OperationInterface operation = null;

  SystemInterface system = null;
  boolean writeFile = false;
  String fileName = null;
  private boolean runAsThread = false;
  protected String[][] resultTable = null;

  /**
   * <p>
   * Constructor for ThermodynamicOperations.
   * </p>
   */
  public ThermodynamicOperations() {}

  /**
   * <p>
   * Constructor for ThermodynamicOperations.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ThermodynamicOperations(SystemInterface system) {
    this.system = system;
  }

  /**
   * <p>
   * Getter for the field <code>system</code>.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getSystem() {
    return system;
  }

  /**
   * <p>
   * Setter for the field <code>system</code>.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setSystem(SystemInterface system) {
    this.system = system;
  }

  /**
   * <p>
   * TPSolidflash.
   * </p>
   */
  public void TPSolidflash() {
    operation = new SolidFlash1(system);
    getOperation().run();
  }

  /**
   * Method to perform a flash at given temperature, pressure and specified volume. The number of
   * moles in the system are changed to match the specified volume.
   *
   * @param volumeSpec is the specified volume
   * @param unit Supported units are m3
   */
  public void TPVflash(double volumeSpec, String unit) {
    unit = "m3";
    TPflash();
    double startVolume = system.getVolume(unit);
    system.setTotalNumberOfMoles(system.getNumberOfMoles() * volumeSpec / startVolume);
    system.init(3);
  }

  /**
   * <p>
   * TPflash.
   * </p>
   */
  public void TPflash() {
    // Check if system is Soreide-Whitson and use the special flash if so
    if (system != null
        && system.getClass().getName().equals("neqsim.thermo.system.SystemSoreideWhitson")) {
      TPflashSoreideWhitson();
      return;
    }
    double flowRate = system.getTotalNumberOfMoles();
    if (flowRate < 1e-5) {
      system.setTotalNumberOfMoles(1.0);
      system.init(1);
    }
    operation =
        new neqsim.thermodynamicoperations.flashops.TPflash(system, system.doSolidPhaseCheck());
    if (!isRunAsThread()) {
      getOperation().run();
    } else {
      run();
    }

    if (flowRate < 1e-5) {
      final double minimumFlowRate = 1e-50;
      if (flowRate < minimumFlowRate) {
        system.setTotalNumberOfMoles(minimumFlowRate);
      } else {
        system.setTotalNumberOfMoles(flowRate);
      }
      system.init(1);
    }
  }

  /**
   * <p>
   * TPflashSoreideWhitson.
   * </p>
   */
  public void TPflashSoreideWhitson() {
    double flowRate = system.getTotalNumberOfMoles();
    if (flowRate < 1e-5) {
      system.setTotalNumberOfMoles(1.0);
      system.init(1);
    }
    operation =
        new neqsim.thermodynamicoperations.flashops.TPflash(system, system.doSolidPhaseCheck());

    boolean rerun = true;
    int iterationCount = 0;
    while (rerun) {
      operation =
          new neqsim.thermodynamicoperations.flashops.TPflash(system, system.doSolidPhaseCheck());
      if (!isRunAsThread()) {
        getOperation().run();
      } else {
        run();
      }
      rerun = ((neqsim.thermo.system.SystemSoreideWhitson) system).calcSalinity();
      iterationCount++;
      if (iterationCount >= 10) {
        System.err.println(
            "Warning: Maximum number of iterations (10) reached in TPflash salinity loop. Stopping further execution.");
        break;
      }
    }
    // --- End salinity logic ---

    if (flowRate < 1e-5) {
      final double minimumFlowRate = 1e-50;
      if (flowRate < minimumFlowRate) {
        system.setTotalNumberOfMoles(minimumFlowRate);
      } else {
        system.setTotalNumberOfMoles(flowRate);
      }
      system.init(1);
    }
  }

  /**
   * <p>
   * TPflash.
   * </p>
   *
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public void TPflash(boolean checkForSolids) {
    operation = new neqsim.thermodynamicoperations.flashops.TPflash(system, checkForSolids);
    getOperation().run();
  }

  /**
   * <p>
   * TPgradientFlash.
   * </p>
   *
   * @param height a double
   * @param temperature a double
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface TPgradientFlash(double height, double temperature) {
    operation = new TPgradientFlash(system, height, temperature);
    getOperation().run();
    return operation.getThermoSystem();
  }

  /**
   * <p>
   * dTPflash.
   * </p>
   *
   * @param comps an array of {@link java.lang.String} objects
   */
  public void dTPflash(String[] comps) {
    operation = new dTPflash(system, comps);
    getOperation().run();
  }

  /**
   * <p>
   * saturateWithWater.
   * </p>
   */
  public void saturateWithWater() {
    operation = new SaturateWithWater(system);
    getOperation().run();
  }

  /**
   * <p>
   * chemicalEquilibrium.
   * </p>
   */
  public void chemicalEquilibrium() {
    if (system.isChemicalSystem()) {
      operation =
          new neqsim.thermodynamicoperations.chemicalequilibrium.ChemicalEquilibrium(system);
      getOperation().run();
    }
  }

  /**
   * <p>
   * PHflash.
   * </p>
   *
   * @param Hspec a double
   * @param type a int
   */
  public void PHflash(double Hspec, int type) {
    if (system.getPhase(0).getNumberOfComponents() == 1) {
      operation = new PHflashSingleComp(system, Hspec, type);
    } else {
      operation = new PHflash(system, Hspec, type);
    }
    getOperation().run();
  }

  /**
   * Method to perform a PH flash calculation.
   *
   * @param Hspec is the enthalpy in the specified unit
   * @param enthalpyUnit Supported units are J, J/mol, J/kg and kJ/kg
   */
  public void PHflash(double Hspec, String enthalpyUnit) {
    double conversionFactor = 1.0;
    switch (enthalpyUnit) {
      case "J":
        conversionFactor = 1.0;
        break;
      case "J/mol":
        conversionFactor = 1.0 / system.getTotalNumberOfMoles();
        break;
      case "J/kg":
        conversionFactor = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
        break;
      case "kJ/kg":
        conversionFactor = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
        break;
      default:
        break;
    }
    PHflash(Hspec / conversionFactor);
  }

  /**
   * Method to perform a PH flash calculation.
   *
   * @param Hspec is the enthalpy in unit Joule to be held constant
   */
  public void PHflash(double Hspec) {
    this.PHflash(Hspec, 0);
  }

  /**
   * Method to perform a PH flash calculation based on GERG2008 EoS.
   *
   * @param Hspec is the enthalpy in unit Joule to be held constant
   */
  public void PHflashGERG2008(double Hspec) {
    operation = new neqsim.thermodynamicoperations.flashops.PHflashGERG2008(system, Hspec);
    getOperation().run();
  }

  /**
   * Method to perform a PH flash calculation based on Leachman EoS.
   *
   * @param Hspec is the enthalpy in unit Joule to be held constant
   */
  public void PHflashLeachman(double Hspec) {
    operation = new neqsim.thermodynamicoperations.flashops.PHflashLeachman(system, Hspec);
    getOperation().run();
  }

  /**
   * Method to perform a PH flash calculation based on Vega EoS.
   *
   * @param Hspec is the enthalpy in unit Joule to be held constant
   */
  public void PHflashVega(double Hspec) {
    operation = new neqsim.thermodynamicoperations.flashops.PHflashVega(system, Hspec);
    getOperation().run();
  }

  /**
   * <p>
   * PUflash.
   * </p>
   *
   * @param Uspec a double
   */
  public void PUflash(double Uspec) {
    operation = new neqsim.thermodynamicoperations.flashops.PUflash(system, Uspec);
    getOperation().run();
  }

  /**
   * <p>
   * PUflash.
   * </p>
   *
   * @param Pspec a double
   * @param Uspec a double
   * @param unitPressure a {@link java.lang.String} object
   * @param unitEnergy a {@link java.lang.String} object
   */
  public void PUflash(double Pspec, double Uspec, String unitPressure, String unitEnergy) {
    system.setPressure(Pspec, unitPressure);
    PUflash(Uspec, unitEnergy);
  }

  /**
   * <p>
   * PUflash.
   * </p>
   *
   * @param Uspec a double
   * @param unitEnergy a {@link java.lang.String} object
   */
  public void PUflash(double Uspec, String unitEnergy) {
    double conversionFactorEntr = 1.0;
    switch (unitEnergy) {
      case "J":
        conversionFactorEntr = 1.0;
        break;
      case "J/mol":
        conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles();
        break;
      case "J/kg":
        conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
        break;
      case "kJ/kg":
        conversionFactorEntr =
            1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
        break;
      default:
        break;
    }
    PUflash(Uspec / conversionFactorEntr);
  }

  /**
   * <p>
   * PHflash2.
   * </p>
   *
   * @param Hspec a double
   * @param type a int
   */
  public void PHflash2(double Hspec, int type) {
    operation = new PHflash(system, Hspec, type);
    getOperation().run();
  }

  /**
   * <p>
   * criticalPointFlash.
   * </p>
   */
  public void criticalPointFlash() {
    operation = new CriticalPointFlash(system);
    getOperation().run();
  }

  /**
   * <p>
   * PHsolidFlash.
   * </p>
   *
   * @param Hspec a double
   */
  public void PHsolidFlash(double Hspec) {
    operation = new PHsolidFlash(system, Hspec);
    getOperation().run();
  }

  /**
   * <p>
   * PSflash.
   * </p>
   *
   * @param Sspec a double
   */
  public void PSflash(double Sspec) {
    if (system.getPhase(0).getNumberOfComponents() == 1) {
      operation = new PSflashSingleComp(system, Sspec, 0);
    } else {
      operation = new PSFlash(system, Sspec, 0);
    }
    getOperation().run();
  }

  /**
   * Method to perform a PS flash calculation for a specified entropy and pressure.
   *
   * @param Sspec is the entropy in the specified unit
   * @param unit Supported units are J/K, J/molK, J/kgK and kJ/kgK
   */
  public void PSflash(double Sspec, String unit) {
    double conversionFactor = 1.0;
    switch (unit) {
      case "J/K":
        break;
      case "J/molK":
        conversionFactor = 1.0 / system.getTotalNumberOfMoles();
        break;
      case "J/kgK":
        conversionFactor = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
        break;
      case "kJ/kgK":
        conversionFactor = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
        break;
      default:
        break;
    }
    PSflash(Sspec / conversionFactor);
  }

  /**
   * <p>
   * TSflash.
   * </p>
   *
   * @param Sspec a double
   */
  public void TSflash(double Sspec) {
    operation = new TSFlash(system, Sspec);
    getOperation().run();
  }

  /**
   * Method to perform a TS flash calculation for a specified entropy and pressure.
   *
   * @param Sspec is the entropy in the specified unit
   * @param unit Supported units are J/K, J/molK, J/kgK and kJ/kgK
   */
  public void TSflash(double Sspec, String unit) {
    double conversionFactor = 1.0;
    switch (unit) {
      case "J/K":
        conversionFactor = 1.0;
        break;
      case "J/molK":
        conversionFactor = 1.0 / system.getTotalNumberOfMoles();
        break;
      case "J/kgK":
        conversionFactor = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
        break;
      case "kJ/kgK":
        conversionFactor = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
        break;
      default:
        break;
    }
    TSflash(Sspec / conversionFactor);
  }

  /**
   * <p>
   * PSflashGERG2008.
   * </p>
   * Run a flash at constant pressure and entropy using the GERG2008 EoS
   *
   * @param Sspec is the specidfied entropy
   */
  public void PSflashGERG2008(double Sspec) {
    operation = new PSFlashGERG2008(system, Sspec);
    getOperation().run();
  }

  /**
   * <p>
   * PSflashLeachman.
   * </p>
   * Run a flash at constant pressure and entropy using the Leachman EoS
   *
   * @param Sspec is the specidfied entropy
   */
  public void PSflashLeachman(double Sspec) {
    operation = new PSFlashLeachman(system, Sspec);
    getOperation().run();
  }

  /**
   * <p>
   * PSflashVega.
   * </p>
   * Run a flash at constant pressure and entropy using the Vega EoS
   *
   * @param Sspec is the specidfied entropy
   */
  public void PSflashVega(double Sspec) {
    operation = new PSFlashVega(system, Sspec);
    getOperation().run();
  }

  /**
   * <p>
   * PSflash2.
   * </p>
   *
   * @param Sspec a double
   */
  public void PSflash2(double Sspec) {
    operation = new PSFlash(system, Sspec, 0);
    getOperation().run();
  }

  /**
   * <p>
   * VSflash.
   * </p>
   *
   * @param volume a double
   * @param entropy a double
   * @param unitVol a {@link java.lang.String} object
   * @param unitEntropy a {@link java.lang.String} object
   */
  public void VSflash(double volume, double entropy, String unitVol, String unitEntropy) {
    double conversionFactorV = 1.0;
    double conversionFactorEntr = 1.0;

    switch (unitVol) {
      case "m3":
        conversionFactorV = 1.0e5;
        break;
      default:
        break;
    }

    switch (unitEntropy) {
      case "J/K":
        conversionFactorEntr = 1.0;
        break;
      case "J/molK":
        conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles();
        break;
      case "J/kgK":
        conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
        break;
      case "kJ/kgK":
        conversionFactorEntr =
            1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
        break;
      default:
        break;
    }
    VSflash(volume * conversionFactorV, entropy / conversionFactorEntr);
  }

  /**
   * <p>
   * VSflash.
   * </p>
   *
   * @param volume a double
   * @param entropy a double
   */
  public void VSflash(double volume, double entropy) {
    operation = new neqsim.thermodynamicoperations.flashops.VSflash(system, volume, entropy);
    getOperation().run();
  }

  /**
   * <p>
   * TVflash.
   * </p>
   *
   * @param Vspec a double
   * @param unit a {@link java.lang.String} object
   */
  public void TVflash(double Vspec, String unit) {
    double conversionFactor = 1.0;
    switch (unit) {
      case "m3":
        conversionFactor = 1.0e5;
        break;
      default:
        break;
    }
    TVflash(Vspec * conversionFactor);
  }

  /**
   * <p>
   * TVflash.
   * </p>
   *
   * @param Vspec a double
   */
  public void TVflash(double Vspec) {
    operation = new TVflash(system, Vspec);
    getOperation().run();
  }

  /**
   * <p>
   * TVfractionFlash.
   * </p>
   *
   * @param Vspec a double volume fraction of first/lightest phase
   */
  public void TVfractionFlash(double Vspec) {
    operation = new neqsim.thermodynamicoperations.flashops.TVfractionFlash(system, Vspec);
    try {
      getOperation().run();
    } catch (Exception e) {
      throw e;
    }
  }

  /**
   * <p>
   * PVrefluxFlash.
   * </p>
   *
   * @param refluxspec a double
   * @param refluxPhase a int
   */
  public void PVrefluxFlash(double refluxspec, int refluxPhase) {
    operation = new PVrefluxflash(system, refluxspec, refluxPhase);
    getOperation().run();
  }

  /**
   * <p>
   * VHflash.
   * </p>
   *
   * @param Vspec a double
   * @param Hspec a double
   */
  public void VHflash(double Vspec, double Hspec) {
    operation = new VHflashQfunc(system, Vspec, Hspec);
    getOperation().run();
  }

  /**
   * <p>
   * VHflash.
   * </p>
   *
   * @param volume a double
   * @param enthalpy a double
   * @param unitVol a {@link java.lang.String} object
   * @param unitEnthalpy a {@link java.lang.String} object
   */
  public void VHflash(double volume, double enthalpy, String unitVol, String unitEnthalpy) {
    double conversionFactorV = 1.0;
    double conversionFactorEntr = 1.0;

    switch (unitVol) {
      case "m3":
        conversionFactorV = 1.0e5;
        break;
      default:
        break;
    }

    switch (unitEnthalpy) {
      case "J/K":
        conversionFactorEntr = 1.0;
        break;
      case "J/mol":
        conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles();
        break;
      case "J/kg":
        conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
        break;
      case "kJ/kg":
        conversionFactorEntr =
            1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
        break;
      default:
        break;
    }
    VHflash(volume * conversionFactorV, enthalpy / conversionFactorEntr);
  }

  /**
   * <p>
   * VUflash.
   * </p>
   *
   * @param volume a double
   * @param energy a double
   * @param unitVol a {@link java.lang.String} object
   * @param unitEnergy a {@link java.lang.String} object
   */
  public void VUflash(double volume, double energy, String unitVol, String unitEnergy) {
    double conversionFactorV = 1.0;
    double conversionFactorEntr = 1.0;

    switch (unitVol) {
      case "m3":
        conversionFactorV = 1.0e5;
        break;
      default:
        break;
    }

    switch (unitEnergy) {
      case "J/K":
        conversionFactorEntr = 1.0;
        break;
      case "J/mol":
        conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles();
        break;
      case "J/kg":
        conversionFactorEntr = 1.0 / system.getTotalNumberOfMoles() / system.getMolarMass();
        break;
      case "kJ/kg":
        conversionFactorEntr =
            1.0 / system.getTotalNumberOfMoles() / system.getMolarMass() / 1000.0;
        break;
      default:
        break;
    }
    VUflash(volume * conversionFactorV, energy / conversionFactorEntr);
  }

  /**
   * <p>
   * VUflash.
   * </p>
   *
   * @param Vspec a double
   * @param Uspec a double
   */
  public void VUflash(double Vspec, double Uspec) {
    operation = new VUflashQfunc(system, Vspec, Uspec);
    getOperation().run();
  }

  /**
   * <p>
   * bubblePointTemperatureFlash.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void bubblePointTemperatureFlash() throws IsNaNException {
    ConstantDutyFlashInterface operation = new BubblePointTemperatureNoDer(system);
    operation.run();
    if (Double.isNaN(system.getTemperature()) || operation.isSuperCritical()) {
      throw new neqsim.util.exception.IsNaNException(this, "bubblePointTemperatureFlash",
          "Could not find solution - possible no bubble point exists");
    }
  }

  /**
   * <p>
   * freezingPointTemperatureFlash.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void freezingPointTemperatureFlash() throws IsNaNException {
    operation = new FreezingPointTemperatureFlash(system);
    getOperation().run();
    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "freezingPointTemperatureFlash",
          "Could not find solution - possible no freezing point exists");
    }
  }

  /**
   * <p>
   * freezingPointTemperatureFlash.
   * </p>
   *
   * @param phaseName a {@link java.lang.String} object
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void freezingPointTemperatureFlash(String phaseName) throws IsNaNException {
    operation = new FreezingPointTemperatureFlash(system);
    getOperation().run();
    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "freezingPointTemperatureFlash",
          "Could not find solution - possible no freezing point exists");
    }
  }

  /**
   * <p>
   * waterDewPointTemperatureFlash.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void waterDewPointTemperatureFlash() throws IsNaNException {
    operation = new WaterDewPointTemperatureFlash(system);
    getOperation().run();
    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "waterDewPointTemperatureFlash",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * waterDewPointTemperatureMultiphaseFlash.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void waterDewPointTemperatureMultiphaseFlash() throws IsNaNException {
    operation = new WaterDewPointTemperatureMultiphaseFlash(system);
    getOperation().run();
  }

  /**
   * <p>
   * waterPrecipitationTemperature.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void waterPrecipitationTemperature() throws IsNaNException {
    double lowTemperature = 0.0;
    dewPointTemperatureFlash();

    if (system.getTemperature() > lowTemperature) {
      lowTemperature = system.getTemperature();
    }

    // if(lowTemperature<273.15 && system.doSolidPhaseCheck()){
    // hydrateFormationTemperature(0);
    // if(system.getTemperature()>lowTemperature) lowTemperature =
    // system.getTemperature();
    // }

    // if(system.getHydrateCheck()){
    // hydrateFormationTemperature(1);
    // if(system.getTemperature()>lowTemperature) lowTemperature =
    // system.getTemperature();
    // hydrateFormationTemperature(2);
    // if(system.getTemperature()>lowTemperature) lowTemperature =
    // system.getTemperature();
    // }

    system.setTemperature(lowTemperature);
    // TPflash();

    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "waterPrecipitationTemperature",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * calcSaltSaturation.
   * </p>
   *
   * @param saltName a {@link java.lang.String} object
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void calcSaltSaturation(String saltName) throws IsNaNException {
    operation = new CalcSaltSatauration(system, saltName);
    getOperation().run();
    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "calcSaltSaturation",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * checkScalePotential.
   * </p>
   *
   * @param phaseNumber a int
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void checkScalePotential(int phaseNumber) throws IsNaNException {
    operation = new CheckScalePotential(system, phaseNumber);
    getOperation().run();
    resultTable = getOperation().getResultTable();
    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "checkScalePotential",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * addIonToScaleSaturation.
   * </p>
   *
   * @param phaseNumber a int
   * @param scaleSaltName a {@link java.lang.String} object
   * @param nameOfIonToBeAdded a {@link java.lang.String} object
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void addIonToScaleSaturation(int phaseNumber, String scaleSaltName,
      String nameOfIonToBeAdded) throws IsNaNException {
    operation = new AddIonToScaleSaturation(system, phaseNumber, scaleSaltName, nameOfIonToBeAdded);
    getOperation().run();
    resultTable = getOperation().getResultTable();
    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "addIonToScaleSaturation",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * hydrateFormationPressure.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void hydrateFormationPressure() throws IsNaNException {
    operation = new HydrateFormationPressureFlash(system);
    getOperation().run();
    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "hydrateFormationPressure",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * calcWAT.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void calcWAT() throws IsNaNException {
    operation = new WATcalc(system);
    getOperation().run();
    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "calcWAT",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * run.
   * </p>
   */
  public void run() {
    setThermoOperationThread(new Thread(operation));
    getThermoOperationThread().start();
  }

  /**
   * <p>
   * waitAndCheckForFinishedCalculation.
   * </p>
   *
   * @param maxTime a int
   * @return a boolean
   */
  public boolean waitAndCheckForFinishedCalculation(int maxTime) {
    try {
      getThermoOperationThread().join(maxTime);
      getThermoOperationThread().interrupt();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    boolean didFinish = !getThermoOperationThread().isInterrupted();
    // getThermoOperationThread().stop();
    return didFinish;
  }

  /**
   * <p>
   * waitToFinishCalculation.
   * </p>
   */
  public void waitToFinishCalculation() {
    try {
      getThermoOperationThread().join();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * calcSolidComlexTemperature.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void calcSolidComlexTemperature() throws IsNaNException {
    operation = new SolidComplexTemperatureCalc(system);
    getOperation().run();
    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "calcSolidComlexTemperature",
          "error in WAT() - could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * calcSolidComlexTemperature.
   * </p>
   *
   * @param comp1 a {@link java.lang.String} object
   * @param comp2 a {@link java.lang.String} object
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void calcSolidComlexTemperature(String comp1, String comp2) throws IsNaNException {
    if (operation == null) {
      operation = new SolidComplexTemperatureCalc(system, comp1, comp2);
    }
    getOperation().run();
    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "calcSolidComlexTemperature",
          "error in WAT() - could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * calcImobilePhaseHydrateTemperature.
   * </p>
   *
   * @param temperature an array of type double
   * @param pressure an array of type double
   * @return an array of type double
   */
  public double[] calcImobilePhaseHydrateTemperature(double[] temperature, double[] pressure) {
    double[] hydTemps = new double[temperature.length];
    SystemInterface systemTemp;
    ThermodynamicOperations opsTemp;
    systemTemp = system.clone();

    for (int i = 0; i < temperature.length; i++) {
      // todo: this function does not actually set hydTemps which is the returned
      // variable
      /*
       * opsTemp = new ThermodynamicOperations(systemTemp);
       * systemTemp.setTemperature(temperature[i]); systemTemp.setPressure(pressure[i]);
       * systemTemp.init(0); systemTemp.display(); try { opsTemp.hydrateFormationTemperature(); }
       * catch (Exception ex) { logger.error(ex.getMessage(),e); } systemTemp.display(); hydTemps[i]
       * = systemTemp.getTemperature();
       */
      opsTemp = new ThermodynamicOperations(systemTemp);
      systemTemp.setTemperature(temperature[i]);
      systemTemp.setPressure(pressure[i]);

      opsTemp.TPflash();
      systemTemp.display();
      systemTemp = systemTemp.phaseToSystem(0);
    }

    opsTemp = new ThermodynamicOperations(systemTemp);
    systemTemp.setHydrateCheck(true);
    systemTemp.setMixingRule(9);
    try {
      opsTemp.hydrateFormationTemperature();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    systemTemp.display();
    return hydTemps;
  }

  /**
   * <p>
   * calcTOLHydrateFormationTemperature.
   * </p>
   *
   * @return a double
   */
  public double calcTOLHydrateFormationTemperature() {
    TPflash();

    SystemInterface systemTemp = system.phaseToSystem(0);
    ThermodynamicOperations opsTemp = new ThermodynamicOperations(systemTemp);
    try {
      opsTemp.hydrateFormationTemperature();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    systemTemp.display();
    system.setTemperature(systemTemp.getTemperature());
    TPflash();
    return system.getTemperature();
  }

  /**
   * <p>
   * hydrateInhibitorConcentration.
   * </p>
   *
   * @param inhibitorName a {@link java.lang.String} object
   * @param hydEqTemperature a double
   */
  public void hydrateInhibitorConcentration(String inhibitorName, double hydEqTemperature) {
    operation = new HydrateInhibitorConcentrationFlash(system, inhibitorName, hydEqTemperature);
    operation.run();
  }

  /**
   * <p>
   * hydrateInhibitorConcentrationSet.
   * </p>
   *
   * @param inhibitorName a {@link java.lang.String} object
   * @param wtfrac a double
   */
  public void hydrateInhibitorConcentrationSet(String inhibitorName, double wtfrac) {
    operation = new HydrateInhibitorwtFlash(system, inhibitorName, wtfrac);
    operation.run();
  }

  /**
   * <p>
   * hydrateFormationTemperature.
   * </p>
   *
   * @param initialTemperatureGuess a double
   */
  public void hydrateFormationTemperature(double initialTemperatureGuess) {
    system.setTemperature(initialTemperatureGuess);
    operation = new HydrateFormationTemperatureFlash(system);
    for (int i = 0; i < system.getPhase(4).getNumberOfComponents(); i++) {
      ((ComponentHydrate) system.getPhase(4).getComponent(i)).getHydrateStructure();
    }
    if (!isRunAsThread()) {
      getOperation().run();
    } else {
      run();
    }
  }

  /**
   * <p>
   * hydrateFormationTemperature.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void hydrateFormationTemperature() throws IsNaNException {
    // guessing temperature
    double factor = 1.0;
    if (system.getPhase(0).hasComponent("methanol")) {
      factor -= 2 * system.getPhase(0).getComponent("methanol").getz()
          / system.getPhase(0).getComponent("water").getz();
    }
    if (system.getPhase(0).hasComponent("MEG")) {
      factor -= 2 * system.getPhase(0).getComponent("MEG").getz()
          / system.getPhase(0).getComponent("water").getz();
    }
    if (factor < 2) {
      factor = 2;
    }

    system.setTemperature(273.0 + system.getPressure() / 100.0 * 20.0 * factor - 20.0);
    if (system.getTemperature() > 298.15) {
      system.setTemperature(273.0 + 25.0);
    }
    // logger.info("guess hydrate temperature " + system.getTemperature());
    operation = new HydrateFormationTemperatureFlash(system);

    for (int i = 0; i < system.getPhase(4).getNumberOfComponents(); i++) {
      ((ComponentHydrate) system.getPhase(4).getComponent(i)).getHydrateStructure();
    }
    if (!isRunAsThread()) {
      getOperation().run();
    } else {
      run();
    }
    // logger.info("Hydrate structure " + (((ComponentHydrate)
    // system.getPhase(4).getComponent("water")).getHydrateStructure() + 1));
  }

  /**
   * <p>
   * hydrateFormationTemperature.
   * </p>
   *
   * @param structure a int
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void hydrateFormationTemperature(int structure) throws IsNaNException {
    system.setTemperature(273.0 + 1.0);
    if (structure == 0) {
      system.setSolidPhaseCheck("water");
      system.setHydrateCheck(true);
      operation = new FreezingPointTemperatureFlash(system);
    } else {
      operation = new HydrateFormationTemperatureFlash(system);
    }

    for (int i = 0; i < system.getPhase(4).getNumberOfComponents(); i++) {
      ((ComponentHydrate) system.getPhases()[4].getComponent(i)).setHydrateStructure(structure - 1);
    }
    if (!isRunAsThread()) {
      getOperation().run();
    } else {
      run();
    }

    if (Double.isNaN(system.getTemperature())) {
      throw new neqsim.util.exception.IsNaNException(this, "hydrateFormationTemperature",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * hydrateEquilibriumLine.
   * </p>
   *
   * @param minimumPressure a double
   * @param maximumPressure a double
   */
  public void hydrateEquilibriumLine(double minimumPressure, double maximumPressure) {
    operation = new HydrateEquilibriumLine(system, minimumPressure, maximumPressure);
    if (!isRunAsThread()) {
      getOperation().run();
    } else {
      run();
    }
  }

  /**
   * <p>
   * calcCricoP.
   * </p>
   *
   * @param cricondenBar an array of type double
   * @param cricondenBarX an array of type double
   * @param cricondenBarY an array of type double
   */
  public void calcCricoP(double[] cricondenBar, double[] cricondenBarX, double[] cricondenBarY) {
    double phasefraction = 1.0 - 1e-10;

    operation = new CricondenBarFlash(system, fileName, phasefraction, cricondenBar, cricondenBarX,
        cricondenBarY);

    getOperation().run();
  }

  /**
   * <p>
   * calcCricoT.
   * </p>
   *
   * @param cricondenTherm an array of type double
   * @param cricondenThermX an array of type double
   * @param cricondenThermY an array of type double
   */
  public void calcCricoT(double[] cricondenTherm, double[] cricondenThermX,
      double[] cricondenThermY) {
    double phasefraction = 1.0 - 1e-10;

    operation = new CricondenThermFlash(system, fileName, phasefraction, cricondenTherm,
        cricondenThermX, cricondenThermY);

    getOperation().run();
  }

  /**
   * <p>
   * waterDewPointLine.
   * </p>
   *
   * @param minimumPressure a double
   * @param maximumPressure a double
   */
  public void waterDewPointLine(double minimumPressure, double maximumPressure) {
    operation = new WaterDewPointEquilibriumLine(system, minimumPressure, maximumPressure);
    if (!isRunAsThread()) {
      getOperation().run();
    } else {
      run();
    }
  }

  /**
   * <p>
   * calcCricondenBar.
   * </p>
   *
   * @return a double
   */
  public double calcCricondenBar() {
    system.init(0);
    operation = new CricondebarFlash(system);
    // operation = new CricondenBarFlash(system);

    // operation = new cricondenBarTemp1(system);
    operation.run();
    return system.getPressure();
  }

  /**
   * <p>
   * bubblePointPressureFlash.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void bubblePointPressureFlash() throws IsNaNException {
    system.init(0);
    ConstantDutyFlashInterface operation = new ConstantDutyPressureFlash(system);
    system.setBeta(1, 1.0 - 1e-10);
    system.setBeta(0, 1e-10);
    operation.run();
    if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
      throw new neqsim.util.exception.IsNaNException(this, "bubblePointPressureFlash",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * bubblePointPressureFlash.
   * </p>
   *
   * @param derivatives a boolean
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void bubblePointPressureFlash(boolean derivatives) throws IsNaNException {
    ConstantDutyFlashInterface operation = null;
    if (derivatives) {
      operation = new BubblePointPressureFlashDer(system);
    } else {
      operation = new BubblePointPressureFlash(system);
    }
    try {
      operation.run();
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
    if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
      throw new neqsim.util.exception.IsNaNException(this, "bubblePointPressureFlash",
          "Could not find solution - possible no bubble point exists");
    }
  }

  /**
   * <p>
   * constantPhaseFractionPressureFlash.
   * </p>
   *
   * @param fraction a double
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void constantPhaseFractionPressureFlash(double fraction) throws IsNaNException {
    system.init(0);
    if (fraction < 1e-10) {
      fraction = 1e-10;
    }
    if (fraction > 1.0 - 1e-10) {
      fraction = 1.0 - 1.0e-10;
    }
    ConstantDutyFlashInterface operation = new ConstantDutyPressureFlash(system);
    system.setBeta(1, 1.0 - fraction);
    system.setBeta(0, fraction);
    operation.run();
    if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
      throw new neqsim.util.exception.IsNaNException(this, "constantPhaseFractionPressureFlash",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * constantPhaseFractionTemperatureFlash.
   * </p>
   *
   * @param fraction a double
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void constantPhaseFractionTemperatureFlash(double fraction) throws IsNaNException {
    system.init(0);
    if (fraction < 1e-10) {
      fraction = 1e-10;
    }
    if (fraction > 1.0 - 1e-10) {
      fraction = 1.0 - 1.0e-10;
    }
    ConstantDutyFlashInterface operation = new ConstantDutyTemperatureFlash(system);
    // ensure the phase fractions sum to unity
    system.setBeta(1, 1.0 - fraction);
    system.setBeta(0, fraction);
    operation.run();
    if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
      throw new neqsim.util.exception.IsNaNException(this, "constantPhaseFractionTemperatureFlash",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * dewPointMach.
   * </p>
   *
   * @param componentName a {@link java.lang.String} object
   * @param specification a {@link java.lang.String} object
   * @param spec a double
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void dewPointMach(String componentName, String specification, double spec)
      throws IsNaNException {
    // int componentNumber =
    // system.getPhase(0).getComponent(componentName).getComponentNumber();

    double dn = 0;
    if (system.getPhase(0).hasComponent(componentName)) {
      dn = system.getNumberOfMoles() / 1.0e6;
      system.addComponent(componentName, dn);
    } else {
      throw new neqsim.util.exception.IsNaNException(this, "dewPointMach",
          "Specified component is not present in mixture: " + componentName);
    }
    double newTemperature = system.getTemperature();
    double oldTemperature = newTemperature;
    int iterations = 0;
    if (specification.equals("dewPointTemperature")) {
      // logger.info("new temperature " + newTemperature);
      do {
        iterations++;
        system.init(0);
        dewPointTemperatureFlash();
        newTemperature = system.getTemperature();
        // logger.info("new temperature " + newTemperature);
        double oldMoles = system.getPhase(0).getComponent(componentName).getNumberOfmoles();
        if (iterations > 1) {
          system.addComponent(componentName, -(iterations / (30.0 + iterations))
              * (newTemperature - spec) / ((newTemperature - oldTemperature) / dn));
        } else {
          system.addComponent(componentName, system.getNumberOfMoles() / 1.0e6);
        }
        dn = system.getPhase(0).getComponent(componentName).getNumberOfmoles() - oldMoles;
        oldTemperature = newTemperature;
      } while (Math
          .abs(dn / system.getPhase(0).getComponent(componentName).getNumberOfmoles()) > 1e-9
          || iterations < 5 || iterations > 105);

      dewPointTemperatureFlash();
    }

    if (Double.isNaN(system.getPressure())) {
      throw new neqsim.util.exception.IsNaNException(this, "dewPointMach",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * dewPointTemperatureFlash.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void dewPointTemperatureFlash() throws IsNaNException {
    ConstantDutyFlashInterface operation =
        new neqsim.thermodynamicoperations.flashops.saturationops.DewPointTemperatureFlash(system);
    operation.run();
    if (Double.isNaN(system.getTemperature()) || operation.isSuperCritical()) {
      throw new neqsim.util.exception.IsNaNException(this, "dewPointTemperatureFlash",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * dewPointTemperatureFlash.
   * </p>
   *
   * @param derivatives a boolean
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void dewPointTemperatureFlash(boolean derivatives) throws IsNaNException {
    ConstantDutyFlashInterface operation =
        new neqsim.thermodynamicoperations.flashops.saturationops.DewPointTemperatureFlash(system);
    if (derivatives) {
      operation = new DewPointTemperatureFlashDer(system);
    }
    operation.run();
    if (Double.isNaN(system.getTemperature()) || operation.isSuperCritical()) {
      throw new neqsim.util.exception.IsNaNException(this, "dewPointTemperatureFlash",
          "Could not find solution - possible no dew point exists");
    }
  }

  /**
   * <p>
   * dewPointPressureFlashHC.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void dewPointPressureFlashHC() throws IsNaNException {
    // try{
    system.init(0);
    ConstantDutyFlashInterface operation = new HCdewPointPressureFlash(system);
    operation.run();
    if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
      throw new neqsim.util.exception.IsNaNException(this, "dewPointPressureFlashHC",
          "Could not find solution - possible no dew point exists");
    }
    // }
  }

  /**
   * <p>
   * dewPointPressureFlash.
   * </p>
   *
   * @throws neqsim.util.exception.IsNaNException if any.
   */
  public void dewPointPressureFlash() throws IsNaNException {
    // try{
    system.init(0);
    ConstantDutyFlashInterface operation = new DewPointPressureFlash(system);
    operation.run();
    if (Double.isNaN(system.getPressure()) || operation.isSuperCritical()) {
      throw new neqsim.util.exception.IsNaNException(this, "dewPointPressureFlash",
          "Could not find solution - possible no dew point exists");
    }
    // }
  }

  /**
   * <p>
   * getJfreeChart.
   * </p>
   *
   * @return a {@link org.jfree.chart.JFreeChart} object
   */
  public org.jfree.chart.JFreeChart getJfreeChart() {
    return getOperation().getJFreeChart("");
  }

  // public void dewPointPressureFlash(){
  // constantDutyFlashInterface operation = new constantDutyPressureFlash(system);
  // operation.setBeta((1-1e-7));
  // operation.run();
  // }
  /**
   * <p>
   * calcPTphaseEnvelope.
   * </p>
   */
  public void calcPTphaseEnvelope() {
    operation = new PTphaseEnvelope(system, fileName, (1.0 - 1e-10), 1.0, false);
    // thisThread = new Thread(operation);
    // thisThread.start();
    getOperation().run();
  }

  /**
   * <p>
   * calcPTphaseEnvelope.
   * </p>
   *
   * @param bubfirst a boolean
   * @param lowPres a double
   */
  public void calcPTphaseEnvelope(boolean bubfirst, double lowPres) {
    double phasefraction = 1.0 - 1e-10;
    if (bubfirst) {
      phasefraction = 1.0e-10;
    }
    operation = new PTphaseEnvelope(system, fileName, phasefraction, lowPres, bubfirst);

    // thisThread = new Thread(operation);
    // thisThread.start();
    getOperation().run();
  }

  /**
   * <p>
   * calcPTphaseEnvelope.
   * </p>
   *
   * @param lowPres a double
   */
  public void calcPTphaseEnvelope(double lowPres) {
    operation = new PTphaseEnvelope(system, fileName, 1e-10, lowPres, true);
    // thisThread = new Thread(operation);
    // thisThread.start();
    getOperation().run();
  }

  /**
   * <p>
   * calcPTphaseEnvelope.
   * </p>
   *
   * @param bubfirst a boolean
   */
  public void calcPTphaseEnvelope(boolean bubfirst) {
    double phasefraction = 1.0 - 1e-10;
    if (bubfirst) {
      phasefraction = 1.0e-10;
    }
    operation = new PTphaseEnvelope(system, fileName, phasefraction, 1.0, bubfirst);

    // thisThread = new Thread(operation);
    // thisThread.start();
    if (!isRunAsThread()) {
      getOperation().run();
    } else {
      run();
    }
  }

  /**
   * <p>
   * calcPTphaseEnvelope.
   * </p>
   *
   * @param lowPres a double
   * @param phasefraction a double
   */
  public void calcPTphaseEnvelope(double lowPres, double phasefraction) {
    operation = new PTphaseEnvelope(system, fileName, phasefraction, lowPres, true);

    // thisThread = new Thread(operation);
    // thisThread.start();
    getOperation().run();
  }

  /**
   * <p>
   * calcPTphaseEnvelopeNew.
   * </p>
   */
  public void calcPTphaseEnvelope2() {
    operation = new PTphaseEnvelopeNew2(system, fileName, (1.0 - 1e-10), 1.0, false);
    // thisThread = new Thread(operation);
    // thisThread.start();
    getOperation().run();
  }

  /**
   * <p>
   * calcPTphaseEnvelopeNew.
   * </p>
   */
  public void calcPTphaseEnvelopeNew() {
    // double phasefraction = 1.0 - 1e-10;
    // operation = new pTphaseEnvelope(system, fileName, phasefraction, 1.0);
    getOperation().run();
  }

  /**
   * Calculates a phase envelope matrix using PTphaseEnvelopeNew3.
   *
   * @param minPressure minimum pressure (bar)
   * @param maxPressure maximum pressure (bar)
   * @param minTemp minimum temperature (C)
   * @param maxTemp maximum temperature (C)
   * @param pressureStep step size for pressure (bar)
   * @param tempStep step size for temperature (C)
   */
  public void calcPTphaseEnvelopeNew3(double minPressure, double maxPressure, double minTemp,
      double maxTemp, double pressureStep, double tempStep) {
    operation = new PTphaseEnvelopeNew3(system, minPressure, maxPressure, minTemp, maxTemp,
        pressureStep, tempStep);
    operation.run();
  }

  /**
   * <p>
   * OLGApropTable.
   * </p>
   *
   * @param minTemp a double
   * @param maxTemp a double
   * @param temperatureSteps a int
   * @param minPres a double
   * @param maxPres a double
   * @param pressureSteps a int
   * @param filename a {@link java.lang.String} object
   * @param TABtype a int
   */
  public void OLGApropTable(double minTemp, double maxTemp, int temperatureSteps, double minPres,
      double maxPres, int pressureSteps, String filename, int TABtype) {
    if (TABtype == 0) {
      operation = new OLGApropertyTableGeneratorWaterKeywordFormat(system);

      ((OLGApropertyTableGeneratorWaterKeywordFormat) operation).setPressureRange(minPres, maxPres,
          pressureSteps);
      ((OLGApropertyTableGeneratorWaterKeywordFormat) operation).setTemperatureRange(minTemp,
          maxTemp, temperatureSteps);
      ((OLGApropertyTableGeneratorWaterKeywordFormat) operation).run();
      ((OLGApropertyTableGeneratorWaterKeywordFormat) operation).writeOLGAinpFile(filename);
    } else {
      operation = new OLGApropertyTableGeneratorWaterStudents(system);
      ((OLGApropertyTableGeneratorWaterStudents) operation).setFileName(filename);
      ((OLGApropertyTableGeneratorWaterStudents) operation).setPressureRange(minPres, maxPres,
          pressureSteps);
      ((OLGApropertyTableGeneratorWaterStudents) operation).setTemperatureRange(minTemp, maxTemp,
          temperatureSteps);
      getOperation().run();
      ((OLGApropertyTableGeneratorWaterStudents) operation).writeOLGAinpFile(filename);
    }
  }

  /**
   * <p>
   * OLGApropTablePH.
   * </p>
   *
   * @param minEnthalpy a double
   * @param maxEnthalpy a double
   * @param enthalpySteps a int
   * @param minPres a double
   * @param maxPres a double
   * @param pressureSteps a int
   * @param filename a {@link java.lang.String} object
   * @param TABtype a int
   */
  public void OLGApropTablePH(double minEnthalpy, double maxEnthalpy, int enthalpySteps,
      double minPres, double maxPres, int pressureSteps, String filename, int TABtype) {
    operation = new OLGApropertyTableGeneratorWaterStudentsPH(system);
    ((OLGApropertyTableGeneratorWaterStudentsPH) operation).setFileName(filename);
    ((OLGApropertyTableGeneratorWaterStudentsPH) operation).setPressureRange(minPres, maxPres,
        pressureSteps);
    ((OLGApropertyTableGeneratorWaterStudentsPH) operation).setEnthalpyRange(minEnthalpy,
        maxEnthalpy, enthalpySteps);
    getOperation().run();
  }

  /**
   * <p>
   * calcPloadingCurve.
   * </p>
   */
  public void calcPloadingCurve() {
    operation = new PloadingCurve2(system);
    // thisThread = new Thread(operation);
    // thisThread.start();
    getOperation().run();
  }

  /**
   * <p>
   * calcHPTphaseEnvelope.
   * </p>
   */
  public void calcHPTphaseEnvelope() {
    operation = new HPTphaseEnvelope(system);
    // thisThread = new Thread(getOperation());
    // thisThread.start();
    operation.run();
  }

  /**
   * <p>
   * printToFile.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void printToFile(String name) {
    getOperation().printToFile(name);
  }

  // public double[] get(String name){
  // return operation.get(name);
  // }
  /**
   * <p>
   * getData.
   * </p>
   *
   * @return an array of type double
   */
  public double[][] getData() {
    return getOperation().getPoints(0);
  }

  /**
   * <p>
   * getDataPoints.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getDataPoints() {
    String[][] str =
        new String[getOperation().getPoints(0).length][getOperation().getPoints(0)[0].length];
    for (int i = 0; i < getOperation().getPoints(0).length; i++) {
      for (int j = 0; j < getOperation().getPoints(0)[0].length; j++) {
        str[i][j] = Double.toString(getOperation().getPoints(0)[i][j]);
      }
    }
    return str;
  }

  /**
   * <p>
   * Getter for the field <code>resultTable</code>.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable() {
    return resultTable;
  }

  /**
   * <p>
   * dewPointTemperatureCondensationRate.
   * </p>
   *
   * @return a double
   */
  public double dewPointTemperatureCondensationRate() {
    double dT = 1.1;
    try {
      dewPointTemperatureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    system.setTemperature(system.getTemperature() - dT);
    TPflash();
    double condensationRate = system.getPhase(1).getMass() / (system.getVolume() * 1.0e-5);
    try {
      dewPointTemperatureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return condensationRate / dT;
  }

  /**
   * <p>
   * displayResult.
   * </p>
   */
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    try {
      getThermoOperationThread().join();
    } catch (Exception ex) {
      logger.error("Thread did not finish", ex);
    }
    getOperation().displayResult();
  }

  /**
   * <p>
   * Setter for the field <code>resultTable</code>.
   * </p>
   *
   * @param resultTable an array of {@link java.lang.String} objects
   */
  public void setResultTable(String[][] resultTable) {
    this.resultTable = resultTable;
  }

  /**
   * <p>
   * display.
   * </p>
   */
  @ExcludeFromJacocoGeneratedReport
  public void display() {
    if (resultTable == null) {
      return;
    }

    JFrame dialog = new JFrame("System-Report");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] names = new String[resultTable[0].length]; // {"", "", ""};
    for (int i = 0; i < names.length; i++) {
      names[i] = "";
    }
    JTable Jtab = new JTable(resultTable, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
  }

  /**
   * <p>
   * get.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return an array of type double
   */
  public double[] get(String name) {
    return getOperation().get(name);
  }

  /**
   * <p>
   * Getter for the field <code>operation</code>.
   * </p>
   *
   * @return the operation
   */
  public OperationInterface getOperation() {
    return operation;
  }

  /**
   * <p>
   * isRunAsThread.
   * </p>
   *
   * @return the runAsThread
   */
  public boolean isRunAsThread() {
    return runAsThread;
  }

  /**
   * <p>
   * Setter for the field <code>runAsThread</code>.
   * </p>
   *
   * @param runAsThread the runAsThread to set
   */
  public void setRunAsThread(boolean runAsThread) {
    this.runAsThread = runAsThread;
  }

  /**
   * <p>
   * Getter for the field <code>thermoOperationThread</code>.
   * </p>
   *
   * @return the thermoOperationThread
   */
  public Thread getThermoOperationThread() {
    return thermoOperationThread;
  }

  /**
   * <p>
   * Setter for the field <code>thermoOperationThread</code>.
   * </p>
   *
   * @param thermoOperationThread the thermoOperationThread to set
   */
  public void setThermoOperationThread(Thread thermoOperationThread) {
    this.thermoOperationThread = thermoOperationThread;
  }

  /**
   * <p>
   * addData.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param data an array of type double
   */
  public void addData(String name, double[][] data) {
    operation.addData(name, data);
  }

  /**
   * <p>
   * calcIonComposition.
   * </p>
   *
   * @param phaseNumber a int
   */
  public void calcIonComposition(int phaseNumber) {
    operation = new CalcIonicComposition(system, phaseNumber);
    getOperation().run();
    resultTable = getOperation().getResultTable();
  }

  /**
   * <p>
   * Wrapper for flash calculations.
   * </p>
   *
   * @param flashType Type of flash.
   * @param spec1 Value of spec1
   * @param spec2 Value of spec2
   * @param unitSpec1 Unit of spec1
   * @param unitSpec2 Unit of spec2
   */
  public void flash(FlashType flashType, double spec1, double spec2, String unitSpec1,
      String unitSpec2) {
    switch (flashType) {
      case PT:
        system.setPressure(spec1, unitSpec1);
        system.setTemperature(spec2, unitSpec2);
        TPflash();
        break;
      case TP:
        system.setTemperature(spec1, unitSpec1);
        system.setPressure(spec2, unitSpec2);
        TPflash();
        break;
      case TV:
        system.setTemperature(spec1, unitSpec1);
        TVflash(spec2, unitSpec2);
        break;
      case PH:
        system.setPressure(spec1, unitSpec1);
        PHflash(spec2, unitSpec2);
        break;
      case TS:
        system.setTemperature(spec1, unitSpec1);
        TSflash(spec2, unitSpec2);
        break;
      default:
        break;
    }
  }

  /**
   * Perform flashes and return System properties per set of Spec1 and Spec2. Possible to specify
   * fractions for each value of Spec1.
   *
   * @param Spec1 Flash pressure in bar absolute.
   * @param Spec2 Flash specification. Depends on FlashMode. Temperature in Kelvin, entalphy in
   *        J/mol or entropy in J/molK.
   * @param FlashMode 1 - PT 2 - PH 3 - PS
   * @param components Not yet in use.
   * @param onlineFractions Specify fractions per sample instance or null to use static composition
   *        specified in system.
   * @return Object CalculationResult object
   */
  public CalculationResult propertyFlash(List<Double> Spec1, List<Double> Spec2, int FlashMode,
      List<String> components, List<List<Double>> onlineFractions) {
    FlashType flashType;
    if (FlashMode == 1) {
      flashType = FlashType.PT;
    } else if (FlashMode == 2) {
      flashType = FlashType.PH;
    } else if (FlashMode == 3) {
      flashType = FlashType.PS;
    } else {
      flashType = null;
    }

    Double[][] fluidProperties = new Double[Spec1.size()][SystemProperties.nCols];
    String[] calculationError = new String[Spec1.size()];

    Double[] sum = new Double[Spec1.size()];
    String[] systemComponents = this.system.getComponentNames();
    if (components != null) {
      for (String inputCompName : components) {
        if (!this.system.hasComponent(inputCompName)) {
          for (int t = 0; t < Spec1.size(); t++) {
            calculationError[t] = "Input component list does not match fluid component list.";
          }
        }
      }
    } else {
      components = Arrays.asList(systemComponents);
    }

    // Verify that sum of fractions equals 1/100, i.e., assume percentages
    boolean hasOnlineFractions = onlineFractions != null;

    if (hasOnlineFractions) {
      double range = 5;
      for (int t = 0; t < sum.length; t++) {
        sum[t] = 0.0;
        for (int comp = 0; comp < onlineFractions.size(); comp++) {
          sum[t] = sum[t] + onlineFractions.get(comp).get(t).doubleValue();
        }
        if (!((sum[t] >= 1 - range / 100 && sum[t] <= 1 + range / 100)
            || (sum[t] >= 100 - range && sum[t] <= 100 + range))) {
          calculationError[t] = "Sum of fractions must be approximately 1 or 100, currently ("
              + String.valueOf(sum[t]) + ")";
        }
      }

      if (this.system.getTotalNumberOfMoles() == 0) {
        this.system.setTotalNumberOfMoles(1);
      }
    } else {
      /*
       * // New attempt:
       *
       * // Have to init here to get correct MoleFractionsSum() this.system.init(0);
       *
       * // Annoying that MoleFractionsSum is not normalized. sum[0] =
       * this.system.getMoleFractionsSum();
       */

      double[] fraction = this.system.getMolarComposition();
      sum[0] = 0.0;
      for (int comp = 0; comp < fraction.length; comp++) {
        sum[0] = sum[0] + fraction[comp];
      }

      double range = 1e-8;
      if (!((sum[0] >= 1 - range && sum[0] <= 1 + range)
          || (sum[0] >= 100 - range && sum[0] <= 100 + range))) {
        for (int t = 0; t < Spec1.size(); t++) {
          calculationError[t] = "Sum of fractions must be approximately to 1 or 100, currently ("
              + String.valueOf(sum[0]) + ")";
          if (sum[0] == 0.0) {
            calculationError[t] = calculationError[t] + ". Have you called init(0)?";
          }
        }
      }
    }

    for (int t = 0; t < Spec1.size(); t++) {
      try {
        if (flashType == null) {
          throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
              "ThermodynamicOperations", "propertyFlash", "FlashMode", "must be 1, 2 or 3"));
        }

        Double Sp1 = Spec1.get(t);
        Double Sp2 = Spec2.get(t);

        if (Sp1 == null || Sp2 == null || Double.isNaN(Sp1) || Double.isNaN(Sp2)) {
          calculationError[t] = "Sp1 or Sp2 is NaN";
          logger.info("Sp1 or Sp2 is NULL for datapoint {}", t);
          continue;
        }

        // Skip if sum is not similar to 100%
        if (calculationError[t] != null) {
          logger.info("{}", calculationError[t]);
          continue;
        }

        if (hasOnlineFractions) {
          // Ensure that fraction is inserted for the correct component (in case of
          // mismatch of component input and fluid component list)
          double[] fraction = new double[this.system.getNumberOfComponents()];
          // For all components defined in system
          for (int compIndex = 0; compIndex < fraction.length; compIndex++) {
            // Loop all input component names / fractions
            for (int index = 0; index < components.size(); index++) {
              if (systemComponents[compIndex] == ComponentInterface
                  .getComponentNameFromAlias(components.get(index))) {
                fraction[compIndex] = onlineFractions.get(index).get(t).doubleValue();
                break;
              }
            }
          }

          this.system.setMolarComposition(fraction);
          this.system.init(0);
        }

        this.system.setPressure(Sp1);
        switch (flashType) {
          case PT:
            this.system.setTemperature(Sp2);
            this.TPflash();
            break;
          case PH:
            this.PHflash(Sp2, "J/mol");
            break;
          case PS:
            this.PSflash(Sp2, "J/molK");
            break;
          default:
            throw new RuntimeException(new neqsim.util.exception.InvalidInputException(
                "ThermodynamicOperations", "propertyFlash", "FlashMode", "must be 1, 2 or 3"));
        }
        this.system.init(2);
        this.system.initPhysicalProperties();

        fluidProperties[t] = this.system.getProperties().getValues();
      } catch (Exception ex) {
        calculationError[t] = ex.getMessage();
        logger.warn(calculationError[t]);
      }
    }

    return new CalculationResult(fluidProperties, calculationError);
  }

  /**
   * Definitions of flash types.
   */
  public static enum FlashType {
    TP, PT, PH, PS, TV, TS
  }
}
