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

import java.security.Principal;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import gateway.controller.util.GatewayUtil;
import gateway.controller.util.PiazzaRestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import model.data.FileRepresentation;
import model.job.metadata.ResourceMetadata;
import model.job.type.IngestJob;
import model.request.PiazzaJobRequest;
import model.request.SearchRequest;
import model.response.DataResourceListResponse;
import model.response.DataResourceResponse;
import model.response.ErrorResponse;
import model.response.JobResponse;
import model.response.PiazzaResponse;
import model.response.SuccessResponse;
import util.PiazzaLogger;

/**
 * REST controller serving end points that are related to Piazza data, such as loading or accessing spatial data.
 * 
 * @author Patrick.Doody
 *
 */
@Api
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
	private static final String DEFAULT_ORDER = "desc";
	private static final String DEFAULT_SORTBY = "metadata.createdOn";
	private static final String DEFAULT_SORTBY_ES = "dataResource.metadata.createdOn"; // schema for Elasticsearch
	@Autowired
	private RestTemplate restTemplate;

	/**
	 * Returns a queried list of Data Resources previously loaded into Piazza.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/get_data
	 * 
	 * @param user
	 *            The user making the request
	 * @return The list of results, with pagination information included. ErrorResponse if something goes wrong.
	 */
	@RequestMapping(value = "/data", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Query Piazza Data", notes = "Sends a simple GET Query for fetching lists of Piazza Data.", tags = "Data")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Search results that match the query string.", response = DataResourceListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getData(
			@ApiParam(value = "A general keyword search to apply to all Datasets.") @RequestParam(value = "keyword", required = false) String keyword,
			@ApiParam(value = "Filter datasets that were created by a specific Job Id.") @RequestParam(value = "createdByJobId", required = false) String createdByJobId,
			@ApiParam(value = "Paginating large datasets. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false, defaultValue = DEFAULT_SORTBY) String sortBy,
			@ApiParam(value = "Filter for the username that published the service.") @RequestParam(value = "createdBy", required = false) String createdBy,
			Principal user) {
		try {
			// Log the request
			// logger.log(String.format("User %s requested Data List query.", gatewayUtil.getPrincipalName(user)),
			// PiazzaLogger.INFO);

			// Validate params
			String validationError = null;
			if ((order != null && (validationError = gatewayUtil.validateInput("order", order)) != null)
					|| (page != null && (validationError = gatewayUtil.validateInput("page", page)) != null)
					|| (perPage != null && (validationError = gatewayUtil.validateInput("perPage", perPage)) != null)) {
				return new ResponseEntity<PiazzaResponse>(new ErrorResponse(validationError, "Gateway"), HttpStatus.BAD_REQUEST);
			}

			// Proxy the request to Pz-Access
			String url = String.format("%s/%s?page=%s&perPage=%s", ACCESS_URL, "data", page, perPage);
			// Attach keywords if specified
			if ((keyword != null) && (keyword.isEmpty() == false)) {
				url = String.format("%s&keyword=%s", url, keyword);
			}
			// Add username if specified
			if ((createdBy != null) && (createdBy.isEmpty() == false)) {
				url = String.format("%s&userName=%s", url, createdBy);
			}
			// Add optional pagination
			if ((order != null) && (order.isEmpty() == false)) {
				url = String.format("%s&order=%s", url, order);
			}
			if ((sortBy != null) && (sortBy.isEmpty() == false)) {
				url = String.format("%s&sortBy=%s", url, sortBy);
			}
			if ((createdByJobId != null) && (createdByJobId.isEmpty() == false)) {
				url = String.format("%s&createdByJobId=%s", url, createdByJobId);
			}
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate.getForEntity(url, DataResourceListResponse.class).getBody(),
						HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Data by user %s: %s", gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Returns a queried list of Data Resources previously loaded into Piazza that have been loaded by the current user.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/get_data
	 * 
	 * @param user
	 *            The user making the request
	 * @return The list of results, with pagination information included. ErrorResponse if something goes wrong.
	 */
	@RequestMapping(value = "/data/me", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Query Piazza Data", notes = "Sends a simple GET Query for fetching lists of Piazza Data for the authenticated user.", tags = "Data", response = DataResourceListResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Search results that match the query string.", response = DataResourceListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getDataForCurrentUser(
			@ApiParam(value = "A general keyword search to apply to all Datasets.") @RequestParam(value = "keyword", required = false) String keyword,
			@ApiParam(value = "Filter datasets that were created by a specific Job Id.") @RequestParam(value = "createdByJobId", required = false) String createdByJobId,
			@ApiParam(value = "Paginating large datasets. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false) String sortBy,
			Principal user) {
		return getData(keyword, createdByJobId, page, perPage, order, sortBy, gatewayUtil.getPrincipalName(user), user);
	}

	/**
	 * Process the request to Ingest data. This endpoint will process an ingest request. If a file is to be specified,
	 * then the ingestDataFile() endpoint should be called, which is a multipart request.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/post_data
	 * 
	 * @param job
	 *            The Ingest Job, describing the data to be ingested.
	 * @param user
	 *            The user submitting the request
	 * @return The Response containing the Job Id, or containing the appropriate ErrorResponse
	 */
	@RequestMapping(value = "/data", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@ApiOperation(value = "Load Data into Piazza", notes = "Loads Data into the Piazza Core metadata holdings. Piazza can either host the Data, or reflect an external location where the data is stored. Data must be loaded into Piazza before core components such as the ServiceController, or other external services, are able to consume that Data.", tags = "Data")
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "The Id of the Job created to handle the Loading of the Data.", response = JobResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> ingestData(
			@ApiParam(name = "data", value = "The description, location, and metadata for the Data to be loaded into Piazza.", required = true) @Valid @RequestBody IngestJob job,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Data Load Job of type %s.", gatewayUtil.getPrincipalName(user),
					job.getData().getDataType().getClass().getName()), PiazzaLogger.INFO);
			// Ensure the user isn't trying to hack a dataId into their request.
			job.getData().setDataId(null);
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.jobType = job;
			request.createdBy = gatewayUtil.getPrincipalName(user);
			String jobId = gatewayUtil.sendJobRequest(request, null);

			// Return the Job Id of the newly created Job
			return new ResponseEntity<PiazzaResponse>(new JobResponse(jobId), HttpStatus.CREATED);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Loading Data for user %s of type %s:  %s", gatewayUtil.getPrincipalName(user),
					job.getData().getDataType(), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Processes the request to Ingest data as a file.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/post_data_file
	 * 
	 * @param job
	 *            The ingest job, describing the data to be ingested param file The file bytes
	 * @param user
	 *            The user submitting the request
	 * @return The response containing the Job Id, or containing the appropriate ErrorResponse
	 */
	@RequestMapping(value = "/data/file", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@ApiOperation(value = "Load a Data File into Piazza", notes = "Loads a local user Data file into Piazza. This functions the same as /data endpoint, but a file is specified instead of a URI.", tags = "Data")
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "The Id of the Job created to handle the Loading of the Data.", response = JobResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> ingestDataFile(
			@ApiParam(value = "The description, location, and metadata for the Data to be loaded into Piazza. This is the identical model to the LoadJob as specified in the body of the /data request. It is only noted as a string type here because of a Swagger deficiency.", required = true) @Valid @RequestPart String data,
			@ApiParam(value = "The file to be uploaded.", required = true) @RequestPart final MultipartFile file, Principal user) {
		try {
			IngestJob job;
			try {
				// Serialize the JSON payload of the multipart request
				job = new ObjectMapper().readValue(data, IngestJob.class);
			} catch (Exception exception) {
				return new ResponseEntity<PiazzaResponse>(new ErrorResponse(
						String.format("Incorrect JSON passed through the `data` parameter. Please verify input. Error: %s",
								exception.getMessage()),
						"Gateway"), HttpStatus.BAD_REQUEST);
			}
			// Ensure the user isn't trying to hack a dataId into their request.
			job.getData().setDataId(null);
			// Ensure the file was uploaded. This is required.
			if (file == null) {
				throw new Exception("File not specified in request.");
			}
			// Log the request
			logger.log(String.format("User %s requested Data Load Job of type %s with file: %s", gatewayUtil.getPrincipalName(user),
					job.getData().getDataType().getClass().getName(), file.getOriginalFilename()), PiazzaLogger.INFO);

			// Validate the Job inputs to ensure we are able to process the file
			// and attach it to the job metadata.
			if (job.getHost() == false) {
				throw new Exception("Host parameter must be set to true when loading a file.");
			} else if (job.getData().getDataType() instanceof FileRepresentation == false) {
				throw new Exception("The uploaded file cannot be attached to the specified Data Type: "
						+ job.getData().getDataType().getClass().getName());
			}
			// Send the file to S3.
			String jobId = gatewayUtil.getUuid();
			job = gatewayUtil.pushS3File(jobId, job, file);
			// Create the Request to send to Kafka
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.jobType = job;
			request.createdBy = gatewayUtil.getPrincipalName(user);
			jobId = gatewayUtil.sendJobRequest(request, jobId);

			// Return the Job Id of the newly created Job
			return new ResponseEntity<PiazzaResponse>(new JobResponse(jobId), HttpStatus.CREATED);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Loading Data File for user %s of type %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets the metadata for a Data Resource
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/get_data
	 * 
	 * @param dataId
	 *            The Id of the Resource
	 * @param user
	 *            The user submitting the request
	 * @return The status and metadata of the data resource, or appropriate ErrorResponse if failed.
	 */
	@RequestMapping(value = "/data/{dataId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Get Metadata for Loaded Data", notes = "Reads all metadata for a Data item that has been previously loaded into Piazza.", tags = "Data")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Metadata describing the Data Item that matches the specified Data Id. Includes release metadata, and spatial metadata, etc.", response = DataResourceResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getMetadata(
			@ApiParam(value = "Id of the Data item to pull Metadata for.", required = true) @PathVariable(value = "dataId") String dataId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Resource Metadata for %s.", gatewayUtil.getPrincipalName(user), dataId),
					PiazzaLogger.INFO);
			// Proxy the request to Pz-Access
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate
						.getForEntity(String.format("%s/%s/%s", ACCESS_URL, "data", dataId), DataResourceResponse.class).getBody(),
						HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Loading Metadata for item %s by user %s: %s", dataId, gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes the Data Resource from Piazza.
	 * 
	 * @param dataId
	 *            The Id of the data item to delete
	 * @param user
	 *            The user submitting the request
	 * @return 200 OK if deleted, error response if not.
	 */
	@RequestMapping(value = "/data/{dataId}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Delete Loaded Data", notes = "Deletes an entry to Data that has been previously loaded into Piazza. If the file was hosted by Piazza, then that file will also be deleted.", tags = "Data")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Message indicating confirmation of delete", response = SuccessResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> deleteData(
			@ApiParam(value = "Id of the Data item to Delete.", required = true) @PathVariable(value = "dataId") String dataId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Delete of Data Id %s.", gatewayUtil.getPrincipalName(user), dataId),
					PiazzaLogger.INFO);
			// Proxy the request to Pz-ingest
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate
						.exchange(String.format("%s/%s/%s", INGEST_URL, "data", dataId), HttpMethod.DELETE, null, SuccessResponse.class)
						.getBody(), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Data for item %s by user %s: %s", dataId, gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Update the metadata of a Data Resource
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/post_data
	 * 
	 * @param dataId
	 *            The Id of the resource
	 * @param user
	 *            the user submitting the request
	 * @return OK if successful; error if not.
	 */
	@RequestMapping(value = "/data/{dataId}", method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Update Metadata for Loaded Data.", notes = "This will update the metadata for a specific data item. Non-null values will overwrite. This will only update the corresponding 'metadata' field in the Data item. Spatial metadata, and file information cannot be updated. For cases where spatial metadata or file data needs to change, and re-load of the Data must be done.", tags = "Data")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Confirmation that the Metadata has been updated.", response = SuccessResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> updateMetadata(
			@ApiParam(value = "Id of the Data item to update the Metadata for.", required = true) @PathVariable(value = "dataId") String dataId,
			@ApiParam(value = "The Resource Metadata object containing the updated metadata fields to write.", required = true) @Valid @RequestBody ResourceMetadata metadata,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Update of Metadata for %s.", gatewayUtil.getPrincipalName(user), dataId),
					PiazzaLogger.INFO);
			// Proxy the request to Ingest
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate
						.postForEntity(String.format("%s/%s/%s", INGEST_URL, "data", dataId), metadata, SuccessResponse.class).getBody(),
						HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Updating Metadata for item %s by user %s: %s", dataId, gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Proxies an ElasticSearch DSL query to the Pz-Search component to return a list of DataResource items.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/post_data_query
	 * 
	 * @return The list of DataResource items matching the query.
	 */
	@RequestMapping(value = "/data/query", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	@ApiOperation(value = "Query Metadata in Piazza Data holdings", notes = "Sends a complex query message to the Piazza Search component, that allow users to search for loaded data. Searching is capable of filtering by keywords, spatial metadata, or other dynamic information.", tags = {
			"Data", "Search" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Search results that match the query string.", response = DataResourceListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> searchData(
			@ApiParam(value = "The Query string for the Search component.", required = true) @Valid @RequestBody SearchRequest query,
			@ApiParam(value = "Paginating large datasets. This will determine the starting page for the query.") @RequestParam(value = "page", required = false) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false) Integer perPage,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false) String sortBy,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s sending a complex query for Search.", gatewayUtil.getPrincipalName(user)), PiazzaLogger.INFO);

			// Send the query to the Pz-Search component
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Object> entity = new HttpEntity<Object>(query, headers);

			String paramPage = (page == null) ? "" : "page=" + page.toString();
			String paramPerPage = (perPage == null) ? "" : "perPage=" + perPage.toString();
			String paramOrder = (order == null) ? "" : "order=" + order;
			String paramSortBy = (sortBy == null) ? "" : "sortBy=" + sortBy;

			DataResourceListResponse searchResponse = restTemplate.postForObject(
					String.format("%s/%s?%s&%s&%s&%s", SEARCH_URL, SEARCH_ENDPOINT, paramPage, paramPerPage, paramOrder, paramSortBy),
					entity, DataResourceListResponse.class);
			// Respond
			return new ResponseEntity<PiazzaResponse>(searchResponse, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Data by user %s: %s", gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Downloads the bytes of a file that is stored within Piazza.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Data/get_file_dataId
	 * 
	 * @param dataId
	 *            The Id of the Data to download
	 * @param user
	 *            The user submitting the request
	 * @return The bytes of the file as a download, or an Error if the file cannot be retrieved.
	 */
	@RequestMapping(value = "/file/{dataId}", method = RequestMethod.GET)
	@ApiOperation(value = "Download Data File", notes = "Gets the Bytes of Data loaded into Piazza. Only works for Data that is stored internally by Piazza.", tags = "Data")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The downloaded data file.", response = Byte[].class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getFile(
			@ApiParam(value = "The Id of the Data to download.", required = true) @PathVariable(value = "dataId") String dataId,
			@ApiParam(value = "Specify the name of the file that the user wishes to retrieve the data as. This will set the content-disposition header.") @RequestParam(value = "fileName", required = false) String fileName,
			Principal user) throws Exception {
		try {
			// Log the request
			logger.log(String.format("User %s requested file download for Data %s", gatewayUtil.getPrincipalName(user), dataId),
					PiazzaLogger.INFO);

			// Get the bytes of the Data
			String url = String.format("%s/file/%s.json", ACCESS_URL, dataId);
			// Attach keywords if specified
			if ((fileName != null) && (fileName.isEmpty() == false)) {
				url = String.format("%s?fileName=%s", url, fileName);
			}

			// Proxy the request to Ingest
			try {
				// Stream the bytes back
				return restTemplate.getForEntity(url, byte[].class);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error downloading file for Data %s by user %s: %s", dataId, gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.INFO);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
