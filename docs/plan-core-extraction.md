# Plan: Extract Platform-Neutral Code from common-1.20.1 to core

## Overview

This plan identifies code in `common-1.20.1` that can be moved to `core` to maximize code sharing. The `core` module must remain free of Minecraft, AE2, and Architectury dependencies.

## Analysis Summary

### Files That MUST Stay in common-1.20.1 (MC/AE2 dependent)

| File | Reason |
|------|--------|
| `ChatAE.java` | Uses MC Logger, Architectury events, MC server |
| `ChatAEClient.java` | Client initialization |
| `ChatAERegistries.java` | MC registries, Architectury DeferredRegister |
| `ChatAENetwork.java` | Architectury NetworkChannel, MC FriendlyByteBuf, ServerPlayer |
| `ChatAECommands.java` | Brigadier commands, MC CommandSourceStack |
| `RecipeIndexService.java` | MC RecipeManager, ItemStack, Ingredient |
| `RecipeIndexReloadListener.java` | MC PreparableReloadListener |
| `ChatAESessionsSavedData.java` | MC SavedData, CompoundTag, NBT |
| `AiTerminalMenu.java` | MC AbstractContainerMenu |
| `AiTerminalScreen.java` | MC GUI, GuiGraphics, EditBox, Button |
| `AiTerminalHost.java` | AE2 IActionHost, ICraftingRequester |
| `AiTerminalPart.java` | AE2 AbstractDisplayPart |
| `AiTerminalPartOperations.java` | AE2 IGrid, ICraftingPlan, AEItemKey |
| `AiTerminalPartModelIds.java` | MC ResourceLocation |
| `ChatAEPartRegistries.java` | Architectury DeferredRegister, AE2 PartItem |
| `ChatAEScreens.java` | Architectury MenuRegistry |
| `TerminalContextFactory.java` | MC ServerPlayer, BlockEntity, AE2 IPartHost |
| `TeamAccess.java` | Architectury Platform, MC ServerPlayer |
| `AuditLogger.java` | Uses ChatAE.LOGGER (MC Logger) |

### Files/Logic That CAN Be Moved to core

#### 1. PromptStore.java → Move to core
**Current location:** `common.llm.PromptStore`
**Analysis:** Pure Java with no MC dependencies. Uses only `PromptContext`, `PromptId` (already in core), and `ConcurrentHashMap`.
**Action:** Move entire file to `core.agent.PromptStore`

#### 2. PromptRuntime.java → Partial extraction
**Current location:** `common.llm.PromptRuntime`
**Analysis:**
- `render()` method uses `PromptStore` and `PromptTemplate` - can be in core
- `promptHash()` method is pure Java SHA-256 - can be in core
- `reload()` method needs MC server - must stay in common
**Action:**
- Create `core.agent.PromptResolver` with `resolve()` and `promptHash()` methods
- Keep `PromptRuntime` in common as thin wrapper that handles reload

#### 3. LlmConfigLoader.java → Partial extraction
**Current location:** `common.llm.LlmConfigLoader`
**Analysis:**
- JSON parsing logic is pure Java (Gson)
- File path resolution uses `Platform.getConfigFolder()` (Architectury)
- Config writing uses `ChatAE.LOGGER`
**Action:**
- Create `core.agent.LlmConfigParser` with pure JSON parsing logic
- Keep file I/O and path resolution in common

#### 4. ChatAENetwork.java → Extract helper methods
**Current location:** `common.ChatAENetwork`
**Analysis:** Several pure utility methods can be extracted:
- `resolveEffectiveLocale(clientLocale, aiLocaleOverride)` - pure string logic
- `ITEM_TAG_PATTERN` and item tag validation logic (minus MC registry check)
**Action:**
- Create `core.util.LocaleResolver` with `resolveEffectiveLocale()`
- Create `core.util.ItemTagParser` with pattern and parsing (validation stays in common)

