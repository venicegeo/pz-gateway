# Gateway
Component that handles all user-facing requests via REST endpoints. 

## Setup

For local machine testing, run the following Maven command:

```
mvn spring-boot:run
```

POST requests handled via _http://localhost:8080/job_

Sample request payload:

```
{
	"apiKey": "my-api-key-38n987",
	"job": {
		"type": "get",
		"jobId": "9f87sj879"
	}
}
```

## JSON Payload Format

See the GEOINT Services RedMine files for UML Diagrams on JSON Interfaces.
