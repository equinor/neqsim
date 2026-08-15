package neqsim.thermodynamicoperations.propertygenerator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.exception.InvalidInputException;

/**
 * Generate an OLGA hydrate equilibrium curve from a NeqSim fluid.
 *
 * <p>
 * OLGA checks hydrate risk against a tabulated hydrate equilibrium curve rather than computing hydrate thermodynamics
 * itself. The curve is a library object of pressure/temperature pairs, referenced from a flowpath:
 * </p>
 *
 * <pre>
 * HYDRATECURVE LABEL = "HYD", PRESSURE = (...) bara, \
 *              TEMPERATURE = (...) C
 *
 * NETWORKCOMPONENT TYPE=FLOWPATH, TAG=FLOWPATH_1
 *  ...
 *  HYDRATECHECK HYDRATECURVE = "HYD"
 * ENDNETWORKCOMPONENT
 * </pre>
 *
 * <p>
 * The alternative inside OLGA is the Hammerschmidt correlation, which is a crude inhibitor shift. Exporting the curve
 * from NeqSim gives OLGA the same rigorous hydrate model - including a real MEG or methanol inhibited curve - that the
 * NeqSim side of a study uses, so the two agree on where the hydrate boundary is.
 * </p>
 *
 * <p>
 * The fluid must contain water. The generator works on a copy, so the caller's fluid keeps its state.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class OLGAhydrateCurveGenerator extends neqsim.thermodynamicoperations.BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OLGAhydrateCurveGenerator.class);

  /** Fewest converged points that still make a usable curve. */
  private static final int MINIMUM_CURVE_POINTS = 2;

  /** Working copy of the caller's fluid. */
  private final SystemInterface thermoSystem;
  /** Pressures the curve is evaluated at, in bara. */
  private double[] pressures;
  /** Converged pressures, in bara. */
  private double[] curvePressures;
  /** Hydrate equilibrium temperatures at the converged pressures, in degrees Celsius. */
  private double[] curveTemperatures;
  /** Label the OLGA case refers to in HYDRATECHECK. */
  private String curveLabel = "HYDRATECURVE";

  /**
   * Constructor for OLGAhydrateCurveGenerator.
   *
   * @param system fluid to evaluate, must contain water
   */
  public OLGAhydrateCurveGenerator(SystemInterface system) {
    this.thermoSystem = system.clone();
    this.thermoSystem.setHydrateCheck(true);
  }

  /**
   * Set the pressures the hydrate curve is evaluated at.
   *
   * @param minPressure lowest pressure in bara, must be positive
   * @param maxPressure highest pressure in bara, must exceed minPressure
   * @param numberOfSteps number of pressures, at least two
   */
  public void setPressureRange(double minPressure, double maxPressure, int numberOfSteps) {
    if (numberOfSteps < MINIMUM_CURVE_POINTS) {
      throw new IllegalArgumentException("A hydrate curve needs at least " + MINIMUM_CURVE_POINTS + " pressures");
    }
    if (minPressure <= 0.0 || maxPressure <= minPressure) {
      throw new IllegalArgumentException(
          "Require 0 < minPressure < maxPressure, got " + minPressure + " and " + maxPressure);
    }
    pressures = new double[numberOfSteps];
    double step = (maxPressure - minPressure) / (numberOfSteps - 1.0);
    for (int i = 0; i < numberOfSteps; i++) {
      pressures[i] = minPressure + i * step;
    }
  }

  /**
   * Set the curve label written to the file.
   *
   * <p>
   * The OLGA case refers to this exact string in {@code HYDRATECHECK HYDRATECURVE="..."}.
   * </p>
   *
   * @param label curve label, ignored when null or empty
   */
  public void setCurveLabel(String label) {
    if (label != null && label.trim().length() > 0) {
      this.curveLabel = label.trim();
    }
  }

  /**
   * Get the curve label written to the file.
   *
   * @return curve label
   */
  public String getCurveLabel() {
    return curveLabel;
  }

  /**
   * Get the pressures of the converged curve.
   *
   * @return pressures in bara, empty before {@link #run()}
   */
  public double[] getCurvePressures() {
    return curvePressures == null ? new double[0] : curvePressures.clone();
  }

  /**
   * Get the hydrate equilibrium temperatures of the converged curve.
   *
   * @return temperatures in degrees Celsius, empty before {@link #run()}
   */
  public double[] getCurveTemperatures() {
    return curveTemperatures == null ? new double[0] : curveTemperatures.clone();
  }

  /**
   * Get the flowpath line that activates the curve.
   *
   * @return the HYDRATECHECK keyword line to place inside the OLGA flowpath
   */
  public String getHydrateCheckKeyword() {
    return " HYDRATECHECK HYDRATECURVE=\"" + curveLabel + "\"";
  }

  /**
   * Compute the hydrate equilibrium temperature at every pressure.
   *
   * <p>
   * Pressures where the hydrate flash does not converge are dropped rather than written as zero, because a zero
   * temperature in the curve silently moves the hydrate boundary instead of failing.
   * </p>
   *
   * @throws IllegalStateException if the pressure range was not set, or too few points converge
   */
  @Override
  public void run() {
    if (pressures == null) {
      throw new IllegalStateException("Call setPressureRange(...) before run()");
    }
    if (!thermoSystem.hasComponent("water")) {
      throw new IllegalStateException(
          "A hydrate curve needs water in the fluid; add a water component before generating the curve");
    }

    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
    List<Double> okPressures = new ArrayList<Double>();
    List<Double> okTemperatures = new ArrayList<Double>();

    for (int i = 0; i < pressures.length; i++) {
      try {
        thermoSystem.setPressure(pressures[i], "bara");
        ops.hydrateFormationTemperature();
        double temperature = thermoSystem.getTemperature("C");
        if (Double.isNaN(temperature) || Double.isInfinite(temperature)) {
          logger.warn("Hydrate temperature did not converge at {} bara", pressures[i]);
          continue;
        }
        okPressures.add(Double.valueOf(pressures[i]));
        okTemperatures.add(Double.valueOf(temperature));
      } catch (Exception ex) {
        logger.warn("Hydrate temperature failed at {} bara: {}", pressures[i], ex.getMessage());
      }
    }

    if (okPressures.size() < MINIMUM_CURVE_POINTS) {
      throw new IllegalStateException(
          "Only " + okPressures.size() + " hydrate points converged; a curve needs at least " + MINIMUM_CURVE_POINTS
              + ". Check that the fluid contains water and that the pressure range is realistic.");
    }

    curvePressures = new double[okPressures.size()];
    curveTemperatures = new double[okTemperatures.size()];
    for (int i = 0; i < okPressures.size(); i++) {
      curvePressures[i] = okPressures.get(i).doubleValue();
      curveTemperatures[i] = okTemperatures.get(i).doubleValue();
    }
    logger.info("Hydrate curve generated with {} of {} points", curvePressures.length, pressures.length);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    for (int i = 0; i < getCurvePressures().length; i++) {
      logger.info("hydrate point {} bara {} C", curvePressures[i], curveTemperatures[i]);
    }
  }

  /**
   * Write the curve as an OLGA HYDRATECURVE keyword block.
   *
   * <p>
   * The file is included in an OLGA case with {@code FILES} or pasted into the case at library level, and referenced
   * from the flowpath with {@link #getHydrateCheckKeyword()}.
   * </p>
   *
   * @param filename output file path
   * @throws InvalidInputException if the curve has not been generated
   */
  public void writeOLGAinpFile(String filename) throws InvalidInputException {
    if (curvePressures == null) {
      throw new InvalidInputException(this, "writeOLGAinpFile", "filename", "- call run() before writing the curve");
    }
    File outputFile = new File(filename);
    File parent = outputFile.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      logger.error("Could not create output directory {}", parent.getAbsolutePath());
      return;
    }
    try (FileOutputStream outputStream = new FileOutputStream(outputFile);
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"))) {
      writer.write(toKeywordBlock());
    } catch (IOException ex) {
      logger.error("Failed writing OLGA hydrate curve to " + filename, ex);
    }
  }

  /**
   * Render the curve as the OLGA HYDRATECURVE keyword block.
   *
   * @return keyword block text
   */
  public String toKeywordBlock() {
    StringBuilder sb = new StringBuilder();
    sb.append("! Hydrate equilibrium curve generated by NeqSim\n");
    sb.append("! Reference it from the flowpath with:\n");
    sb.append("!").append(getHydrateCheckKeyword()).append("\n");
    sb.append("HYDRATECURVE LABEL = \"").append(curveLabel).append("\", \\\n");
    sb.append("             PRESSURE = (");
    for (int i = 0; i < curvePressures.length; i++) {
      sb.append(format(curvePressures[i]));
      if (i < curvePressures.length - 1) {
        sb.append(",");
      }
    }
    sb.append(") bara, \\\n");
    sb.append("             TEMPERATURE = (");
    for (int i = 0; i < curveTemperatures.length; i++) {
      sb.append(format(curveTemperatures[i]));
      if (i < curveTemperatures.length - 1) {
        sb.append(",");
      }
    }
    sb.append(") C\n");
    return sb.toString();
  }

  /**
   * Format a number in plain fixed point.
   *
   * <p>
   * OLGA reads the file with a locale-independent parser, so the decimal separator must be a point and the value must
   * not be rendered in exponent form.
   * </p>
   *
   * @param value number to format
   * @return fixed-point representation
   */
  private static String format(double value) {
    return String.format(Locale.US, "%.4f", Double.valueOf(value));
  }
}
