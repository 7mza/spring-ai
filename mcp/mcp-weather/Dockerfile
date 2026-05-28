FROM eclipse-temurin:25-jre-alpine AS builder
WORKDIR /workspace
COPY ./build/libs/*.jar app.jar
RUN mkdir extracted
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted --launcher
FROM eclipse-temurin:25-jre-alpine
RUN apk update && apk add nodejs npm
RUN npm install -g npm@latest
RUN npm cache clean --force && apk cache clean && rm -rf /var/cache/apk/*
WORKDIR /application
COPY --from=builder /workspace/extracted/dependencies/ ./
COPY --from=builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/ ./
EXPOSE 3000
ENTRYPOINT ["npx", "--yes", "supergateway", "--stdio", "java org.springframework.boot.loader.launch.JarLauncher", "--outputTransport", "streamableHttp", "--port", "3000", "--healthEndpoint", "/health", "--logLevel", "none"]
