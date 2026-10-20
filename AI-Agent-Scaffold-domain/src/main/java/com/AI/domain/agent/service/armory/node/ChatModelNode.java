package com.AI.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.AI.domain.agent.model.entity.ArmoryCommandEntity;
import com.AI.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.AI.domain.agent.model.valobj.AiAgentRegisterVO;
import com.AI.domain.agent.service.armory.AbstractArmorySupport;
import com.AI.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话模型装配节点：复用前序节点的模型 API，接入 MCP 工具，生成可调用的 ChatModel。
 * 本节点不提交用户问题，也不生成模型回答；但 MCP 初始化和工具发现会发生外部调用。
 * 设计上应接在 AiApiNode 后面；当前 AiApiNode.get() 仍返回默认处理器，尚未路由到此节点。
 */
@Slf4j
@Service
public class ChatModelNode extends AbstractArmorySupport {

    @Resource
    private AgentNode agentNode;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - ChatModelNode");

        // 依赖前序 AiApiNode 已将模型服务地址、密钥和接口路径组装为 OpenAiApi。
        OpenAiApi openAiApi = dynamicContext.getOpenAiApi();

        // 配置对象中的 ChatModel 只保存模型名称、工具配置，不是实际执行对话的模型实例。
        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        AiAgentConfigTableVO.Module.ChatModel chatModelConfig = aiAgentConfigTableVO.getModule().getChatModel();

        // 按配置逐个建立 MCP 会话；不使用工具时需提供空列表，当前代码没有对 null 兜底。
        List<McpSyncClient> mcpSyncClients = new ArrayList<>();
        List<AiAgentConfigTableVO.Module.ChatModel.ToolMcp> toolMcpList = chatModelConfig.getToolMcpList();
        for (AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp : toolMcpList) {
            mcpSyncClients.add(createMcpSyncClient(toolMcp));
        }

        // 将外部工具包装成 Spring AI 回调；getToolCallbacks() 会向 MCP 服务查询可用工具。
        // 工具实际执行由后续模型调用触发，此处只完成模型和工具的装配。
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chatModelConfig.getModel())
                        .toolCallbacks(SyncMcpToolCallbackProvider.builder().mcpClients(mcpSyncClients).build().getToolCallbacks())
                        .build())
                .build();

        // 供后续节点或调用方读取，不会自动注册为 Spring Bean，也没有在此调用 chatModel.call()。
        dynamicContext.setChatModel(chatModel);

        // 交给策略树选择下一步；当前 get() 返回默认末端处理器。
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {

        return agentNode;
    }

    /**
     * 创建并初始化一个同步 MCP 客户端，当前支持远程 SSE 服务和本地 Stdio 进程。
     * 两种配置同时存在时优先使用 SSE；均未配置则抛出异常。
     * 返回的客户端持有连接或进程资源，当前节点尚未实现统一关闭管理。
     */
    private McpSyncClient createMcpSyncClient(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) {

        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.SSEServerParameters sseConfig = toolMcp.getSse();
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StdioServerParameters stdioConfig = toolMcp.getStdio();

        if (null != sseConfig) {
            // SSE 通过网络连接外部工具服务，例如 https://mcp.example.com/sse?apikey=<密钥>。
            String originalBaseUri = sseConfig.getBaseUri();
            String baseUri;
            String sseEndpoint;

            // 现有实现按首次出现的 sse 字符串拆分，并保留其后路径与查询参数。
            // 这里不是标准 URI 解析；域名等其他位置包含 sse 时可能截取错误。
            int queryParamStartIndex = originalBaseUri.indexOf("sse");
            if (queryParamStartIndex != -1) {
                baseUri = originalBaseUri.substring(0, queryParamStartIndex - 1);
                sseEndpoint = originalBaseUri.substring(queryParamStartIndex - 1);
            } else {
                // 地址未包含 sse 字符串时，使用单独配置的连接路径。
                baseUri = originalBaseUri;
                sseEndpoint = sseConfig.getSseEndpoint();
            }

            // 未提供连接路径时使用默认 /sse。
            sseEndpoint = StringUtils.isBlank(sseEndpoint) ? "/sse" : sseEndpoint;

            HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                    .builder(baseUri) // 工具服务的根地址。
                    .sseEndpoint(sseEndpoint) // SSE 路径，可能包含认证用查询参数。
                    .build();

            // 当前 SSE 请求超时按“分钟”解释，不是毫秒；与下方 Stdio 的“秒”不同。
            McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(sseConfig.getRequestTimeout())).build();
            // initialize() 会真正连接服务并交换协议版本、服务信息和能力，不只是创建对象。
            var init_sse = mcpSyncClient.initialize();

            log.info("Tool SSE MCP Initialized {}", init_sse);
            return mcpSyncClient;
        }

        if (null != stdioConfig) {
            AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StdioServerParameters.ServerParameters serverParameters = stdioConfig.getServerParameters();
            // Stdio 通过本地进程的标准输入、输出交换协议消息，命令、参数和环境变量须来自可信配置。
            // 环境变量可能携带密钥，不要直接打印完整配置。
            var stdioParams = ServerParameters.builder(serverParameters.getCommand())
                    .args(serverParameters.getArgs())
                    .env(serverParameters.getEnv())
                    .build();

            // Jackson 负责 MCP 消息的 JSON 序列化和反序列化；此处请求超时单位为秒。
            var mcpClient = McpClient.sync(new StdioClientTransport(stdioParams, new JacksonMcpJsonMapper(new ObjectMapper())))
                    .requestTimeout(Duration.ofSeconds(stdioConfig.getRequestTimeout())).build();
            // 初始化时会启动配置的本地命令并完成 MCP 握手，而不是连接远程 HTTP 服务。
            var init_stdio = mcpClient.initialize();

            log.info("Tool Stdio MCP Initialized {}", init_stdio);
            return mcpClient;
        }

        // 当前没有实现其他传输方式，不能在两种配置均为空时创建客户端。
        throw new RuntimeException("toolMcp sse and stdio is null!");
    }
}
