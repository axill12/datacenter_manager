FROM openjdk:21
COPY ./out/production/WorkloadCompactor_improvement /program
WORKDIR /program
ENTRYPOINT ["java", "Servers.Server10"]
EXPOSE 6843