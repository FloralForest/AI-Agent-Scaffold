# 运行时与 Maven 的 Java 17 编译目标保持一致。
FROM eclipse-temurin:17-jre-jammy

# 配置
ENV PARAMS=""

# 时区
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 文件名与 app 模块的 Maven finalName 保持一致。
COPY target/AI-Agent-Scaffold-app.jar /AI-Agent-Scaffold-app.jar

# JVM 参数必须放在 -jar 前；exec 让 Java 接收容器停止信号。
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /AI-Agent-Scaffold-app.jar $PARAMS"]
