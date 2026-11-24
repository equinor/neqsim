package neqsim.process.fielddevelopment.olga;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import neqsim.process.equipment.reservoir.InflowPerformance;

/**
 * Exports a well's liquid inflow performance relationship and trajectory as OLGA GENKEY text: case-level
 * {@code MATERIAL}/{@code WALL}/{@code TABULAR} library keywords followed by a {@code NETWORKCOMPONENT
 * TYPE=FLOWPATH} well network-component block, ready to embed inside a full genkey case.
 *
 * <p>
 * This bridges two already-validated NeqSim pieces -- {@link InflowPerformance} (the liquid IPR model) and an
 * externally supplied trajectory (node coordinates and per-segment diameters, e.g. from a real deviation survey or a
 * synthetic trajectory generator) -- into the real OLGA keyword syntax the Petroleum Experts/Schlumberger OLGA
 * engine reads: {@code MATERIAL}, {@code WALL}, a {@code TABULAR} inflow-performance table, {@code GEOMETRY},
 * {@code PIPE} segments, {@code HEATTRANSFER}, and {@code RESERVOIRCONTACT}.
 * </p>
 *
 * <h2>Validated against a real OLGA rule-check</h2>
 * <p>
 * This class's exact output was embedded in a full genkey case (adapted from the {@code olga-genkey} skill's own
 * {@code Basic.genkey} reference case) and run through the real, installed OLGA engine's {@code -exitRC} rule check
 * repeatedly, not just inspected by eye. That process caught several real structural bugs no unit test alone found
 * (unit tests only assert substring presence, not validity within OLGA's own keyword hierarchy):
 * </p>
 * <ul>
 * <li>{@code TABULAR} and {@code WALL} nested inside the {@code FLOWPATH} instead of at case level
 * ("Unknown keyword type ... for FLOWPATH").</li>
 * <li>{@code POSITION LABEL=Bottomhole} referencing the wrong (last, not first) pipe.</li>
 * <li>{@code GEOMETRY} and {@code HEATTRANSFER} entirely missing ("Missing required keywords").</li>
 * <li>A bare {@code WALL ... MATERIAL=(STEEL)} reference with no matching {@code MATERIAL} declaration ("MATERIAL
 * with LABEL or NUMBER STEEL ... is not found") -- OLGA has no built-in material by that name.</li>
 * </ul>
 * <p>
 * All are fixed and covered by regression tests; see {@code OlgaWellGenkeyExporterTest} for the specifics.
 * </p>
 *
 * <h2>Trajectory convention</h2>
 * <p>
 * {@code xMetre}/{@code yMetre} are per-NODE coordinates (meters, OLGA's own elevation convention: {@code yMetre}
 * negative with depth), starting at the well's own origin (index 0, the deepest/reservoir-contact end).
 * {@code diameterMetre} is per-SEGMENT, so it has exactly one fewer entry than {@code xMetre}/{@code yMetre} --
 * segment {@code i} (1-indexed PIPE {@code i}) runs from node {@code i-1} to node {@code i} with diameter
 * {@code diameterMetre[i-1]}.
 * </p>
 *
 * <h2>Deliberately out of scope</h2>
 * <p>
 * This class exports only what its real inputs actually determine. It does not fabricate a placeholder for data it
 * has no way to know:
 * </p>
 * <ul>
 * <li><b>No choke valve / Cv table.</b> A well-specific choke restriction is not implied by an IPR or a trajectory;
 * a non-well-specific placeholder Cv table would misrepresent an actual choke restriction. Add a choke to the
 * embedding case separately once real Cv data is available.</li>
 * <li><b>No segment grading.</b> Each consecutive node pair becomes exactly one {@code PIPE} with
 * {@code NSEGMENT=1}. Provide more trajectory nodes for finer resolution; this class does not invent a grading
 * heuristic.</li>
 * <li><b>No fabricated GOR/WGR.</b> {@link InflowPerformance} models stock-tank liquid rate only and carries no
 * gas-oil or water-gas ratio. {@code GORST=}/{@code WGR=} are omitted from {@code RESERVOIRCONTACT} unless supplied
 * explicitly (they are optional overrides in OLGA's own genkey format; omitting them defers to the fluid file's own
 * composition).</li>
 * <li><b>No assumed material properties.</b> {@link #setWall(double, String, String, double, double, double)}
 * requires a real conductivity/heat-capacity/density, not just a material name -- an earlier version of this class
 * accepted a bare name like {@code "STEEL"} and rule-checked as invalid because OLGA has no built-in material by
 * that name.</li>
 * </ul>
 *
 * <h2>Example usage</h2>
 *
 * <pre>{@code
 * InflowPerformance ipr = InflowPerformance.composite(200.0, 70.68, 66.94);
 * OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
 * exporter.setTrajectory(new double[] {0.0, 0.0, 0.0}, new double[] {0.0, -1000.0, -2000.0},
 *     new double[] {0.1500, 0.1240});
 * exporter.setInflowPerformance(ipr);
 * exporter.setReservoirTemperature(90.0, "C");
 * exporter.setPipeRoughness(2.0e-5, "m"); // real tubing roughness, not a guess
 * exporter.setWall(0.012, "m", "CARBON_STEEL", 50.0, 500.0, 7850.0); // real material properties, not a guess
 * exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "C"); // real ambient U-value/temperature, not a guess
 * exporter.setPvtFluidLabel("MY_FLUID"); // the FLUID= label a PVT table generator already produced
 * String genkeyText = exporter.toGenkeyText();
 * }</pre>
 *
 * @author NeqSim
 * @version 1.0
 * @see InflowPerformance
 * @see neqsim.process.equipment.reservoir.WellFlow
 * @see neqsim.thermodynamicoperations.propertygenerator.OLGApropertyTableGenerator
 */
