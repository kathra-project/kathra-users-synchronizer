FROM openjdk:11-jdk-slim

COPY target/jar-dependencies/* /deployments/java/
COPY target/*.jar /deployments/java/

ENTRYPOINT ["java","-cp","/deployments/java/*","org.kathra.UserSynchronizer"]