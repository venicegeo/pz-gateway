/**
 * Copyright 2016, RadiantBlue Technologies, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package gateway.controller;

import gateway.auth.AuthConnector;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import messaging.job.JobMessageFactory;
import messaging.job.KafkaClientFactory;
import model.data.FileRepresentation;
import model.data.location.FileLocation;
import model.data.location.S3FileStore;
import model.job.type.GetJob;
import model.job.type.GetResource;
import model.job.type.IngestJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import util.PiazzaLogger;
import util.UUIDFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Controller that handles the incoming POST requests to the Gateway service.
 * 
 * @author Patrick.Doody
 * 
 */
@RestController
public class GatewayController {
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private UUIDFactory uuidFactory;
	private Producer<String, String> producer;
	private RestTemplate restTemplate = new RestTemplate();
	@Value("${kafka.host}")
	private String KAFKA_HOST;
	@Value("${kafka.port}")
	private String KAFKA_PORT;
	@Value("${kafka.group}")
	private String KAFKA_GROUP;
	@Value("${dispatcher.host}")
	private String DISPATCHER_HOST;
	@Value("${dispatcher.port}")
	private String DISPATCHER_PORT;
	@Value("${s3.bucketname}")
	private String AMAZONS3_BUCKET_NAME;
	@Value("${s3.domain}")
	private String AMAZONS3_DOMAIN;
	@Value("${s3.key.access:}")
	private String AMAZONS3_ACCESS_KEY;
	@Value("${s3.key.private:}")
	private String AMAZONS3_PRIVATE_KEY;

	/**
	 * Initializing the Kafka Producer on Controller startup.
	 */
	@PostConstruct
	public void init() {
		producer = KafkaClientFactory.getProducer(KAFKA_HOST, KAFKA_PORT);
	}

	@PreDestroy
	public void cleanup() {
		producer.close();
	}

	/**
	 * Handles an incoming Piazza Job request by passing it along from the
	 * external users to the internal Piazza components.
	 * 
	 * @param json
	 *            The JSON Payload
	 * @return Response object.
	 */
	@RequestMapping(value = "/job", method = RequestMethod.POST)
	public PiazzaResponse job(@RequestParam(required = true) String body,
			@RequestParam(required = false) final MultipartFile file) {
		// Deserialize the incoming JSON to Request Model objects
		PiazzaJobRequest request;
		try {
			request = JobMessageFactory.parseRequestJson(body);
		} catch (Exception exception) {
			logger.log(String.format("An Invalid Job Request sent to the Gateway: %s", exception.getMessage()),
					PiazzaLogger.WARNING);
			return new ErrorResponse(null, "Error Parsing Job Request: " + exception.getMessage(), "Gateway");
		}

		// Authenticate and Authorize the request
		try {
			AuthConnector.verifyAuth(request);
		} catch (SecurityException securityEx) {
			logger.log("Non-authorized connection to Gateway Blocked.", PiazzaLogger.WARNING);
			return new ErrorResponse(null, "Authentication Error", "Gateway");
		}

		// Determine if this Job is processed via synchronous REST, or via Kafka
		// message queues.
		if ((request.jobType instanceof GetJob) || (request.jobType instanceof GetResource)) {
			return sendRequestToDispatcherViaRest(request);
		} else {
			return sendRequestToDispatcherViaKafka(request, file);
		}
	}

	/**
	 * Forwards the Job request along to internal Piazza components via
	 * synchronous REST calls. This is for cases where the Job is meant to be
	 * processed synchronously and the user wants a response immediately for
	 * their request.
	 * 
	 * @param request
	 *            The Job Request
	 * @return The response object
	 */
	private PiazzaResponse sendRequestToDispatcherViaRest(PiazzaJobRequest request) {
		// REST GET request to Dispatcher to fetch the status of the Job ID.
		// TODO: I would like a way to normalize this.
		String id = null, serviceName = null;
		if (request.jobType instanceof GetJob) {
			id = ((GetJob) request.jobType).getJobId();
			serviceName = "job";
		} else if (request.jobType instanceof GetResource) {
			id = ((GetResource) request.jobType).getResourceId();
			serviceName = "data";
		}
		try {
			PiazzaResponse dispatcherResponse = restTemplate.getForObject(
					String.format("http://%s:%s/%s/%s", DISPATCHER_HOST, DISPATCHER_PORT, serviceName, id),
					PiazzaResponse.class);
			logger.log(String.format("Sent Job %s to Dispatcher %s REST services", id, serviceName), PiazzaLogger.INFO);
			return dispatcherResponse;
		} catch (RestClientException exception) {
			logger.log("Could not relay message to Dispatcher.", PiazzaLogger.ERROR);
			return new ErrorResponse(null, "Error connecting to Dispatcher service: " + exception.getMessage(),
					"Gateway");
		}
	}

