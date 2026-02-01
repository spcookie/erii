FROM eclipse-temurin:17

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

WORKDIR /erii
COPY build/libs/install/erii .
RUN chmod +x /erii/bin/erii

VOLUME /erii/store

EXPOSE 8080
EXPOSE 8082
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec /erii/bin/erii"]