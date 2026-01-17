FROM amazoncorretto:17

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

WORKDIR /erii
COPY build/libs/erii-all.jar erii-all.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar erii-all.jar"]
