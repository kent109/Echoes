# Echoes

Spring Boot 后端服务。Maven 多模块：根 `echoes-parent`（packaging=pom）聚合唯一的应用模块 `echoes`。

## 换行符

仓库内存放 LF，工作树一律 CRLF，由 `.gitattributes` 强制。新建文本文件不要留下 LF 结尾。
例外（保持 LF）：`*.sh`、`Dockerfile`、`*.yml`、`*.yaml`——这些会被 Linux 容器或 CI 读取，`\r` 会导致 `bad interpreter` 之类的问题。

所有文本文件必须以换行符结尾。末尾缺换行会让行尾转换静默失效：文件里会留下一个孤立的 `\r`（不跟 `\n`），Git 的 safe-crlf 机制判定"转换会丢信息"，把该文件当二进制处理，于是 `text=auto` 不生效、`git add --renormalize` 会把 CRLF 原始字节直接写进 index。

## 构建与运行

- 构建：根目录执行 `mvn clean package`，产物在 `echoes/target/`
- 运行：`java -jar echoes/target/echoes-0.0.1-SNAPSHOT.jar`
- 启动前必须设置环境变量 `POSTGRES_PASSWORD`。占位符解析不出来的值不会报错，而是把字面量 `${POSTGRES_PASSWORD}` 原样当密码传给 PG，启动时以 `Password 认证失败` 告终（Hikari 启动即建连）——起不来，但报错信息不会告诉你是占位符没配。
- 本地 PostgreSQL 18：Windows 服务 `postgresql-x64-18`，监听 5432，库名 `echoes`（需先 `CREATE DATABASE echoes`，Hibernate 只建表不建库），`psql` 在 `D:\Program Files\PostgreSQL\18\bin`
- `ddl-auto: update` 由 Hibernate 自动建表/加列，当前是开发期策略

## Spring AI

版本由根 pom 的 `spring-ai-bom`（2.0.2）统一管理，它是 2.x 线的最新稳定版；2.1.0 目前只有 RC，基线是 Boot 4.2，不要引。

`spring.ai.model.chat` 是单选开关，不是多选：置为 `openai` 时 Ollama 的 ChatModel 自动配置整个退出（反之亦然），未设置则两个提供方都启用、注入 `ChatModel` 会因歧义而失败。默认走 `openai`，本地无 key 调试时设 `AI_CHAT_MODEL=ollama`。

`OPENAI_API_KEY` 只从进程外部注入（yml 里不留明文）。写成 `${OPENAI_API_KEY:}` 的冒号不能省：不带空默认值时，字面量 `${OPENAI_API_KEY}` 会被当成 token 发出去，得到的是 `UnauthorizedException: 401` 而不是缺配置的提示。key 缺失或错误都不影响启动（模型 bean 不校验凭据，只在请求时由 SDK 报错），跟 datasource 那种启动即失败的坑方向相反。连本地 OpenAI 兼容网关时保持空值即可。Ollama 默认 `http://localhost:11434`，无需配置。

Windows 上设置方式：`setx OPENAI_API_KEY "..."`（只对之后新起的进程生效，IDEA 需要重启才能把它传给子进程）、IDEA Run Configuration 的 Environment variables，或者 VM options `-DOPENAI_API_KEY=...`（必须写在 `-jar` 之前）。三者实测都能被 `${OPENAI_API_KEY:}` 解析到——占位符走 `Environment`，而它同时包含 systemProperties 和 systemEnvironment，且 system property 优先级更高，两者同时存在时 `-D` 盖掉环境变量。代价是命令行参数在 Windows 上全局可读（`wmic process get CommandLine`、任务管理器都能看到），比环境变量更暴露；写进 IDEA 共享的 `.idea/runConfigurations/*.xml` 等于把密钥提交进仓库。

### 会话记忆

消息存在 `SPRING_AI_CHAT_MEMORY` 表（`JdbcChatMemoryRepository`），键是客户端传来的 `conversationId`，服务端不生成也不保存会话。

