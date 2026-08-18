package neqsim.process.processmodel.dexpi;

/**
 * Immutable options for the opt-in native DEXPI 2.0 Plant export path.
 *
 * <p>
 * The default keeps the metadata-only output introduced for controlled provenance. Boundary connectors are emitted
 * only when explicitly requested, so the legacy metadata-free and metadata-only byte paths remain unchanged.
 * </p>
 */
public final class Dexpi20PlantExportOptions {
  /** Supported handling for material streams that cross the exported process boundary. */
  public enum BoundaryConnectionMode {
    /** Preserve the existing writer output without synthesized boundary objects. */
    NONE,
    /** Connect every detected feed and product boundary through a directional off-page connector. */
    EXPLICIT_OFF_PAGE_CONNECTORS
  }

  private final Dexpi20PlantExportMetadata metadata;
  private final BoundaryConnectionMode boundaryConnectionMode;

  private Dexpi20PlantExportOptions(Builder builder) {
    metadata = builder.metadata;
    boundaryConnectionMode = builder.boundaryConnectionMode;
  }

  /**
   * Starts an export-options definition using controlled DEXPI Plant metadata.
   *
   * @param metadata caller-supplied export provenance and plant identity
   * @return options builder
   */
  public static Builder builder(Dexpi20PlantExportMetadata metadata) {
    return new Builder(metadata);
  }

  /** @return controlled export metadata */
  public Dexpi20PlantExportMetadata getMetadata() {
    return metadata;
  }

  /** @return selected process-boundary handling */
  public BoundaryConnectionMode getBoundaryConnectionMode() {
    return boundaryConnectionMode;
  }

  /** Builder for {@link Dexpi20PlantExportOptions}. */
  public static final class Builder {
    private final Dexpi20PlantExportMetadata metadata;
    private BoundaryConnectionMode boundaryConnectionMode = BoundaryConnectionMode.NONE;

    private Builder(Dexpi20PlantExportMetadata metadata) {
      if (metadata == null) {
        throw new IllegalArgumentException("metadata must not be null");
      }
      this.metadata = metadata;
    }

    /**
     * Selects how feeds and products crossing the exported process boundary are represented.
     *
     * @param mode boundary handling; never {@code null}
     * @return this builder
     */
    public Builder boundaryConnectionMode(BoundaryConnectionMode mode) {
      if (mode == null) {
        throw new IllegalArgumentException("boundaryConnectionMode must not be null");
      }
      boundaryConnectionMode = mode;
      return this;
    }

    /**
     * Builds immutable export options.
     *
     * @return immutable options
     */
    public Dexpi20PlantExportOptions build() {
      return new Dexpi20PlantExportOptions(this);
    }
  }
}
