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

import gateway.controller.util.PiazzaRestController;

import java.security.Principal;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import messaging.job.JobMessageFactory;
import messaging.job.KafkaClientFactory;
import model.data.FileRepresentation;
import model.data.location.FileLocation;
import model.data.location.S3FileStore;
import model.job.PiazzaJobType;
import model.job.type.GetJob;
import model.job.type.GetResource;
import model.job.type.IngestJob;
import model.job.type.SearchMetadataIngestJob;
import model.job.type.SearchQueryJob;
import model.request.FileRequest;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Controller that handles the incoming POST requests to the Gateway service.
 * 
 * This controller is now legacy.
 * 
 * @author Patrick.Doody, Russell.Orf
 * 
 */
@CrossOrigin
@RestController
@Deprecated
public class LegacyController extends PiazzaRestController {
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private UUIDFactory uuidFactory;
	private Producer<String, String> producer;
	private RestTemplate restTemplate = new RestTemplate();
	private AmazonS3 s3Client;
	@Value("${vcap.services.pz-kafka.credentials.host}")
	private String KAFKA_ADDRESS;
	@Value("${dispatcher.url}")
	private String DISPATCHER_URL;
	@Value("${vcap.services.pz-blobstore.credentials.bucket}")
	private String AMAZONS3_BUCKET_NAME;
	@Value("${s3.domain}")
	private String AMAZONS3_DOMAIN;
	@Value("${vcap.services.pz-blobstore.credentials.access_key_id:}")
	private String AMAZONS3_ACCESS_KEY;
	@Value("${vcap.services.pz-blobstore.credentials.secret_access_key:}")
	private String AMAZONS3_PRIVATE_KEY;
	@Value("${SPACE}")
	private String SPACE;

	/**
	 * Initializing the Kafka Producer on Controller startup.
	 */
	@PostConstruct
	public void init() {
		System.out.print("Paired with Dispatcher at " + DISPATCHER_URL);
		producer = KafkaClientFactory.getProducer(KAFKA_ADDRESS.split(":")[0], KAFKA_ADDRESS.split(":")[1]);
		// Connect to S3 Bucket. Only apply credentials if they are present.
		if ((AMAZONS3_ACCESS_KEY.isEmpty()) && (AMAZONS3_PRIVATE_KEY.isEmpty())) {
			s3Client = new AmazonS3Client();
		} else {
			BasicAWSCredentials credentials = new BasicAWSCredentials(AMAZONS3_ACCESS_KEY, AMAZONS3_PRIVATE_KEY);
			s3Client = new AmazonS3Client(credentials);
		}

	}

	@PreDestroy
	public void cleanup() {
		producer.close();
	}

	/**
	 * Requests a file that has been prepared by the Accessor component. This is
	 * a separate method off of the /job endpoint because the return type is
	 * greatly different.
	 * 
	 * @param body
	 *            The JSON Payload of the FileRequestJob. All other Job Types
	 *            will be invalid.
	 */
	@RequestMapping(value = "/file", method = RequestMethod.POST)
	public ResponseEntity<byte[]> accessFile(@RequestParam(required = true) String body) throws Exception {
		try {
			// Parse the Request String
			FileRequest request = new ObjectMapper().readValue(body, FileRequest.class);

			// The Request object will contain the information needed to acquire
			// the file bytes. Pass this off to the Dispatcher to get the file
			// from the Access component.
			ResponseEntity<byte[]> dispatcherResponse = restTemplate.getForEntity(
					String.format("%s/file/%s", DISPATCHER_URL, request.dataId), byte[].class);
			logger.log(String.format("Sent File Request Job %s to Dispatcher.", request.dataId), PiazzaLogger.INFO);
			// The status code of the response gets swallowed up no matter what
			// we do. Infer the status code that we should use based on the type
			// of Response the REST service responds with.
			return dispatcherResponse;
		} catch (Exception exception) {
			String message = String.format("Error Sending Message to Dispatcher: %s", exception.getMessage());
			logger.log(message, PiazzaLogger.ERROR);
			throw new Exception(message);
		}
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
	public ResponseEntity<PiazzaResponse> job(@RequestParam(required = true) String body,
			@RequestParam(required = false) final MultipartFile file, Principal user) {

		String userName = (user != null) ? user.getName() : null;
		System.out.println("The currently authenticated, authorized user is: " + userName);

		// Deserialize the incoming JSON to Request Model objects
		PiazzaJobRequest request;
		try {
			request = JobMessageFactory.parseRequestJson(body);
			request.userName = userName;
		} catch (Exception exception) {
			logger.log(String.format("An Invalid Job Request sent to the Gateway: %s", exception.getMessage()),
					PiazzaLogger.WARNING);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, "Error Parsing Job Request: "
					+ exception.getMessage(), "Gateway"), HttpStatus.BAD_REQUEST);
		}

