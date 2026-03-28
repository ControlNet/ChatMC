# ChatAE Architecture: Core ↔ Common Dependency Visualization

## Dependency Direction (Clean Architecture)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              LOADERS                                         │
│  ┌─────────────────────┐              ┌─────────────────────┐               │
│  │   forge-1.20.1      │              │   fabric-1.20.1     │               │
│  │   (1 file)          │              │   (2 files)         │               │
│  └──────────┬──────────┘              └──────────┬──────────┘               │
│             │                                    │                           │
│             └────────────────┬───────────────────┘                           │
│                              ▼                                               │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                        common-1.20.1                                   │  │
│  │                        (26 files, ~1660 lines)                         │  │
│  │   MC/AE2/Architectury dependent                                        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                              │                                               │
│                              │ imports (one-way)                             │
│                              ▼                                               │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                           core                                         │  │
│  │                        (62 files, ~770 lines)                          │  │
│  │   Pure Java (no MC/AE2 deps)                                           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘

✓ core has ZERO imports from common (verified)
✓ Dependency flows one direction: common → core
```

---

## Core Module Structure

```
core/
├── agent/                    # LLM/Agent orchestration
│   ├── AgentDecision         ◄─── AgentRunner
│   ├── AgentLoopResult       ◄─── AgentRunner, ChatAENetwork
│   ├── AgentReasoningService ◄─── AgentRunner
│   ├── ConversationHistoryBuilder ◄─── AgentRunner
│   ├── LangChainToolCallParser
│   ├── LlmConfig             ◄─── LlmConfigLoader, LlmRuntimeManager
│   ├── LlmModelFactory
│   ├── LlmProvider           ◄─── LlmConfigLoader
│   ├── LlmRateLimiter        ◄─── AgentRunner
│   ├── LlmRuntime            ◄─── LlmRuntimeManager
│   ├── Logger
│   ├── PromptContext         ◄─── PromptStore, PromptRuntime
│   ├── PromptId              ◄─── PromptStore, PromptFileManager, AgentRunner
│   ├── PromptTemplate        ◄─── PromptRuntime
│   ├── ReflectiveToolCallParser
│   ├── ToolCallParser
│   └── ToolCallParsingService
│
├── audit/                    # Audit logging
│   ├── AuditEvent            ◄─── ChatAENetwork
│   ├── AuditLogger           ◄─── common.audit.AuditLogger (implements)
│   ├── AuditOutcome          ◄─── ChatAENetwork
│   ├── LlmAuditEvent         ◄─── common.audit.AuditLogger
│   └── LlmAuditOutcome
│
├── client/                   # Client-side state
│   ├── ClientAiSettings      ◄─── AiTerminalScreen
│   ├── ClientSessionIndex    ◄─── ChatAENetwork, AiTerminalScreen
│   └── ClientSessionStore    ◄─── ChatAENetwork, AiTerminalScreen
│
├── net/                      # Network packets (DTOs)
│   ├── c2s/
│   │   ├── C2SApprovalDecisionPacket  ◄─── ChatAENetwork
│   │   ├── C2SCreateSessionPacket     ◄─── ChatAENetwork
│   │   ├── C2SDeleteSessionPacket     ◄─── ChatAENetwork
│   │   ├── C2SOpenSessionPacket       ◄─── ChatAENetwork
│   │   ├── C2SRequestSessionListPacket ◄─── ChatAENetwork
│   │   ├── C2SSendChatPacket          ◄─── ChatAENetwork
│   │   └── C2SUpdateSessionPacket     ◄─── ChatAENetwork
│   └── s2c/
│       ├── S2CSessionListPacket       ◄─── ChatAENetwork
│       └── S2CSessionSnapshotPacket   ◄─── ChatAENetwork
│
├── policy/                   # Risk/Policy engine
│   ├── PolicyDecision        ◄─── ToolRouter
│   ├── PolicyEngine
│   └── RiskLevel             ◄─── ToolRouter, ChatAENetwork
│
├── proposal/                 # Approval workflow
│   ├── ApprovalDecision      ◄─── ChatAESessionsSavedData, AiTerminalScreen
│   ├── Proposal              ◄─── ChatAENetwork, AiTerminalScreen, ChatAESessionsSavedData
│   ├── ProposalDetails       ◄─── ChatAENetwork, AiTerminalScreen, ChatAESessionsSavedData
│   └── ProposalFactory       ◄─── ToolRouter
│
├── recipes/                  # Recipe search
│   ├── RecipeIndexSnapshot   ◄─── RecipeIndexService
│   ├── RecipeSearchAlgorithm ◄─── RecipeIndexService
│   ├── RecipeSearchFilters   ◄─── ToolRouter, RecipeIndexService
│   ├── RecipeSearchResult    ◄─── RecipeIndexService
│   └── RecipeSummary         ◄─── RecipeIndexService
│
├── session/                  # Session management
│   ├── ChatMessage           ◄─── ChatAENetwork, AgentRunner, AiTerminalScreen, ChatAESessionsSavedData
│   ├── ChatRole              ◄─── ChatAENetwork, AgentRunner, ChatAESessionsSavedData
│   ├── DecisionLogEntry      ◄─── ChatAENetwork, ChatAESessionsSavedData
│   ├── PersistedSessions     ◄─── ChatAESessionsSavedData
│   ├── ServerSessionManager  ◄─── ChatAENetwork
│   ├── SessionAccess
│   ├── SessionListScope      ◄─── ChatAENetwork, AiTerminalScreen
│   ├── SessionMetadata       ◄─── ChatAENetwork, ChatAESessionsSavedData
│   ├── SessionSnapshot       ◄─── ChatAENetwork, AgentRunner, AiTerminalScreen, ChatAESessionsSavedData
│   ├── SessionState          ◄─── ChatAENetwork, ChatAESessionsSavedData
│   ├── SessionSummary        ◄─── ChatAENetwork, AiTerminalScreen
│   ├── SessionUpdate
│   ├── SessionVisibility     ◄─── ChatAENetwork, AiTerminalScreen, ChatAESessionsSavedData
│   └── TerminalBinding       ◄─── ChatAENetwork, AgentRunner, TerminalContextFactory, ChatAESessionsSavedData
│
├── terminal/                 # Terminal abstraction
│   ├── AiTerminalData        ◄─── AiTerminalHost, AiTerminalPart, AiTerminalPartOperations, TerminalContextFactory
│   └── TerminalContext       ◄─── ToolRouter, AgentRunner, TerminalContextFactory
│
└── tools/                    # Tool definitions
    ├── LocalCommandParser
    ├── ParseOutcome
    ├── ToolArgs              ◄─── ToolRouter
    ├── ToolCall              ◄─── ToolRouter, ChatAENetwork, AgentRunner, ChatAESessionsSavedData
    ├── ToolError
    ├── ToolOutcome           ◄─── ToolRouter, ChatAENetwork, AgentRunner
    ├── ToolPolicy            ◄─── ToolRouter
    └── ToolResult            ◄─── ToolRouter, ChatAENetwork