#### 5. ToolRouter.java → Partial extraction
**Current location:** `common.tools.ToolRouter`
**Analysis:**
- Tool dispatch logic is mostly pure Java
- Uses `TerminalContext` (already in core)
- Uses `ChatAE.LOGGER` for error logging
- Uses `ChatAE.RECIPE_INDEX` for recipe tools
**Action:**
- Create `core.tools.ToolDispatcher` interface with `dispatch(toolName, argsJson)` method
- Create `core.tools.ToolExecutor` with the switch logic, taking dependencies as constructor params
- Keep `ToolRouter` in common as implementation that provides MC-specific dependencies

#### 6. AgentRunner.java → Partial extraction
**Current location:** `common.agent.AgentRunner`
**Analysis:**
- Uses LangGraph4j (allowed in core per AGENTS.md)
- Uses `ServerPlayer` for player context
- Uses `ChatAENetwork.SESSIONS` for session access
- Uses `PromptRuntime.render()` for prompts
- Uses `TerminalContextFactory` for terminal resolution
**Action:**
- Create `core.agent.AgentGraph` with the graph definition and node logic
- Create `core.agent.AgentContext` interface for player/session/terminal abstraction
- Keep `AgentRunner` in common as the MC-specific implementation

#### 7. Session Serialization Logic
**Current location:** `ChatAESessionsSavedData.java`
**Analysis:**
- NBT serialization is MC-specific
- But the serialization structure/schema could be abstracted
**Action:**
- Create `core.session.SessionSerializer` interface
- Create `core.session.SessionSerializationSchema` with field names/structure
- Keep NBT implementation in common

---

## Detailed Implementation Plan

### Phase 1: Pure Utility Extractions (Low Risk)

#### 1.1 Create `core.util.LocaleResolver`
```java
package space.controlnet.chatae.core.util;

public final class LocaleResolver {
    public static String resolveEffectiveLocale(String clientLocale, String aiLocaleOverride) {
        String override = aiLocaleOverride == null ? "" : aiLocaleOverride.trim();
        if (!override.isBlank()) {
            return override;
        }
        String locale = clientLocale == null ? "" : clientLocale.trim();
        return locale.isBlank() ? "en_us" : locale;
    }
}
```
**Update:** `ChatAENetwork.resolveEffectiveLocale()` → delegate to `LocaleResolver`

#### 1.2 Create `core.util.ItemTagParser`
```java
package space.controlnet.chatae.core.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Predicate;

public final class ItemTagParser {
    public static final Pattern ITEM_TAG_PATTERN =
        Pattern.compile("<item\\s+id=\"([^\"]+)\"(?:\\s+display_name=\"([^\"]+)\")?\\s*>");

    public static Optional<String> findInvalidItemTag(String text, Predicate<String> itemIdValidator) {
        // Pure parsing logic, validation delegated to predicate
    }

    public static String extractAttribute(String tag, String attr) {
        // Pure string parsing
    }
}
```
**Update:** `ChatAENetwork.findInvalidItemTag()` → use `ItemTagParser` with MC validator

### Phase 2: Move PromptStore to core

#### 2.1 Move `PromptStore.java` to `core.agent`
- File is already pure Java
- Just move and update imports

#### 2.2 Create `core.agent.PromptResolver`
```java
package space.controlnet.chatae.core.agent;

public final class PromptResolver {
    private final PromptStore store;

    public String resolve(PromptId promptId, String locale, Map<String, String> variables) {
        String template = store.resolve(new PromptContext(promptId, locale, variables));
        return PromptTemplate.render(template, variables);
    }

    public static Optional<String> computeHash(String prompt) {
        // SHA-256 logic from PromptRuntime.promptHash()
    }
}
```
**Update:** `PromptRuntime` becomes thin wrapper calling `PromptResolver`

### Phase 3: Extract LLM Config Parsing

#### 3.1 Create `core.agent.LlmConfigParser`
```java
package space.controlnet.chatae.core.agent;

public final class LlmConfigParser {
    public static LlmConfig parse(String json, LlmConfig defaults) {
        // All the JSON parsing logic from LlmConfigLoader
    }

    public static String toJson(LlmConfig config) {
        // Serialization logic
    }
}
```
**Update:** `LlmConfigLoader` handles file I/O, delegates parsing to `LlmConfigParser`

### Phase 4: Tool Execution Abstraction

