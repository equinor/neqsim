package neqsim.process.equipment.util;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Reconciles a stream to measured production data in a single unit: it matches a target gas-oil ratio (GOR), a target
 * produced <b>gas-phase</b> standard volumetric rate, and a target produced water rate.
 *
 * <p>
 * This complements {@link GORfitter}, which only matches the GOR and preserves total mass (so the produced gas rate
 * drifts from a measured target). {@code ProductionRateFitter} performs, in order: (1) an optional GOR fit at standard
 * conditions, (2) scaling of the total hydrocarbon flow so the <b>gas-phase</b> standard volume rate equals the
 * measured gas rate, and (3) setting the aqueous flow to the measured produced-water rate. The result is a stream
 * consistent with measured gas rate, GOR, and water rate for wells-to-facility production models.
 * </p>
 *
 * @author neqsim
 * @version $Id$
 */
public class ProductionRateFitter extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ProductionRateFitter.class);

  /** Molar mass of water [kg/mol]. */
  private static final double WATER_MOLAR_MASS = 0.0180153;
  /** Produced-water density used to convert a volumetric water rate to mass [kg/Sm3]. */
  private double waterDensity = 1000.0;

  private String referenceConditions = "standard";
  private double GOR = -1.0;
  private boolean fitGas = false;
  private double gasRate = 0.0;
  private String gasRateUnit = "MSm3/day";
  private boolean fitWater = false;
  private double waterRate = 0.0;
  private String waterRateUnit = "Sm3/day";

  /**
   * Constructor for ProductionRateFitter.
   *
   * @param name name of the unit
   * @param stream inlet stream
   */
  public ProductionRateFitter(String name, StreamInterface stream) {
    super(name, stream);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    this.inStream = inletStream;
    try {
      this.outStream = inletStream.clone(this.getName() + " out stream");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * Set the target gas-oil ratio (gas/oil standard volume) to match.
   *
   * @param gor target GOR [Sm3/Sm3]; a non-positive value disables GOR fitting
   */
  public void setGOR(double gor) {
    this.GOR = gor;
  }

  /**
   * Getter for the target GOR.
   *
   * @return the target GOR [Sm3/Sm3]
   */
  public double getGOR() {
    return GOR;
  }

  /**
   * Set the target produced gas-phase standard volumetric rate to match.
   *
   * @param rate target gas rate; a non-positive value disables gas-rate fitting
   * @param unit rate unit, for example {@code "MSm3/day"}, {@code "Sm3/day"}, {@code "Sm3/hr"}, or {@code "Sm3/sec"}
   */
  public void setGasRate(double rate, String unit) {
    this.gasRate = rate;
    this.gasRateUnit = unit;
    this.fitGas = rate > 0.0;
  }

  /**
   * Set the target produced-water volumetric rate to match.
   *
   * @param rate target water rate; a negative value disables water fitting
   * @param unit rate unit, for example {@code "Sm3/day"}, {@code "Sm3/hr"}, or {@code "Sm3/sec"}
   */
  public void setWaterRate(double rate, String unit) {
    this.waterRate = rate;
    this.waterRateUnit = unit;
    this.fitWater = rate >= 0.0;
  }

  /**
   * Setter for the produced-water density used in the volumetric-to-mass conversion.
   *
   * @param density produced-water density [kg/Sm3]
   */
  public void setWaterDensity(double density) {
    this.waterDensity = density;
  }

  /**
   * Getter for the reference conditions.
   *
   * @return the reference conditions ({@code "standard"} or {@code "actual"})
   */
  public String getReferenceConditions() {
    return referenceConditions;
  }

  /**
   * Setter for the reference conditions.
   *
   * @param referenceConditions {@code "standard"} (default) or {@code "actual"}
   */
  public void setReferenceConditions(String referenceConditions) {
    this.referenceConditions = referenceConditions;
  }

  /**
   * Convert a volumetric rate expressed at standard conditions to Sm3/sec.
   *
   * @param rate the rate value
   * @param unit the rate unit
   * @return the rate in Sm3/sec
   */
  private double toSm3PerSec(double rate, String unit) {
    if (unit == null) {
      return rate;
    }
    switch (unit) {
    case "MSm3/day":
      return rate * 1.0e6 / (24.0 * 3600.0);
    case "Sm3/day":
      return rate / (24.0 * 3600.0);
    case "Sm3/hr":
      return rate / 3600.0;
    case "Sm3/sec":
      return rate;
    default:
      logger.warn("Unknown rate unit {}; assuming Sm3/sec", unit);
      return rate;
    }
  }

  /** Standard-condition moles per Sm3 of gas (ideal gas). */
  private double molesPerSm3() {
    return ThermodynamicConstantsInterface.atm
        / (ThermodynamicConstantsInterface.R * ThermodynamicConstantsInterface.standardStateTemperature);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface tempFluid = inStream.getThermoSystem().clone();
    double inletP = inStream.getThermoSystem().getPressure();
    double inletT = inStream.getThermoSystem().getTemperature();

    if (tempFluid.getFlowRate("kg/sec") < 1e-9) {
      outStream.setThermoSystem(tempFluid);
      outStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      return;
    }

    // 1. Optional GOR fit at standard conditions.
    if (GOR > 1e-15) {
      SystemInterface f = tempFluid.clone();
      double massFlow = f.getFlowRate("kg/sec");
      if (!getReferenceConditions().equals("actual")) {
        f.setTemperature(15.0, "C");
        f.setPressure(ThermodynamicConstantsInterface.referencePressure, "bara");
      }
      ThermodynamicOperations ops = new ThermodynamicOperations(f);
      try {
        ops.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      if (f.hasPhaseType("gas") && f.hasPhaseType("oil")) {
        f.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
        double currGOR = f.getPhase("gas").getCorrectedVolume() / f.getPhase("oil").getCorrectedVolume();
        double dev = GOR / currGOR;
        double[] moleChange = new double[f.getNumberOfComponents()];
        for (int i = 0; i < f.getNumberOfComponents(); i++) {
          moleChange[i] = (dev - 1.0) * f.getPhase("gas").getComponent(i).getNumberOfMolesInPhase();
        }
        f.init(0);
        for (int i = 0; i < f.getNumberOfComponents(); i++) {
          f.addComponent(i, moleChange[i]);
        }
        f.setPressure(inletP);
        f.setTemperature(inletT);
        f.setTotalFlowRate(massFlow, "kg/sec");
        ThermodynamicOperations ops2 = new ThermodynamicOperations(f);
        try {
          ops2.TPflash();
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }
        tempFluid = f;
      }
    }

    // 2. Match the produced gas-phase standard volumetric rate.
    if (fitGas) {
      double targetGasSm3PerSec = toSm3PerSec(gasRate, gasRateUnit);
      SystemInterface s = tempFluid.clone();
      s.setTemperature(15.0, "C");
      s.setPressure(ThermodynamicConstantsInterface.referencePressure, "bara");
      ThermodynamicOperations ops = new ThermodynamicOperations(s);
      try {
        ops.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      double gasMoles = s.hasPhaseType("gas") ? s.getPhase("gas").getNumberOfMolesInPhase() : 0.0;
      double gasSm3PerSec = gasMoles / molesPerSm3();
      if (gasSm3PerSec > 1e-12) {
        double scale = targetGasSm3PerSec / gasSm3PerSec;
        double massFlow = tempFluid.getFlowRate("kg/sec");
        tempFluid.setTotalFlowRate(massFlow * scale, "kg/sec");
      } else {
        logger.warn("No gas phase at standard conditions; gas-rate fit skipped.");
      }
    }

    // 3. Match the produced-water rate.
    if (fitWater) {
      double targetWaterSm3PerSec = toSm3PerSec(waterRate, waterRateUnit);
      double targetWaterMolPerSec = targetWaterSm3PerSec * waterDensity / WATER_MOLAR_MASS;
      double currWaterMol = 0.0;
      if (tempFluid.getPhase(0).hasComponent("water")) {
        currWaterMol = tempFluid.getPhase(0).getComponent("water").getNumberOfmoles();
      }
      double delta = targetWaterMolPerSec - currWaterMol;
      tempFluid.addComponent("water", delta);
    }

    tempFluid.setPressure(inletP);
    tempFluid.setTemperature(inletT);
    ThermodynamicOperations finalOps = new ThermodynamicOperations(tempFluid);
    try {
      finalOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    tempFluid.initProperties();
    outStream.setThermoSystem(tempFluid);
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }
}