```

---

## Common Module → Core Dependencies (by file)

### Heavy Users (10+ core imports)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ChatAENetwork.java (31 core imports)                                        │
│ ════════════════════════════════════                                        │
│                                                                             │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐                   │
│  │   audit     │     │   client    │     │   policy    │                   │
│  │ AuditEvent  │     │ ClientSess* │     │ RiskLevel   │                   │
│  │ AuditOutcome│     └─────────────┘     └─────────────┘                   │
│  └─────────────┘                                                            │
│                                                                             │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐                   │
│  │  proposal   │     │   session   │     │   tools     │                   │
│  │ Approval*   │     │ ChatMessage │     │ ToolCall    │                   │
│  │ Proposal    │     │ ChatRole    │     │ ToolOutcome │                   │
│  │ ProposalDet │     │ Decision*   │     │ ToolResult  │                   │
│  └─────────────┘     │ ServerSess* │     └─────────────┘                   │
│                      │ SessionList*│                                        │
│  ┌─────────────┐     │ SessionMeta │     ┌─────────────┐                   │
│  │   net/c2s   │     │ SessionSnap │     │   net/s2c   │                   │
│  │ C2SApproval │     │ SessionState│     │ S2CSession* │                   │
│  │ C2SCreate*  │     │ SessionSumm │     └─────────────┘                   │
│  │ C2SDelete*  │     │ SessionVis* │                                        │
│  │ C2SOpen*    │     │ TerminalBind│                                        │
│  │ C2SRequest* │     └─────────────┘                                        │
│  │ C2SSend*    │                                                            │
│  │ C2SUpdate*  │                                                            │
│  └─────────────┘                                                            │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ ChatAESessionsSavedData.java (13 core imports)                              │
│ ══════════════════════════════════════════════                              │
│                                                                             │
│  proposal: ApprovalDecision, Proposal, ProposalDetails                      │
│  session:  ChatMessage, ChatRole, DecisionLogEntry, PersistedSessions,      │
│            SessionMetadata, SessionSnapshot, SessionState,                  │
│            SessionVisibility, TerminalBinding                               │
│  tools:    ToolCall                                                         │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ AgentRunner.java (13 core imports)                                          │
│ ══════════════════════════════════                                          │
│                                                                             │
│  agent:    AgentDecision, AgentLoopResult, AgentReasoningService,           │
│            ConversationHistoryBuilder, LlmRateLimiter, PromptId             │
│  session:  ChatMessage, ChatRole, SessionSnapshot, TerminalBinding          │
│  terminal: TerminalContext                                                  │
│  tools:    ToolCall, ToolOutcome                                            │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ ToolRouter.java (11 core imports)                                           │
│ ═════════════════════════════════                                           │
│                                                                             │
│  policy:   PolicyDecision, RiskLevel                                        │
│  proposal: Proposal, ProposalFactory                                        │
│  recipes:  RecipeSearchFilters                                              │
│  terminal: TerminalContext                                                  │
│  tools:    ToolArgs, ToolCall, ToolOutcome, ToolPolicy, ToolResult          │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ AiTerminalScreen.java (10 core imports)                                     │
│ ═══════════════════════════════════════                                     │
│                                                                             │
│  client:   ClientSessionIndex, ClientSessionStore                           │
│  proposal: ApprovalDecision, Proposal, ProposalDetails                      │
│  session:  ChatMessage, SessionListScope, SessionSnapshot,                  │
│            SessionSummary, SessionVisibility                                │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Medium Users (3-9 core imports)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ RecipeIndexService.java (5 core imports)                                    │
│   recipes: RecipeSearchFilters, RecipeSearchResult, RecipeSearchAlgorithm,  │
│            RecipeSummary, RecipeIndexSnapshot                               │
├─────────────────────────────────────────────────────────────────────────────┤
│ PromptRuntime.java (3 core imports)                                         │
│   agent: PromptContext, PromptId, PromptTemplate                            │
├─────────────────────────────────────────────────────────────────────────────┤
│ TerminalContextFactory.java (3 core imports)                                │
│   session: TerminalBinding                                                  │
│   terminal: AiTerminalData, TerminalContext                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│ LlmConfigLoader.java (2 core imports)                                       │
│   agent: LlmConfig, LlmProvider                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│ LlmRuntimeManager.java (2 core imports)                                     │
│   agent: LlmConfig, LlmRuntime                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│ PromptStore.java (2 core imports)                                           │
│   agent: PromptContext, PromptId                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│ AuditLogger.java (2 core imports)                                           │
│   audit: AuditEvent, LlmAuditEvent                                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Light Users (1-2 core imports)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ AiTerminalHost.java         → terminal: AiTerminalData                      │
│ AiTerminalPart.java         → terminal: AiTerminalData                      │
│ AiTerminalPartOperations.java → terminal: AiTerminalData                    │
│ PromptFileManager.java      → agent: PromptId                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

### No Core Imports (Pure MC/AE2)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ChatAE.java                 (mod initialization)                            │
│ ChatAEClient.java           (client init)                                   │
│ ChatAERegistries.java       (MC registries)                                 │
│ ChatAECommands.java         (Brigadier commands)                            │
│ ChatAEScreens.java          (screen factory)                                │
│ ChatAEPartRegistries.java   (AE2 part registration)                         │
│ AiTerminalPartModelIds.java (resource locations)                            │
│ AiTerminalMenu.java         (MC container menu)                             │
│ RecipeIndexReloadListener.java (MC reload listener)                         │
│ TeamAccess.java             (FTB Teams integration)                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Dependency Flow Diagram

```
                                    ┌─────────────────────┐
                                    │    forge-1.20.1     │
                                    │    fabric-1.20.1    │
                                    └──────────┬──────────┘
                                               │
                                               ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                                  common-1.20.1                                        │
