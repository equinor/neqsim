package neqsim.process.allocation;

import java.io.Serializable;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * A tagged custody (sales / export / disposal) outlet stream that receives an allocated share of each production
 * source.
 *
 * <p>
 * Custody outlets are the terminal product streams of the process (export gas, stabilised oil, produced water, fuel
 * gas, ...). The allocation network reports, for every source and component, how much of that source ends up in each
 * custody outlet. Each outlet is tagged with a {@link ProductType} so contributions can also be summarised per
 * marketable product.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CustodyOutlet implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** User-facing name of the custody outlet. */
  private final String name;

  /** Terminal product stream of the process. */
  private final StreamInterface stream;

  /** Product category used for aggregation. */
  private final ProductType productType;

  /**
   * Creates a custody outlet.
   *
   * @param name the outlet name; must be non-null and unique within an allocation
   * @param stream the terminal product stream; must be non-null
   * @param productType the product category; must be non-null
   */
  public CustodyOutlet(String name, StreamInterface stream, ProductType productType) {
    this.name = name;
    this.stream = stream;
    this.productType = productType;
  }

  /**
   * Gets the outlet name.
   *
   * @return the outlet name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the terminal product stream.
   *
   * @return the stream
   */
  public StreamInterface getStream() {
    return stream;
  }

  /**
   * Gets the product category of this outlet.
   *
   * @return the product type
   */
  public ProductType getProductType() {
    return productType;
  }
}
