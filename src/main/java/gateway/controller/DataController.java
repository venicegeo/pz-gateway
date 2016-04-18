package gateway.controller;

import gateway.controller.util.GatewayUtil;

import java.security.Principal;

import messaging.job.JobMessageFactory;
import model.data.FileRepresentation;
import model.job.type.IngestJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import util.PiazzaLogger;

/**
 * REST controller serving end points that are related to Piazza data, such as
 * loading or accessing spatial data.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin
@RestController
public class DataController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${access.host}")
	private String ACCESS_HOST;
	@Value("${access.port}")
	private String ACCESS_PORT;
	@Value("${access.protocol}")
	private String ACCESS_PROTOCOL;

	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";
	private RestTemplate restTemplate = new RestTemplate();

	/**
	 * Returns a queried list of Data Resources previously loaded into Piazza.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/get_data
	 * 
	 * @param user
	 *            The user making the request
	 * @return The list of results, with pagination information included.
	 *         ErrorResponse if something goes wrong.
	 */
	@RequestMapping(value = "/data", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getData(
			@RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Data List query.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Proxy the request to Pz-Access
			PiazzaResponse dataResponse = restTemplate.getForObject(String.format("%s://%s:%s/%s?page=%s&per_page=%s",
					ACCESS_PROTOCOL, ACCESS_HOST, ACCESS_PORT, "data", page, pageSize), PiazzaResponse.class);
			HttpStatus status = dataResponse instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
					: HttpStatus.OK;
			// Respond
			return new ResponseEntity<PiazzaResponse>(dataResponse, status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Data by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Process the request to Ingest data. This endpoint will process an ingest
	 * request. If a file is to be specified, then the ingestDataFile() endpoint
	 * should be called, which is a multipart request.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/post_data
	 * 
	 * @param job
	 *            The Ingest Job, describing the data to be ingested.
	 * @param user
	 *            The user submitting the request
	 * @return The Response containing the Job ID, or containing the appropriate
	 *         ErrorResponse
	 */
	@RequestMapping(value = "/data", method = RequestMethod.POST)
	public ResponseEntity<PiazzaResponse> ingestData(@RequestBody IngestJob job, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Ingest Job of type %s.", gatewayUtil.getPrincipalName(user),
					job.getData().getDataType()), PiazzaLogger.INFO);
			// Create the Request to send to Kafka
			String newJobId = gatewayUtil.getUuid();
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.jobType = job;
			request.userName = gatewayUtil.getPrincipalName(user);
			ProducerRecord<String, String> message = JobMessageFactory.getRequestJobMessage(request, newJobId);
			// Send the message to Kafka
			gatewayUtil.sendKafkaMessage(message);
			// Return the Job ID of the newly created Job
			return new ResponseEntity<PiazzaResponse>(new PiazzaResponse(newJobId), HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Loading Data for user %s of type %s:  %s",
					gatewayUtil.getPrincipalName(user), job.getData().getDataType(), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Processes the request to Ingest data as a file.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/post_data_file
	 * 
	 * @param job
	 *            The ingest job, describing the data to be ingested param file
	 *            The file bytes
	 * @param user
	 *            The user submitting the request
	 * @return The response containing the Job ID, or containing the appropriate
	 *         ErrorResponse
	 */
	@RequestMapping(value = "/data/file", method = RequestMethod.POST)
	public ResponseEntity<PiazzaResponse> ingestDataFile(@RequestParam(required = true) IngestJob job,
			@RequestParam(required = true) final MultipartFile file, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Ingest Job of type %s with file",
					gatewayUtil.getPrincipalName(user), job.getData().getDataType(), file.getOriginalFilename()),
					PiazzaLogger.INFO);
			// Validate the Job inputs to ensure we are able to process the file
			// and attach it to the job metadata.
			if (job.getHost() == false) {
				throw new Exception("Host parameter must be set to true when loading a file.");
			} else if (job.getData().getDataType() instanceof FileRepresentation == false) {
				throw new Exception("The uploaded file cannot be attached to the specified Data Type: "
						+ job.getData().getDataType().getType());
			}
			// Send the file to S3.
			String jobId = gatewayUtil.getUuid();
			job = gatewayUtil.pushS3File(jobId, job, file);
			// Create the Request to send to Kafka
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.jobType = job;
			request.userName = gatewayUtil.getPrincipalName(user);
			ProducerRecord<String, String> message = JobMessageFactory.getRequestJobMessage(request, jobId);
			// Send the message to Kafka
			gatewayUtil.sendKafkaMessage(message);
			// Return the Job ID of the newly created Job
			return new ResponseEntity<PiazzaResponse>(new PiazzaResponse(jobId), HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Loading Data File for user %s of type %s: %s",
					gatewayUtil.getPrincipalName(user), job.getData().getDataType(), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets the metadata for a Data Resource
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/get_data
	 * 
	 * @param dataId
	 *            The ID of the Resource
	 * @param user
	 *            The user submitting the request
	 * @return The status and metadata of the data resource, or appropriate
	 *         ErrorResponse if failed.
	 */
	@RequestMapping(value = "/data/{dataId}", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getMetadata(@PathVariable(value = "dataId") String dataId, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Resource Metadata for %s.", gatewayUtil.getPrincipalName(user),
					dataId), PiazzaLogger.INFO);
			// Proxy the request to Pz-Access
			PiazzaResponse dataResponse = restTemplate.getForObject(
					String.format("%s://%s:%s/%s/%s", ACCESS_PROTOCOL, ACCESS_HOST, ACCESS_PORT, "data", dataId),
					PiazzaResponse.class);
			HttpStatus status = dataResponse instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
					: HttpStatus.OK;
			// Respond
			return new ResponseEntity<PiazzaResponse>(dataResponse, status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Loading Metadata for item %s by user %s: %s", dataId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Proxies an ElasticSearch DSL query to the Pz-Search component to return a
	 * list of DataResource items.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/post_data_query
	 * 
	 * @return The list of DataResource items matching the query.
	 */
	@RequestMapping(value = "/data/query", method = RequestMethod.POST)
	public ResponseEntity<PiazzaResponse> searchData(Principal user) {
		try {
			return null;

			// Log the request

			// Send the query to the Pz-Search component

			// Gather the results and return the response

		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Data by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
