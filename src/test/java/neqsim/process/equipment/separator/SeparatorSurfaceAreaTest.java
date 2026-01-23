package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Geometry tests for separator wetted/unwetted surface area helpers.
 */
public class SeparatorSurfaceAreaTest {
  @Test
  public void testHorizontalWettedAreaAtHalfLevel() {
    Separator separator = new Separator("AreaTest");
    separator.setInternalDiameter(1.0); // 1 m ID -> radius 0.5 m
    separator.setSeparatorLength(5.0);
    separator.setLiquidLevel(0.5); // 50% level fraction

    double wettedArea = separator.getWettedArea();
    double unwettedArea = separator.getUnwettedArea();
    double totalArea = separator.getInnerSurfaceArea();

    double expectedTotalArea = 2.0 * Math.PI * 0.5 * 5.0 + 2.0 * Math.PI * Math.pow(0.5, 2);
    double expectedWettedArea = 0.5 * expectedTotalArea;

    assertEquals(expectedTotalArea, totalArea, 1.0e-6);
    assertEquals(expectedWettedArea, wettedArea, 1.0e-6);
    assertEquals(expectedWettedArea, unwettedArea, 1.0e-6);
    assertEquals(totalArea, wettedArea + unwettedArea, 1.0e-6);
  }
}

