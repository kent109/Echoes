# 使用官方OpenJDK 25镜像
FROM openjdk:25-jre-slim

# 将jar包复制到镜像中
COPY echoes/target/echoes-0.0.1-SNAPSHOT.jar app.jar

# 暴露端口（必须与application.yml一致）
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java","-Dspring.profiles.active=prod","-Xms256m","-Xmx256m","-jar","app.jar"]
