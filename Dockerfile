FROM maven:3-jdk-11-slim AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml install
RUN mvn -f /usr/src/app/pom.xml dependency:copy-dependencies

FROM openjdk:11-jre-slim

COPY --from=build /usr/src/app/target/jar-dependencies/* /deployments/java/
COPY --from=build /usr/src/app/target/*.jar /deployments/java/

ENTRYPOINT ["java","-cp","/deployments/java/*","org.kathra.UserSynchronizer"]