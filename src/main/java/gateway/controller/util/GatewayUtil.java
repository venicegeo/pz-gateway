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
package gateway.controller.util;

import java.io.IOException;
import java.security.Principal;
import java.util.concurrent.ExecutionException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3EncryptionClient;
import com.amazonaws.services.s3.model.CryptoConfiguration;
import com.amazonaws.services.s3.model.KMSEncryptionMaterialsProvider;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;

import exception.PiazzaJobException;
import gateway.auth.PiazzaAuthenticationToken;
import messaging.job.KafkaClientFactory;
import model.data.FileRepresentation;
import model.data.location.FileLocation;
import model.data.location.S3FileStore;
import model.job.type.IngestJob;
import model.logger.AuditElement;
import model.logger.Severity;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.JobResponse;
import model.response.PiazzaResponse;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Utility class that defines common procedures for handling requests, responses, and brokered end points to internal
 * Piazza components.
 * 
 * @author Patrick.Doody
 *
 */
@Component
public class GatewayUtil {
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UUIDFactory uuidFactory;
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private RestTemplate restTemplate;

	@Value("${vcap.services.pz-kafka.credentials.host}")
	private String KAFKA_HOSTS;
	@Value("${s3.domain}")
	private String AMAZONS3_DOMAIN;
	@Value("${vcap.services.pz-blobstore.credentials.access_key_id:}")
	private String AMAZONS3_ACCESS_KEY;
	@Value("${vcap.services.pz-blobstore.credentials.secret_access_key:}")
	private String AMAZONS3_PRIVATE_KEY;
	@Value("${vcap.services.pz-blobstore.credentials.bucket}")
	private String AMAZONS3_BUCKET_NAME;
	@Value("${jobmanager.url}")
	private String JOBMANAGER_URL;
	@Value("${vcap.services.pz-blobstore.credentials.encryption_key}")
	private String S3_KMS_CMK_ID;

	private final static Logger LOG = LoggerFactory.getLogger(GatewayUtil.class);

	private Producer<String, String> producer;
	private AmazonS3 s3Client;

	/**
	 * Initializing the Kafka Producer on Controller startup.
	 */
	@PostConstruct
	public void init() {
		// Kafka Producer.
		producer = KafkaClientFactory.getProducer(KAFKA_HOSTS);
		logger.log("Connecting to Kafka Cluster", Severity.INFORMATIONAL,
				new AuditElement("gateway", "connectedToKafkaCluster", KAFKA_HOSTS));
		// Connect to S3 Bucket. Only apply credentials if they are present.
		if ((AMAZONS3_ACCESS_KEY.isEmpty()) && (AMAZONS3_PRIVATE_KEY.isEmpty())) {
			s3Client = new AmazonS3Client();
		} else {
			BasicAWSCredentials credentials = new BasicAWSCredentials(AMAZONS3_ACCESS_KEY, AMAZONS3_PRIVATE_KEY);
			// Set up encryption using the KMS CMK Key
			KMSEncryptionMaterialsProvider materialProvider = new KMSEncryptionMaterialsProvider(S3_KMS_CMK_ID);
			s3Client = new AmazonS3EncryptionClient(credentials, materialProvider,
					new CryptoConfiguration().withKmsRegion(Regions.US_EAST_1)).withRegion(Region.getRegion(Regions.US_EAST_1));
		}
	}

	@PreDestroy
	public void cleanup() {
		producer.close();
	}

	/**
	 * Sends a Job Request to the Job Manager. This will generate a Job Id and return it once the Job Manager has
	 * indexed the Job into its database.
	 * 
	 * @param request
	 *            The Job Request
	 * @return The Job Id
	 */
	public String sendJobRequest(PiazzaJobRequest request, String jobId) throws PiazzaJobException {
		
		// Generate a Job Id
		final String finalJobId = jobId == null ? getUuid() : jobId;
		
		try {
			// Send the message to Job Manager
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<PiazzaJobRequest> entity = new HttpEntity<PiazzaJobRequest>(request, headers);
			// Log this request
			logger.log(
					String.format("Forwarding Job %s for user %s with Type %s", finalJobId, request.createdBy,
							request.jobType.getClass().getSimpleName()),
					Severity.INFORMATIONAL, new AuditElement(request.createdBy, "requestJob", finalJobId));
			ResponseEntity<PiazzaResponse> jobResponse = restTemplate
					.postForEntity(String.format("%s/%s?jobId=%s", JOBMANAGER_URL, "requestJob", finalJobId), entity, PiazzaResponse.class);
			// Check if the response was an error.
			if (jobResponse.getBody() instanceof ErrorResponse) {
				throw new PiazzaJobException(((ErrorResponse) jobResponse.getBody()).message);
			}
			// Return the Job Id from the response.
			return ((JobResponse) jobResponse.getBody()).data.getJobId();
		} catch (Exception exception) {
			String error = String.format("Error with Job Manager when Requesting New Piazza Job: %s", exception.getMessage());
			LOG.error(error, exception);
			// Log the failure
			logger.log(String.format("Job Request at Gateway failed for Job %s", finalJobId), Severity.ERROR,
					new AuditElement(request.createdBy, "failedRequestJob", finalJobId));
			throw new PiazzaJobException(error);
		}
	}

