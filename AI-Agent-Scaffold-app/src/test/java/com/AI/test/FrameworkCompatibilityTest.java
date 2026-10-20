package com.AI.test;

import com.AI.domain.agent.model.entity.ArmoryCommandEntity;
import com.AI.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.AI.domain.agent.service.armory.ArmoryService;
import com.AI.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.AI.domain.agent.service.armory.node.AiApiNode;
import com.AI.domain.agent.service.armory.node.RootNode;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.springai.SpringAI;
import com.google.adk.tools.ToolContext;
import com.google.adk.tools.mcp.DefaultMcpTransportBuilder;
import com.google.adk.tools.mcp.McpSessionManager;
import com.google.adk.tools.mcp.McpTool;
import com.google.adk.tools.mcp.SseServerParameters;
import com.google.adk.tools.mcp.StreamableHttpServerParameters;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AI 框架版本兼容离线回归")
@Timeout(15)
class FrameworkCompatibilityTest {

    @Test
    @DisplayName("聚合工具包不覆盖应用的 Spring 和日志版本")
    void bundledFrameworkClassesShouldNotOverrideManagedVersions() {
        // 工具聚合 JAR 内含旧框架类，必须验证真正被类加载器选中的版本。
        assertThat(SpringBootVersion.getVersion()).startsWith("3.5.");
        assertThat(SpringVersion.getVersion()).startsWith("6.2.");
        assertThat(LoggerFactory.class.getProtectionDomain().getCodeSource().getLocation().toString())
                .contains("slf4j-api-2.");
        assertThat(LoggerFactory.getILoggerFactory().getClass().getProtectionDomain()
                .getCodeSource().getLocation().toString()).contains("logback-classic-1.5.");
    }

    @Test
    @DisplayName("ADK 通过 Spring AI 适配器获取模型回复")
    void adkShouldInvokeSpringAiModelWithoutNetwork() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("离线适配回复")))));
        SpringAI adapter = new SpringAI(model, "offline-model");
        LlmRequest request = LlmRequest.builder().model("offline-model")
                .contents(List.of(Content.fromParts(Part.fromText("离线适配问题")))).build();

        LlmResponse response = adapter.generateContent(request, false).blockingFirst();

        assertThat(response.content().orElseThrow().parts().orElseThrow().get(0).text())
                .contains("离线适配回复");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains("离线适配问题");
    }

    @Test
    @DisplayName("Spring AI 工具适配调用统一版本的 MCP 客户端")
    void springAiShouldInvokeMcpToolWithoutNetwork() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.getClientInfo()).thenReturn(new McpSchema.Implementation("offline-client", "1.0"));
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(
                McpSchema.CallToolResult.builder().addTextContent("离线工具回复").isError(false).build());
        McpSchema.Tool tool = McpSchema.Tool.builder().name("offline_echo")
                .description("离线回显工具")
                .inputSchema(McpJsonMapper.getDefault(), "{\"type\":\"object\",\"properties\":{}}")
                .build();
        SyncMcpToolCallback callback = new SyncMcpToolCallback(client, tool);

        assertThat(callback.call("{\"text\":\"离线参数\"}")).contains("离线工具回复");

        ArgumentCaptor<McpSchema.CallToolRequest> request = ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(client).callTool(request.capture());
        assertThat(request.getValue().name()).isEqualTo("offline_echo");
        assertThat(request.getValue().arguments()).containsEntry("text", "离线参数");
    }

    @Test
    @DisplayName("ADK 工具通过统一 MCP 客户端传参并解析工具结果")
    void adkShouldInvokeMcpToolWithoutNetwork() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(
                McpSchema.CallToolResult.builder().addTextContent("离线工具结果").isError(false).build());
        McpSchema.Tool definition = McpSchema.Tool.builder().name("offline_adk_echo")
                .description("离线 ADK 回显工具")
                .inputSchema(McpJsonMapper.getDefault(), "{\"type\":\"object\",\"properties\":{}}")
                .build();
        // 会话管理器与客户端均替换为模拟对象，不初始化外部 MCP 会话。
        McpTool tool = new McpTool(definition, client, mock(McpSessionManager.class));

        Map<String, Object> result = tool.runAsync(Map.of("text", "离线参数"), mock(ToolContext.class))
                .blockingGet();

        assertThat(result.toString()).contains("离线工具结果");
        ArgumentCaptor<McpSchema.CallToolRequest> request = ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(client).callTool(request.capture());
        assertThat(request.getValue().name()).isEqualTo("offline_adk_echo");
        assertThat(request.getValue().arguments()).containsEntry("text", "离线参数");
    }

    @Test
    @DisplayName("ADK 可构建统一版本的三种 MCP 传输器")
    void adkShouldBuildMcpTransportsWithoutConnecting() {
        Object[] parameters = {
                ServerParameters.builder("offline-command-not-executed").build(),
                SseServerParameters.builder().url("https://mcp.invalid").build(),
                StreamableHttpServerParameters.builder().url("https://mcp.invalid/mcp").build()
        };
        DefaultMcpTransportBuilder builder = new DefaultMcpTransportBuilder();
        for (Object parameter : parameters) {
            // 仅构建并关闭传输器；不调用 connect/initialize，不联网或启动子进程。
            McpClientTransport transport = builder.build(parameter);
            assertThat(transport).isNotNull();
            transport.closeGracefully().block(Duration.ofSeconds(2));
        }
    }

    @Test
    @DisplayName("策略树按现有装配链路执行且上下文存取保持不变")
    void strategyTreeShouldKeepExistingRoutingAndContextBehavior() throws Exception {
        AiApiNode apiNode = new AiApiNode();
        RootNode root = new RootNode();
        ReflectionTestUtils.setField(root, "aiApiNode", apiNode);
        DefaultArmoryFactory factory = new DefaultArmoryFactory();
        ReflectionTestUtils.setField(factory, "rootNode", root);
        ArmoryService service = new ArmoryService();
        ReflectionTestUtils.setField(service, "defaultArmoryFactory", factory);
        DefaultArmoryFactory.DynamicContext context = new DefaultArmoryFactory.DynamicContext();
        context.setValue("offline-key", "离线上下文");

        assertThat(context.<String>getValue("offline-key")).isEqualTo("离线上下文");
        assertThat(root.get(new ArmoryCommandEntity(), context)).isSameAs(apiNode);
        // 当前业务节点尚未构建真实模型，默认末端返回空值，不在本次依赖修复中补写业务。
        assertThat(factory.armoryStrategyHandler().apply(new ArmoryCommandEntity(), context)).isNull();
        service.acceptArmoryAgents(List.of(new AiAgentConfigTableVO()));
    }
}
