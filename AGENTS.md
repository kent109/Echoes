# Echoes

Spring Boot 后端服务。Maven 多模块：根 `echoes-parent`（packaging=pom）聚合唯一的应用模块 `echoes`。

## 换行符

仓库内存放 LF，工作树一律 CRLF，由 `.gitattributes` 强制。新建文本文件不要留下 LF 结尾。
例外（保持 LF）：`*.sh`、`Dockerfile`、`*.yml`、`*.yaml`——这些会被 Linux 容器或 CI 读取，`\r` 会导致 `bad interpreter` 之类的问题。

所有文本文件必须以换行符结尾。末尾缺换行会让行尾转换静默失效：文件里会留下一个孤立的 `\r`（不跟 `\n`），Git 的 safe-crlf 机制判定"转换会丢信息"，把该文件当二进制处理，于是 `text=auto` 不生效、`git add --renormalize` 会把 CRLF 原始字节直接写进 index。

## 构建与运行

- 构建：根目录执行 `mvn clean package`，产物在 `echoes/target/`
- 运行：`java -jar echoes/target/echoes-0.0.1-SNAPSHOT.jar`
- 启动前必须设置环境变量 `POSTGRES_PASSWORD`。`application.yml` 用的是 `${POSTGRES_PASSWORD}` 占位，没配会在启动时直接报 `Could not resolve placeholder`，不会静默连错库。
- 本地 PostgreSQL 18：Windows 服务 `postgresql-x64-18`，监听 5432，库名 `echoes`（需先 `CREATE DATABASE echoes`，Hibernate 只建表不建库），`psql` 在 `D:\Program Files\PostgreSQL\18\bin`
- `ddl-auto: update` 由 Hibernate 自动建表/加列，当前是开发期策略

## 运行时约束

框架是 Spring Boot 4.1.2 + Java 25，其自身 jar 是 class file 61（最低 JRE 17），这一点无法通过编译参数绕过。

交付目标机目前只有 JRE 8，直接 `java -jar` 会抛 `UnsupportedClassVersionError`。

## 本机工具链

- JDK 25
- JDK 8
- Maven 3.6.3
- 编译仍用 JDK 25 的 javac，靠 `maven.compiler.source/target` 控制目标版本，无需切换 JDK
