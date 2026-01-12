package space.controlnet.chatae.core.terminal;

import java.util.List;
import java.util.Optional;

/**
 * Shared DTOs for AE2 terminal interactions (block + part).
 */
public final class AiTerminalData {
    private AiTerminalData() {
    }

    public record Ae2Entry(String itemId, long amount, boolean craftable) {
    }

    public record Ae2ListResult(List<Ae2Entry> results, Optional<String> nextPageToken, Optional<String> error) {
        public Ae2ListResult {
            results = List.copyOf(results);
            nextPageToken = nextPageToken == null ? Optional.empty() : nextPageToken;
            error = error == null ? Optional.empty() : error;
        }
    }

    public record Ae2PlanItem(String itemId, long amount) {
    }

    public record Ae2CraftSimulation(
            String jobId,
            String status,
            List<Ae2PlanItem> missingItems,
            Optional<String> error) {
        public Ae2CraftSimulation {
            missingItems = List.copyOf(missingItems);
            error = error == null ? Optional.empty() : error;
        }
    }

    public record Ae2CraftRequest(String jobId, String status, Optional<String> error) {
        public Ae2CraftRequest {
            error = error == null ? Optional.empty() : error;
        }
    }

    public record Ae2JobStatus(
            String jobId,
            String status,
            List<Ae2PlanItem> missingItems,
            Optional<String> error) {
        public Ae2JobStatus {
            missingItems = List.copyOf(missingItems);
            error = error == null ? Optional.empty() : error;
        }
    }
}
