package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.pvtsimulation.flowassurance.SurfCooldownAnalyzer;
import neqsim.thermo.system.SystemInterface;

/**
 * Couples the pipeline pressure design to the thermal design.
 *
 * <p>
 * These two are normally done by different people and are usually treated as independent. They are not. The steel wall
 * is part of the cooldown thermal mass, so a decision that thins the wall - typically installing a high-integrity
 * pressure protection system so the line need not be rated for full shut-in - removes stored heat and lengthens the
 * insulation required to hold the same no-touch time.
 * </p>
 *
 * <p>
 * A real case: rating a tie-back for full shut-in required a 22.2 mm wall, and 75 mm of insulation gave more than the 8
 * h no-touch target. Protecting it instead and rating it for the flowing pressure dropped the wall to 14.3 mm, and the
 * same 75 mm of insulation then gave only 7.9 h. The saving in steel was real, but so was the extra insulation, and
 * neither analysis on its own would have shown it.
 * </p>
 *
 * <p>
 * This class sweeps insulation thickness for each candidate wall thickness and reports the insulation each one needs to
 * meet the no-touch requirement, so the trade is visible in one table.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TiebackThermalDesign implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Class logger. */
  private static final Logger logger = LogManager.getLogger(TiebackThermalDesign.class);

  /** The produced fluid; must carry water or the hydrate assessment is meaningless. */
  private final SystemInterface fluid;

  /** Internal diameter in metres. */
  private double internalDiameterM = 0.2032;

  /** Candidate steel wall thicknesses in metres. */
  private double[] wallThicknessesM = { 0.0127, 0.0143, 0.0159, 0.0175, 0.0191, 0.0222 };

  /** Candidate insulation thicknesses in metres. */
  private double[] insulationThicknessesM = { 0.025, 0.050, 0.075, 0.100, 0.125, 0.150 };

  /** Insulation thermal conductivity in W/mK. */
  private double insulationConductivity = 0.22;

  /** External film coefficient in W/m2K. */
  private double externalHtc = 500.0;

  /** Seabed temperature in degrees Celsius. */
  private double seabedTemperatureC = 4.0;

  /** Operating temperature at the point being assessed, in degrees Celsius. */
  private double operatingTemperatureC = 35.0;

  /** Required no-touch time in hours. */
  private double requiredNoTouchTimeHours = 8.0;

  /** Results, one row per wall thickness. */
  private final List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

  /** Hydrate equilibrium temperature in degrees Celsius, from the fluid. */
  private double hydrateEquilibriumC = Double.NaN;

  /** True when the fluid carries water. */
  private boolean waterPresent = false;

  /** When true, a fluid without water fails the study rather than passing silently. */
  private boolean requireWater = true;

  /**
   * Controls whether a fluid without water fails the study.
   *
   * <p>
   * Default is true. A cooldown sweep on a dry fluid returns an unbounded no-touch time for every wall and insulation
   * combination, which in a design context is almost always the wrong fluid file rather than a deliberately dry gas.
   * </p>
   *
   * @param requireWaterIn false to allow a dry fluid through with a warning
   * @return this design, for chaining
   */
  public TiebackThermalDesign setRequireWater(boolean requireWaterIn) {
    this.requireWater = requireWaterIn;
    return this;
  }

  /**
   * Creates a thermal design study for a fluid.
   *
   * @param fluidIn the produced fluid, which must carry a water component
   */
  public TiebackThermalDesign(SystemInterface fluidIn) {
    this.fluid = fluidIn;
  }

  /**
   * Runs the wall-versus-insulation sweep.
   *
   * @return the result rows, one per candidate wall thickness
   */
  public List<Map<String, Object>> calculate() {
    results.clear();
    for (int w = 0; w < wallThicknessesM.length; w++) {
      double wall = wallThicknessesM[w];
      double requiredInsulation = Double.NaN;
      double noTouchAtRequired = Double.NaN;
      List<Map<String, Object>> sweep = new ArrayList<Map<String, Object>>();

      for (int k = 0; k < insulationThicknessesM.length; k++) {
        double insulation = insulationThicknessesM[k];
        SurfCooldownAnalyzer analyzer = new SurfCooldownAnalyzer(fluid);
        // A design study must not accept an unbounded no-touch time from a fluid with no water.
        analyzer.setRequireWater(requireWater);
        analyzer.setInternalDiameter(internalDiameterM);
        analyzer.setWallThickness(wall);
        analyzer.setInsulationThickness(insulation);
        analyzer.setInsulationConductivity(insulationConductivity);
        analyzer.setExternalHTC(externalHtc);
        analyzer.setSeabedTemperature(seabedTemperatureC);
        analyzer.setOperatingTemperature(operatingTemperatureC);
        analyzer.setRequiredNoTouchTimeHours(requiredNoTouchTimeHours);
        analyzer.setTotalTimeHours(Math.max(48.0, 4.0 * requiredNoTouchTimeHours));
        analyzer.calculate();

        waterPresent = analyzer.isWaterPresent();
        hydrateEquilibriumC = analyzer.getHydrateEquilibriumTemperatureK() - 273.15;
        double noTouch = analyzer.getNoTouchTimeHours();

        Map<String, Object> point = new LinkedHashMap<String, Object>();
        point.put("insulationThickness_mm", Double.valueOf(insulation * 1000.0));
        point.put("noTouchTime_hours", Double.valueOf(noTouch));
        point.put("timeConstant_hours", Double.valueOf(analyzer.getTimeConstantHours()));
        point.put("verdict", analyzer.getVerdict());
        sweep.add(point);

        if (Double.isNaN(requiredInsulation) && noTouch >= requiredNoTouchTimeHours) {
          requiredInsulation = insulation;
          noTouchAtRequired = noTouch;
        }
      }

      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("wallThickness_mm", Double.valueOf(wall * 1000.0));
      row.put("requiredInsulation_mm",
          Double.isNaN(requiredInsulation) ? null : Double.valueOf(requiredInsulation * 1000.0));
      row.put("noTouchTimeAtRequiredInsulation_hours",
          Double.isNaN(noTouchAtRequired) ? null : Double.valueOf(noTouchAtRequired));
      row.put("requirementMet", Boolean.valueOf(!Double.isNaN(requiredInsulation)));
      row.put("sweep", sweep);
      results.add(row);
    }

    if (!waterPresent) {
      logger.warn("TiebackThermalDesign: the fluid carries no water, so every no-touch time in "
          + "this study is meaningless; add water before using these numbers");
    }
    logInsulationPenalty();
    return results;
  }

  /**
   * Logs the insulation penalty implied by choosing the thinnest wall.
   */
  private void logInsulationPenalty() {
    if (results.size() < 2) {
      return;
    }
    Object thinnest = results.get(0).get("requiredInsulation_mm");
    Object thickest = results.get(results.size() - 1).get("requiredInsulation_mm");
    if (thinnest instanceof Double && thickest instanceof Double) {
      double penalty = ((Double) thinnest).doubleValue() - ((Double) thickest).doubleValue();
      if (penalty > 0.0) {
        logger.info(
            "TiebackThermalDesign: thinning the wall from {} to {} mm costs {} mm of extra "
                + "insulation to hold the {} h no-touch requirement",
            results.get(results.size() - 1).get("wallThickness_mm"), results.get(0).get("wallThickness_mm"),
            Double.valueOf(penalty), Double.valueOf(requiredNoTouchTimeHours));
      }
    }
  }

  /**
   * Returns the insulation required for a given wall thickness.
   *
   * @param wallThicknessMm wall thickness in millimetres
   * @return required insulation in millimetres, or NaN when no candidate met the requirement or the wall thickness was
   * not in the sweep
   */
  public double getRequiredInsulationForWall(double wallThicknessMm) {
    for (int i = 0; i < results.size(); i++) {
      Double wall = (Double) results.get(i).get("wallThickness_mm");
      if (Math.abs(wall.doubleValue() - wallThicknessMm) < 1.0e-6) {
        Object insulation = results.get(i).get("requiredInsulation_mm");
        return insulation == null ? Double.NaN : ((Double) insulation).doubleValue();
      }
    }
    return Double.NaN;
  }

  /**
   * Returns the study as JSON.
   *
   * @return JSON string with the basis and every wall/insulation combination
   */
  public String toJson() {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    Map<String, Object> basis = new LinkedHashMap<String, Object>();
    basis.put("internalDiameter_m", Double.valueOf(internalDiameterM));
    basis.put("insulationConductivity_W_mK", Double.valueOf(insulationConductivity));
    basis.put("seabedTemperature_C", Double.valueOf(seabedTemperatureC));
    basis.put("operatingTemperature_C", Double.valueOf(operatingTemperatureC));
    basis.put("requiredNoTouchTime_hours", Double.valueOf(requiredNoTouchTimeHours));
    basis.put("hydrateEquilibrium_C", Double.isNaN(hydrateEquilibriumC) ? null : Double.valueOf(hydrateEquilibriumC));
    basis.put("waterPresentInFluid", Boolean.valueOf(waterPresent));
    out.put("basis", basis);
    out.put("wallThicknessCases", results);
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create();
    return gson.toJson(out);
  }

  /**
   * Sets the internal diameter.
   *
   * @param diameterM internal diameter in metres
   * @return this design, for chaining
   */
  public TiebackThermalDesign setInternalDiameter(double diameterM) {
    this.internalDiameterM = diameterM;
    return this;
  }

  /**
   * Sets the candidate wall thicknesses.
   *
   * @param thicknessesM wall thicknesses in metres
   * @return this design, for chaining
   */
  public TiebackThermalDesign setWallThicknesses(double[] thicknessesM) {
    this.wallThicknessesM = thicknessesM.clone();
    return this;
  }

  /**
   * Sets the candidate insulation thicknesses.
   *
   * @param thicknessesM insulation thicknesses in metres, increasing
   * @return this design, for chaining
   */
  public TiebackThermalDesign setInsulationThicknesses(double[] thicknessesM) {
    this.insulationThicknessesM = thicknessesM.clone();
    return this;
  }

  /**
   * Sets the insulation thermal conductivity.
   *
   * @param conductivity conductivity in W/mK
   * @return this design, for chaining
   */
  public TiebackThermalDesign setInsulationConductivity(double conductivity) {
    this.insulationConductivity = conductivity;
    return this;
  }

  /**
   * Sets the external film coefficient.
   *
   * @param htc external heat transfer coefficient in W/m2K
   * @return this design, for chaining
   */
  public TiebackThermalDesign setExternalHTC(double htc) {
    this.externalHtc = htc;
    return this;
  }

  /**
   * Sets the seabed temperature.
   *
   * @param temperatureC seabed temperature in degrees Celsius
   * @return this design, for chaining
   */
  public TiebackThermalDesign setSeabedTemperature(double temperatureC) {
    this.seabedTemperatureC = temperatureC;
    return this;
  }

  /**
   * Sets the operating temperature at the point being assessed.
   *
   * @param temperatureC operating temperature in degrees Celsius
   * @return this design, for chaining
   */
  public TiebackThermalDesign setOperatingTemperature(double temperatureC) {
    this.operatingTemperatureC = temperatureC;
    return this;
  }

  /**
   * Sets the no-touch time requirement.
   *
   * @param hours required no-touch time in hours
   * @return this design, for chaining
   */
  public TiebackThermalDesign setRequiredNoTouchTime(double hours) {
    this.requiredNoTouchTimeHours = hours;
    return this;
  }

  /**
   * Returns whether the fluid carried water.
   *
   * @return true when water was present, false when every result is meaningless
   */
  public boolean isWaterPresent() {
    return waterPresent;
  }
}
