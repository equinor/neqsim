package neqsim.process.equipment.separator.entrainment;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;

/**
 * Database of separator internals and inlet device performance data.
 *
 * <p>
 * Loads performance specifications from CSV files in the resources/designdata directory. This
 * provides a catalog of standard mist eliminator types, inlet devices, and coalescer plates with
 * their grade efficiency parameters, K-factor limits, pressure drops, and mechanical properties.
 * </p>
 *
 * <p>
 * The data is sourced from open literature:
 * </p>
 * <ul>
 * <li>Brunazzi, E., Paglianti, A. (1998), "Mechanistic pressure drop model for wire mesh mist
 * eliminators", <i>Chem. Eng. Sci.</i>, 53(19), 3373-3380.</li>
 * <li>Phillips, H., Listak, R. (1996), "Vane-type mist eliminators", <i>Chem. Eng. Prog.</i>,
 * 92(4), 50-55.</li>
 * <li>Hoffmann, A.C., Stein, L.E. (2008), <i>Gas Cyclones and Swirl Tubes</i>, 2nd ed.,
 * Springer.</li>
 * <li>Polderman, H.G. et al. (1997), "Design rules for plate pack coalescers", conference
 * paper.</li>
 * <li>Arnold, K., Stewart, M. (2008), <i>Surface Production Operations</i>, Vol. 1.</li>
 * <li>Bothamley, M. (2013), "Gas/Liquid Separators — Quantifying Separation Performance", Part 1-3,
 * <i>Oil and Gas Facilities</i>.</li>
 * <li>Verlaan, C.C.J. (2001), PhD Thesis, Delft University of Technology.</li>
 * <li>Svrcek, W.Y., Monnery, W.D. (1993), "Design Two-Phase Separators within the Right Limits",
 * <i>Chem. Eng. Prog.</i></li>
 * </ul>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class SeparatorInternalsDatabase implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(SeparatorInternalsDatabase.class);

  /** Singleton instance. */
  private static volatile SeparatorInternalsDatabase instance;

  /** Loaded internals records. */
  private List<InternalsRecord> internalsRecords = new ArrayList<InternalsRecord>();

  /** Loaded inlet device records. */
  private List<InletDeviceRecord> inletDeviceRecords = new ArrayList<InletDeviceRecord>();

  /** Loaded vendor grade-efficiency curve records. */
  private List<VendorCurveRecord> vendorCurveRecords = new ArrayList<VendorCurveRecord>();

  /** Whether data has been loaded. */
  private boolean loaded = false;

  /**
   * Data record for a separator internals specification.
   */
  public static class InternalsRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Internals type (WIRE_MESH, VANE_PACK, AXIAL_CYCLONE, PLATE_PACK, GRAVITY). */
    public String internalsType;
    /** Subtype description. */
    public String subType;
    /** Manufacturer or source. */
    public String manufacturer;
    /** d50 cut diameter [um]. */
    public double d50_um;
    /** Sharpness parameter. */
    public double sharpness;
    /** Maximum efficiency. */
    public double maxEfficiency;
    /** Maximum K-factor [m/s]. */
    public double maxKFactor;
    /** Minimum K-factor [m/s]. */
    public double minKFactor;
    /** Typical pressure drop [mbar]. */
    public double pressureDrop_mbar;
    /** Applicable design standard. */
    public String designStandard;
    /** Material of construction. */
    public String material;
    /** Maximum operating temperature [degC]. */
    public double maxTemperature_C;
    /** Minimum thickness requirement [mm]. */
    public double minThickness_mm;
    /** Weight per unit area [kg/m2]. */
    public double weight_kg_m2;
    /** Literature reference. */
    public String reference;

    /**
     * Creates a GradeEfficiencyCurve from this record.
     *
     * @return grade efficiency curve
     */
    public GradeEfficiencyCurve toGradeEfficiencyCurve() {
      double d50_m = d50_um * 1e-6;
      if ("WIRE_MESH".equals(internalsType)) {
        return GradeEfficiencyCurve.wireMesh(d50_m, sharpness, maxEfficiency);
      } else if ("VANE_PACK".equals(internalsType)) {
        return GradeEfficiencyCurve.vanePack(d50_m, sharpness, maxEfficiency);
      } else if ("AXIAL_CYCLONE".equals(internalsType)) {
        return GradeEfficiencyCurve.axialCyclone(d50_m, sharpness, maxEfficiency);
      } else if ("PLATE_PACK".equals(internalsType)) {
        return GradeEfficiencyCurve.platePack(d50_m, maxEfficiency);
      } else if ("GRAVITY".equals(internalsType)) {
        return GradeEfficiencyCurve.gravity(d50_m);
      }
      return GradeEfficiencyCurve.wireMeshDefault();
    }
  }

  /**
   * Data record for an inlet device specification.
   */
  public static class InletDeviceRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Device type (NONE, DEFLECTOR_PLATE, HALF_PIPE, etc.). */
    public String deviceType;
    /** Subtype description. */
    public String subType;
    /** Minimum recommended momentum [Pa]. */
    public double minMomentum_Pa;
    /** Maximum recommended momentum [Pa]. */
    public double maxMomentum_Pa;
    /** Typical bulk separation efficiency. */
    public double typicalBulkEfficiency;
    /** DSD multiplier (downstream d50 / upstream d50). */
    public double dsdMultiplier;
    /** Pressure drop velocity head coefficient. */
    public double pressureDropCoeff;
    /** Maximum capacity per unit area [m3/s/m2]. */
    public double maxCapacity;
    /** Material of construction. */
    public String material;
    /** Literature reference. */
    public String reference;
  }

  /**
   * Data record for a vendor-certified grade efficiency curve from factory acceptance testing (FAT).
   *
   * <p>
   * Each record contains measured efficiency points at specific droplet diameters, obtained from
   * standardised testing (EN 13544, ISO 29042, or vendor in-house methods). This provides the
   * "vendor-certified internals curves" capability comparable to commercial tools like MySep.
   * </p>
   *
   * <p>
   * The diameter and efficiency arrays are stored as semicolon-separated values in the CSV and
   * parsed into arrays on load. A {@link GradeEfficiencyCurve} with linear interpolation between
   * measured points is created via {@link #toGradeEfficiencyCurve()}.
   * </p>
   */
  public static class VendorCurveRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Unique curve identifier (e.g., "VC001"). */
    public String curveId;
    /** Internals type (WIRE_MESH, VANE_PACK, AXIAL_CYCLONE, PLATE_PACK). */
    public String internalsType;
    /** Vendor name. */
    public String vendorName;
    /** Product family or model designation. */
    public String productFamily;
    /** Test standard used (e.g., "EN 13544 / ISO 29042"). */
    public String testStandard;
    /** Test fluid (e.g., "Air-Water", "N2-Exxsol"). */
    public String testFluid;
    /** Test pressure [bar]. */
    public double testPressure_bar;
    /** Test temperature [degC]. */
    public double testTemperature_C;
    /** Measured droplet diameters [um]. */
    public double[] diameterPoints_um;
    /** Measured efficiencies [0-1] corresponding to diameterPoints_um. */
    public double[] efficiencyPoints;
    /** Maximum K-factor at test conditions [m/s]. */
    public double maxKFactor;
    /** Date of factory acceptance test. */
    public String testDate;
    /** Certificate or report reference number. */
    public String certificateRef;
    /** Additional notes. */
    public String notes;

    /**
     * Creates a custom GradeEfficiencyCurve from the measured points using linear interpolation.
     *
     * @return grade efficiency curve interpolating measured vendor data
     */
    public GradeEfficiencyCurve toGradeEfficiencyCurve() {
      if (diameterPoints_um == null || efficiencyPoints == null
          || diameterPoints_um.length < 2 || diameterPoints_um.length != efficiencyPoints.length) {
        // Fall back to a generic sigmoid if data is incomplete
        return GradeEfficiencyCurve.wireMeshDefault();
      }
      double[] diametersM = new double[diameterPoints_um.length];
      for (int i = 0; i < diameterPoints_um.length; i++) {
        diametersM[i] = diameterPoints_um[i] * 1e-6;
      }
      return GradeEfficiencyCurve.custom(diametersM, efficiencyPoints);
    }
  }

  /**
   * Private constructor — use getInstance().
   */
  private SeparatorInternalsDatabase() {
    loadData();
  }

  /**
   * Gets the singleton instance, loading data from CSV on first access.
   *
   * @return database instance
   */
  public static SeparatorInternalsDatabase getInstance() {
    if (instance == null) {
      synchronized (SeparatorInternalsDatabase.class) {
        if (instance == null) {
          instance = new SeparatorInternalsDatabase();
        }
      }
    }
    return instance;
  }

  /**
   * Loads data from CSV files in resources/designdata/.
   */
  private void loadData() {
    if (loaded) {
      return;
    }

    loadInternalsData();
    loadInletDeviceData();
    loadVendorCurveData();
    loaded = true;
  }

  /**
   * Loads separator internals data from SeparatorInternals.csv.
   */
  private void loadInternalsData() {
    try {
      InputStream is = getClass().getResourceAsStream("/designdata/SeparatorInternals.csv");
      if (is == null) {
        logger.warn("SeparatorInternals.csv not found in resources, using defaults");
        createDefaultInternalsRecords();
        return;
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      String header = reader.readLine(); // Skip header
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        String[] parts = line.split(",", -1);
        if (parts.length < 15) {
          continue;
        }
        InternalsRecord rec = new InternalsRecord();
        rec.internalsType = parts[0].trim();
        rec.subType = parts[1].trim();
        rec.manufacturer = parts[2].trim();
        rec.d50_um = parseDouble(parts[3]);
        rec.sharpness = parseDouble(parts[4]);
        rec.maxEfficiency = parseDouble(parts[5]);
        rec.maxKFactor = parseDouble(parts[6]);
        rec.minKFactor = parseDouble(parts[7]);
        rec.pressureDrop_mbar = parseDouble(parts[8]);
        rec.designStandard = parts[9].trim();
        rec.material = parts[10].trim();
        rec.maxTemperature_C = parseDouble(parts[11]);
        rec.minThickness_mm = parseDouble(parts[12]);
        rec.weight_kg_m2 = parseDouble(parts[13]);
        rec.reference = parts[14].trim();
        internalsRecords.add(rec);
      }
      reader.close();
    } catch (Exception ex) {
      logger.error("Error loading SeparatorInternals.csv: " + ex.getMessage(), ex);
      createDefaultInternalsRecords();
    }
  }

  /**
   * Loads inlet device data from SeparatorInletDevices.csv.
   */
  private void loadInletDeviceData() {
    try {
      InputStream is = getClass().getResourceAsStream("/designdata/SeparatorInletDevices.csv");
      if (is == null) {
        logger.warn("SeparatorInletDevices.csv not found in resources, using defaults");
        createDefaultInletDeviceRecords();
        return;
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      String header = reader.readLine(); // Skip header
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        String[] parts = line.split(",", -1);
        if (parts.length < 10) {
          continue;
        }
        InletDeviceRecord rec = new InletDeviceRecord();
        rec.deviceType = parts[0].trim();
        rec.subType = parts[1].trim();
        rec.minMomentum_Pa = parseDouble(parts[2]);
        rec.maxMomentum_Pa = parseDouble(parts[3]);
        rec.typicalBulkEfficiency = parseDouble(parts[4]);
        rec.dsdMultiplier = parseDouble(parts[5]);
        rec.pressureDropCoeff = parseDouble(parts[6]);
        rec.maxCapacity = parseDouble(parts[7]);
        rec.material = parts[8].trim();
        rec.reference = parts[9].trim();
        inletDeviceRecords.add(rec);
      }
      reader.close();
    } catch (Exception ex) {
      logger.error("Error loading SeparatorInletDevices.csv: " + ex.getMessage(), ex);
      createDefaultInletDeviceRecords();
    }
  }

  /**
   * Loads vendor-certified grade efficiency curve data from SeparatorVendorCurves.csv.
   *
   * <p>
   * Each row contains measured efficiency points at specific droplet diameters from factory
   * acceptance testing. The diameter and efficiency arrays are semicolon-separated within their
   * respective CSV fields.
   * </p>
   */
  private void loadVendorCurveData() {
    try {
      InputStream is = getClass().getResourceAsStream("/designdata/SeparatorVendorCurves.csv");
      if (is == null) {
        logger.info("SeparatorVendorCurves.csv not found - vendor curves not loaded");
        return;
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      String header = reader.readLine(); // Skip header
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        String[] parts = line.split(",", -1);
        if (parts.length < 14) {
          continue;
        }
        VendorCurveRecord rec = new VendorCurveRecord();
        rec.curveId = parts[0].trim();
        rec.internalsType = parts[1].trim();
        rec.vendorName = parts[2].trim();
        rec.productFamily = parts[3].trim();
        rec.testStandard = parts[4].trim();
        rec.testFluid = parts[5].trim();
        rec.testPressure_bar = parseDouble(parts[6]);
        rec.testTemperature_C = parseDouble(parts[7]);
        rec.diameterPoints_um = parseSemicolonDoubles(parts[8]);
        rec.efficiencyPoints = parseSemicolonDoubles(parts[9]);
        rec.maxKFactor = parseDouble(parts[10]);
        rec.testDate = parts[11].trim();
        rec.certificateRef = parts[12].trim();
        rec.notes = parts.length > 13 ? parts[13].trim() : "";

        if (rec.diameterPoints_um != null && rec.efficiencyPoints != null
            && rec.diameterPoints_um.length >= 2
            && rec.diameterPoints_um.length == rec.efficiencyPoints.length) {
          vendorCurveRecords.add(rec);
        } else {
          logger.warn("Skipping vendor curve " + rec.curveId + ": invalid data point arrays");
        }
      }
      reader.close();
    } catch (Exception ex) {
      logger.error("Error loading SeparatorVendorCurves.csv: " + ex.getMessage(), ex);
    }
  }

  /**
   * Creates default internals records when CSV is not available.
   */
  private void createDefaultInternalsRecords() {
    InternalsRecord wm = new InternalsRecord();
    wm.internalsType = "WIRE_MESH";
    wm.subType = "Standard Knitted";
    wm.d50_um = 8.0;
    wm.sharpness = 2.5;
    wm.maxEfficiency = 0.998;
    wm.maxKFactor = 0.107;
    wm.reference = "Brunazzi and Paglianti (1998)";
    internalsRecords.add(wm);

    InternalsRecord vp = new InternalsRecord();
    vp.internalsType = "VANE_PACK";
    vp.subType = "Double Pocket";
    vp.d50_um = 10.0;
    vp.sharpness = 2.3;
    vp.maxEfficiency = 0.995;
    vp.maxKFactor = 0.12;
    vp.reference = "Phillips and Listak (1996)";
    internalsRecords.add(vp);

    InternalsRecord ac = new InternalsRecord();
    ac.internalsType = "AXIAL_CYCLONE";
    ac.subType = "Standard Tube";
    ac.d50_um = 3.0;
    ac.sharpness = 4.0;
    ac.maxEfficiency = 0.998;
    ac.maxKFactor = 0.30;
    ac.reference = "Hoffmann and Stein (2008)";
    internalsRecords.add(ac);
  }

  /**
   * Creates default inlet device records when CSV is not available.
   */
  private void createDefaultInletDeviceRecords() {
    InletDeviceRecord iv = new InletDeviceRecord();
    iv.deviceType = "INLET_VANE";
    iv.subType = "Single Row";
    iv.maxMomentum_Pa = 6000;
    iv.typicalBulkEfficiency = 0.80;
    iv.dsdMultiplier = 0.55;
    iv.pressureDropCoeff = 1.5;
    iv.reference = "Verlaan (2001)";
    inletDeviceRecords.add(iv);
  }

  /**
   * Finds all internals records matching the specified type.
   *
   * @param type internals type (e.g., "WIRE_MESH", "VANE_PACK")
   * @return list of matching records
   */
  public List<InternalsRecord> findByType(String type) {
    List<InternalsRecord> results = new ArrayList<InternalsRecord>();
    for (InternalsRecord rec : internalsRecords) {
      if (rec.internalsType.equalsIgnoreCase(type)) {
        results.add(rec);
      }
    }
    return results;
  }

  /**
   * Finds a specific internals record by type and subtype.
   *
   * @param type internals type
   * @param subType subtype description
   * @return matching record or null
   */
  public InternalsRecord findByTypeAndSubType(String type, String subType) {
    for (InternalsRecord rec : internalsRecords) {
      if (rec.internalsType.equalsIgnoreCase(type) && rec.subType.equalsIgnoreCase(subType)) {
        return rec;
      }
    }
    return null;
  }

  /**
   * Finds all inlet device records matching the specified type.
   *
   * @param type device type (e.g., "INLET_VANE", "INLET_CYCLONE")
   * @return list of matching records
   */
  public List<InletDeviceRecord> findInletDeviceByType(String type) {
    List<InletDeviceRecord> results = new ArrayList<InletDeviceRecord>();
    for (InletDeviceRecord rec : inletDeviceRecords) {
      if (rec.deviceType.equalsIgnoreCase(type)) {
        results.add(rec);
      }
    }
    return results;
  }

  /**
   * Gets all internals records.
   *
   * @return all records
   */
  public List<InternalsRecord> getAllInternals() {
    return new ArrayList<InternalsRecord>(internalsRecords);
  }

  /**
   * Gets all inlet device records.
   *
   * @return all records
   */
  public List<InletDeviceRecord> getAllInletDevices() {
    return new ArrayList<InletDeviceRecord>(inletDeviceRecords);
  }

  /**
   * Gets all vendor curve records.
   *
   * @return all vendor curve records
   */
  public List<VendorCurveRecord> getAllVendorCurves() {
    return new ArrayList<VendorCurveRecord>(vendorCurveRecords);
  }

  /**
   * Finds vendor curve records matching the specified internals type.
   *
   * @param internalsType type (e.g., "WIRE_MESH", "VANE_PACK", "AXIAL_CYCLONE")
   * @return list of matching records
   */
  public List<VendorCurveRecord> findVendorCurvesByType(String internalsType) {
    List<VendorCurveRecord> results = new ArrayList<VendorCurveRecord>();
    for (VendorCurveRecord rec : vendorCurveRecords) {
      if (rec.internalsType.equalsIgnoreCase(internalsType)) {
        results.add(rec);
      }
    }
    return results;
  }

  /**
   * Finds vendor curve records from a specific vendor.
   *
   * @param vendorName vendor name (case-insensitive)
   * @return list of matching records
   */
  public List<VendorCurveRecord> findVendorCurvesByVendor(String vendorName) {
    List<VendorCurveRecord> results = new ArrayList<VendorCurveRecord>();
    for (VendorCurveRecord rec : vendorCurveRecords) {
      if (rec.vendorName.equalsIgnoreCase(vendorName)) {
        results.add(rec);
      }
    }
    return results;
  }

  /**
   * Finds a vendor curve record by its unique curve ID.
   *
   * @param curveId curve identifier (e.g., "VC001")
   * @return matching record or null
   */
  public VendorCurveRecord findVendorCurveById(String curveId) {
    for (VendorCurveRecord rec : vendorCurveRecords) {
      if (rec.curveId.equalsIgnoreCase(curveId)) {
        return rec;
      }
    }
    return null;
  }

  /**
   * Finds vendor curve records matching internals type and vendor.
   *
   * @param internalsType type (e.g., "WIRE_MESH")
   * @param vendorName vendor name
   * @return list of matching records
   */
  public List<VendorCurveRecord> findVendorCurvesByTypeAndVendor(String internalsType,
      String vendorName) {
    List<VendorCurveRecord> results = new ArrayList<VendorCurveRecord>();
    for (VendorCurveRecord rec : vendorCurveRecords) {
      if (rec.internalsType.equalsIgnoreCase(internalsType)
          && rec.vendorName.equalsIgnoreCase(vendorName)) {
        results.add(rec);
      }
    }
    return results;
  }

  /**
   * Returns a JSON catalog of all available separator internals.
   *
   * @return JSON string
   */
  public String toCatalogJson() {
    Map<String, Object> catalog = new LinkedHashMap<String, Object>();

    List<Map<String, Object>> internalsList = new ArrayList<Map<String, Object>>();
    for (InternalsRecord rec : internalsRecords) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("type", rec.internalsType);
      item.put("subType", rec.subType);
      item.put("d50_um", rec.d50_um);
      item.put("maxEfficiency", rec.maxEfficiency);
      item.put("maxKFactor_m_s", rec.maxKFactor);
      item.put("pressureDrop_mbar", rec.pressureDrop_mbar);
      item.put("material", rec.material);
      item.put("reference", rec.reference);
      internalsList.add(item);
    }
    catalog.put("internals", internalsList);

    List<Map<String, Object>> devicesList = new ArrayList<Map<String, Object>>();
    for (InletDeviceRecord rec : inletDeviceRecords) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("type", rec.deviceType);
      item.put("subType", rec.subType);
      item.put("maxMomentum_Pa", rec.maxMomentum_Pa);
      item.put("bulkEfficiency", rec.typicalBulkEfficiency);
      item.put("dsdMultiplier", rec.dsdMultiplier);
      item.put("material", rec.material);
      item.put("reference", rec.reference);
      devicesList.add(item);
    }
    catalog.put("inletDevices", devicesList);

    List<Map<String, Object>> vendorList = new ArrayList<Map<String, Object>>();
    for (VendorCurveRecord rec : vendorCurveRecords) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("curveId", rec.curveId);
      item.put("internalsType", rec.internalsType);
      item.put("vendorName", rec.vendorName);
      item.put("productFamily", rec.productFamily);
      item.put("testStandard", rec.testStandard);
      item.put("testFluid", rec.testFluid);
      item.put("testPressure_bar", rec.testPressure_bar);
      item.put("testTemperature_C", rec.testTemperature_C);
      item.put("dataPoints", rec.diameterPoints_um != null ? rec.diameterPoints_um.length : 0);
      item.put("maxKFactor_m_s", rec.maxKFactor);
      item.put("testDate", rec.testDate);
      item.put("certificateRef", rec.certificateRef);
      vendorList.add(item);
    }
    catalog.put("vendorCurves", vendorList);

    return new GsonBuilder().setPrettyPrinting().create().toJson(catalog);
  }

  /**
   * Parses a double from string, returning 0 for empty or invalid.
   *
   * @param s string to parse
   * @return parsed value or 0
   */
  private static double parseDouble(String s) {
    if (s == null || s.trim().isEmpty()) {
      return 0.0;
    }
    try {
      return Double.parseDouble(s.trim());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  /**
   * Parses a semicolon-separated list of doubles (e.g., "1.0;2.5;5.0;10.0").
   *
   * @param s semicolon-separated double values
   * @return array of parsed doubles, or null if input is empty
   */
  private static double[] parseSemicolonDoubles(String s) {
    if (s == null || s.trim().isEmpty()) {
      return null;
    }
    String[] tokens = s.trim().split(";");
    List<Double> values = new ArrayList<Double>();
    for (String token : tokens) {
      String t = token.trim();
      if (!t.isEmpty()) {
        try {
          values.add(Double.parseDouble(t));
        } catch (NumberFormatException e) {
          // skip invalid tokens
        }
      }
    }
    if (values.isEmpty()) {
      return null;
    }
    double[] result = new double[values.size()];
    for (int i = 0; i < values.size(); i++) {
      result[i] = values.get(i);
    }
    return result;
  }
}
