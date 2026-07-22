package neqsim.process.allocation;

/**
 * Custody product category used to aggregate per-component allocations into sales/export product groups (gas,
 * oil/condensate, water).
 *
 * <p>
 * A custody outlet (export gas, stabilised oil, produced water, ...) is tagged with one {@code ProductType} so that
 * {@link ProductionAllocationResult} can report per-source contributions to each marketable product, in addition to the
 * raw per-stream and per-component numbers.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public enum ProductType {
  /** Export / sales gas (predominantly vapour hydrocarbons). */
  GAS,

  /** Stabilised oil or condensate (predominantly liquid hydrocarbons). */
  OIL,

  /** Produced or treated water (predominantly aqueous phase). */
  WATER,

  /** Mixed multiphase custody stream that does not map to a single product group. */
  MIXED,

  /** Product category not classified. */
  UNKNOWN
}
