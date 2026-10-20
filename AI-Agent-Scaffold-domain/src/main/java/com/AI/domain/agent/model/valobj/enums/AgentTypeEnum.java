package com.AI.domain.agent.model.valobj.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 将 YAML 中的工作流类型映射到对应的装配策略节点。 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum AgentTypeEnum {

    Loop("循环执行","loop","loopAgentNode"),
    Parallel("并行执行","parallel","parallelAgentNode"),
    Sequential("串行执行","sequential","sequentialAgentNode"),

    ;

    private String name;
    private String type;
    private String node;

    public static AgentTypeEnum fromType(String type) {
        if (type == null) {
            return null;
        }
        for (AgentTypeEnum value : values()) {
            if (value.getType().equalsIgnoreCase(type)) {
                return value;
            }
        }
        return null;
    }

}