│                                                                                       │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐     │
│  │  ChatAENetwork │  │  AgentRunner   │  │  ToolRouter    │  │ SessionsSaved  │     │
│  │  (31 imports)  │  │  (13 imports)  │  │  (11 imports)  │  │  (13 imports)  │     │
│  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘     │
│          │                   │                   │                   │               │
│          │    ┌──────────────┼───────────────────┼───────────────────┘               │
│          │    │              │                   │                                   │
│          ▼    ▼              ▼                   ▼                                   │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐     │
│  │ RecipeIndex    │  │ PromptRuntime  │  │ LlmConfig*     │  │ TerminalCtx*   │     │
│  │ Service        │  │ PromptStore    │  │ LlmRuntime*    │  │ AuditLogger    │     │
│  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘     │
│          │                   │                   │                   │               │
└──────────┼───────────────────┼───────────────────┼───────────────────┼───────────────┘
           │                   │                   │                   │
           ▼                   ▼                   ▼                   ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                                      core                                             │
│                                                                                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐│
│  │   session   │  │   agent     │  │   tools     │  │  proposal   │  │   audit     ││
│  │ 15 types    │  │ 18 types    │  │ 10 types    │  │ 4 types     │  │ 5 types     ││
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘│
│                                                                                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                  │
│  │   recipes   │  │  terminal   │  │   policy    │  │   net/*     │                  │
│  │ 5 types     │  │ 2 types     │  │ 3 types     │  │ 9 types     │                  │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘                  │
│                                                                                       │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Entanglement Analysis

### Tightly Coupled (Hard to Separate)

| Common File | Core Dependencies | Coupling Reason |
|-------------|-------------------|-----------------|
| `ChatAENetwork` | 31 types | Central hub for networking, sessions, tools |
| `ChatAESessionsSavedData` | 13 types | NBT serialization of all session types |
| `AgentRunner` | 13 types | LangGraph4j + session + tools integration |
| `ToolRouter` | 11 types | Tool dispatch needs policy + proposal + terminal |
| `AiTerminalScreen` | 10 types | UI needs session + proposal + client state |

### Loosely Coupled (Easy to Refactor)

| Common File | Core Dependencies | Notes |
|-------------|-------------------|-------|
| `PromptStore` | 2 types | Pure Java, can move to core |
| `PromptRuntime` | 3 types | Thin wrapper, logic can move |
| `LlmConfigLoader` | 2 types | JSON parsing can move |
| `AuditLogger` | 2 types | Just implements core interface |

### No Coupling (Pure MC/AE2)

10 files have zero core imports - these are purely platform-specific.

---

## Summary Statistics

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         DEPENDENCY STATISTICS                                │
├─────────────────────────────────────────────────────────────────────────────┤
│  Total core types used by common:  ~71 unique types                         │
│  Total import statements:          ~95 imports                              │
│                                                                             │
│  Core packages most used:                                                   │
│    1. session   (15 types) - ChatMessage, SessionSnapshot, etc.             │
│    2. agent     (12 types) - AgentDecision, PromptId, etc.                  │
│    3. tools     (8 types)  - ToolCall, ToolOutcome, etc.                    │
│    4. net       (9 types)  - All packet records                             │
│    5. proposal  (4 types)  - Proposal, ApprovalDecision, etc.               │
│                                                                             │
│  Common files by core dependency count:                                     │
│    Heavy (10+):  5 files                                                    │
│    Medium (3-9): 7 files                                                    │
│    Light (1-2):  4 files                                                    │
│    None:         10 files                                                   │
└─────────────────────────────────────────────────────────────────────────────┘
```
