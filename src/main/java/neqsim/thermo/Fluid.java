package neqsim.thermo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Fluid class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Fluid {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Fluid.class);

  neqsim.thermo.system.SystemInterface fluid = null;
  private boolean hasWater = false;
  private boolean autoSelectModel = false;
  private String thermoModel = "srk";
  private String thermoMixingRule = "classic";

  /**
   * <p>
   * Constructor for Fluid.
   * </p>
   */
  public Fluid() {}

  /**
   * <p>
   * Getter for the field <code>fluid</code>.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public neqsim.thermo.system.SystemInterface getFluid() {
    return fluid;
  }

  /**
   * <p>
   * create2.
   * </p>
   *
   * @param componentNames an array of {@link java.lang.String} objects
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public neqsim.thermo.system.SystemInterface create2(String[] componentNames) {
    double[] comp = new double[componentNames.length];
    for (int i = 0; i < componentNames.length; i++) {
      comp[i] = 1.0;
    }
    return create2(componentNames, comp, "mol/sec");
  }

  /**
   * <p>
   * create2.
   * </p>
   *
   * @param componentNames an array of {@link java.lang.String} objects
   * @param flowrate an array of type double
   * @param unit a {@link java.lang.String} object
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public neqsim.thermo.system.SystemInterface create2(String[] componentNames, double[] flowrate,
      String unit) {
    setThermoModel();
    createFluid(componentNames, flowrate, unit);
    if (isHasWater()) {
      fluid.addComponent("water", 0.1);
    }
    fluid.createDatabase(true);
    fluid.setMixingRule(fluid.getMixingRule());
    if (isHasWater()) {
      fluid.setMultiPhaseCheck(true);
    }
    if (isAutoSelectModel()) {
      fluid = fluid.autoSelectModel();
    }
    fluid.init(0);
    return fluid;
  }

  /**
   * <p>
   * create.
   * </p>
   *
   * @param fluidType a {@link java.lang.String} object
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public neqsim.thermo.system.SystemInterface create(String fluidType) {
    String[] compNames = null;
    double[] flowrate = null;
    this.setThermoModel();
    if (fluidType.equals("water")) {
      compNames = new String[] {"water"};
      flowrate = new double[] {1.0};
      createFluid(compNames, flowrate, "mole/sec");
    }
    if (fluidType.equals("dry gas")) {
      compNames =
          new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane"};
      flowrate = new double[] {0.01, 0.02, 0.82, 0.11, 0.05, 0.01, 0.012};
      createFluid(compNames, flowrate, "mole/sec");
    } else if (fluidType.equals("rich gas")) {
      compNames =
          new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane",
              "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10"};
      flowrate = new double[] {0.01, 0.02, 0.82, 0.11, 0.05, 0.01, 0.012, 0.01, 0.01, 0.01, 0.01,
          0.01, 0.01, 0.01};
      createFluid(compNames, flowrate, "mole/sec");
    } else if (fluidType.equals("air")) {
      compNames = new String[] {"nitrogen", "oxygen"};
      flowrate = new double[] {0.78, 0.22};
      createFluid(compNames, flowrate, "mole/sec");
    } else if (fluidType.equals("combustion air")) {
      compNames = new String[] {"nitrogen", "oxygen", "CO2", "water"};
      flowrate = new double[] {0.78084, 0.20946, 0.033, 0.1};
      createFluid(compNames, flowrate, "mole/sec");
    } else if (fluidType.equals("gas condensate")) {
      compNames =
          new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane",
              "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10"};
      flowrate = new double[] {0.01, 0.02, 0.32, 0.05, 0.03, 0.01, 0.012, 0.01, 0.01, 0.01, 0.01,
          0.01, 0.01, 0.01};
      createFluid(compNames, flowrate, "mole/sec");
      String[] charNames = new String[] {"C10-C15", "C16-C19", "C20-C30", "C31-C50"};
      double[] charFlowrate = new double[] {0.1, 0.08, 0.05, 0.01};
      double[] molarMass = new double[] {0.20, 0.3, 0.36, 0.4};
      double[] density = new double[] {700.0e-3, 810.0e-3, 880.0e-3, 920.0e-3};
      getFluid().addCharacterized(charNames, charFlowrate, molarMass, density);
    } else if (fluidType.equals("petrol")) {
      compNames = new String[] {"n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane",
          "nC10", "nC11", "nC12"};
      flowrate = new double[] {0.1, 0.1, 0.1, 0.1, 0.3, 0.1, 0.1, 0.1};
      createFluid(compNames, flowrate, "mole/sec");
    } else if (fluidType.equals("diesel")) {
      compNames = new String[] {"n-heptane", "n-octane", "n-nonane", "nC10", "nC11", "nC12", "nC13",
          "nC14"};
      flowrate = new double[] {0.1, 0.1, 0.1, 0.3, 0.1, 0.1, 0.1, 0.1};
      createFluid(compNames, flowrate, "mole/sec");
    } else if (fluidType.equals("light oil")) {
      compNames =
          new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane",
              "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10"};
      flowrate = new double[] {0.01, 0.02, 0.52, 0.11, 0.05, 0.01, 0.012, 0.01, 0.01, 0.01, 0.01,
          0.01, 0.01, 0.01};
      createFluid(compNames, flowrate, "mole/sec");
      String[] charNames = new String[] {"C10-C15", "C16-C19", "C20-C30", "C31-C50"};
      double[] charFlowrate = new double[] {0.2, 0.1, 0.05, 0.01};
      double[] molarMass = new double[] {0.20, 0.3, 0.36, 0.4};
      double[] density = new double[] {700.0e-3, 810.0e-3, 880.0e-3, 920.0e-3};
      getFluid().addCharacterized(charNames, charFlowrate, molarMass, density);
    } else if (fluidType.equals("black oil")) {
      compNames = new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
          "n-butane", "i-pentane", "n-pentane", "n-hexane"};
      flowrate = new double[] {0.01, 0.02, 0.22, 0.11, 0.05, 0.01, 0.012, 0.01, 0.01, 0.01};
      createFluid(compNames, flowrate, "mole/sec");
      String[] charNames = new String[] {"C10-C15", "C16-C19", "C20-C30", "C31-C50", "C51-C80"};
      double[] charFlowrate = new double[] {0.2, 0.1, 0.1, 0.05, 0.01};
      double[] molarMass = new double[] {0.20, 0.25, 0.3, 0.36, 0.4};
      double[] density = new double[] {700.0e-3, 750.0e-3, 810.0e-3, 880.0e-3, 920.0e-3};
      getFluid().addCharacterized(charNames, charFlowrate, molarMass, density);
    } else if (fluidType.equals("black oil with water")) {
      compNames = new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
          "n-butane", "i-pentane", "n-pentane", "n-hexane"};
      flowrate = new double[] {0.01, 0.02, 0.22, 0.11, 0.05, 0.01, 0.012, 0.01, 0.01, 0.01};
      createFluid(compNames, flowrate, "mole/sec");
      String[] charNames = new String[] {"C10-C15", "C16-C19", "C20-C30", "C31-C50", "C51-C80"};
      double[] charFlowrate = new double[] {0.2, 0.1, 0.1, 0.05, 0.01};
      double[] molarMass = new double[] {0.20, 0.25, 0.3, 0.36, 0.4};
      double[] density = new double[] {700.0e-3, 750.0e-3, 810.0e-3, 880.0e-3, 920.0e-3};
      getFluid().addCharacterized(charNames, charFlowrate, molarMass, density);
      setHasWater(true);
    } else if (fluidType.equals("heavy oil")) {
      compNames = new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
          "n-butane", "i-pentane", "n-pentane", "n-hexane"};
      flowrate = new double[] {0.01, 0.01, 0.12, 0.11, 0.05, 0.01, 0.012, 0.01, 0.01, 0.01};
      createFluid(compNames, flowrate, "mole/sec");
      String[] charNames = new String[] {"C10-C15", "C16-C19", "C20-C30", "C31-C50", "C51-C80"};
      double[] charFlowrate = new double[] {0.2, 0.2, 0.2, 0.1, 0.1};
      double[] molarMass = new double[] {0.20, 0.25, 0.3, 0.36, 0.4};
      double[] density = new double[] {700.0e-3, 750.0e-3, 810.0e-3, 880.0e-3, 920.0e-3};
      getFluid().addCharacterized(charNames, charFlowrate, molarMass, density);
    } else if (neqsim.util.database.NeqSimDataBase.hasComponent(fluidType)) {
      compNames = new String[] {fluidType};
      flowrate = new double[] {1.0};
      createFluid(compNames, flowrate, "mole/sec");
    } else {
      return null;
    }

    if (isHasWater()) {
      fluid.addComponent("water", 0.1);
    }
    fluid.createDatabase(true);
    fluid.setMixingRule(fluid.getMixingRule());
    if (isHasWater()) {
      fluid.setMultiPhaseCheck(true);
    }
    if (isAutoSelectModel()) {
      fluid = fluid.autoSelectModel();
    }
    fluid.init(0);
    return fluid;
  }

  /**
   * <p>
   * createFluid.
   * </p>
   *
   * @param componentNames an array of {@link java.lang.String} objects
   * @param flowrate an array of type double
   * @param unit a {@link java.lang.String} object
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public neqsim.thermo.system.SystemInterface createFluid(String[] componentNames,
      double[] flowrate, String unit) {
    setThermoModel();
    if (componentNames.length != flowrate.length) {
      logger.error("component names and mole fractions need to be same length...");
    }

    for (int i = 0; i < componentNames.length; i++) {
      fluid.addComponent(componentNames[i], flowrate[i], unit);
    }

    return fluid;
  }

  /**
   * <p>
   * addComponment.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void addComponment(String name) {
    fluid.addComponent(name, 1.0);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    if (isHasWater()) {
      fluid.setMultiPhaseCheck(true);
    }
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    neqsim.thermo.Fluid fluidCreator = new neqsim.thermo.Fluid();

    neqsim.thermo.system.SystemInterface fluid = fluidCreator.create("petrol");
    fluid.display();

    neqsim.thermo.system.SystemInterface fluid2 = fluidCreator.create("dry gas");
    fluid2.display();
    fluid2.getNumberOfComponents();

    neqsim.thermo.system.SystemInterface fluid3 = fluidCreator.create("black oil with water");
    fluid3.display();
    fluid3.getNumberOfComponents();
  }

  /**
   * <p>
   * isHasWater.
   * </p>
   *
   * @return a boolean
   */
  public boolean isHasWater() {
    return hasWater;
  }

  /**
   * <p>
   * Setter for the field <code>hasWater</code>.
   * </p>
   *
   * @param hasWater a boolean
   */
  public void setHasWater(boolean hasWater) {
    this.hasWater = hasWater;
  }

  /**
   * <p>
   * isAutoSelectModel.
   * </p>
   *
   * @return a boolean
   */
  public boolean isAutoSelectModel() {
    return autoSelectModel;
  }

  /**
   * <p>
   * Setter for the field <code>autoSelectModel</code>.
   * </p>
   *
   * @param autoSelectModel a boolean
   */
  public void setAutoSelectModel(boolean autoSelectModel) {
    this.autoSelectModel = autoSelectModel;
  }

  /**
   * <p>
   * Getter for the field <code>thermoModel</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getThermoModel() {
    return thermoModel;
  }

  /**
   * Init fluid object?.
   */
  private void setThermoModel() {
    if (thermoModel.equals("srk")) {
      fluid = new neqsim.thermo.system.SystemSrkEos();
    } else if (thermoModel.equals("pr")) {
      fluid = new neqsim.thermo.system.SystemPrEos();
    } else if (thermoModel.equals("cpa")) {
      fluid = new neqsim.thermo.system.SystemSrkCPAstatoil();
    } else {
      fluid = new neqsim.thermo.system.SystemSrkEos();
    }
  }

  /**
   * <p>
   * Setter for the field <code>thermoModel</code>.
   * </p>
   *
   * @param thermoModel a {@link java.lang.String} object
   */
  public void setThermoModel(String thermoModel) {
    this.thermoModel = thermoModel;
  }

  /**
   * <p>
   * Getter for the field <code>thermoMixingRule</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getThermoMixingRule() {
    return thermoMixingRule;
  }

  /**
   * <p>
   * Setter for the field <code>thermoMixingRule</code>.
   * </p>
   *
   * @param thermoMixingRule a {@link java.lang.String} object
   */
  public void setThermoMixingRule(String thermoMixingRule) {
    this.thermoMixingRule = thermoMixingRule;
  }
}
