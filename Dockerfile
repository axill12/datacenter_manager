FROM openjdk:21
COPY ./out/production/WorkloadCompactor_improvement /program
WORKDIR /program
ENTRYPOINT ["java", "Server1"]
EXPOSE 6834