public final class OlgaWellGenkeyExporter {
  /** Well label, used to derive the flowpath tag and as a comment header. */
  private final String wellLabel;

  /** Per-node horizontal distance, meters. */
  private double[] xMetre;
  /** Per-node vertical distance, meters (negative with depth). */
  private double[] yMetre;
  /** Per-segment diameter, meters -- one fewer entry than {@link #xMetre}/{@link #yMetre}. */
  private double[] diameterMetre;

  /** The liquid inflow performance relationship to tabulate. */
  private InflowPerformance inflowPerformance;
  /** Number of points sampled from {@link InflowPerformance#curve(int)}. */
  private int iprCurvePoints = 11;
  /** Genkey {@code LABEL=} for the {@code TABULAR} IPR table, referenced by {@code RESERVOIRCONTACT}. */
  private String iprLabel = "MY_IPR_TAB";

  /** Reservoir/bottom-hole temperature, Celsius. {@link Double#NaN} until set. */
  private double reservoirTemperatureCelsius = Double.NaN;

  /** Pipe roughness, meters. {@link Double#NaN} until set -- no fabricated default. */
  private double pipeRoughnessMetre = Double.NaN;

  /** Tubing wall thickness, meters. {@link Double#NaN} until set -- no fabricated default. */
  private double wallThicknessMetre = Double.NaN;
  /** Wall material label, e.g. {@code "STEEL"}. {@code null} until set -- no fabricated default. */
  private String wallMaterialLabel;
  /** Wall material thermal conductivity, W/m-C. {@link Double#NaN} until set -- no fabricated default. */
  private double wallConductivityWPerMC = Double.NaN;
  /** Wall material specific heat capacity, J/kg-C. {@link Double#NaN} until set -- no fabricated default. */
  private double wallHeatCapacityJPerKgC = Double.NaN;
  /** Wall material density, kg/m3. {@link Double#NaN} until set -- no fabricated default. */
  private double wallDensityKgPerM3 = Double.NaN;

  /** Overall heat-transfer coefficient, W/m2-C. {@link Double#NaN} until set -- no fabricated default. */
  private double heatTransferUValueWPerM2C = Double.NaN;
  /** Ambient temperature for {@code HEATTRANSFER}, Celsius. {@link Double#NaN} until set -- no fabricated default. */
  private double ambientTemperatureCelsius = Double.NaN;

  /** Optional {@code FLUID=} label for the {@code BRANCH} statement; {@code null} omits it. */
  private String pvtFluidLabel;

  /** Optional gas-oil ratio override for {@code RESERVOIRCONTACT}; {@link Double#NaN} omits it. */
  private double gasOilRatioSm3Sm3 = Double.NaN;
  /** Optional water-gas ratio override for {@code RESERVOIRCONTACT}; {@link Double#NaN} omits it. */
  private double waterGasRatioSm3Sm3 = Double.NaN;

