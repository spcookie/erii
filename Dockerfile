FROM eclipse-temurin:17

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

WORKDIR /erii
COPY build/install/erii .
RUN chmod +x /erii/bin/erii
COPY fonts/*.ttc /usr/share/fonts/truetype/custom/
RUN fc-cache -f -v

VOLUME /erii/store

EXPOSE 8080
EXPOSE 8082
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec /erii/bin/erii"]