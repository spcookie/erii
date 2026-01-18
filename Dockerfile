FROM amazoncorretto:17-alpine

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

WORKDIR /erii
COPY build/libs/erii-all.jar erii-all.jar

VOLUME /erii/store

EXPOSE 8080
EXPOSE 8082
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar erii-all.jar"]
