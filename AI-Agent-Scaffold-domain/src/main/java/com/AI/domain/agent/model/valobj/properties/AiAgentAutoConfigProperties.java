package com.AI.domain.agent.model.valobj.properties;

import com.AI.domain.agent.model.valobj.AiAgentConfigTableVO;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 绑定 ai.agent.config 前缀下的智能体定义。
 * tables 保存待装配的智能体配置；enabled 当前尚未被启动装配监听器检查。
 */
@Data
@ConfigurationProperties(prefix = "ai.agent.config", ignoreInvalidFields = true)
public class AiAgentAutoConfigProperties {

    private boolean enabled = false;

    private Map<String, AiAgentConfigTableVO> tables;
}
