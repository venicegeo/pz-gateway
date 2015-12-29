# Gateway

Handles all user-facing requests to Piazza via REST endpoints. The purpose of this component is to allow for external users to be able to create jobs such as ingesting data or executing services, and to provide the mechanisms for querying the status of submitted jobs. 

Upon validation and authentication of a submitted request, the Gateway communicates directly with the [Dispatcher](https://github.com/venicegeo/pz-dispatcher) component, which internally routes messages to the appropriate Piazza components. 

## Vagrant

Run ``vagrant up`` to start the Gateway REST service. The service will be accessible via http://gateway.dev:8081. This machine's functionality depends on the [Dispatcher](https://github.com/venicegeo/pz-dispatcher) and [Job Manager](https://github.com/venicegeo/pz-jobmanager) components also being running their own Vagrant machines.

## Interface

Once running, the Job service will be accessible via the http://gateway.dev:8081/job address. Requests take on the form of a JSON Payload that is POSTED to this endpoint.

## Authentication and Authorization

Each request will contain the API Key of the submitting user. This API Key will be used to authenticate the users permission to access the Piazza system, and authorize their access to the requested resources. Every request that is sent to the Gateway, without exception, must contain an API key. 

### Job Submission

Each Job sent to the Piazza system through the Gateway is assigned a UUID. This UUID is then used to uniquely identify the Job within the system.

In every case, the bare minimum of the response from the Job Endpoint will be a JSON object containing the Job ID of the submnitted request. Since many of the Jobs requested will be long-running processes, the service will immediately return a Job ID. This Job ID can then be used to check the status of the long-running Job process in order to get information such as status, progress, or time remaining. 

For instance 

```
{
	"type": "job"
	"jobId": "784c11b2-0426-490c-9fde-986f16fc2bfb"
}
```

### Job Types

Below are the Job requests that can be sent to the Gateway. This list will be updated as new Job types become supported. 

#### Job Status

Once a Job has been submitted, and a UUID has been received by the submitting client, the UUID can then be used in order to retrieve the current status of that Job. The response to this request will contain the current status of the Job, and if available, the result of the Job. Results of Jobs may vary, and could be anything from a simple number to a large GeoTIFF, so the result for a Job is dependant on the type of Job that submitted it. In some cases, the result of a Job may be a resource URL (such as a WFS or WMS) or UUID that can be used to access that resource from another endpoint.

```
{
	"apiKey": "my-api-key-38n987",
	"jobType": {
		"type": "get",
		"jobId": "8504ceff-2af6-405b-bd8a-6804e7759676"
	}
}
```

Job Status is queried through the ``get`` Job type. The ``jobId`` is then specified. It is important to note that the ``get`` Job type does not produce a new Job. It is a synchronous fetching of a current Job's status; thus creating a Job of type ``get`` will not result in the creation of a new Job.

```
{
	"type": "status",
	"type": "job",
	"jobId": "8504ceff-2af6-405b-bd8a-6804e7759676",
	"ready": false,
	"status": "Submitted",
	"progress": {
		"percentComplete": null,
		"timeRemaining": null,
		"timeSpent": null
	}
}
```

The resulting JSON will contain the current status information for that Job. The ``ready`` attribute can be checked to determine if the Job's result is done yet. The ``status`` and ``progress`` objects contain information to the Job's current status. 


#### Job Abort

Users who submit a Job that is currently running, can request that Job be cancelled using the ``abort`` Job type. This will dispatch the event throughout the Piazza application that all components handling this Job should stop immediately. Since the cancelling of a Job can potentially be a long-running process, this ``abort`` Job type will have a new Job created for the abort request. Thus, the ``abort`` Job request will merely return a new Job ID, which can then be submitted via the ``get`` Job to check the status of the abort request.

```
{
	"apiKey": "my-api-key-38n987",
	"jobType": {
		"type": "abort",
		"jobId": "8504ceff-2af6-405b-bd8a-6804e7759676"
	}
}
```

#### Ingest

This is used for ingesting data into the Piazza Ingest component. This will spawn off a job to parse the appropriate data information from the JSON payload, and begin and Ingest Job. Since ingesting large datasets could take a long period of time, this request will synchronously return the Job ID which can then be queried for updates on the status of the Job. 

```
{
	"apiKey": "my-api-key-38n987",
	"jobType": {
		"type": "ingest",
		"data" : {
			"key": "value"
		}
	}
}
```
