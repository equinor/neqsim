package neqsim.thermo.phase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable provenance manifest for repository-distributed binary volumetric Pitzer observations.
 *
 * <p>
 * The manifest binds every declared source lineage to its calibration or validation role, citation, license,
 * redistribution decision, file checksum, uncertainty basis and qualification, row count, and state envelope. It checks
 * metadata-to-observation consistency before repository-distributed observations are fitted or evaluated. It does not
 * prove experimental independence or calculate a checksum from source bytes.
 * </p>
 */
public final class PitzerBinaryVolumetricDatasetProvenance implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Intended use of one source lineage. */
  public enum DatasetRole {
    /** Observations may be supplied to the weighted regression. */
    CALIBRATION,
    /** Observations remain untouched until holdout evaluation. */
    VALIDATION
  }

  /** Audited permission to redistribute the represented rows. */
  public enum RedistributionStatus {
    /** Redistribution is explicitly permitted under the recorded license. */
    PERMITTED,
    /** Redistribution is explicitly restricted. */
    RESTRICTED,
    /** Redistribution permission has not been established. */
    UNKNOWN
  }

  /** Audited suitability of the uncertainty values used for weighted fitting or validation. */
  public enum UncertaintyQualification {
    /** Values are documented absolute one-sigma standard uncertainties for the represented rows. */
    QUALIFIED_ABSOLUTE_ONE_SIGMA,
    /** Values only repeat an instrument accuracy, precision, or repeatability specification. */
    INSTRUMENT_SPECIFICATION_ONLY,
    /** A defensible absolute one-sigma mapping has not been established. */
    UNRESOLVED
  }

  private final List<SourceRecord> sources;
  private final Map<String, SourceRecord> sourcesByGroup;

  /**
   * Construct an immutable manifest.
   *
   * @param sourceRecords one record for every represented laboratory or source lineage
   */
  public PitzerBinaryVolumetricDatasetProvenance(List<SourceRecord> sourceRecords) {
    if (sourceRecords == null || sourceRecords.isEmpty()) {
      throw new IllegalArgumentException("Volumetric Pitzer provenance sources must not be null or empty");
    }
    Map<String, SourceRecord> sortedSources = new TreeMap<String, SourceRecord>();
    for (SourceRecord source : sourceRecords) {
      if (source == null) {
        throw new IllegalArgumentException("Volumetric Pitzer provenance sources must not contain null");
      }
      if (sortedSources.put(source.getSourceGroup(), source) != null) {
        throw new IllegalArgumentException("Duplicate volumetric Pitzer source lineage: " + source.getSourceGroup());
      }
    }
    sourcesByGroup = Collections.unmodifiableMap(sortedSources);
    sources = Collections.unmodifiableList(new ArrayList<SourceRecord>(sortedSources.values()));
  }

  /**
   * Validate repository-distributed observations against one declared role.
   *
   * <p>
   * Every observation must have a declared source, permitted redistribution, explicitly qualified absolute one-sigma
   * uncertainty values, the declared role and molality range. The common temperature and pressure must lie within each
   * used source envelope, and the number of observations for each
   * source must exactly match its manifest record.
   * </p>
   *
   * @param observations observations to validate
   * @param role required dataset role
   * @param temperatureK common temperature in K
   * @param pressurePa common absolute pressure in Pa
   */
  public void validateRepositoryObservations(List<PitzerBinaryVolumetricRegression.Observation> observations,
      DatasetRole role, double temperatureK, double pressurePa) {
    if (observations == null || observations.isEmpty()) {
      throw new IllegalArgumentException("Repository volumetric Pitzer observations must not be null or empty");
    }
    if (role == null) {
      throw new IllegalArgumentException("Volumetric Pitzer dataset role must not be null");
    }
    requirePositive(temperatureK, "Dataset temperature");
    requirePositive(pressurePa, "Dataset pressure");

    Map<String, Integer> observedCounts = new TreeMap<String, Integer>();
    for (PitzerBinaryVolumetricRegression.Observation observation : observations) {
      if (observation == null) {
        throw new IllegalArgumentException("Repository volumetric Pitzer observations must not contain null");
      }
      SourceRecord source = sourcesByGroup.get(observation.getSourceGroup());
      if (source == null) {
        throw new IllegalArgumentException("Observation has no provenance record: " + observation.getSourceGroup());
      }
      if (source.getRole() != role) {
        throw new IllegalArgumentException("Observation source role mismatch for " + observation.getSourceGroup());
      }
      source.requireRepositoryAdmissible();
      if (!source.containsState(observation.getMolality(), temperatureK, pressurePa)) {
        throw new IllegalArgumentException(
            "Observation is outside the declared source envelope: " + observation.getSourceGroup());
      }
      Integer previousCount = observedCounts.get(observation.getSourceGroup());
      observedCounts.put(observation.getSourceGroup(), previousCount == null ? 1 : previousCount + 1);
    }

    for (SourceRecord source : sources) {
      if (source.getRole() == role) {
        Integer actualCount = observedCounts.get(source.getSourceGroup());
        int count = actualCount == null ? 0 : actualCount;
        if (count != source.getObservationCount()) {
          throw new IllegalArgumentException("Observation count mismatch for " + source.getSourceGroup() + ": expected "
              + source.getObservationCount() + " but found " + count);
        }
      }
    }
  }

  /** @return immutable source records sorted by source-lineage identifier */
  public List<SourceRecord> getSources() {
    return Collections.unmodifiableList(new ArrayList<SourceRecord>(sources));
  }

  /**
   * Return one source record.
   *
   * @param sourceGroup laboratory or source-lineage identifier
   * @return matching source record, or null when absent
   */
  public SourceRecord getSource(String sourceGroup) {
    if (sourceGroup == null) {
      return null;
    }
    return sourcesByGroup.get(sourceGroup.trim());
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    return value.trim();
  }

  private static String optionalText(String value) {
    return value == null ? "" : value.trim();
  }

  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void requireRange(double minimum, double maximum, double lowerBound, String name) {
    if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum < lowerBound || maximum < minimum) {
      throw new IllegalArgumentException(name + " range is invalid");
    }
  }

  /** Immutable provenance record for one laboratory or source lineage. */
  public static final class SourceRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String sourceGroup;
    private final DatasetRole role;
    private final String citation;
    private final String sourceUrl;
    private final String licenseId;
    private final String licenseUrl;
    private final RedistributionStatus redistributionStatus;
    private final String sha256;
    private final String uncertaintyBasis;
    private final UncertaintyQualification uncertaintyQualification;
    private final int observationCount;
    private final double minimumMolality;
    private final double maximumMolality;
    private final double minimumTemperatureK;
    private final double maximumTemperatureK;
    private final double minimumPressurePa;
    private final double maximumPressurePa;

    /**
     * Construct a source-lineage record without a machine-qualified uncertainty decision.
     *
     * @deprecated Repository validation rejects the unresolved uncertainty status assigned by this constructor. Use the
     *             constructor that accepts {@link UncertaintyQualification}.
     */
    @Deprecated
    public SourceRecord(String sourceGroup, DatasetRole role, String citation, String sourceUrl, String licenseId,
        String licenseUrl, RedistributionStatus redistributionStatus, String sha256, String uncertaintyBasis,
        int observationCount, double minimumMolality, double maximumMolality, double minimumTemperatureK,
        double maximumTemperatureK, double minimumPressurePa, double maximumPressurePa) {
      this(sourceGroup, role, citation, sourceUrl, licenseId, licenseUrl, redistributionStatus, sha256, uncertaintyBasis,
          UncertaintyQualification.UNRESOLVED, observationCount, minimumMolality, maximumMolality, minimumTemperatureK,
          maximumTemperatureK, minimumPressurePa, maximumPressurePa);
    }

    /**
     * Construct a source-lineage record.
     *
     * @param sourceGroup non-empty laboratory or source-lineage identifier
     * @param role calibration or validation role
     * @param citation full citation or DOI description
     * @param sourceUrl stable source URL
     * @param licenseId license identifier, or an explicit unknown/restricted marker
     * @param licenseUrl license URL; mandatory when redistribution is permitted
     * @param redistributionStatus audited redistribution decision
     * @param sha256 lowercase or uppercase SHA-256 of the distributed source file
     * @param uncertaintyBasis description of the absolute one-sigma uncertainty mapping
     * @param uncertaintyQualification audited suitability of that mapping for weighted repository use
     * @param observationCount exact number of represented observations
     * @param minimumMolality minimum formula-unit molality in mol/kg solvent
     * @param maximumMolality maximum formula-unit molality in mol/kg solvent
     * @param minimumTemperatureK minimum temperature in K
     * @param maximumTemperatureK maximum temperature in K
     * @param minimumPressurePa minimum absolute pressure in Pa
     * @param maximumPressurePa maximum absolute pressure in Pa
     */
    public SourceRecord(String sourceGroup, DatasetRole role, String citation, String sourceUrl, String licenseId,
        String licenseUrl, RedistributionStatus redistributionStatus, String sha256, String uncertaintyBasis,
        UncertaintyQualification uncertaintyQualification, int observationCount, double minimumMolality,
        double maximumMolality, double minimumTemperatureK, double maximumTemperatureK, double minimumPressurePa,
        double maximumPressurePa) {
      this.sourceGroup = requireText(sourceGroup, "Source group");
      if (role == null) {
        throw new IllegalArgumentException("Dataset role must not be null");
      }
      this.role = role;
      this.citation = requireText(citation, "Source citation");
      this.sourceUrl = requireText(sourceUrl, "Source URL");
      this.licenseId = requireText(licenseId, "License identifier");
      this.licenseUrl = optionalText(licenseUrl);
      if (redistributionStatus == null) {
        throw new IllegalArgumentException("Redistribution status must not be null");
      }
      this.redistributionStatus = redistributionStatus;
      String normalizedChecksum = requireText(sha256, "Source SHA-256").toLowerCase(Locale.ROOT);
      if (!normalizedChecksum.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("Source SHA-256 must contain exactly 64 hexadecimal characters");
      }
      this.sha256 = normalizedChecksum;
      this.uncertaintyBasis = requireText(uncertaintyBasis, "Uncertainty basis");
      if (uncertaintyQualification == null) {
        throw new IllegalArgumentException("Uncertainty qualification must not be null");
      }
      this.uncertaintyQualification = uncertaintyQualification;
      if (observationCount <= 0) {
        throw new IllegalArgumentException("Source observation count must be positive");
      }
      this.observationCount = observationCount;
      requireRange(minimumMolality, maximumMolality, 0.0, "Source molality");
      requireRange(minimumTemperatureK, maximumTemperatureK, Double.MIN_VALUE, "Source temperature");
      requireRange(minimumPressurePa, maximumPressurePa, Double.MIN_VALUE, "Source pressure");
      this.minimumMolality = minimumMolality;
      this.maximumMolality = maximumMolality;
      this.minimumTemperatureK = minimumTemperatureK;
      this.maximumTemperatureK = maximumTemperatureK;
      this.minimumPressurePa = minimumPressurePa;
      this.maximumPressurePa = maximumPressurePa;

      if (redistributionStatus == RedistributionStatus.PERMITTED && this.licenseUrl.isEmpty()) {
        throw new IllegalArgumentException("Permitted redistribution requires a non-empty license URL");
      }
    }

    private boolean containsState(double molality, double temperatureK, double pressurePa) {
      return molality >= minimumMolality && molality <= maximumMolality && temperatureK >= minimumTemperatureK
          && temperatureK <= maximumTemperatureK && pressurePa >= minimumPressurePa && pressurePa <= maximumPressurePa;
    }

    private void requireRepositoryAdmissible() {
      if (redistributionStatus != RedistributionStatus.PERMITTED) {
        throw new IllegalArgumentException("Repository redistribution is not permitted for " + sourceGroup);
      }
      if (uncertaintyQualification != UncertaintyQualification.QUALIFIED_ABSOLUTE_ONE_SIGMA) {
        throw new IllegalArgumentException("Repository uncertainty is not qualified as absolute one-sigma for "
            + sourceGroup + ": " + uncertaintyQualification);
      }
    }

    /** @return laboratory or source-lineage identifier */
    public String getSourceGroup() {
      return sourceGroup;
    }

    /** @return calibration or validation role */
    public DatasetRole getRole() {
      return role;
    }

    /** @return full citation or DOI description */
    public String getCitation() {
      return citation;
    }

    /** @return stable source URL */
    public String getSourceUrl() {
      return sourceUrl;
    }

    /** @return recorded license identifier */
    public String getLicenseId() {
      return licenseId;
    }

    /** @return recorded license URL, or an empty string */
    public String getLicenseUrl() {
      return licenseUrl;
    }

    /** @return audited redistribution status */
    public RedistributionStatus getRedistributionStatus() {
      return redistributionStatus;
    }

    /** @return normalized lowercase SHA-256 of the distributed source file */
    public String getSha256() {
      return sha256;
    }

    /** @return description of the absolute one-sigma uncertainty mapping */
    public String getUncertaintyBasis() {
      return uncertaintyBasis;
    }

    /** @return audited suitability of the uncertainty mapping for weighted repository use */
    public UncertaintyQualification getUncertaintyQualification() {
      return uncertaintyQualification;
    }

    /** @return exact number of represented observations */
    public int getObservationCount() {
      return observationCount;
    }

    /** @return minimum formula-unit molality in mol/kg solvent */
    public double getMinimumMolality() {
      return minimumMolality;
    }

    /** @return maximum formula-unit molality in mol/kg solvent */
    public double getMaximumMolality() {
      return maximumMolality;
    }

    /** @return minimum temperature in K */
    public double getMinimumTemperatureK() {
      return minimumTemperatureK;
    }

    /** @return maximum temperature in K */
    public double getMaximumTemperatureK() {
      return maximumTemperatureK;
    }

    /** @return minimum absolute pressure in Pa */
    public double getMinimumPressurePa() {
      return minimumPressurePa;
    }

    /** @return maximum absolute pressure in Pa */
    public double getMaximumPressurePa() {
      return maximumPressurePa;
    }
  }
}