  /**
   * Constructor for OlgaWellGenkeyExporter.
   *
   * @param wellLabel the well's own label, used as the flowpath tag and comment header; must not be null or blank
   */
  public OlgaWellGenkeyExporter(String wellLabel) {
    if (wellLabel == null || wellLabel.trim().isEmpty()) {
      throw new IllegalArgumentException("wellLabel must not be null or blank");
    }
    this.wellLabel = wellLabel;
  }

  /**
   * Set the well's own trajectory: per-node coordinates and per-segment diameter.
   *
   * @param xMetre per-node horizontal distance in meters, at least two entries
   * @param yMetre per-node vertical distance in meters, same length as {@code xMetre}
   * @param diameterMetre per-segment diameter in meters, exactly one fewer entry than {@code xMetre}
   * @throws IllegalArgumentException if any array is null, lengths do not match the documented convention, fewer
   * than two nodes are given, or any value is not finite or not positive where positivity is required
   */
  public void setTrajectory(double[] xMetre, double[] yMetre, double[] diameterMetre) {
    if (xMetre == null || yMetre == null || diameterMetre == null) {
      throw new IllegalArgumentException("xMetre, yMetre and diameterMetre must not be null");
    }
    if (xMetre.length != yMetre.length) {
      throw new IllegalArgumentException(
          "xMetre and yMetre must have the same length, got " + xMetre.length + " and " + yMetre.length);
    }
    if (xMetre.length < 2) {
      throw new IllegalArgumentException("a trajectory needs at least two nodes, got " + xMetre.length);
    }
    if (diameterMetre.length != xMetre.length - 1) {
      throw new IllegalArgumentException("diameterMetre must have exactly one fewer entry than xMetre/yMetre ("
          + (xMetre.length - 1) + " segments for " + xMetre.length + " nodes), got " + diameterMetre.length);
    }
    for (double value : xMetre) {
      requireFinite(value, "xMetre");
    }
    for (double value : yMetre) {
      requireFinite(value, "yMetre");
    }
    for (double value : diameterMetre) {
      requireFinite(value, "diameterMetre");
      if (value <= 0.0) {
        throw new IllegalArgumentException("diameterMetre entries must be positive, got " + value);
      }
    }
    this.xMetre = xMetre.clone();
    this.yMetre = yMetre.clone();
    this.diameterMetre = diameterMetre.clone();
  }

  /**
   * Set the liquid inflow performance relationship to tabulate.
   *
   * @param inflowPerformance the inflow relationship, must not be null
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "The exporter must read the caller's own live InflowPerformance state at toGenkeyText() "
          + "time; InflowPerformance has no defensive-copy mechanism, and copying would silently diverge from "
          + "the caller's model if it is updated after this setter is called")
  public void setInflowPerformance(InflowPerformance inflowPerformance) {
    if (inflowPerformance == null) {
      throw new IllegalArgumentException("inflowPerformance must not be null");
    }
    this.inflowPerformance = inflowPerformance;
  }

  /**
   * Set the number of points sampled from {@link InflowPerformance#curve(int)} for the {@code TABULAR} IPR table.
   *
   * @param points number of points, at least two; defaults to 11 if never called
   * @throws IllegalArgumentException if fewer than two points are requested
   */
  public void setIprCurvePoints(int points) {
    if (points < 2) {
      throw new IllegalArgumentException("an IPR curve needs at least two points, got " + points);
    }
    this.iprCurvePoints = points;
  }

  /**
   * Set the genkey {@code LABEL=} used for the {@code TABULAR} IPR table and referenced by {@code RESERVOIRCONTACT}.
   *
   * @param label the label, must not be null or blank; defaults to {@code "MY_IPR_TAB"} if never called
   */
  public void setIprLabel(String label) {
    if (label == null || label.trim().isEmpty()) {
      throw new IllegalArgumentException("label must not be null or blank");
    }
    this.iprLabel = label;
  }

