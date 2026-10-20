package com.AI.test.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("模型示例配置离线测试")
class ModelExampleSettingsTest {

    private static final String TEST_KEY = "offline-only-key";

    @Test
    @DisplayName("真实模型开关关闭时拒绝创建配置")
    void disabledConfigurationShouldFailBeforeReadingCredentials() {
        assertThatThrownBy(() -> ModelExampleSettings.create(false, null, null, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("开关未开启");
    }

    @Test
    @DisplayName("读取配置并去除多余空白及地址末尾斜杠")
    void validConfigurationShouldBeNormalized() {
        ModelExampleSettings settings = ModelExampleSettings.create(true,
                " https://model.invalid/v1/// ", " " + TEST_KEY + " ", " offline-model ");

        assertThat(settings.getBaseUrl()).isEqualTo("https://model.invalid/v1");
        assertThat(settings.getApiKey()).isEqualTo(TEST_KEY);
        assertThat(settings.getModel()).isEqualTo("offline-model");
    }

    @Test
    @DisplayName("地址、密钥、模型名称均不能为空")
    void requiredConfigurationShouldNotBeBlank() {
        assertThatThrownBy(() -> ModelExampleSettings.create(true, " ", TEST_KEY, "offline-model"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("接口地址");
        assertThatThrownBy(() -> ModelExampleSettings.create(true, "https://model.invalid/v1", "", "offline-model"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("模型密钥");
        assertThatThrownBy(() -> ModelExampleSettings.create(true, "https://model.invalid/v1", TEST_KEY, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("模型名称");
    }

    @Test
    @DisplayName("拒绝非 HTTPS 地址及包含账号、查询参数或片段的地址")
    void unsafeBaseUrlShouldBeRejected() {
        String[] urls = {"http://model.invalid/v1", "https://user@model.invalid/v1",
                "https://model.invalid/v1?key=value", "https://model.invalid/v1#fragment"};
        for (String url : urls) {
            assertThatThrownBy(() -> ModelExampleSettings.create(true, url, TEST_KEY, "offline-model"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
        }
    }

    @Test
    @DisplayName("配置对象转为文本时不暴露密钥")
    void textRepresentationShouldNotExposeCredentials() {
        ModelExampleSettings settings = ModelExampleSettings.create(true,
                "https://model.invalid/v1", TEST_KEY, "offline-model");

        assertThat(settings.toString()).contains("凭据已隐藏").doesNotContain(TEST_KEY);
    }
}
