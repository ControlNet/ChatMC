# MineAgent Architecture: Core ↔ Common Dependency Visualization

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
│   ├── AgentLoopResult       ◄─── AgentRunner, MineAgentNetwork
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
│   ├── AuditEvent            ◄─── MineAgentNetwork
│   ├── AuditLogger           ◄─── common.audit.AuditLogger (implements)
│   ├── AuditOutcome          ◄─── MineAgentNetwork
│   ├── LlmAuditEvent         ◄─── common.audit.AuditLogger
│   └── LlmAuditOutcome
│
├── client/                   # Client-side state
│   ├── ClientAiSettings      ◄─── AiTerminalScreen
│   ├── ClientSessionIndex    ◄─── MineAgentNetwork, AiTerminalScreen
│   └── ClientSessionStore    ◄─── MineAgentNetwork, AiTerminalScreen
│
├── net/                      # Network packets (DTOs)
│   ├── c2s/
│   │   ├── C2SApprovalDecisionPacket  ◄─── MineAgentNetwork
│   │   ├── C2SCreateSessionPacket     ◄─── MineAgentNetwork
│   │   ├── C2SDeleteSessionPacket     ◄─── MineAgentNetwork
│   │   ├── C2SOpenSessionPacket       ◄─── MineAgentNetwork
│   │   ├── C2SRequestSessionListPacket ◄─── MineAgentNetwork
│   │   ├── C2SSendChatPacket          ◄─── MineAgentNetwork
│   │   └── C2SUpdateSessionPacket     ◄─── MineAgentNetwork
│   └── s2c/
│       ├── S2CSessionListPacket       ◄─── MineAgentNetwork
│       └── S2CSessionSnapshotPacket   ◄─── MineAgentNetwork
│
├── policy/                   # Risk/Policy engine
│   ├── PolicyDecision        ◄─── ToolRouter
│   ├── PolicyEngine
│   └── RiskLevel             ◄─── ToolRouter, MineAgentNetwork
│
├── proposal/                 # Approval workflow
│   ├── ApprovalDecision      ◄─── MineAgentSessionsSavedData, AiTerminalScreen
│   ├── Proposal              ◄─── MineAgentNetwork, AiTerminalScreen, MineAgentSessionsSavedData
│   ├── ProposalDetails       ◄─── MineAgentNetwork, AiTerminalScreen, MineAgentSessionsSavedData
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
│   ├── ChatMessage           ◄─── MineAgentNetwork, AgentRunner, AiTerminalScreen, MineAgentSessionsSavedData
│   ├── ChatRole              ◄─── MineAgentNetwork, AgentRunner, MineAgentSessionsSavedData
│   ├── DecisionLogEntry      ◄─── MineAgentNetwork, MineAgentSessionsSavedData
│   ├── PersistedSessions     ◄─── MineAgentSessionsSavedData
│   ├── ServerSessionManager  ◄─── MineAgentNetwork
│   ├── SessionAccess
│   ├── SessionListScope      ◄─── MineAgentNetwork, AiTerminalScreen
│   ├── SessionMetadata       ◄─── MineAgentNetwork, MineAgentSessionsSavedData
│   ├── SessionSnapshot       ◄─── MineAgentNetwork, AgentRunner, AiTerminalScreen, MineAgentSessionsSavedData
│   ├── SessionState          ◄─── MineAgentNetwork, MineAgentSessionsSavedData
│   ├── SessionSummary        ◄─── MineAgentNetwork, AiTerminalScreen
│   ├── SessionUpdate
│   ├── SessionVisibility     ◄─── MineAgentNetwork, AiTerminalScreen, MineAgentSessionsSavedData
│   └── TerminalBinding       ◄─── MineAgentNetwork, AgentRunner, TerminalContextFactory, MineAgentSessionsSavedData
│
├── terminal/                 # Terminal abstraction
│   ├── AiTerminalData        ◄─── AiTerminalHost, AiTerminalPart, AiTerminalPartOperations, TerminalContextFactory
│   └── TerminalContext       ◄─── ToolRouter, AgentRunner, TerminalContextFactory
│
└── tools/                    # Tool definitions
    ├── LocalCommandParser
    ├── ParseOutcome
    ├── ToolArgs              ◄─── ToolRouter
├── ToolCall              ◄─── ToolRouter, MineAgentNetwork, AgentRunner, MineAgentSessionsSavedData
    ├── ToolError
├── ToolOutcome           ◄─── ToolRouter, MineAgentNetwork, AgentRunner
    ├── ToolPolicy            ◄─── ToolRouter
└── ToolResult            ◄─── ToolRouter, MineAgentNetwork
```

---

## Common Module → Core Dependencies (by file)

### Heavy Users (10+ core imports)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ MineAgentNetwork.java (31 core imports)                                     │
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
│ MineAgentSessionsSavedData.java (13 core imports)                           │
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
│ MineAgent.java             (mod initialization)                             │
│ MineAgentClient.java       (client init)                                    │
│ MineAgentRegistries.java   (MC registries)                                  │
│ MineAgentCommands.java     (Brigadier commands)                             │
│ MineAgentScreens.java      (screen factory)                                 │
│ MineAgentAePartRegistries.java (AE2 part registration)                      │
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
│  │ MineAgentNetwork │  │  AgentRunner │  │  ToolRouter    │  │ SessionsSaved  │     │
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
| `MineAgentNetwork` | 31 types | Central hub for networking, sessions, tools |
| `MineAgentSessionsSavedData` | 13 types | NBT serialization of all session types |
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