  /**
   * Set the reservoir/bottom-hole temperature referenced by {@code RESERVOIRCONTACT}.
   *
   * @param temperature the temperature, must be finite
   * @param unit {@code "C"} or {@code "K"}
   * @throws IllegalArgumentException if the temperature is not finite or the unit is not recognised
   */
  public void setReservoirTemperature(double temperature, String unit) {
    requireFinite(temperature, "temperature");
    if ("C".equalsIgnoreCase(unit)) {
      this.reservoirTemperatureCelsius = temperature;
    } else if ("K".equalsIgnoreCase(unit)) {
      this.reservoirTemperatureCelsius = temperature - 273.15;
    } else {
      throw new IllegalArgumentException("unit must be \"C\" or \"K\", got \"" + unit + "\"");
    }
  }

  /**
   * Set the tubing's own pipe roughness -- real, well-specific completion data. There is no default: roughness
   * materially affects the pressure-drop correlation and must come from the actual tubing, not a guess.
   *
   * @param roughness pipe roughness, must be positive and finite
   * @param unit {@code "m"} or {@code "mm"}
   * @throws IllegalArgumentException if the roughness is not positive/finite or the unit is not recognised
   */
  public void setPipeRoughness(double roughness, String unit) {
    requireFinite(roughness, "roughness");
    if (roughness <= 0.0) {
      throw new IllegalArgumentException("roughness must be positive, got " + roughness);
    }
    if ("m".equalsIgnoreCase(unit)) {
      this.pipeRoughnessMetre = roughness;
    } else if ("mm".equalsIgnoreCase(unit)) {
      this.pipeRoughnessMetre = roughness / 1000.0;
    } else {
      throw new IllegalArgumentException("unit must be \"m\" or \"mm\", got \"" + unit + "\"");
    }
  }

  /**
   * Set the tubing wall's own thickness and a real material definition -- real, well-specific completion data. A
   * real OLGA rule-check confirmed {@code WALL} (referencing a {@code MATERIAL}) is a required keyword for every
   * {@code PIPE} in this exporter's own installed OLGA version, so this class cannot validly omit it. There is no
   * default for any of the five parameters: an earlier version of this method accepted only a bare material LABEL
   * (e.g. {@code "STEEL"}) with no conductivity/capacity/density, which rule-checked as invalid ("MATERIAL with
   * LABEL or NUMBER STEEL ... is not found") because OLGA has no built-in material by that name -- a real
   * {@code MATERIAL} keyword with real properties must be declared, not assumed to already exist.
   *
   * @param thickness wall thickness, must be positive and finite
   * @param thicknessUnit {@code "m"} or {@code "mm"}
   * @param materialLabel wall material label (e.g. {@code "STEEL"}), must not be null or blank
   * @param conductivity thermal conductivity in W/m-C, must be positive and finite
   * @param heatCapacity specific heat capacity in J/kg-C, must be positive and finite
   * @param density density in kg/m3, must be positive and finite
   * @throws IllegalArgumentException if the thickness/conductivity/heatCapacity/density are not positive/finite,
   * the thickness unit is not recognised, or the material label is null/blank
   */
  public void setWall(double thickness, String thicknessUnit, String materialLabel, double conductivity,
      double heatCapacity, double density) {
    requireFinite(thickness, "thickness");
    if (thickness <= 0.0) {
      throw new IllegalArgumentException("thickness must be positive, got " + thickness);
    }
    if (materialLabel == null || materialLabel.trim().isEmpty()) {
      throw new IllegalArgumentException("materialLabel must not be null or blank");
    }
    requireFinite(conductivity, "conductivity");
    if (conductivity <= 0.0) {
      throw new IllegalArgumentException("conductivity must be positive, got " + conductivity);
    }
    requireFinite(heatCapacity, "heatCapacity");
    if (heatCapacity <= 0.0) {
      throw new IllegalArgumentException("heatCapacity must be positive, got " + heatCapacity);
    }
    requireFinite(density, "density");
    if (density <= 0.0) {
      throw new IllegalArgumentException("density must be positive, got " + density);
    }
    if ("m".equalsIgnoreCase(thicknessUnit)) {
      this.wallThicknessMetre = thickness;
    } else if ("mm".equalsIgnoreCase(thicknessUnit)) {
      this.wallThicknessMetre = thickness / 1000.0;
    } else {
      throw new IllegalArgumentException("thicknessUnit must be \"m\" or \"mm\", got \"" + thicknessUnit + "\"");
    }
    this.wallMaterialLabel = materialLabel;
    this.wallConductivityWPerMC = conductivity;
    this.wallHeatCapacityJPerKgC = heatCapacity;
    this.wallDensityKgPerM3 = density;
  }

