package neqsim.process.mechanicaldesign.data;

import java.util.Optional;
import neqsim.process.mechanicaldesign.DesignLimitData;

/**
 * Data source used to supply mechanical design limits for process equipment.
 */
public interface MechanicalDesignDataSource {
  /**
   * Retrieve design limit data for a given equipment type and company identifier.
   *
   * @param equipmentTypeName canonical equipment type identifier (e.g. "Pipeline").
   * @param companyIdentifier company specific design code identifier.
   * @return optional design limit data if available.
   */
  Optional<DesignLimitData> getDesignLimits(String equipmentTypeName, String companyIdentifier);
}
