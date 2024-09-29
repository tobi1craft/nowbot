FROM amazoncorretto:23-alpine
WORKDIR /app
COPY build/libs/nowbot*.jar nowbot.jar

ENTRYPOINT ["java", "-jar", "nowbot.jar"]