  /**
   * Set the flowpath's own overall (lumped) heat-transfer coefficient and ambient temperature -- real, well- and
   * environment-specific data. There is no default: a real OLGA rule-check confirmed {@code HEATTRANSFER} is a
   * required keyword for a {@code FLOWPATH}, so this class cannot validly omit it, but it will not fabricate a
   * value either -- the caller must supply a real overall coefficient and ambient temperature (e.g. from the same
   * completion/environment data used elsewhere in the study).
   *
   * @param overallCoefficient overall heat-transfer coefficient, must be positive and finite
   * @param overallCoefficientUnit {@code "W/m2-C"} or {@code "W/m2-K"} (numerically identical: both are per unit
   * temperature DIFFERENCE, so a degree Celsius and a kelvin differ by the same amount)
   * @param ambientTemperature ambient temperature, must be finite
   * @param ambientTemperatureUnit {@code "C"} or {@code "K"}
   * @throws IllegalArgumentException if the coefficient is not positive/finite, the ambient temperature is not
   * finite, or either unit is not recognised
   */
  public void setHeatTransfer(double overallCoefficient, String overallCoefficientUnit, double ambientTemperature,
      String ambientTemperatureUnit) {
    requireFinite(overallCoefficient, "overallCoefficient");
    if (overallCoefficient <= 0.0) {
      throw new IllegalArgumentException("overallCoefficient must be positive, got " + overallCoefficient);
    }
    if (!"W/m2-C".equalsIgnoreCase(overallCoefficientUnit) && !"W/m2-K".equalsIgnoreCase(overallCoefficientUnit)) {
      throw new IllegalArgumentException(
          "overallCoefficientUnit must be \"W/m2-C\" or \"W/m2-K\", got \"" + overallCoefficientUnit + "\"");
    }
    requireFinite(ambientTemperature, "ambientTemperature");
    if ("C".equalsIgnoreCase(ambientTemperatureUnit)) {
      this.ambientTemperatureCelsius = ambientTemperature;
    } else if ("K".equalsIgnoreCase(ambientTemperatureUnit)) {
      this.ambientTemperatureCelsius = ambientTemperature - 273.15;
    } else {
      throw new IllegalArgumentException(
          "ambientTemperatureUnit must be \"C\" or \"K\", got \"" + ambientTemperatureUnit + "\"");
    }
    this.heatTransferUValueWPerM2C = overallCoefficient;
  }

  /**
   * Set the {@code FLUID=} label referenced by this flowpath's {@code BRANCH} statement -- the label a PVT table
   * generator (e.g. {@link neqsim.thermodynamicoperations.propertygenerator.OLGApropertyTableGenerator}) already
   * produced. Optional: when never called, {@link #toGenkeyText()} emits a clearly marked {@code TODO} comment
   * instead of a fabricated fluid reference, because a genkey flowpath is not valid OLGA input without a real one.
   *
   * @param label the PVT fluid label, must not be null or blank
   */
  public void setPvtFluidLabel(String label) {
    if (label == null || label.trim().isEmpty()) {
      throw new IllegalArgumentException("label must not be null or blank");
    }
    this.pvtFluidLabel = label;
  }

  /**
   * Set an explicit gas-oil ratio override for {@code RESERVOIRCONTACT}. Optional: {@link InflowPerformance} models
   * liquid rate only and carries no GOR of its own, so this is omitted from the output entirely unless supplied
   * here -- never fabricated as zero.
   *
   * @param gasOilRatio gas-oil ratio in Sm3/Sm3, must be finite and zero or greater
   * @throws IllegalArgumentException if the ratio is not finite or negative
   */
  public void setGasOilRatio(double gasOilRatio) {
    requireFinite(gasOilRatio, "gasOilRatio");
    if (gasOilRatio < 0.0) {
      throw new IllegalArgumentException("gasOilRatio must be zero or greater, got " + gasOilRatio);
    }
    this.gasOilRatioSm3Sm3 = gasOilRatio;
  }

