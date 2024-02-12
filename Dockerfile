FROM openjdk:21
COPY ./out/production/WorkloadCompactor_improvement /program
WORKDIR /program
ENTRYPOINT ["java", "Servers.Server4"]
EXPOSE 6837