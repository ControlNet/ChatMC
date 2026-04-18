# MineAgent

MineAgent is an in-game AI assistant for Minecraft 1.20.1. It gives you an AI Terminal that lets you chat with an agent, search crafting information, manage chat sessions, and approve or deny actions before they run.

## Open MineAgent

You can open the AI Terminal in two ways:

- Run `/mineagent open`
- Press `K` (default keybind: **Open AI Terminal**)

## What MineAgent can do

### Chat with an in-game AI assistant

Use the AI Terminal like a chat window inside Minecraft. You can ask questions, send requests, and get answers directly in the terminal UI.

### Search crafting information

MineAgent can look up:

- recipes that craft an item
- recipes that use an item as an ingredient

This is useful when you want quick crafting help without leaving the game.

### Manage saved sessions

MineAgent keeps chat sessions so you can continue earlier conversations instead of starting over every time. Sessions can be created, reopened, renamed, deleted, and shared with different visibility settings.

### Ask for approval before actions

When the assistant wants to perform an action that should not run automatically, MineAgent shows an approval step in the terminal. You can approve or deny the action before it continues.

### Use extra tools when configured

The base mod includes built-in Minecraft recipe tools. If you configure additional integrations, MineAgent can also:

- fetch data with the HTTP tool
- load external MCP tools from your local configuration

## First-time setup

If you want MineAgent to talk to an LLM service, start with:

- `config/mineagent/llm.toml` — model and LLM connection settings

If you want MineAgent to load MCP tools, use:

- `config/mineagent/mcp.json` — MCP server and tool configuration

If you want to customize prompts, use:

- `config/mineagent/prompts/` — prompt override files

## Optional extensions

The official extensions for AE2 (to extend the tools) and Matrix (to host a bridge to Matrix) are still in WIP. Keep updated!
