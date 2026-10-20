package com.AI.test.api.model;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class LangChain4jApiTest {

    // 仅供手动调用真实模型，Maven 回归测试不会执行此入口。
    public static void main(String[] args) {
        ModelExampleSettings settings = ModelExampleSettings.fromCode();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(settings.getBaseUrl())
                .apiKey(settings.getApiKey())
                .modelName(settings.getModel())
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();

        log.info("模型回复：{}", model.chat("你好"));
    }
}
