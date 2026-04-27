FROM eclipse-temurin:17

ENV TZ=Asia/Shanghai

WORKDIR /erii

COPY ./erii-core/build/install/erii-core/bin ./bin
COPY ./erii-core/build/install/erii-core/lib ./lib

RUN chmod +x ./bin/erii-core

VOLUME /erii/conf
VOLUME /erii/store
VOLUME /erii/erii-cli

EXPOSE 8000 8080 8082

ENTRYPOINT ["sh", "-c", "exec ./bin/erii-core"]