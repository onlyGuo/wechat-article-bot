# syntax=docker/dockerfile:1.7

ARG NODE_IMAGE=node:20-bookworm-slim
ARG MAVEN_IMAGE=maven:3.9.16-eclipse-temurin-17-noble
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-noble
ARG FRONTEND_BUILD=source
ARG BACKEND_BUILD=source

FROM ${NODE_IMAGE} AS frontend-source
WORKDIR /workspace/webui
ARG TARGETPLATFORM
COPY webui/package.json webui/package-lock.json ./
RUN --mount=type=cache,id=npm-${TARGETPLATFORM},target=/root/.npm,sharing=locked npm ci
COPY webui/ ./
RUN npm run build -- --outDir dist --emptyOutDir

FROM scratch AS frontend-prebuilt
COPY webui/dist/ /workspace/webui/dist/

FROM frontend-${FRONTEND_BUILD} AS frontend

FROM ${MAVEN_IMAGE} AS backend-source
WORKDIR /workspace
ARG TARGETPLATFORM
COPY pom.xml ./
COPY src/ ./src/
COPY --from=frontend /workspace/webui/dist/ ./src/main/resources/static/
RUN --mount=type=cache,id=m2-${TARGETPLATFORM},target=/root/.m2,sharing=locked \
    mvn -B -DskipTests -Dskip.installnodenpm -Dskip.npm package

FROM scratch AS backend-prebuilt
COPY target/article-bot-*.jar /workspace/target/

FROM backend-${BACKEND_BUILD} AS backend

FROM ${RUNTIME_IMAGE} AS runtime
WORKDIR /app

ARG APP_VERSION=dev
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="wechat-article-bot" \
      org.opencontainers.image.description="AI-powered WeChat article management workspace" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}"

ENV SERVER_PORT=8081 \
    STORAGE_PATH=/app/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

RUN mkdir -p /app/data/uploads && chown -R 10001:10001 /app && chmod -R g=u /app
COPY --from=backend --chown=10001:10001 /workspace/target/article-bot-*.jar /app/app.jar

USER 10001:10001
EXPOSE 8081
VOLUME ["/app/data/uploads"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