  /**
   * Set an explicit water-gas ratio override for {@code RESERVOIRCONTACT}. Optional, for the same reason as
   * {@link #setGasOilRatio(double)}.
   *
   * @param waterGasRatio water-gas ratio in Sm3/Sm3, must be finite and zero or greater
   * @throws IllegalArgumentException if the ratio is not finite or negative
   */
  public void setWaterGasRatio(double waterGasRatio) {
    requireFinite(waterGasRatio, "waterGasRatio");
    if (waterGasRatio < 0.0) {
      throw new IllegalArgumentException("waterGasRatio must be zero or greater, got " + waterGasRatio);
    }
    this.waterGasRatioSm3Sm3 = waterGasRatio;
  }

  /**
   * Get the well label this exporter was constructed with.
   *
   * @return the well label
   */
  public String getWellLabel() {
    return wellLabel;
  }

  /**
   * Build the OLGA GENKEY text: case-level {@code MATERIAL}/{@code TABULAR} keywords followed by the well's own
   * network-component block.
   *
   * @return the genkey text, ready to embed inside a full case
   * @throws IllegalStateException if the trajectory, inflow performance, reservoir temperature, pipe roughness,
   * wall/material, or heat-transfer coefficient/ambient temperature have not been set -- each is real, well- or
   * environment-specific data this class will not fabricate a default for
   */
  public String toGenkeyText() {
    if (xMetre == null) {
      throw new IllegalStateException("setTrajectory(...) must be called before toGenkeyText()");
    }
    if (inflowPerformance == null) {
      throw new IllegalStateException("setInflowPerformance(...) must be called before toGenkeyText()");
    }
    if (Double.isNaN(reservoirTemperatureCelsius)) {
      throw new IllegalStateException("setReservoirTemperature(...) must be called before toGenkeyText()");
    }
    if (Double.isNaN(pipeRoughnessMetre)) {
      throw new IllegalStateException("setPipeRoughness(...) must be called before toGenkeyText()");
    }
    if (Double.isNaN(wallThicknessMetre) || wallMaterialLabel == null) {
      throw new IllegalStateException("setWall(...) must be called before toGenkeyText()");
    }
    if (Double.isNaN(heatTransferUValueWPerM2C) || Double.isNaN(ambientTemperatureCelsius)) {
      throw new IllegalStateException("setHeatTransfer(...) must be called before toGenkeyText()");
    }

    StringBuilder text = new StringBuilder();
    String flowpathTag = "FLOWPATH_" + sanitizeTag(wellLabel);
    String geometryLabel = "GEOM_" + sanitizeTag(wellLabel);
    String wallLabel = "WALL_" + sanitizeTag(wellLabel);

    text.append("!").append(repeat('*', 86)).append('\n');
    text.append("! ").append(wellLabel).append(" -- generated by neqsim ").append(getClass().getName()).append('\n');
    text.append("!").append(repeat('*', 86)).append('\n');
    // MATERIAL, WALL and TABULAR are all case-level LIBRARY keywords, not valid statements inside
    // a NETWORKCOMPONENT TYPE=FLOWPATH block -- confirmed by a real OLGA rule-check against this
    // exact output ("Unknown keyword type TABULAR for FLOWPATH", and, before that, "Unknown
    // keyword type WALL for FLOWPATH"). This matches AFA's own well-inflow-pi skill convention,
    // which likewise writes WALL/TABULAR in one library-keyword section before any
    // NETWORKCOMPONENT. An earlier version of this method nested these inside the FLOWPATH.
    text.append("MATERIAL LABEL=\"").append(wallMaterialLabel).append("\", CAPACITY=")
        .append(formatNumber(wallHeatCapacityJPerKgC)).append(" J/kg-C, CONDUCTIVITY=")
        .append(formatNumber(wallConductivityWPerMC)).append(" W/m-C, DENSITY=")
        .append(formatNumber(wallDensityKgPerM3)).append(" kg/m3\n");
    text.append("WALL LABEL=\"").append(wallLabel).append("\", THICKNESS=(").append(formatNumber(wallThicknessMetre))
        .append(") m, MATERIAL=(\"").append(wallMaterialLabel).append("\")\n");
    appendTabular(text);
    text.append("NETWORKCOMPONENT TYPE=FLOWPATH, TAG=").append(flowpathTag).append('\n');
    text.append(" PARAMETERS LABEL=\"").append(wellLabel).append("\", INITIALIZATION=USEGLOBAL\n");
    // GEOMETRY is a required FLOWPATH keyword (confirmed by a real OLGA rule-check: "Missing
    // required keywords. GEOMETRY"), matching the bundled AdvancedWell.genkey reference case's own
    // `GEOMETRY LABEL="GEOM-1", YSTART=<elevation> m`. YSTART is this class's own node 0
    // elevation -- already-known real trajectory data, not a guess.
    text.append(" GEOMETRY LABEL=\"").append(geometryLabel).append("\", YSTART=").append(formatNumber(yMetre[0]))
        .append(" m\n");
    if (pvtFluidLabel != null) {
      text.append(" BRANCH FLUID=\"").append(pvtFluidLabel).append("\"\n");
    } else {
      text.append(" ! TODO: BRANCH FLUID=\"<pvt-fluid-label>\" -- setPvtFluidLabel(...) was never called, so this\n");
      text.append(" ! flowpath has no fluid reference and is NOT valid OLGA input without one.\n");
    }

    appendPipes(text, wallLabel);

    // HEATTRANSFER is also a required FLOWPATH keyword (confirmed by the same rule-check:
    // "Missing required keywords. HEATTRANSFER"), using the simpler lumped-coefficient form
    // (matching the bundled Basic.genkey reference case's own `TAMBIENT=`/`HAMBIENT=` convention),
    // alongside (not instead of) the layered WALL/MATERIAL model above: WALL describes the pipe's
    // own conduction layers, HEATTRANSFER the ambient boundary condition outside them -- both
    // required together, confirmed by Basic.genkey's own reference case using both simultaneously.
    text.append(" HEATTRANSFER LABEL=\"HEATTRANS-1\", TAMBIENT=").append(formatNumber(ambientTemperatureCelsius))
        .append(" C, HAMBIENT=").append(formatNumber(heatTransferUValueWPerM2C)).append(" W/m2-C\n");

    appendReservoirContact(text);

    // "Bottomhole" (the RESERVOIRCONTACT position) is physically at node 0 -- the deepest/
    // reservoir-contact end this class's own trajectory convention documents -- which is the
    // START of PIPE-1, not the last pipe. Confirmed against the equivalent, already-validated
    // reference statement in agentic_flow_assurance's well-inflow-pi skill
    // (`POSITION LABEL=Bottomhole, PIPE="<first pipe>", SECTION=1`); an earlier version of this
    // line referenced the LAST pipe instead, placing "Bottomhole" at the wellhead end.
    text.append("POSITION LABEL=Bottomhole, PIPE=\"").append(pipeLabel(1)).append("\", SECTION=1\n");
    text.append("ENDNETWORKCOMPONENT\n");

    return text.toString();
  }

