FROM fnproject/fn-java-fdk-build:jdk17-1.0.207 as build-stage
WORKDIR /function
ENV MAVEN_OPTS -Dhttp.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyHost= -Dhttps.proxyPort= -Dhttp.nonProxyHosts= -Dmaven.repo.local=/usr/share/maven/ref/repository
ADD pom.xml /function/pom.xml
RUN ["mvn", "package", "dependency:copy-dependencies", "-DincludeScope=runtime", "-DskipTests=true", "-Dmdep.prependGroupId=true", "-DoutputDirectory=target", "--fail-never"]
ADD src /function/src
RUN ["mvn", "package"]
FROM fnproject/fn-java-fdk:jre17-1.0.207
WORKDIR /function
COPY --from=build-stage /function/target/*.jar /function/app/
ADD func.yaml /
RUN chmod 777 /func.yaml
CMD ["com.example.fn.HelloFunction::handleRequest"]

