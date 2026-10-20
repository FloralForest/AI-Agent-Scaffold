package com.AI.test.api.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class OfflineAgentSupport {

    private OfflineAgentSupport() {
    }

    static LlmAgent agent(String name, String instruction, String outputKey,
                          Function<LlmRequest, String> answer) {
        return builder(name, instruction, outputKey, answer).build();
    }

    static LlmAgent.Builder builder(String name, String instruction, String outputKey,
                                    Function<LlmRequest, String> answer) {
        // 使用真实 ADK 编排，仅替换外部模型响应，避免测试发出付费请求。
        BaseLlm model = new BaseLlm("offline-model") {
            @Override
            public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
                return Flowable.fromCallable(() -> LlmResponse.builder()
                                .content(Content.builder().role("model")
                                        .parts(Part.fromText(answer.apply(request))).build())
                                .turnComplete(true)
                                .build())
                        .subscribeOn(Schedulers.io());
            }

            @Override
            public BaseLlmConnection connect(LlmRequest request) {
                throw new UnsupportedOperationException("离线示例不支持建立实时模型连接");
            }
        };
        return LlmAgent.builder().name(name).model(model).instruction(instruction)
                .outputKey(outputKey).includeContents(LlmAgent.IncludeContents.NONE);
    }

    static RunResult run(BaseAgent root) {
        InMemoryRunner runner = new InMemoryRunner(root, "offline-examples");
        try {
            Session session = runner.sessionService()
                    .createSession(runner.appName(), "offline-user").blockingGet();
            List<Event> events = runner.runAsync(session.userId(), session.id(),
                            Content.fromParts(Part.fromText("执行离线智能体示例")))
                    .toList().timeout(15, TimeUnit.SECONDS).blockingGet();
            // 重新读取已保存的会话，验证输出状态在本次调用结束后仍然存在。
            Session stored = runner.sessionService().getSession(runner.appName(),
                    session.userId(), session.id(), Optional.empty()).blockingGet();
            return new RunResult(Map.copyOf(stored.state()), List.copyOf(events));
        } finally {
            runner.close().blockingAwait(5, TimeUnit.SECONDS);
        }
    }

    // 显式声明结果类型和访问方法，不依赖记录类自动生成成员。
    static final class RunResult {

        private final Map<String, Object> state;
        private final List<Event> events;

        private RunResult(Map<String, Object> state, List<Event> events) {
            this.state = state;
            this.events = events;
        }

        Map<String, Object> getState() {
            return state;
        }

        List<Event> getEvents() {
            return events;
        }
    }
}
