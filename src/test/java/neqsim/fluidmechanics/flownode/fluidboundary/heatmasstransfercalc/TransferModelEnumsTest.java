package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MassTransferModel, InterfacialAreaModel, and WallHeatTransferModel enums.
 */
public class TransferModelEnumsTest {

  @Test
  void testMassTransferModelEnumValues() {
    // Verify all enum values exist
    assertEquals(3, MassTransferModel.values().length);
    assertNotNull(MassTransferModel.KRISHNA_STANDART_FILM);
    assertNotNull(MassTransferModel.PENETRATION_THEORY);
    assertNotNull(MassTransferModel.SURFACE_RENEWAL);
  }

  @Test
  void testMassTransferModelDisplayNames() {
    assertEquals("Krishna-Standart Film Model",
        MassTransferModel.KRISHNA_STANDART_FILM.getDisplayName());
    assertEquals("Penetration Theory", MassTransferModel.PENETRATION_THEORY.getDisplayName());
    assertEquals("Surface Renewal Theory", MassTransferModel.SURFACE_RENEWAL.getDisplayName());
  }

  @Test
  void testMassTransferModelDescriptions() {
    assertNotNull(MassTransferModel.KRISHNA_STANDART_FILM.getDescription());
    assertNotNull(MassTransferModel.PENETRATION_THEORY.getDescription());
    assertNotNull(MassTransferModel.SURFACE_RENEWAL.getDescription());
  }

  @Test
  void testInterfacialAreaModelEnumValues() {
    assertEquals(3, InterfacialAreaModel.values().length);
    assertNotNull(InterfacialAreaModel.GEOMETRIC);
    assertNotNull(InterfacialAreaModel.EMPIRICAL_CORRELATION);
    assertNotNull(InterfacialAreaModel.USER_DEFINED);
  }

  @Test
  void testInterfacialAreaModelDisplayNames() {
    assertEquals("Geometric Model", InterfacialAreaModel.GEOMETRIC.getDisplayName());
    assertEquals("Empirical Correlation",
        InterfacialAreaModel.EMPIRICAL_CORRELATION.getDisplayName());
    assertEquals("User Defined", InterfacialAreaModel.USER_DEFINED.getDisplayName());
  }

  @Test
  void testWallHeatTransferModelEnumValues() {
    assertEquals(4, WallHeatTransferModel.values().length);
    assertNotNull(WallHeatTransferModel.CONSTANT_WALL_TEMPERATURE);
    assertNotNull(WallHeatTransferModel.CONSTANT_HEAT_FLUX);
    assertNotNull(WallHeatTransferModel.CONVECTIVE_BOUNDARY);
    assertNotNull(WallHeatTransferModel.ADIABATIC);
  }

  @Test
  void testWallHeatTransferModelDisplayNames() {
    assertEquals("Constant Wall Temperature",
        WallHeatTransferModel.CONSTANT_WALL_TEMPERATURE.getDisplayName());
    assertEquals("Constant Heat Flux", WallHeatTransferModel.CONSTANT_HEAT_FLUX.getDisplayName());
    assertEquals("Convective Boundary", WallHeatTransferModel.CONVECTIVE_BOUNDARY.getDisplayName());
    assertEquals("Adiabatic", WallHeatTransferModel.ADIABATIC.getDisplayName());
  }

  @Test
  void testWallHeatTransferModelDescriptions() {
    assertNotNull(WallHeatTransferModel.CONSTANT_WALL_TEMPERATURE.getDescription());
    assertNotNull(WallHeatTransferModel.CONSTANT_HEAT_FLUX.getDescription());
    assertNotNull(WallHeatTransferModel.CONVECTIVE_BOUNDARY.getDescription());
    assertNotNull(WallHeatTransferModel.ADIABATIC.getDescription());
  }

  @Test
  void testEnumToString() {
    assertEquals("Krishna-Standart Film Model", MassTransferModel.KRISHNA_STANDART_FILM.toString());
    assertEquals("Geometric Model", InterfacialAreaModel.GEOMETRIC.toString());
    assertEquals("Adiabatic", WallHeatTransferModel.ADIABATIC.toString());
  }
}