	/**
	 * Forwards the Job request along to the internal Piazza components via
	 * Kafka. This is meant for Jobs that will return a job ID, and are
	 * potentially long-running, and are thus asynchronous.
	 * 
	 * @param request
	 *            The Job Request
	 * @param file
	 *            The file being uploaded
	 * @return The response object, which will contain the Job ID
	 */
	private PiazzaResponse sendRequestToDispatcherViaKafka(PiazzaJobRequest request, MultipartFile file) {
		String jobId;
		try {
			// Create a GUID for this new Job from the UUIDGen component
			jobId = uuidFactory.getUUID();
		} catch (RestClientException exception) {
			logger.log("Could not connect to UUID Service for UUID.", PiazzaLogger.ERROR);
			return new ErrorResponse(null,
					"Could not generate Job ID. Core Piazza Components were not found (UUIDGen).", "UUIDGen");
		}

		// If an Ingest job, persist the file to the Amazon S3 filesystem
		if (request.jobType instanceof IngestJob && file != null) {
			try {
				if (((IngestJob) request.jobType).getHost() == true) {
					// Connect to our S3 Bucket
					BasicAWSCredentials credentials = new BasicAWSCredentials(AMAZONS3_ACCESS_KEY, AMAZONS3_PRIVATE_KEY);
					AmazonS3 client = new AmazonS3Client(credentials);
					// The content length must be specified.
					ObjectMetadata metadata = new ObjectMetadata();
					metadata.setContentLength(file.getSize());
					// Send the file. The key corresponds with the S3 file name.
					String fileKey = String.format("%s-%s", jobId, file.getOriginalFilename());
					client.putObject(AMAZONS3_BUCKET_NAME, fileKey, file.getInputStream(), metadata);
					// Note the S3 file path in the Ingest Job. This will be
					// used later to pull the file in the Ingest component.
					IngestJob ingestJob = (IngestJob) request.jobType;
					if (ingestJob.getData().getDataType() instanceof FileRepresentation) {
						// Attach the file to the FileLocation object
						FileLocation fileLocation = new S3FileStore(AMAZONS3_BUCKET_NAME, fileKey, AMAZONS3_DOMAIN);
						((FileRepresentation) ingestJob.getData().getDataType()).setLocation(fileLocation);
						logger.log(String.format("S3 File for Job %s Persisted to %s:%s", jobId, AMAZONS3_BUCKET_NAME,
								fileKey), PiazzaLogger.INFO);
					} else {
						// Only FileRepresentation objects can have a file
						// attached to them. Otherwise, this is an invalid input
						// and an error needs to be thrown.
						return new ErrorResponse(null,
								"The uploaded file cannot be attached to the specified Data Type: "
										+ ingestJob.getData().getDataType().getType(), "Gateway");
					}
				} else {
					return new ErrorResponse(
							null,
							"Invalid input: Host parameter for an Ingest Job cannot be set to false if a file has been specified.",
							"Gateway");
				}
			} catch (AmazonServiceException awsServiceException) {
				logger.log(String.format("AWS S3 Upload Error on Job %s: %s", jobId, awsServiceException.getMessage()),
						PiazzaLogger.ERROR);
				awsServiceException.printStackTrace();
				return new ErrorResponse(null, "The file was rejected by Piazza persistent storage. Reason: "
						+ awsServiceException.getMessage(), "Gateway");
			} catch (Exception exception) {
				logger.log(String.format("Error Processing S3 Upload on Job %s: %s", jobId, exception.getMessage()),
						PiazzaLogger.ERROR);
				exception.printStackTrace();
				return new ErrorResponse(null, "An Internal error was encountered while persisting the file: "
						+ exception.getMessage(), "Gateway");
			}
		}

		// Create the Kafka Message for an incoming Job to be created.
		final ProducerRecord<String, String> message;
		try {
			message = JobMessageFactory.getRequestJobMessage(request, jobId);
		} catch (JsonProcessingException exception) {
			exception.printStackTrace();
			logger.log(String.format("Error Creating Kafka Message for Job %s", jobId), PiazzaLogger.ERROR);
			return new ErrorResponse(jobId, "Error Creating Message for Job", "Gateway");
		}

		// Fire off a Kafka Message and then wait for a ack response from the
		// kafka broker
		try {
			producer.send(message).get();
		} catch (Exception exception) {
			logger.log(String.format("Timeout sending Message for Job %s through Kafka: %s", jobId,
					exception.getMessage()), PiazzaLogger.ERROR);
			return new ErrorResponse(
					jobId,
					"The Gateway did not receive a response from Kafka; the request could not be forwarded along to Piazza.",
					"Gateway");
		}

		logger.log(String.format("Sent Job %s with Kafka Topic %s and Key %s to Dispatcher.", jobId, message.topic(),
				message.key()), PiazzaLogger.INFO);

		// Respond immediately with the new Job GUID
		return new PiazzaResponse(jobId);
	}
}