  /**
   * Build the {@code TABULAR} IPR block from {@link InflowPerformance#curve(int)}.
   *
   * @param text the output being built
   */
  private void appendTabular(StringBuilder text) {
    List<double[]> rows = inflowPerformance.curve(iprCurvePoints);
    double reservoirPressure = inflowPerformance.getReservoirPressure();
    double[] deltaP = new double[rows.size()];
    double[] stdFlowRate = new double[rows.size()];
    for (int i = 0; i < rows.size(); i++) {
      deltaP[i] = reservoirPressure - rows.get(i)[0];
      stdFlowRate[i] = rows.get(i)[1];
    }
    text.append("TABULAR LABEL=\"").append(iprLabel).append("\", MODELOPTION=VOLUME, DELTAP=(")
        .append(formatArray(deltaP)).append(") bar, \\\n");
    text.append("        STDFLOWRATE=(").append(formatArray(stdFlowRate)).append(") Sm3/d, PHASE=LIQUID\n");
  }

  /**
   * Build one {@code PIPE} statement per consecutive node pair, {@code NSEGMENT=1} throughout -- see this class's
   * own Javadoc for why segment grading is deliberately out of scope.
   *
   * @param text the output being built
   * @param wallLabel the case-level {@code WALL} label every {@code PIPE} references
   */
  private void appendPipes(StringBuilder text, String wallLabel) {
    for (int i = 1; i < xMetre.length; i++) {
      double dx = xMetre[i] - xMetre[i - 1];
      double dy = yMetre[i] - yMetre[i - 1];
      double segmentLength = Math.sqrt(dx * dx + dy * dy);
      if (segmentLength <= 0.0) {
        throw new IllegalStateException(
            "trajectory nodes " + (i - 1) + " and " + i + " coincide (zero-length segment); every PIPE needs a "
                + "positive length");
      }
      text.append("PIPE ROUGHNESS=").append(formatNumber(pipeRoughnessMetre)).append(" m, LABEL=\"")
          .append(pipeLabel(i)).append("\", WALL=\"").append(wallLabel).append("\", NSEGMENT=1, LSEGMENT=(")
          .append(formatNumber(segmentLength)).append(") m, XEND=").append(formatNumber(xMetre[i]))
          .append(" m, YEND=").append(formatNumber(yMetre[i])).append(", ZEND=0 m, DIAMETER=")
          .append(formatNumber(diameterMetre[i - 1])).append(" m\n");
    }
  }