两个坑：`spring.ai.chat.memory.repository.jdbc.initialize-schema` 默认 `EMBEDDED`，只给内嵌库建表——PG 必须显式置 `always`，否则第一次写记忆就撞不存在的表；表里 `conversation_id` 是 `VARCHAR(36)`，超长要到 insert 才报错，入口按 36 校验。窗口默认只带最近 20 条消息。

写路径是整窗重插（`SELECT` 读旧窗口 → `DELETE` 该会话 → `INSERT` 全部），不是追加。后果有两个：同一 `conversationId` 并发请求会互相踩；流式端点被客户端中途断开时，实测只落 `USER` 一行、`ASSISTANT` 丢失，下次请求模型会看到两条连续的 user 消息且那段没答完的问题凭空消失。

### 工具调用与记忆

框架不会把工具交互写进记忆，两层都堵死了：`saveAll()` 过滤掉 `ToolResponseMessage` 和带 toolCalls 的 `AssistantMessage`；表只有 `content/type/timestamp/sequence_id/conversation_id`，没有 tool call id、工具名、入参列，读回来时 `MessageRowMapper` 对 `TOOL` 直接返回 `null`。挂不到记忆 advisor 上重试：`MessageChatMemoryAdvisor` 的 order 是 `Integer.MIN_VALUE+200`，`ToolCallingAdvisor` 是 `+300`，工具那一整轮发生在记忆 advisor 的视野之外，它的 `before/after` 只会看见 USER 和最终 ASSISTANT 文本。

项目的做法是在工具层记账（`com.gemform.be.echoes.tool`）：`MemoryRecordingToolCallback` 执行完委托工具，把 `[tool] 名(入参) -> 结果` 作为 `AssistantMessage` 写进记忆，结果截断到 500 字符（记忆窗口会把每行原样回放给模型）。注册走 `MemoryRecordingTools.wrap(chatMemory, toolObject)`，先由 `MethodToolCallbackProvider` 拿到原始 callback 再逐个包装。工具清单在 `ChatClientConfiguration#chatToolCallbacks` 组装成一个 `ToolCallback[]` bean，`ChatClient` 自身不带默认工具——由 `ChatController` 按请求里的 `useTools`（缺省视为 true）决定这次 `.tools(...)` 挂不挂。清单是每轮请求都原样发给模型的，模型自己决定要不要发 tool_call，服务端不需要"判断要不要用工具"这种分支；真要把工具摘掉只有两种正当场景：调用方不该有某个能力（这时权限要由服务端根据身份判定，不能信客户端传来的 `useTools`），或工具数量多到 schema 挤占上下文。注意同一个名字既进 `defaultTools` 又出现在单次 `.tools(...)` 会在构建请求时抛 `IllegalStateException: Multiple tools with the same name`，直接 500。

会话 id 从 `ToolContext` 取，键沿用 `ChatMemory.CONVERSATION_ID`，必须由调用方 `.toolContext(Map)` 显式传——`DefaultToolCallingManager.buildToolContext()` 只读 `ToolCallingChatOptions.getToolContext()`，advisor 参数不会合并进来。这个键会原样进 `ToolContext` 但不会出到网络请求：`OpenAiChatOptions` 只在 `mutate()` 复制它，不进 `ChatCompletionRequest`。要是以后需要把记忆里的工具摘要与真实 `TOOL` 行对上，就得自配键名。

## 运行时约束

框架是 Spring Boot 4.1.2 + Java 25，其自身 jar 是 class file 61（最低 JRE 17），这一点无法通过编译参数绕过。

交付目标机目前只有 JRE 8，直接 `java -jar` 会抛 `UnsupportedClassVersionError`。

## 本机工具链

- JDK 25
- JDK 8
- Maven 3.6.3
- 编译仍用 JDK 25 的 javac，靠根 pom 的 `java.version`（starter-parent 会转成 `maven.compiler.release`）控制目标版本，无需切换 JDK