#### 4.1 Create `core.tools.ToolExecutionContext`
```java
package space.controlnet.chatae.core.tools;

public interface ToolExecutionContext {
    Optional<TerminalContext> getTerminal();
    RecipeSearchResult searchRecipes(String query, RecipeSearchFilters filters, Optional<String> pageToken, int limit);
    Optional<RecipeSummary> getRecipe(String recipeId);
    boolean isRecipeIndexReady();
    void logError(String message, Throwable error);
}
```

#### 4.2 Create `core.tools.ToolExecutor`
```java
package space.controlnet.chatae.core.tools;

public final class ToolExecutor {
    public static ToolOutcome execute(ToolExecutionContext ctx, ToolCall call, boolean approved) {
        // All the switch logic from ToolRouter.execute()
        // Uses ctx for MC-specific operations
    }
}
```
**Update:** `ToolRouter` implements `ToolExecutionContext` and delegates to `ToolExecutor`

### Phase 5: Agent Graph Abstraction (Complex)

#### 5.1 Create `core.agent.AgentSessionContext`
```java
package space.controlnet.chatae.core.agent;

public interface AgentSessionContext {
    UUID getPlayerId();
    UUID getSessionId();
    String getEffectiveLocale();
    TerminalBinding getBinding();
    List<ChatMessage> getSessionMessages();
    void appendMessage(ChatMessage message);
    Optional<TerminalContext> getTerminal();
}
```

#### 5.2 Create `core.agent.AgentGraphBuilder`
```java
package space.controlnet.chatae.core.agent;

public final class AgentGraphBuilder {
    // Graph definition logic extracted from AgentRunner
    // Node actions defined as functional interfaces
}
```

This is more complex and may not be worth the effort given LangGraph4j's tight integration.

---

## Summary of Changes

### New Files in core (8 files)
1. `core/util/LocaleResolver.java` - Locale resolution logic
2. `core/util/ItemTagParser.java` - Item tag parsing
3. `core/agent/PromptStore.java` - Moved from common
4. `core/agent/PromptResolver.java` - Prompt resolution with hash
5. `core/agent/LlmConfigParser.java` - JSON config parsing
6. `core/tools/ToolExecutionContext.java` - Tool execution abstraction
7. `core/tools/ToolExecutor.java` - Tool dispatch logic
8. `core/session/SessionSerializationSchema.java` - Field name constants (optional)

### Modified Files in common (6 files)
1. `ChatAENetwork.java` - Use LocaleResolver, ItemTagParser
2. `PromptRuntime.java` - Delegate to PromptResolver
3. `LlmConfigLoader.java` - Delegate to LlmConfigParser
4. `ToolRouter.java` - Implement ToolExecutionContext, delegate to ToolExecutor
5. `AgentRunner.java` - Minor refactoring (optional)
6. `AuditLogger.java` - No change needed (already implements core interface)

### Estimated Impact
- **Lines moved to core:** ~300-400 lines
- **Core module growth:** From ~770 lines to ~1100-1200 lines (~40% increase)
- **Common module reduction:** From ~1660 lines to ~1400 lines (~15% reduction)

---

## Recommended Priority

1. **High Priority (Easy wins)**
   - LocaleResolver (5 min)
   - ItemTagParser (15 min)
   - Move PromptStore (5 min)
   - PromptResolver (20 min)

2. **Medium Priority (Moderate effort)**
   - LlmConfigParser (30 min)
   - ToolExecutor + ToolExecutionContext (45 min)

3. **Low Priority (Complex, may not be worth it)**
   - AgentGraph abstraction (2+ hours, tight LangGraph4j coupling)
   - Session serialization schema (minimal benefit)

---

## Notes

- The `AgentRunner` is tightly coupled to LangGraph4j and MC types (`ServerPlayer`). Full extraction would require significant abstraction layers that may not be worth the complexity.
- The `ChatAENetwork` file is the largest and most complex, but most of its logic is inherently MC-specific (networking, packet handling).
- The UI code (`AiTerminalScreen`) is entirely MC-specific and cannot be extracted.
- AE2 integration code (`AiTerminalPart`, `AiTerminalPartOperations`) is entirely AE2-specific.
