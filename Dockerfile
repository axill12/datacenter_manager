FROM openjdk:21
COPY ./out/production/WorkloadCompactor_improvement /program
WORKDIR /program
ENTRYPOINT ["java", "Server2"]
EXPOSE 6835