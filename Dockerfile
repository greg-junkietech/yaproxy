FROM openjdk:11-jre

WORKDIR /app

ADD target/${project.build.finalName}-jar-with-dependencies.jar app.jar
# ADD target/yaproxy-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
# ADD config.yml .

ENTRYPOINT ["java", "-jar", "app.jar", "config.yml"]