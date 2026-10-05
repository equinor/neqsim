package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Tests the S8 checkpoint-transition-chain checkpoint-transition-chain documentation contract. */
class H2sOxygenS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpointTransitionChainDocumentationTest {
  private static final Path DOCUMENT = Paths.get("docs", "chemicalreactions",
      "h2s_oxygen_s8_stream_application_batch_reconciliation_checkpoint_manifest_transition_chain_transition_ledger_transition_chain_checkpoint_transition_chain_checkpoint_transition_chain.md");

  /** Verify adjacency, numerical, digest, and evidence boundaries remain explicit. */
  @Test
  void testDocumentationContract() throws IOException {
    String documentation = new String(Files.readAllBytes(DOCUMENT), StandardCharsets.UTF_8);

    assertTrue(documentation.startsWith("---\n"));
    assertTrue(documentation.contains("checkpoint-transition-chain checkpoint-transition-chain identifier"));
    assertTrue(documentation.contains("previous candidate\ncheckpoint sequence"));
    assertTrue(documentation.contains("Gaps, forks, reordering, duplicate receipt digests"));
    assertTrue(documentation.contains("overflow-detecting exact arithmetic"));
    assertTrue(
        documentation.contains("Receipt-state,\ntransition-state, and represented-entry-state families close exactly"));
    assertTrue(documentation.contains("versioned canonical SHA-256 digest"));
    assertTrue(documentation.contains("compared in constant time"));
    assertTrue(documentation.contains("allocates no identifier or checkpoint\nsequence"));
    assertTrue(documentation.contains("performs no flash calculation"));
    assertTrue(documentation.contains("no stream, fluid, process, pipeline, or injection mutation"));
  }
}
