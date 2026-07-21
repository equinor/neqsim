package neqsim.process.engineering.calculation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Registry of standard-specific kernels exposed through the public engineering workflow. */
public final class EquipmentDesignKernelRegistry {
  /** Explicit implementation lookup status. */
  public enum Status {
    /** An executable kernel is registered. */
    IMPLEMENTED,
    /** No kernel has been adapted to the common API. */
    NOT_IMPLEMENTED
  }

  /** Immutable registry lookup that never represents missing support as an empty success. */
  public static final class Lookup {
    private final StandardType standardType;
    private final Status status;
    private final EquipmentDesignKernel<?, ?> kernel;

    private Lookup(StandardType standardType, Status status, EquipmentDesignKernel<?, ?> kernel) {
      this.standardType = standardType;
      this.status = status;
      this.kernel = kernel;
    }

    /** @return requested standard */
    public StandardType getStandardType() {
      return standardType;
    }

    /** @return explicit implementation status */
    public Status getStatus() {
      return status;
    }

    /** @return whether an executable kernel is registered */
    public boolean isImplemented() {
      return status == Status.IMPLEMENTED;
    }

    /**
     * Require the registered kernel.
     *
     * @return registered kernel
     * @throws IllegalStateException if the standard has no common kernel
     */
    public EquipmentDesignKernel<?, ?> requireKernel() {
      if (kernel == null) {
        throw new IllegalStateException("No equipment design kernel is implemented for " + standardType.getCode());
      }
      return kernel;
    }

    /** @return implementation class name, or {@code None} when not implemented */
    public String getImplementationClassName() {
      return kernel == null ? "None" : kernel.getClass().getSimpleName();
    }

    /** @return registered maturity, or {@link StandardSupportLevel#CATALOGUED} when absent */
    public StandardSupportLevel getMaturity() {
      return kernel == null ? StandardSupportLevel.CATALOGUED : kernel.maturity();
    }

    /**
     * Check whether the registered kernel implements an explicit edition basis.
     *
     * @param edition requested edition
     * @return {@code true} only when a kernel exists and supports the edition
     */
    public boolean supports(StandardEdition edition) {
      return kernel != null && kernel.supports(edition);
    }
  }

  private static final Map<StandardType, EquipmentDesignKernel<?, ?>> KERNELS;

  static {
    Map<StandardType, EquipmentDesignKernel<?, ?>> kernels = new EnumMap<StandardType, EquipmentDesignKernel<?, ?>>(
        StandardType.class);
    register(kernels, new Api617CompressorDesignKernel());
    register(kernels, new PumpApi610DesignKernel());
    register(kernels, new Api521ReliefDesignKernel());
    register(kernels, new Api526OrificeSelectionKernel());
    register(kernels, new Api12JSeparatorDesignKernel());
    KERNELS = Collections.unmodifiableMap(kernels);
  }

  private EquipmentDesignKernelRegistry() {
    // Utility class.
  }

  /**
   * Look up a standard-specific kernel.
   *
   * @param standardType standard to inspect
   * @return explicit implemented or not-implemented result
   */
  public static Lookup lookup(StandardType standardType) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }
    EquipmentDesignKernel<?, ?> kernel = KERNELS.get(standardType);
    return new Lookup(standardType, kernel == null ? Status.NOT_IMPLEMENTED : Status.IMPLEMENTED, kernel);
  }

  /**
   * List standards with an executable common design kernel.
   *
   * @return deterministic immutable standard set
   */
  public static Set<StandardType> getRegisteredStandards() {
    return Collections.unmodifiableSet(EnumSet.copyOf(KERNELS.keySet()));
  }

  private static void register(Map<StandardType, EquipmentDesignKernel<?, ?>> kernels,
      EquipmentDesignKernel<?, ?> kernel) {
    EquipmentDesignKernel<?, ?> previous = kernels.put(kernel.standard(), kernel);
    if (previous != null) {
      throw new IllegalStateException("Duplicate equipment design kernel for " + kernel.standard().getCode());
    }
  }
}
