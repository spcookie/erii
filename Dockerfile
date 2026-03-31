FROM eclipse-temurin:17

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

WORKDIR /erii
COPY erii-core/build/install/erii-core .
COPY erii-plugins/build/plugins ./plugins
RUN chmod +x ./bin/erii-core

VOLUME /erii/store
VOLUME /erii/souls
VOLUME /erii/rules
VOLUME /erii/logs

EXPOSE 8080
EXPOSE 8082

ENTRYPOINT ["sh", "-c", "exec ./bin/erii-core"]