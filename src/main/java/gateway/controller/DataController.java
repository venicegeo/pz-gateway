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

import gateway.controller.util.GatewayUtil;
import gateway.controller.util.PiazzaRestController;

import java.security.Principal;

import messaging.job.JobMessageFactory;
import model.data.FileRepresentation;
import model.job.metadata.ResourceMetadata;
import model.job.type.IngestJob;
import model.request.PiazzaJobRequest;
import model.response.DataResourceListResponse;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST controller serving end points that are related to Piazza data, such as
 * loading or accessing spatial data.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin
@RestController
public class DataController extends PiazzaRestController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${search.url}")
	private String SEARCH_URL;
	@Value("${search.data.endpoint}")
	private String SEARCH_ENDPOINT;
	@Value("${ingest.url}")
	private String INGEST_URL;
	@Value("${access.url}")
	private String ACCESS_URL;
	@Value("${SPACE}")
	private String SPACE;

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
			@RequestParam(value = "keyword", required = false) String keyword, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Data List query.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Proxy the request to Pz-Access
			String url = String.format("%s/%s?page=%s&pageSize=%s", ACCESS_URL, "data", page, pageSize);
			// Attach keywords if specified
			if ((keyword != null) && (keyword.isEmpty() == false)) {
				url = String.format("%s&keyword=%s", url, keyword);
			}
			PiazzaResponse dataResponse = restTemplate.getForObject(url, PiazzaResponse.class);
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
			logger.log(String.format("User %s requested Data Load Job of type %s.", gatewayUtil.getPrincipalName(user),
					job.getData().getDataType()), PiazzaLogger.INFO);
			// Create the Request to send to Kafka
			String newJobId = gatewayUtil.getUuid();
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.jobType = job;
			request.userName = gatewayUtil.getPrincipalName(user);
			ProducerRecord<String, String> message = JobMessageFactory.getRequestJobMessage(request, newJobId, SPACE);
			// Send the message to Kafka
			gatewayUtil.sendKafkaMessage(message);
			// Attempt to wait until the user is able to query the Job ID
			// immediately.
			gatewayUtil.verifyDatabaseInsertion(newJobId);
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
	public ResponseEntity<PiazzaResponse> ingestDataFile(@RequestParam(required = true) String data,
			@RequestParam(required = true) final MultipartFile file, Principal user) {
		try {
			IngestJob job;
			try {
				// Serialize the JSON payload of the multipart request
				job = new ObjectMapper().readValue(data, IngestJob.class);
			} catch (Exception exception) {
				throw new Exception(String.format(
						"Incorrect JSON passed through the `data` parameter. Please verify input. Error: %s",
						exception.getMessage()));
			}
			// Ensure the file was uploaded. This is required.
			if (file == null) {
				throw new Exception("File not specified in request.");
			}
			// Log the request
			logger.log(String.format("User %s requested Data Load Job of type %s with file",
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
			ProducerRecord<String, String> message = JobMessageFactory.getRequestJobMessage(request, jobId, SPACE);
			// Send the message to Kafka
			gatewayUtil.sendKafkaMessage(message);
			// Attempt to wait until the user is able to query the Job ID
			// immediately.
			gatewayUtil.verifyDatabaseInsertion(jobId);
			// Return the Job ID of the newly created Job
			return new ResponseEntity<PiazzaResponse>(new PiazzaResponse(jobId), HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Loading Data File for user %s of type %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
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
					String.format("%s/%s/%s", ACCESS_URL, "data", dataId), PiazzaResponse.class);
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
	 * Update the metadata of a Data Resource
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/post_data
	 * 
	 * @param dataId
	 *            The ID of the resource
	 * @param user
	 *            the user submitting the request
	 * @return OK if successful; error if not.
	 */
	@RequestMapping(value = "/data/{dataId}", method = RequestMethod.POST)
	public ResponseEntity<PiazzaResponse> updateMetadata(@PathVariable(value = "dataId") String dataId,
			@RequestBody ResourceMetadata metadata, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Update of Metadata for %s.",
					gatewayUtil.getPrincipalName(user), dataId), PiazzaLogger.INFO);
			// Proxy the request to Ingest
			PiazzaResponse response = restTemplate.postForObject(String.format("%s/%s/%s", INGEST_URL, "data", dataId),
					metadata, PiazzaResponse.class);
			if (response instanceof ErrorResponse) {
				throw new Exception(((ErrorResponse) response).message);
			}
			// Response
			return null;
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Updating Metadata for item %s by user %s: %s", dataId,
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
	public ResponseEntity<PiazzaResponse> searchData(@RequestBody Object query, Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s sending a complex query for Search.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Send the query to the Pz-Search component
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Object> entity = new HttpEntity<Object>(query, headers);
			DataResourceListResponse searchResponse = restTemplate.postForObject(
					String.format("%s/%s", SEARCH_URL, SEARCH_ENDPOINT), entity, DataResourceListResponse.class);
			// Respond
			return new ResponseEntity<PiazzaResponse>(searchResponse, HttpStatus.OK);
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
	 * Downloads the bytes of a file that is stored within Piazza.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/get_file_dataId
	 * 
	 * @param dataId
	 *            The ID of the Data to download
	 * @param user
	 *            The user submitting the request
	 * @return The bytes of the file as a download, or an Error if the file
	 *         cannot be retrieved.
	 */
	@RequestMapping(value = "/file/{dataId}", method = RequestMethod.GET)
	public ResponseEntity<byte[]> getFile(@PathVariable(value = "dataId") String dataId,
			@RequestParam(value = "fileName", required = false) String fileName, Principal user) throws Exception {
		try {
			// Log the request
			logger.log(String.format("User %s requested file download for Data %s", gatewayUtil.getPrincipalName(user),
					dataId), PiazzaLogger.INFO);

			// Get the bytes of the Data
			String url = String.format("%s/file/%s", ACCESS_URL, dataId);
			// Attach keywords if specified
			if ((fileName != null) && (fileName.isEmpty() == false)) {
				url = String.format("%s?fileName=%s", url, fileName);
			}
			ResponseEntity<byte[]> accessResponse = restTemplate.getForEntity(url, byte[].class);

			// Stream the bytes back
			return accessResponse;
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error downloading file for Data %s by user %s: %s", dataId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.INFO);

			throw new Exception(error);
		}
	}
}
