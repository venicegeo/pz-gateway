# pz-gateway
The pz-gateway project handles all user-facing requests to Piazza via REST endpoints. The purpose of this component is to allow for external users to be able to interact with Piazza data, services, events, and other core Piazza functionality.

## Requirements
Before building and running the pz-gateway project, please ensure that the following components are available and/or installed, as necessary:
- [Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (JDK for building/developing, otherwise JRE is fine)
- [Maven (v3 or later)](https://maven.apache.org/install.html)
- [Git](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git) (for checking out repository source)
- [Eclipse](https://www.eclipse.org/downloads/), or any maven-supported IDE
- [RabbitMQ](https://www.rabbitmq.com/download.html)
- [ElasticSearch](https://www.elastic.co/)
- [Amazon S3](https://docs.aws.amazon.com/AmazonS3/latest/gsg/GetStartedWithS3.html) bucket access
- Access to Nexus is required to build

Ensure that the nexus url environment variable `ARTIFACT_STORAGE_URL` is set:

	$ export ARTIFACT_STORAGE_URL={Artifact Storage URL}

For additional details on prerequisites, please refer to the Piazza Developer's Guide [Core Overview](https://github.com/venicegeo/pz-docs/blob/master/documents/devguide/02-pz-core.md) or [Piazza Gateway](https://github.com/venicegeo/pz-docs/blob/master/documents/devguide/07-pz-gateway.md) sections. Also refer to the [prerequisites for using Piazza](https://github.com/venicegeo/pz-docs/blob/master/documents/devguide/03-jobs.md) section for additional details.


***
## Setup, Configuring & Running

### Setup
Create the directory the repository must live in, and clone the git repository:

    $ mkdir -p {PROJECT_DIR}/src/github.com/venicegeo
	$ cd {PROJECT_DIR}/src/github.com/venicegeo
    $ git clone git@github.com:venicegeo/pz-gateway.git
    $ cd pz-gateway

>__Note:__ In the above commands, replace {PROJECT_DIR} with the local directory path for where the project source is to be installed.

### Configuring
As noted in the Requirements section, to build and run this project, RabbitMQ, ElasticSearch, and S3 Buckets are required. The `application.properties` file controls URL information for these components it connects to - check if any port or host information needs to change for certain components. Since the Gateway proxies to all internal Piazza components, then depending on what you are attempting to debug locally, you may need to set one or many of these parameters. For example, if you are debugging `pz-ingest` as well, then you would change the `ingest.url` property in `application.properties` to your own local machine.

To edit the port that the service is running on, edit the `server.port` property.

### Building & Running locally

To build and run the Gateway service locally, pz-gateway can be run using Eclipse any maven-supported IDE. Alternatively, pz-gateway can be run through command line interface (CLI), by navigating to the project directory and run:

    $ mvn clean install -U spring-boot:run

With optional parameters:

	$ java -jar target/piazza-gateway-1.0.0.jar --jobmanager.prefix=localhost --servicecontroller.port=8088 --servicecontroller.prefix=localhost --servicecontroller.protocol=http --ingest.url=http://localhost:8084 --access.url=http://localhost:8085

This will run a Tomcat server locally with the Gateway service running on port 8081.

> __Note:__ This Maven build depends on having access to the `Piazza-Group` repository as defined in the `pom.xml` file. If your Maven configuration does not specify credentials to this Repository, this Maven build will fail.

