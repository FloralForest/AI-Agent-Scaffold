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

## Docker 运行环境

基础镜像为 `eclipse-temurin:17-jre-jammy`。在 app 模块目录执行 `sh build.sh`，构建镜像 `system/ai-agent-scaffold:1.0`，与 Compose 和启动脚本保持一致。

父 POM 的 `java_jvm` 使用 Java 17 的 `-Xlog` GC 日志语法；使用文件日志前需准备对应目录并确认写权限。该属性不会自动注入 Docker，额外 JVM 参数通过 `JAVA_OPTS` 传入。
