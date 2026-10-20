package com.AI.test;

import com.AI.Application;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("AI 框架依赖离线回归")
class FrameworkDependencyTest {

    private static final String CHAT_RESPONSE = "{\"id\":\"offline-id\",\"object\":\"chat.completion\","
            + "\"created\":1,\"model\":\"offline-model\","
            + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"离线回复\"},"
            + "\"finish_reason\":\"stop\"}],"
            + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";

    @Test
    @DisplayName("Spring AI 构建提示词并调用本地工具")
    void springAiShouldBuildPromptAndInvokeLocalTool() {
        Prompt prompt = new Prompt(new UserMessage("离线校验"));
        assertThat(prompt.getContents()).isEqualTo("离线校验");

        // 不调用模型，仅验证工具参数的结构生成、绑定及本地执行。
        ToolCallback[] callbacks = ToolCallbacks.from(new OfflineTools());
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].call("{\"text\":\"离线校验\"}"))
                .contains("离线校验");
    }

    @Test
    @DisplayName("LangChain4j 消息序列化与还原")
    void langChain4jShouldRoundTripMessages() {
        dev.langchain4j.data.message.UserMessage message =
                dev.langchain4j.data.message.UserMessage.from("离线校验");
        String json = dev.langchain4j.data.message.ChatMessageSerializer.messageToJson(message);
        dev.langchain4j.data.message.ChatMessage restored =
                dev.langchain4j.data.message.ChatMessageDeserializer.messageFromJson(json);

        assertThat(restored).isEqualTo(message);
    }

    @Test
    @DisplayName("Spring AI 使用模拟接口校验请求与回复")
    void springAiShouldCallCompatibleEndpointWithoutNetwork() {
        RestClient.Builder restClient = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClient).build();
        server.expect(requestTo("https://model.invalid/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer offline-token"))
                .andRespond(withSuccess(CHAT_RESPONSE, MediaType.APPLICATION_JSON));
        OpenAiApi api = OpenAiApi.builder().baseUrl("https://model.invalid/v1")
                .apiKey("offline-token").completionsPath("/chat/completions")
                .embeddingsPath("/embeddings").restClientBuilder(restClient).build();
        OpenAiChatModel model = OpenAiChatModel.builder().openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("offline-model").build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();

        assertThat(model.call("离线校验")).isEqualTo("离线回复");
        server.verify();
    }

    @Test
    @DisplayName("LangChain4j 使用模拟接口校验请求与回复")
    void langChain4jShouldCallCompatibleEndpointWithoutNetwork() {
        dev.langchain4j.http.client.HttpClient httpClient =
                mock(dev.langchain4j.http.client.HttpClient.class);
        dev.langchain4j.http.client.HttpClientBuilder httpBuilder =
                mock(dev.langchain4j.http.client.HttpClientBuilder.class, RETURNS_SELF);
        when(httpBuilder.build()).thenReturn(httpClient);
        when(httpClient.execute(any())).thenAnswer(invocation -> {
            dev.langchain4j.http.client.HttpRequest request = invocation.getArgument(0);
            assertThat(request.url()).isEqualTo("https://model.invalid/v1/chat/completions");
            assertThat(request.headers().get("Authorization")).contains("Bearer offline-token");
            return dev.langchain4j.http.client.SuccessfulHttpResponse.builder()
                    .statusCode(200).headers(Map.of("Content-Type", List.of("application/json")))
                    .body(CHAT_RESPONSE).build();
        });
        dev.langchain4j.model.openai.OpenAiChatModel model = dev.langchain4j.model.openai.OpenAiChatModel.builder()
                .baseUrl("https://model.invalid/v1").apiKey("offline-token")
                .modelName("offline-model").httpClientBuilder(httpBuilder).maxRetries(0).build();

        assertThat(model.chat("离线校验")).isEqualTo("离线回复");
    }

    @Test
    @DisplayName("Google ADK 构建智能体并创建内存会话")
    void googleAdkShouldBuildAgentAndCreateLocalSession() {
        SequentialAgent agent = SequentialAgent.builder()
                .name("offline_agent")
                .subAgents(List.of())
                .build();
        Session session = new InMemorySessionService()
                .createSession("offline-app", "offline-user")
                .blockingGet();
        Content content = Content.fromParts(Part.fromText("离线校验"));

        assertThat(agent.name()).isEqualTo("offline_agent");
        assertThat(session.userId()).isEqualTo("offline-user");
        assertThat(content.parts().orElseThrow().get(0).text()).contains("离线校验");
    }

    @Test
    @DisplayName("不提供密钥、不连接数据库时加载应用上下文")
    void applicationContextShouldLoadWithoutModelCredentialsOrDatabase() {
        // 不加载环境配置文件、不启动服务端口，也不连接真实数据库。
        new ApplicationContextRunner()
                .withUserConfiguration(Application.class)
                .withPropertyValues("spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("cache");
                    assertThat(context).doesNotHaveBean(org.springframework.ai.chat.model.ChatModel.class);
                });
    }

    public static class OfflineTools {

        @Tool(description = "原样返回输入文本，不调用外部服务")
        public String echo(String text) {
            return text;
        }
    }
}
