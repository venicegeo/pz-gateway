## Running pz-gateway locally

To run the Gateway service locally (without Vagrant), perhaps through Eclipse or through CLI, navigate to the project directory and run

    mvn clean install -U spring-boot:run

	With optional parameters:
	java -jar target/piazza-gateway-1.0.0.jar --search.url=http://localhost:8581 --jobmanager.prefix=localhost --servicecontroller.port=8088 --servicecontroller.prefix=localhost --servicecontroller.protocol=http --logger.url=http://192.168.46.46:14600 --workflow.url=http://192.168.50.50:14400 --ingest.url=http://localhost:8084 --access.url=http://localhost:8085

To build and run this project, RabbitMQ, ElasticSearch, and S3 Buckets are required.  For details on these prerequisites, refer to the
[Piazza Developer's Guide](https://pz-docs.geointservices.io/devguide/index.html#_piazza_core_overview).

This will run a Tomcat server locally with the Gateway service running on port 8081.

NOTE: This Maven build depends on having access to the `Piazza-Group` repository as defined in the `pom.xml` file. If your Maven configuration does not specify credentials to this Repository, this Maven build will fail. 

Check the `application.properties` file if any port or host information needs to change for certain components. Since the Gateway proxies to all internal Piazza components, then depending on what you are attempting to debug locally, you may need to set one or many of these parameters. For example, if you are debugging `pz-ingest` as well, then you would change the `ingest.url` property in `application.properties` to your own local machine.
