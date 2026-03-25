FROM eclipse-temurin:17

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

WORKDIR /erii
COPY erii-core/build/install/erii-core .
COPY erii-plugins/build/plugins ./plugins
RUN chmod +x /erii/bin/erii
COPY fonts/*.ttc /usr/share/fonts/truetype/custom/
RUN fc-cache -f -v

VOLUME /erii/store

EXPOSE 8080
EXPOSE 8082

ENTRYPOINT ["sh", "-c", "exec ./bin/erii"]