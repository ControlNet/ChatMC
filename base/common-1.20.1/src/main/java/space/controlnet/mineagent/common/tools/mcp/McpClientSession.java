package space.controlnet.mineagent.common.tools.mcp;

import com.google.gson.JsonObject;
import space.controlnet.mineagent.common.tools.mcp.McpSchemaMapper.McpRemoteTool;

import java.util.List;

interface McpClientSession extends AutoCloseable {
    List<McpRemoteTool> listTools() throws McpTransportException;

    JsonObject callTool(String remoteToolName, String argumentsJson) throws McpTransportException;

    @Override
    void close();
}
