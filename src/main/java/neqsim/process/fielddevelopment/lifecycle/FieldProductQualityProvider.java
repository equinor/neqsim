package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;

/** Supplies product-quality measurements from a detailed process, analyser set, or external model. */
public interface FieldProductQualityProvider extends Serializable {

  /**
   * Measures product and discharge quality at the current solved process operating point.
   *
   * @param model solved lifecycle model
   * @param specifications limits to evaluate
   * @return measured compliance result
   */
  ProductSpecificationResult evaluate(FieldLifecycleModel model, FieldProductSpecifications specifications);
}
