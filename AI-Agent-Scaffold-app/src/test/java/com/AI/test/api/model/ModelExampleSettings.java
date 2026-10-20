package com.AI.test.api.model;

import java.net.URI;

/**
 * 模型API配置整合
 * */
final class ModelExampleSettings {

    // 仅供本机手动测试：填好下面三项后，再将开关改为 true，运行示例 main 方法。
    private static final boolean ENABLE_REAL_MODEL = true;
    private static final String BASE_URL = "https://api.edgefn.net/v1";
    // 真实密钥只能在本机填写，不要提交到 Git，也不要输出到日志。
    private static final String API_KEY = "";
    private static final String MODEL_NAME = "DeepSeek-R1-0528-Qwen3-8B";

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    private ModelExampleSettings(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    String getBaseUrl() {
        return baseUrl;
    }

    String getApiKey() {
        return apiKey;
    }

    String getModel() {
        return model;
    }

    static ModelExampleSettings fromCode() {
        return create(ENABLE_REAL_MODEL, BASE_URL, API_KEY, MODEL_NAME);
    }

    // 可选方式：将示例入口改回此方法，即可继续读取原有环境变量。
    static ModelExampleSettings fromEnvironment() {
        return create("true".equalsIgnoreCase(System.getenv("AI_LIVE_TEST")),
                System.getenv("AI_BASE_URL"), System.getenv("AI_API_KEY"), System.getenv("AI_MODEL"));
    }

    static ModelExampleSettings create(boolean enabled, String baseUrl, String apiKey, String model) {
        // 必须手动开启，防止在开发工具中误运行时产生付费请求。
        if (!enabled) {
            throw new IllegalStateException("真实模型开关未开启：代码配置请设置 ENABLE_REAL_MODEL=true，"
                    + "环境变量方式请设置 AI_LIVE_TEST=true");
        }
        String normalizedBaseUrl = required("接口地址 BASE_URL / AI_BASE_URL", baseUrl).replaceAll("/+$", "");
        URI uri = URI.create(normalizedBaseUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("模型接口必须是 HTTPS 基础地址，按厂商要求包含 /v1 等路径前缀，"
                    + "且不能带账号、查询参数或片段");
        }
        return new ModelExampleSettings(normalizedBaseUrl,
                required("模型密钥 API_KEY / AI_API_KEY", apiKey), required("模型名称 MODEL_NAME / AI_MODEL", model));
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("请填写" + name);
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "模型示例配置[凭据已隐藏]";
    }
}
