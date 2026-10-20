# xfg-frame-archetype - DDD 脚手架

## Java 环境

- 开发、Maven 构建、测试与运行统一使用 JDK 17；`JAVA_HOME` 指向 JDK 17。
- 在项目根目录运行 `java -version` 和 `mvn -version`，两者均应显示 Java 17。
- IDEA 的 Project SDK、Language Level、Maven Importer JDK 和 Maven Runner JRE 均选择 17。
- 所有子模块继承父 POM 的 `maven.compiler.release=17`，不要再单独配置 Java 8 的 source/target。

```shell
mvn -DskipTests package
java -jar AI-Agent-Scaffold-app/target/AI-Agent-Scaffold-app.jar
```

`-DskipTests` 只跳过测试执行，不代表测试通过；应用启动所需配置和外部依赖需另行准备。

## AI 框架依赖

父 POM 管理版本，`AI-Agent-Scaffold-app` 引入实际依赖；其他业务模块不会自动引入三套 AI SDK。

| 组件 | 版本 | app 依赖 |
| --- | --- | --- |
| Spring Boot | 3.5.16 | Java 17 / Spring 6 基础环境 |
| Spring AI | 1.1.5 | `spring-ai-model`、`spring-ai-openai`、`spring-ai-mcp` |
| LangChain4j | 1.12.2 | `langchain4j`、`langchain4j-open-ai` |
| Google ADK | 0.7.0 | `google-adk`、`google-adk-spring-ai`，核心与适配器统一版本 |
| MCP SDK | 0.17.2 | 由 BOM 统一；`spring-ai-mcp` 负责 Spring AI 工具适配 |
| 策略树工具 | 3.3.0 | `xfg-wrench-starter-design-framework`，串联智能体装配节点 |

Spring AI 使用模型与工具基础 API，未引入 ChatClient 或厂商 Starter，避免与 ADK 的 MCP 依赖混用；LangChain4j 版本与本项目 Jackson 2.21 对齐。两个 OpenAI 兼容协议依赖是普通 SDK，不会自动创建模型 Bean；ADK 核心库也自带部分模型 SDK，但没有配置模型、密钥、Agent Bean 或开发调试 Web 服务。

后续需要调用模型时，再按选定框架添加对应模型实现，并从环境变量或密钥管理服务读取凭据。不能直接复制另一个框架的 Starter 而忽略依赖冲突。

项目默认跳过测试；下列命令仅执行框架离线回归，不连接模型、数据库或外部服务：

```shell
mvn -pl AI-Agent-Scaffold-app -am "-DskipTests=false" "-Dtest=FrameworkDependencyTest,FrameworkCompatibilityTest,SequentialAgentTest,ParallelAgentTest,LoopAgentTest,ModelExampleSettingsTest,SpringAiConsoleTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### 智能体示例

- `SequentialAgentTest`：写入初稿、审查、重构，验证先后顺序和 `outputKey` 状态传递。
- `ParallelAgentTest`：三个独立研究分支并发运行，每个分支使用不同状态键，避免相互覆盖。
- `LoopAgentTest`：初稿后循环审查和改写；审批通过时提前退出，即使一直未通过也最多循环 5 次。
- `OfflineAgentSupport`：使用真实 ADK 编排和内存会话，只替换外部模型响应；单次示例设有 15 秒超时。

这些是编排回归示例，不代表真实模型的输出质量、工具使用或在线并发能力已通过验证。ADK 与 Spring AI 的模型桥接使用官方 `SpringAI` 适配器；兼容回归以模拟模型、模拟 MCP 客户端及不连接服务的传输器构建验证依赖兼容，不执行在线模型或外部工具调用。

当前保留 Java 17 和 Boot 3.5。Spring AI 1.1.5、ADK 核心及适配器 0.7.0、MCP 0.17.2 为一起验证的版本组合，不应只升级其中一个：Spring AI 1.1.6+ 引入 MCP 0.18，移除了 ADK 使用的 JSON 工厂接口；较新 ADK 适配器则面向 Spring AI 2 / Boot 4。适配包排除 `google-adk-dev`，不引入开发调试 Web 服务。策略树工具是包含旧框架类的聚合 JAR，`domain` 显式先声明 Boot 基础依赖，并由兼容测试检查实际加载的 Spring 和日志版本。

示例使用明确的变量类型和普通结果类，结果通过 `getState()`、`getEvents()` 读取；模型配置同样提供显式访问方法，不依赖 `var` 推断或 `record` 自动生成成员。注释、运行提示和测试展示名称使用中文，框架 API、标识符及协议字段保持原样，项目仍须使用 Java 17。

### 手动调用模型

`SpringAiApiTest` 和 `LangChain4jApiTest` 保留 `main` 入口，默认从 `ModelExampleSettings.fromCode()` 读取配置，不要求在 IDEA 中配置环境变量。

在 `ModelExampleSettings` 类顶部修改以下四项，然后直接运行任一模型示例的 `main`：

| 代码配置 | 用途 |
| --- | --- |
| `ENABLE_REAL_MODEL` | 填好其他三项后，显式改为 `true` 才允许调用 |
| `BASE_URL` | 模型服务的 HTTPS 接口基础地址，按厂商要求包含 `/v1` 等前缀，不含 `/chat/completions` |
| `API_KEY` | 本机填写的真实密钥，默认留空，不要提交到 Git |
| `MODEL_NAME` | 该服务实际支持、且账号有权限使用的模型名称，默认留空 |

代码内填写密钥仅供本机临时调试；正式业务仍应使用环境变量或密钥管理服务。配置对象转为文本时会隐藏凭据，但这不能防止源码泄露。

原有环境变量方式仍保留：将示例里的 `ModelExampleSettings.fromCode()` 改回 `ModelExampleSettings.fromEnvironment()`，再配置以下变量。两种方式分别读取，不会互相覆盖：

| 环境变量 | 用途 |
| --- | --- |
| `AI_LIVE_TEST` | 显式设为 `true` 才允许调用 |
| `AI_BASE_URL` | HTTPS API 基础地址，含厂商要求的前缀，例如 `/v1`，不含 `/chat/completions` |
| `AI_API_KEY` | 模型服务密钥，不写入源码或提交到 Git |
| `AI_MODEL` | 该服务实际支持的模型名称 |

Maven 离线回归不会执行这两个 `main`，即使机器已设置密钥也不会调用模型。手动运行会真实请求服务，可能产生费用。

`SpringAiApiTest.main()` 为终端循环对话：输入问题后按回车，等待完整回复后继续输入；保留最近 10 轮成功问答作为上下文。空输入不发送请求，`/clear` 清空上下文，`/exit`、`exit`、`quit` 或 `退出` 结束。请求失败或回复为空时仍可继续输入，该轮不会记入上下文；异常提示不会输出厂商响应原文。`LangChain4jApiTest` 仍是单次调用示例。

## Docker 运行环境

基础镜像为 `eclipse-temurin:17-jre-jammy`。在 app 模块目录执行 `sh build.sh`，构建镜像 `system/ai-agent-scaffold:1.0`，与 Compose 和启动脚本保持一致。

父 POM 的 `java_jvm` 使用 Java 17 的 `-Xlog` GC 日志语法；使用文件日志前需准备对应目录并确认写权限。该属性不会自动注入 Docker，额外 JVM 参数通过 `JAVA_OPTS` 传入。
