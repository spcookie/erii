FROM eclipse-temurin:17

ENV TZ=Asia/Shanghai

WORKDIR /erii

COPY ./erii-core/build/install/erii-core/bin ./bin
COPY ./erii-core/build/install/erii-core/lib ./lib

RUN chmod +x ./bin/erii-core

VOLUME /erii/plugins
VOLUME /erii/rules
VOLUME /erii/souls
VOLUME /erii/store
VOLUME /erii/logs
VOLUME /erii/config
VOLUME /erii/application.conf

EXPOSE 8080 8082

ENTRYPOINT ["sh", "-c", "exec ./bin/erii-core"]