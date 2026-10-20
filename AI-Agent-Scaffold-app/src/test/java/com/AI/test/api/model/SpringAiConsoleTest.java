package com.AI.test.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Spring AI 终端对话离线测试")
class SpringAiConsoleTest {

    @Test
    @DisplayName("逐轮输出回复并将前轮问答传给下一轮")
    void conversationShouldRetainSuccessfulTurns() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("回答一"), response("回答二"));

        String output = runConsole(model, "问题一\n问题二\n/exit\n");

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(0).getInstructions())
                .extracting(Message::getText).containsExactly("问题一");
        assertThat(prompts.getAllValues().get(1).getInstructions())
                .extracting(Message::getText).containsExactly("问题一", "回答一", "问题二");
        assertThat(output).containsSubsequence("正在等待模型回复", "模型：回答一",
                "正在等待模型回复", "模型：回答二", "对话已结束");
    }

    @Test
    @DisplayName("空输入不调用模型且所有退出命令均生效")
    void blankInputAndExitCommandsShouldNotCallModel() {
        String[] commands = {"/exit", "EXIT", "QuIt", "退出"};
        for (String command : commands) {
            ChatModel model = mock(ChatModel.class);

            String output = runConsole(model, "\n  \n\t\n" + command + "\n不应执行\n");

            verifyNoInteractions(model);
            assertThat(output).contains("对话已结束");
        }
    }

    @Test
    @DisplayName("输入流结束时正常退出")
    void endOfInputShouldExitGracefully() {
        ChatModel model = mock(ChatModel.class);

        String output = runConsole(model, "");

        verifyNoInteractions(model);
        assertThat(output).contains("输入已结束");
    }

    @Test
    @DisplayName("清空会话后下一次请求不携带旧上下文")
    void clearCommandShouldResetHistory() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("旧回答"), response("新回答"));

        String output = runConsole(model, "旧问题\n/clear\n新问题\nexit\n");

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(1).getInstructions())
                .extracting(Message::getText).containsExactly("新问题");
        assertThat(output).contains("会话上下文已清空");
    }

    @Test
    @DisplayName("请求失败后可继续输入且不保留失败问答或输出异常敏感原文")
    void failedRequestShouldNotPolluteHistoryOrExposeErrorBody() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("offline-secret-in-error-body"))
                .thenReturn(response("重试回答"));

        String output = runConsole(model, "失败问题\n重试问题\nexit\n");

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(1).getInstructions())
                .extracting(Message::getText).containsExactly("重试问题");
        assertThat(output).contains("请求失败（IllegalStateException）", "模型：重试回答")
                .doesNotContain("offline-secret-in-error-body");
    }

    @Test
    @DisplayName("缺失或空白回复不计入后续上下文")
    void emptyRepliesShouldNotBeRetained() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(null,
                new ChatResponse(List.of()), response("  "), response("有效回答"));

        String output = runConsole(model, "问题一\n问题二\n问题三\n问题四\nexit\n");

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(4)).call(prompts.capture());
        for (Prompt prompt : prompts.getAllValues()) {
            assertThat(prompt.getInstructions()).hasSize(1);
        }
        assertThat(output).contains("模型未返回有效回复", "模型：有效回答");
    }

    @Test
    @DisplayName("只保留最近十轮完整问答并丢弃最旧问答")
    void historyShouldKeepOnlyTenCompleteTurns() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("回答"));
        StringBuilder input = new StringBuilder();
        for (int round = 1; round <= 12; round++) {
            input.append("问题").append(round).append('\n');
        }
        input.append("exit\n");

        runConsole(model, input.toString());

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(12)).call(prompts.capture());
        List<Message> lastRequest = prompts.getAllValues().get(11).getInstructions();
        assertThat(lastRequest).hasSize(21);
        assertThat(lastRequest.get(0).getText()).isEqualTo("问题2");
        assertThat(lastRequest.get(20).getText()).isEqualTo("问题12");
        for (int round = 0; round < 10; round++) {
            assertThat(lastRequest.get(round * 2).getText()).isEqualTo("问题" + (round + 2));
            assertThat(lastRequest.get(round * 2 + 1).getText()).isEqualTo("回答");
        }
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static String runConsole(ChatModel model, String text) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (Scanner input = new Scanner(text);
             PrintStream output = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            SpringAiApiTest.runConversation(model, input, output);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