	/**
	 * Sends a message to Kafka. This will additionally invoke .get() on the message sent, which will block until the
	 * acknowledgement from Kafka has been received that the message entered the Kafka queue.
	 * 
	 * @param message
	 *            The message to send.
	 * @throws Exception
	 *             Any exceptions encountered with the send.
	 */
	public void sendKafkaMessage(ProducerRecord<String, String> message) throws InterruptedException, ExecutionException {
		producer.send(message).get();
	}

	/**
	 * Gets a UUID from the Piazza UUID Factory.
	 * 
	 * @return UUID
	 */
	public String getUuid() throws PiazzaJobException {
		try {
			return uuidFactory.getUUID();
		} catch (Exception exception) {
			String error = String.format("Could not connect to UUID Service for UUID: %s", exception.getMessage());
			LOG.error(error, exception);
			throw new PiazzaJobException(error);
		}
	}

	/**
	 * Safely returns the name of the user who has performed a request to a Gateway endpoint.
	 * 
	 * @param user
	 *            The principal
	 * @return The username. If the request was not authenticated, then that will be returned.
	 */
	public String getPrincipalName(Principal user) {
		return user != null ? user.getName() : "UNAUTHENTICATED";
	}

	/**
	 * Gets the Distinguished Name (DN) for a request from the Authentication Token that was returned by the
	 * Authentication Provider. This assumes the Authentication token is a PiazzaAuthenticationToken as created by the
	 * PiazzaBasicAuthenticationProvider. If unable to get, returns null.
	 * 
	 * @param authentication
	 *            Authentication Token
	 * @return DN of the Authentication Token, or null
	 */
	public String getDistinguishedName(Authentication authentication) {
		if (authentication instanceof PiazzaAuthenticationToken) {
			return ((PiazzaAuthenticationToken) authentication).getDistinguishedName();
		} else {
			return null;
		}
	}

	/**
	 * Handles the uploaded file from the data/file endpoint. This will push the file to S3, and then modify the content
	 * of the job to reference the new S3 location of the file. This push will encrypt the file locally and push the
	 * encrypted bytes - This process uses KMS encryption.
	 * 
	 * @param jobId
	 *            The Id of the Job, used for generating a unique S3 bucket file name.
	 * @param job
	 *            The ingest job, containing the DataResource metadata
	 * @param file
	 *            The file to be uploaded
	 * @return The modified job, with the location of the S3 file added to the metadata
	 */
	public IngestJob pushS3File(String jobId, IngestJob job, MultipartFile file)
			throws AmazonServiceException, AmazonClientException, IOException {
		// The content length must be specified.
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(file.getSize());
		// Send the file to S3. The key corresponds with the S3 file name.
		String fileKey = String.format("%s-%s", jobId, file.getOriginalFilename());
		s3Client.putObject(AMAZONS3_BUCKET_NAME, fileKey, file.getInputStream(), metadata);
		// Note the S3 file path in the Ingest Job.
		// Attach the file to the FileLocation object
		FileLocation fileLocation = new S3FileStore(AMAZONS3_BUCKET_NAME, fileKey, file.getSize(), AMAZONS3_DOMAIN);
		((FileRepresentation) job.getData().getDataType()).setLocation(fileLocation);
		logger.log(String.format("S3 File for Job %s Persisted to %s:%s", jobId, AMAZONS3_BUCKET_NAME, fileKey), Severity.INFORMATIONAL,
				new AuditElement(jobId, "persistS3File", fileKey));
		return job;
	}

	/**
	 * Validates Pagination Inputs for List requests
	 * 
	 * @param type
	 *            The key name of the query parameter
	 * @param value
	 *            The value of the query parameter
	 * @return Error description if errors occurred. Null if no errors occur.
	 */
	public String validateInput(String type, Object value) {
		switch (type) {
		case "order":
			if (!"asc".equals(value) && !"desc".equals(value)) {
				return "'order' parameter must be 'asc' or 'desc'.";
			}
			break;
		case "perPage":
			if (Integer.parseInt(value.toString()) < 0) {
				return "'perPage' parameter must be zero or greater.";
			}
			break;
		case "page":
			if (Integer.parseInt(value.toString()) < 0) {
				return "'page' parameter must be zero or greater.";
			}
			break;
		default:
			LOG.warn("Invalid type provided: {}.", type);
			break;
		}
		return null;
	}
	
	/**
	 * Joins multiple strings (from validateInput), formatting properly if there are 0, 1, or many validation errors.
	 * 
	 *  @param validationErrors
	 *  	The validation string(s) and/or null(s) from validateInput
	 *  @return Null if all validationErrors are null.  A single string containing any validationErrors that are not null, if any.
	 */
	public String joinValidationErrors(String... validationErrors) {
		String joinedErrors = null;
		for (String validationError : validationErrors) {
			if (validationError != null) {
				if (joinedErrors == null) {
					joinedErrors = validationError;
				} else {
					joinedErrors += "  " + validationError;
				}
			}
		}
		return joinedErrors;
	}

	/**
	 * Attempts to deserialize JSON content into the ErrorResponse object, constructs a new object if it fails.
	 * 
	 * @param String
	 *            errorBody The JSON content
	 * @return ErrorResponse object to return to the client
	 */
	public ErrorResponse getErrorResponse(String errorBody) {
		try {
			return objectMapper.readValue(errorBody, ErrorResponse.class);
		} catch (Exception exception) {
			LOG.error(String.format("Error Serializing Error Body (%s) into ErrorResponse class.", errorBody), exception);
			return new ErrorResponse(errorBody, "Gateway");
		}
	}
}