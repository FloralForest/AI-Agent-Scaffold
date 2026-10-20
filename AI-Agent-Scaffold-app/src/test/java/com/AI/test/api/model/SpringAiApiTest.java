package com.AI.test.api.model;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;

import java.io.Console;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SpringAiApiTest {

    private static final int MAX_HISTORY_ROUNDS = 10;

    // 仅供手动调用真实模型，Maven 回归测试不会执行此入口。
    public static void main(String[] args) {
        ModelExampleSettings settings = ModelExampleSettings.fromCode();
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(settings.getBaseUrl())
                .apiKey(settings.getApiKey())
                // 配置地址已包含厂商要求的接口前缀，例如 /v1，此处不再重复。
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .build();
        ChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(settings.getModel()).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();

        Console console = System.console();
        Charset inputCharset = console == null ? StandardCharsets.UTF_8 : console.charset();
        try (Scanner input = new Scanner(System.in, inputCharset)) {
            runConversation(model, input, System.out);
        }
    }

    // 输入与模型解耦，离线回归可替换模型，不会使用真实密钥。
    static void runConversation(ChatModel model, Scanner input, PrintStream output) {
        List<Message> history = new ArrayList<>();
        output.println("终端对话已启动，保留最近 " + MAX_HISTORY_ROUNDS + " 轮上下文。");
        output.println("输入问题后按回车；/clear 清空会话；/exit、exit、quit 或 退出 结束。");

        while (true) {
            output.print("\n你：");
            output.flush();
            if (!input.hasNextLine()) {
                output.println("\n输入已结束，对话退出。");
                return;
            }
            String question = input.nextLine().strip();
            if (question.isEmpty()) {
                continue;
            }
            if ("/exit".equalsIgnoreCase(question) || "exit".equalsIgnoreCase(question)
                    || "quit".equalsIgnoreCase(question) || "退出".equals(question)) {
                output.println("对话已结束。");
                return;
            }
            if ("/clear".equalsIgnoreCase(question)) {
                history.clear();
                output.println("会话上下文已清空。");
                continue;
            }

            UserMessage userMessage = new UserMessage(question);
            List<Message> requestMessages = new ArrayList<>(history);
            requestMessages.add(userMessage);
            output.println("正在等待模型回复……");
            try {
                // 同步等待当前请求完成后，才接收下一条输入。
                ChatResponse response = model.call(new Prompt(requestMessages));
                Generation result = response == null ? null : response.getResult();
                AssistantMessage reply = result == null ? null : result.getOutput();
                if (reply == null || reply.getText() == null || reply.getText().isBlank()) {
                    output.println("模型未返回有效回复，请重试。");
                    continue;
                }
                output.println("模型：" + reply.getText());

                // 只保存成功的完整问答，失败请求不污染后续上下文。
                history.add(userMessage);
                history.add(reply);
                if (history.size() > MAX_HISTORY_ROUNDS * 2) {
                    history.subList(0, 2).clear();
                }
            } catch (RuntimeException exception) {
                // 不输出异常原文，避免厂商响应回显请求凭据。
                output.println("请求失败（" + exception.getClass().getSimpleName()
                        + "），请检查模型配置或网络后重试。");
            }
        }
    }
}