		// Get a Job ID
		String jobId;
		try {
			// Create a GUID for this new Job from the UUIDGen component
			jobId = uuidFactory.getUUID();
		} catch (RestClientException exception) {
			logger.log("Could not connect to UUID Service for UUID.", PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null,
					"Could not generate Job ID. Core Piazza Components were not found (UUIDGen).", "UUIDGen"),
					HttpStatus.SERVICE_UNAVAILABLE);
		}

		// Determine if this Job is processed via synchronous REST, or via Kafka
		// message queues.
		if (isSynchronousJob(request.jobType)) {
			return processSynchronousJob(jobId, request);
		} else {
			return performDispatcherKafka(jobId, request, file);
		}
	}

	/**
	 * Determines if the Job Type is synchronous or not. Synchronous Jobs are
	 * forwarded via REST, asynchronous jobs are forwarded via Kafka.
	 * 
	 * @param jobType
	 *            The Job Type
	 * @return true if synchronous based on the job contents, false if not
	 */
	private boolean isSynchronousJob(PiazzaJobType jobType) {
		boolean isSynchronous = false;
		// GET Jobs are always Synchronous. TODO: Use interfaces for this,
		// instead of static type checks.
		if ((jobType instanceof GetJob) || (jobType instanceof GetResource) || (jobType instanceof SearchQueryJob)
				|| (jobType instanceof SearchMetadataIngestJob)) {
			isSynchronous = true;
		}
		return isSynchronous;
	}

	/**
	 * Processes a Synchronous Job. This will send the Job to the appropriate
	 * Dispatcher endpoint via REST.
	 * 
	 * @param jobId
	 *            The ID of the Job
	 * @param request
	 *            The Job Request
	 * @return The REST response from the Dispatcher
	 */
	private ResponseEntity<PiazzaResponse> processSynchronousJob(String jobId, PiazzaJobRequest request) {
		try {
			ResponseEntity<PiazzaResponse> response;

			// Proxy the REST request to the Dispatcher
			if ((request.jobType instanceof SearchQueryJob) || (request.jobType instanceof SearchMetadataIngestJob)) {
				response = performDispatcherPost(request);
			} else {
				response = performDispatcherGet(request);
			}

			// Return the Response
			return response;
		} catch (Exception exception) {
			logger.log("Could not relay message to Dispatcher.", PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, "Error Processing Request: "
					+ exception.getMessage(), "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Forwards a Search Query request to the internal Dispatcher component via
	 * POST REST.
	 * 
	 * This method is separated out from the other Dispatcher REST method
	 * because it uses a specific POST format, instead of GETS.
	 * 
	 * @param request
	 *            The Job Request
	 * @return The Response from the Dispatcher
	 */
	private ResponseEntity<PiazzaResponse> performDispatcherPost(PiazzaJobRequest request) throws Exception {
		String endpointString = (request.jobType instanceof SearchMetadataIngestJob) ? "searchmetadataingest"
				: "search";
		PiazzaResponse dispatcherResponse = restTemplate.postForObject(
				String.format("%s/%s", DISPATCHER_URL, endpointString), request.jobType, PiazzaResponse.class);
		logger.log(String.format("Sent Search Job For User %s to Dispatcher REST services", request.userName),
				PiazzaLogger.INFO);
		// The status code of the response gets swallowed up no matter what
		// we do. Infer the status code that we should use based on the type
		// of Response the REST service responds with.
		HttpStatus status = dispatcherResponse instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
				: HttpStatus.OK;
		return new ResponseEntity<PiazzaResponse>(dispatcherResponse, status);
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
	private ResponseEntity<PiazzaResponse> performDispatcherGet(PiazzaJobRequest request) throws Exception {
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

		PiazzaResponse dispatcherResponse = restTemplate.getForObject(
				String.format("%s/%s/%s", DISPATCHER_URL, serviceName, id), PiazzaResponse.class);
		logger.log(String.format("Sent Job %s to Dispatcher %s REST services", id, serviceName), PiazzaLogger.INFO);
		// The status code of the response gets swallowed up no matter what
		// we do. Infer the status code that we should use based on the type
		// of Response the REST service responds with.
		HttpStatus status = dispatcherResponse instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
				: HttpStatus.OK;
		return new ResponseEntity<PiazzaResponse>(dispatcherResponse, status);
	}

	/**
	 * Forwards the Job request along to the internal Piazza components via
	 * Kafka. This is meant for Jobs that will return a job ID, and are
	 * potentially long-running, and are thus asynchronous.
	 * 
	 * @param jobId
	 *            The ID of the Job
	 * @param request
	 *            The Job Request
	 * @param file
	 *            The file being uploaded
	 * @return The response object, which will contain the Job ID
	 */
	private ResponseEntity<PiazzaResponse> performDispatcherKafka(String jobId, PiazzaJobRequest request,
			MultipartFile file) {
		// If an Ingest job, persist the file to the Amazon S3 filesystem
		if (request.jobType instanceof IngestJob && file != null) {
			try {
				if (((IngestJob) request.jobType).getHost() == true) {
					// The content length must be specified.
					ObjectMetadata metadata = new ObjectMetadata();
					metadata.setContentLength(file.getSize());
					// Send the file. The key corresponds with the S3 file name.
					String fileKey = String.format("%s-%s", jobId, file.getOriginalFilename());
					s3Client.putObject(AMAZONS3_BUCKET_NAME, fileKey, file.getInputStream(), metadata);
					// Note the S3 file path in the Ingest Job. This will be
					// used later to pull the file in the Ingest component.
					IngestJob ingestJob = (IngestJob) request.jobType;
					if (ingestJob.getData().getDataType() instanceof FileRepresentation) {
						// Attach the file to the FileLocation object
						FileLocation fileLocation = new S3FileStore(AMAZONS3_BUCKET_NAME, fileKey, file.getSize(), AMAZONS3_DOMAIN);
						((FileRepresentation) ingestJob.getData().getDataType()).setLocation(fileLocation);
						logger.log(String.format("S3 File for Job %s Persisted to %s:%s", jobId, AMAZONS3_BUCKET_NAME,
								fileKey), PiazzaLogger.INFO);
					} else {
						// Only FileRepresentation objects can have a file
						// attached to them. Otherwise, this is an invalid input
						// and an error needs to be thrown.
						return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null,
								"The uploaded file cannot be attached to the specified Data Type: "
										+ ingestJob.getData().getDataType().getType(), "Gateway"),
								HttpStatus.BAD_REQUEST);
					}
				} else {
					return new ResponseEntity<PiazzaResponse>(
							new ErrorResponse(
									null,
									"Invalid input: Host parameter for a Data Load Job cannot be set to false if a file has been specified.",
									"Gateway"), HttpStatus.BAD_REQUEST);
				}
			} catch (AmazonServiceException awsServiceException) {
				logger.log(String.format("AWS S3 Upload Error on Job %s: %s", jobId, awsServiceException.getMessage()),
						PiazzaLogger.ERROR);
				awsServiceException.printStackTrace();
				return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null,
						"The file was rejected by Piazza persistent storage. Reason: "
								+ awsServiceException.getMessage(), "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
			} catch (Exception exception) {
				logger.log(String.format("Error Processing S3 Upload on Job %s: %s", jobId, exception.getMessage()),
						PiazzaLogger.ERROR);
				exception.printStackTrace();
				return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null,
						"An Internal error was encountered while persisting the file: " + exception.getMessage(),
						"Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		// Create the Kafka Message for an incoming Job to be created.
		final ProducerRecord<String, String> message;
		try {
			message = JobMessageFactory.getRequestJobMessage(request, jobId, SPACE);
		} catch (JsonProcessingException exception) {
			exception.printStackTrace();
			logger.log(String.format("Error Creating Kafka Message for Job %s", jobId), PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(jobId, "Error Creating Message for Job",
					"Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}

		// Fire off a Kafka Message and then wait for a ack response from the
		// kafka broker
		try {
			producer.send(message).get();
		} catch (Exception exception) {
			logger.log(String.format("Timeout sending Message for Job %s through Kafka: %s", jobId,
					exception.getMessage()), PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(
					new ErrorResponse(
							jobId,
							"The Gateway did not receive a response from Kafka; the request could not be forwarded along to Piazza.",
							"Gateway"), HttpStatus.SERVICE_UNAVAILABLE);
		}

		logger.log(String.format("Sent Job %s with Kafka Topic %s and Key %s to Dispatcher.", jobId, message.topic(),
				message.key()), PiazzaLogger.INFO);

		// Respond immediately with the new Job GUID
		return new ResponseEntity<PiazzaResponse>(new PiazzaResponse(jobId), HttpStatus.CREATED);
	}

	/**
	 * Health Check. Returns OK if this component is up and running.
	 * 
	 */
	@RequestMapping(value = "/health", method = RequestMethod.GET)
	public String healthCheck() {
		return "OK";
	}
}