  /**
   * Build the {@code RESERVOIRCONTACT} block, referencing the same IPR label the {@code TABULAR} block defined.
   *
   * @param text the output being built
   */
  private void appendReservoirContact(StringBuilder text) {
    text.append("RESERVOIRCONTACT LABEL=\"RESERVOIRCONTACT-1\", PRODIPR=\"").append(iprLabel)
        .append("\", POSITION=Bottomhole, \\\n");
    text.append("        INJIPR=\"").append(iprLabel).append("\", PRESSURE=")
        .append(formatNumber(inflowPerformance.getReservoirPressure())).append(" bara, TEMPERATURE=")
        .append(formatNumber(reservoirTemperatureCelsius)).append(" C");
    if (!Double.isNaN(gasOilRatioSm3Sm3) || !Double.isNaN(waterGasRatioSm3Sm3)) {
      text.append(", \\\n        ");
      boolean first = true;
      if (!Double.isNaN(gasOilRatioSm3Sm3)) {
        text.append("GORST=").append(formatNumber(gasOilRatioSm3Sm3)).append(" Sm3/Sm3");
        first = false;
      }
      if (!Double.isNaN(waterGasRatioSm3Sm3)) {
        if (!first) {
          text.append(", ");
        }
        text.append("WGR=").append(formatNumber(waterGasRatioSm3Sm3)).append(" Sm3/Sm3");
      }
    }
    text.append('\n');
  }

  /**
   * Write {@link #toGenkeyText()} to a file, UTF-8 encoded.
   *
   * @param path destination path; parent directories must already exist
   * @throws IOException if the file cannot be written
   */
  public void writeTo(Path path) throws IOException {
    Files.write(path, toGenkeyText().getBytes(StandardCharsets.UTF_8));
  }

  private static String pipeLabel(int index) {
    return "PIPE-" + index;
  }

  private static void requireFinite(double value, String name) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be finite, got " + value);
    }
  }

  /**
   * Format a single number with fixed-point precision, independent of the JVM's default locale -- a comma-decimal
   * locale would otherwise silently corrupt every numeric value this class writes.
   *
   * <p>
   * Uses 8 decimal places uniformly, not the 4 an earlier version of this method used: pipe roughness is routinely
   * 1e-5 to 1e-4 m, and 4 decimal places silently rounded every realistic roughness value straight to
   * {@code 0.0000} -- a genuinely destructive, silent precision loss caught by this class's own rule-check
   * validation, not by code review. 8 decimal places safely represents every quantity this class emits (pressures,
   * rates, coordinates, diameters, and roughness alike) without magnitude-dependent branching.
   * </p>
   *
   * @param value the value to format
   * @return the formatted value
   */
  private static String formatNumber(double value) {
    return String.format(Locale.ROOT, "%.8f", value);
  }

  private static String formatArray(double[] values) {
    StringBuilder joined = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        joined.append(", ");
      }
      joined.append(formatNumber(values[i]));
    }
    return joined.toString();
  }

  private static String repeat(char character, int count) {
    StringBuilder result = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      result.append(character);
    }
    return result.toString();
  }

  /**
   * Replace characters a genkey {@code TAG=}/{@code LABEL=} identifier cannot safely contain (whitespace, quotes)
   * with underscores, so an arbitrary well label can never produce unparsable genkey text.
   *
   * @param label the raw well label
   * @return a tag-safe version of the label
   */
  private static String sanitizeTag(String label) {
    return label.replaceAll("[^A-Za-z0-9_-]", "_");
  }
}
