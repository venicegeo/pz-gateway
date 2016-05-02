package gateway.controller.util;

import java.security.Principal;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import messaging.job.KafkaClientFactory;
import model.data.FileRepresentation;
import model.data.location.FileLocation;
import model.data.location.S3FileStore;
import model.job.type.IngestJob;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import util.PiazzaLogger;
import util.UUIDFactory;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;

/**
 * Utility class that defines common procedures for handling requests,
 * responses, and brokered end points to internal Piazza components.
 * 
 * @author Patrick.Doody
 *
 */
@Component
public class GatewayUtil {
	@Autowired
	private UUIDFactory uuidFactory;
	@Autowired
	PiazzaLogger logger;
	@Value("${vcap.services.pz-kafka.credentials.host}")
	private String KAFKA_ADDRESS;
	@Value("${kafka.group}")
	private String KAFKA_GROUP;
	@Value("${s3.domain}")
	private String AMAZONS3_DOMAIN;
	@Value("${vcap.services.pz-blobstore.credentials.access_key_id:}")
	private String AMAZONS3_ACCESS_KEY;
	@Value("${vcap.services.pz-blobstore.credentials.secret_access_key:}")
	private String AMAZONS3_PRIVATE_KEY;
	@Value("${vcap.services.pz-blobstore.credentials.bucket}")
	private String AMAZONS3_BUCKET_NAME;
	@Value("${jobmanager.host}")
	private String JOBMANAGER_HOST;
	@Value("${jobmanager.port}")
	private String JOBMANAGER_PORT;
	@Value("${jobmanager.protocol}")
	private String JOBMANAGER_PROTOCOL;

	private Producer<String, String> producer;
	private AmazonS3 s3Client;
	private RestTemplate restTemplate = new RestTemplate();

	/**
	 * Initializing the Kafka Producer on Controller startup.
	 */
	@PostConstruct
	public void init() {
		// Kafka Producer.
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
	 * Sends a message to Kafka. This will additionally invoke .get() on the
	 * message sent, which will block until the acknowledgement from Kafka has
	 * been received that the message entered the Kafka queue.
	 * 
	 * @param message
	 *            The message to send.
	 * @throws Exception
	 *             Any exceptions encountered with the send.
	 */
	public void sendKafkaMessage(ProducerRecord<String, String> message) throws Exception {
		producer.send(message).get();
	}

	/**
	 * Gets a UUID from the Piazza UUID Factory.
	 * 
	 * @return UUID
	 */
	public String getUuid() throws Exception {
		try {
			return uuidFactory.getUUID();
		} catch (Exception exception) {
			throw new Exception(String.format("Could not connect to UUID Service for UUID: %s", exception.getMessage()));
		}
	}

	/**
	 * Safely returns the name of the user who has performed a request to a
	 * Gateway endpoint.
	 * 
	 * @param user
	 *            The principal
	 * @return The username. If the request was not authenticated, then that
	 *         will be returned.
	 */
	public String getPrincipalName(Principal user) {
		return user != null ? user.getName() : "UNAUTHENTICATED";
	}

	/**
	 * Handles the uploaded file from the data/file endpoint. This will push the
	 * file to S3, and then modify the content of the job to reference the new
	 * S3 location of the file.
	 * 
	 * @param jobId
	 *            The ID of the Job, used for generating a unique S3 bucket file
	 *            name.
	 * @param job
	 *            The ingest job, containing the DataResource metadata
	 * @param file
	 *            The file to be uploaded
	 * @return The modified job, with the location of the S3 file added to the
	 *         metadata
	 */
	public IngestJob pushS3File(String jobId, IngestJob job, MultipartFile file) throws Exception {
		// The content length must be specified.
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(file.getSize());
		// Send the file to S3. The key corresponds with the S3 file name.
		String fileKey = String.format("%s-%s", jobId, file.getOriginalFilename());
		s3Client.putObject(AMAZONS3_BUCKET_NAME, fileKey, file.getInputStream(), metadata);
		// Note the S3 file path in the Ingest Job.
		// Attach the file to the FileLocation object
		FileLocation fileLocation = new S3FileStore(AMAZONS3_BUCKET_NAME, fileKey, AMAZONS3_DOMAIN);
		((FileRepresentation) job.getData().getDataType()).setLocation(fileLocation);
		logger.log(String.format("S3 File for Job %s Persisted to %s:%s", jobId, AMAZONS3_BUCKET_NAME, fileKey),
				PiazzaLogger.INFO);
		return job;
	}

	/**
	 * <p>
	 * Verifies that the Job has been inserted into the Job Manager's jobs
	 * table. This is intended to be used in cases where the Gateway request
	 * intends to "busily wait" until a newly created Job ID is queryable by the
	 * user. This is to prevent cases where a Job ID is returned to the user
	 * before that ID can actually be queried by the database.
	 * </p>
	 * 
	 * <p>
	 * Since we are using Kafka to asynchronously move messages around, there's
	 * no way for the Gateway to know that the Jobs table has been updated. So
	 * the solution here is to query the Job's table until the Job appears.
	 * There elegant solution that we will eventually move to is a REST-based
	 * solution, where Kafka is not in the loop at all - or perhaps where the
	 * Gateway has query access to the database. However, for now, this is an
	 * easy way to ensure that the Job ID's are immediately queryable by users
	 * who submit a Job.
	 * </p>
	 * 
	 * <p>
	 * This will time out after 3 attempts, in which case it will cease
	 * blocking.
	 * </p>
	 * 
	 * @param jobId
	 *            The Job ID to ensure exists.
	 */
	public void verifyDatabaseInsertion(String id) {
		ResponseEntity<PiazzaResponse> response = null;
		int iteration = 0, delay = 250;
		do {
			// Check the status of the ID
			try {
				Thread.sleep(delay);
				response = restTemplate.getForEntity(String.format("%s://%s:%s/%s/%s", JOBMANAGER_PROTOCOL,
						JOBMANAGER_HOST, JOBMANAGER_PORT, "job", id), PiazzaResponse.class);
			} catch (Exception exception) {
				// ID is not ready yet.
			}
			// If OK, then return. If not OK, then the database has not yet
			// indexed. Wait and try again.
			if (response.getStatusCode() == HttpStatus.OK) {
				return;
			}
			iteration++;
		} while (iteration < 3);
		// If we have reached the maximum number of iterations, then at least
		// log that the ID has not been inserted into the database yet.
		logger.log(
				String.format(
						"Gateway processed an Job ID %s that was returned to a user before it was detected as committed to the database. The user may not be able to query this ID immediately.",
						id), PiazzaLogger.WARNING);
	}
}