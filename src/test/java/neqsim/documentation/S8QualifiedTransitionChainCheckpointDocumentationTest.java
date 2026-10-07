package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Tests the S8 checkpoint-transition-chain checkpoint-transition-chain checkpoint documentation contract. */
class S8QualifiedTransitionChainCheckpointDocumentationTest {
  private static final Path DOCUMENT = Paths.get("docs", "chemicalreactions",
      "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint_manifest_transition_chain_transition_ledger_transition_chain_checkpoint_transition_chain_checkpoint_transition_chain_checkpoint.md");

  /** Verify the public contract, evidence boundary and executable example remain documented. */
  @Test
  void testDocumentationContract() throws IOException {
    String content = new String(Files.readAllBytes(DOCUMENT), StandardCharsets.UTF_8);

    assertTrue(content.contains("S8QualifiedTransitionChainCheckpoint"));
    assertTrue(content.contains("create(identifier, sequence, chain)"));
    assertTrue(content.contains("non-negative monotonically allocated sequence"));
    assertTrue(content.contains("constant time"));
    assertTrue(content.matches("(?s).*defensive\\s+copies.*"));
    assertTrue(content.contains("fail closed"));
    assertTrue(content.contains("no sulfur yield"));
    assertTrue(content.contains("no flash calculation"));
    assertTrue(content.contains("no stream, fluid, process, pipeline or injection mutation"));
    assertTrue(content.contains("#3318"));
  }
}
