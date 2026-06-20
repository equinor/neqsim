package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.expander.ExpanderChartKhader;
import neqsim.process.equipment.expander.RadialExpanderGeometryMap;
import neqsim.thermo.system.SystemInterface;

/**
 * Curated library of reference turbomachinery performance maps shipped with NeqSim.
 *
 * <p>
 * Commercial process simulators ship validated OEM curve libraries; NeqSim has historically relied on user-digitised
 * maps. This class provides a small, versioned, in-repository library of named reference maps so that a user can obtain
 * a physically reasonable, dimensionally-correct map by name without digitising one. The shipped maps are generic,
 * vendor-neutral reference characteristics (representative centrifugal-compressor and radial-inflow-expander shapes),
 * not proprietary OEM data. They are intended as starting points and benchmarks; for fiscal or guarantee work the user
 * must still supply the certified OEM curve.
 * </p>
 *
 * <p>
 * Maps are produced as fully-initialised chart objects: compressor maps as {@link CompressorChartKhader2015} (with a
 * generated surge curve) and expander maps as {@link ExpanderChartKhader}. The Khader 2015 normalisation makes every
 * map composition-aware, so a single reference map can be reused across fluids.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class TurboMachineryChartLibrary implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static final Logger logger = LogManager.getLogger(TurboMachineryChartLibrary.class);

  /** Name of the generic three-speed centrifugal compressor reference map. */
  public static final String GENERIC_CENTRIFUGAL_3SPEED = "GENERIC_CENTRIFUGAL_3SPEED";

  /** Name of the generic cryogenic radial-inflow expander reference map. */
  public static final String GENERIC_CRYO_EXPANDER = "GENERIC_CRYO_EXPANDER";

  /** Name of the geometry-generated radial IFR expander reference map. */
  public static final String GEOMETRY_RADIAL_IFR = "GEOMETRY_RADIAL_IFR";

  /**
   * Default constructor.
   */
  public TurboMachineryChartLibrary() {
  }

  /**
   * Lists the names of the compressor reference maps available in this library.
   *
   * @return a list of compressor map names usable with {@link #getCompressorChart}
   */
  public List<String> listCompressorCharts() {
    List<String> names = new ArrayList<String>();
    names.add(GENERIC_CENTRIFUGAL_3SPEED);
    return names;
  }

  /**
   * Lists the names of the expander reference maps available in this library.
   *
   * @return a list of expander map names usable with {@link #getExpanderChart}
   */
  public List<String> listExpanderCharts() {
    List<String> names = new ArrayList<String>();
    names.add(GENERIC_CRYO_EXPANDER);
    names.add(GEOMETRY_RADIAL_IFR);
    return names;
  }

  /**
   * Returns a named compressor reference map for the supplied fluid.
   *
   * @param name the reference map name (see {@link #listCompressorCharts})
   * @param fluid the working fluid the map will be applied to
   * @param impellerOuterDiameter the impeller outer diameter in m
   * @return a fully-initialised {@link CompressorChartKhader2015} with a generated surge curve
   * @throws IllegalArgumentException if the name is unknown or the fluid is {@code null}
   */
  public CompressorChartKhader2015 getCompressorChart(String name, SystemInterface fluid,
      double impellerOuterDiameter) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid must not be null");
    }
    if (GENERIC_CENTRIFUGAL_3SPEED.equals(name)) {
      return buildGenericCentrifugal(fluid, impellerOuterDiameter);
    }
    throw new IllegalArgumentException("unknown compressor reference map: " + name);
  }

  /**
   * Returns a named expander reference map normalised on the supplied reference fluid.
   *
   * @param name the reference map name (see {@link #listExpanderCharts})
   * @param referenceFluid the fluid used to normalise the map (may be {@code null} to skip composition correction)
   * @return a fully-initialised {@link ExpanderChartKhader}
   * @throws IllegalArgumentException if the name is unknown
   */
  public ExpanderChartKhader getExpanderChart(String name, SystemInterface referenceFluid) {
    if (GENERIC_CRYO_EXPANDER.equals(name)) {
      return buildGenericCryoExpander(referenceFluid);
    }
    if (GEOMETRY_RADIAL_IFR.equals(name)) {
      return buildGeometryExpander(referenceFluid);
    }
    throw new IllegalArgumentException("unknown expander reference map: " + name);
  }

  /**
   * Builds the generic three-speed centrifugal compressor reference map.
   *
   * @param fluid the working fluid the map will be applied to
   * @param impellerOuterDiameter the impeller outer diameter in m
   * @return a populated {@link CompressorChartKhader2015} with a generated surge curve
   */
  private CompressorChartKhader2015 buildGenericCentrifugal(SystemInterface fluid, double impellerOuterDiameter) {
    // reference normalisation conditions: 30 C, 50 bara
    double[] chartConditions = new double[] { 30.0, 50.0 };
    double[] speed = new double[] { 9000.0, 10500.0, 12000.0 };
    double[][] flow = new double[][] { { 1800.0, 2200.0, 2600.0, 3000.0, 3400.0 },
	{ 2100.0, 2600.0, 3100.0, 3600.0, 4100.0 }, { 2400.0, 3000.0, 3600.0, 4200.0, 4800.0 } };
    // heads in J/kg
    double[][] head = new double[][] { { 85000.0, 80000.0, 73000.0, 64000.0, 52000.0 },
	{ 115000.0, 108000.0, 99000.0, 87000.0, 71000.0 }, { 150000.0, 141000.0, 129000.0, 113000.0, 92000.0 } };
    double[][] polyEff = new double[][] { { 0.72, 0.79, 0.82, 0.80, 0.74 }, { 0.73, 0.80, 0.83, 0.81, 0.75 },
	{ 0.72, 0.79, 0.82, 0.80, 0.74 } };

    CompressorChartKhader2015 chart = new CompressorChartKhader2015(fluid, impellerOuterDiameter);
    chart.setCurves(chartConditions, speed, flow, head, flow, polyEff);
    chart.setHeadUnit("kJ/kg");
    chart.generateSurgeCurve();
    return chart;
  }

  /**
   * Builds the generic cryogenic radial-inflow expander reference map (digitised reference shape).
   *
   * @param referenceFluid the fluid used to normalise the map (may be {@code null})
   * @return a populated {@link ExpanderChartKhader}
   */
  private ExpanderChartKhader buildGenericCryoExpander(SystemInterface referenceFluid) {
    double[] igvPositions = new double[] { 0.5, 0.75, 1.0 };
    double[][] uc = new double[][] { { 0.45, 0.55, 0.65, 0.72, 0.80, 0.90 }, { 0.45, 0.55, 0.65, 0.72, 0.80, 0.90 },
	{ 0.45, 0.55, 0.65, 0.72, 0.80, 0.90 } };
    double[][] eta = new double[][] { { 0.64, 0.73, 0.80, 0.82, 0.79, 0.70 }, { 0.67, 0.77, 0.84, 0.86, 0.83, 0.74 },
	{ 0.69, 0.79, 0.85, 0.87, 0.84, 0.75 } };
    // isentropic stage head drop in kJ/kg, larger at more open IGV
    double[][] headDrop = new double[][] { { 30.0, 30.0, 30.0, 30.0, 30.0, 30.0 },
	{ 38.0, 38.0, 38.0, 38.0, 38.0, 38.0 }, { 45.0, 45.0, 45.0, 45.0, 45.0, 45.0 } };

    ExpanderChartKhader chart = new ExpanderChartKhader(referenceFluid, 0.424);
    chart.setCurves(igvPositions, uc, eta, headDrop);
    return chart;
  }

  /**
   * Builds a radial IFR expander reference map from representative blade geometry using the
   * {@link RadialExpanderGeometryMap} mean-line generator.
   *
   * @param referenceFluid the fluid used to normalise the map (may be {@code null})
   * @return a populated {@link ExpanderChartKhader}
   */
  private ExpanderChartKhader buildGeometryExpander(SystemInterface referenceFluid) {
    RadialExpanderGeometryMap generator = new RadialExpanderGeometryMap(0.424, 0.45, 0.45);
    generator.setReferenceFluid(referenceFluid);
    generator.setDesignHeadDropKjPerKg(45.0);
    double[] igvPositions = new double[] { 0.5, 0.75, 1.0 };
    // more closed vanes (lower opening) use a larger absolute flow angle
    double[] nozzleAngleDeg = new double[] { 78.0, 74.0, 70.0 };
    return generator.generateChart(igvPositions, nozzleAngleDeg);
  }
}
