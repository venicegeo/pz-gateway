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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import java.security.Principal;

import javax.validation.Valid;

import model.job.metadata.ResourceMetadata;
import model.job.type.RegisterServiceJob;
import model.request.PiazzaJobRequest;
import model.request.SearchRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;
import model.response.ServiceIdResponse;
import model.response.ServiceListResponse;
import model.response.ServiceResponse;
import model.response.SuccessResponse;
import model.service.metadata.Service;

import org.joda.time.DateTime;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import util.PiazzaLogger;

/**
 * REST controller that handles requests for interacting with the Piazza Service
 * Controller component.
 * 
 * @author Patrick.Doody
 *
 */
@Api
@RestController
public class ServiceController extends PiazzaRestController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${servicecontroller.url}")
	private String SERVICECONTROLLER_URL;
	@Value("${search.url}")
	private String SEARCH_URL;
	@Value("${search.service.endpoint}")
	private String SEARCH_ENDPOINT;

	@Autowired
	private RestTemplate restTemplate;
	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";
	private static final String DEFAULT_SORTBY = "resourceMetadata.createdOn";
	private static final String DEFAULT_ORDER = "desc";

	/**
	 * Registers an external service with the Piazza Service Controller.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Service/post_service
	 * 
	 * @param service
	 *            The service to register.
	 * @param user
	 *            The user submitting the request
	 * @return The Service Id, or appropriate error.
	 */
	@RequestMapping(value = "/service", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)	
	@ApiOperation(value = "Register new Service definition", notes = "Creates a new Service with the Piazza Service Controller; that can be invoked through Piazza jobs with Piazza data.", tags = "Service")
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "The Id of the newly created Service", response = ServiceIdResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> registerService(
			@ApiParam(value = "The metadata for the service. This includes the URL, parameters, inputs and outputs. It also includes other release metadata such as classification and availability.", required = true) @Valid @RequestBody Service service,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Service registration.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);

			// Populate the authoring field in the Service Metadata
			if (service.getResourceMetadata() == null) {
				service.setResourceMetadata(new ResourceMetadata());
			}
			service.getResourceMetadata().createdBy = gatewayUtil.getPrincipalName(user);
			service.getResourceMetadata().createdOn = (new DateTime()).toString();

			// Create the Service Job to forward
			PiazzaJobRequest jobRequest = new PiazzaJobRequest();
			jobRequest.createdBy = gatewayUtil.getPrincipalName(user);
			jobRequest.jobType = new RegisterServiceJob(service);
			// Proxy the request to the Service Controller
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate.postForEntity(String.format("%s/%s", SERVICECONTROLLER_URL, "registerService"), jobRequest, ServiceIdResponse.class).getBody(), HttpStatus.CREATED);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Registering Service by user %s: %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets the metadata for a single service.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Service/get_service
	 * 
	 * @param serviceId
	 *            The Id of the service to retrieve the data for.
	 * @param user
	 *            The user submitting the request
	 * @return Service metadata, or an error.
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Retrieve Service information", notes = "Gets a Service by its Id", tags = "Service")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The Service object.", response = ServiceResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getService(
			@ApiParam(value = "The Id of the Service to retrieve.", required = true) @PathVariable(value = "serviceId") String serviceId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested Service metadata for %s",
					gatewayUtil.getPrincipalName(user), serviceId), PiazzaLogger.INFO);
			// Proxy the request to the Service Controller instance
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate.getForEntity(String.format("%s/%s/%s", SERVICECONTROLLER_URL, "service", serviceId), ServiceResponse.class).getBody(), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting Service %s Info for user %s: %s", serviceId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * De-registers a single service with Piazza.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Service/delete_service
	 * 
	 * @param serviceId
	 *            The Id of the service to delete.
	 * @param user
	 *            The user submitting the request
	 * @return Service metadata, or an error.
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Unregister a Service", notes = "Unregisters a Service by its Id.", tags = "Service")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Confirmation of Deleted.", response = SuccessResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> deleteService(
			@ApiParam(value = "The Id of the Service to unregister.", required = true) @PathVariable(value = "serviceId") String serviceId,
			@ApiParam(value = "Determines if the Service should be completely removed, or just disabled. If set to false, the Service will be entirely deleted.", hidden = true) @RequestParam(value = "softDelete", required = false) boolean softDelete,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested Service deletion of %s",
					gatewayUtil.getPrincipalName(user), serviceId), PiazzaLogger.INFO);

			// Proxy the request to the Service Controller instance
			String url = String.format("%s/%s/%s", SERVICECONTROLLER_URL, "service", serviceId);
			url = (softDelete) ? (String.format("%s?softDelete=%s", url, softDelete)) : (url);
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate.exchange(url, HttpMethod.DELETE, null, SuccessResponse.class).getBody(), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Service %s Info for user %s: %s", serviceId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Updates an existing service with Piazza's Service Controller.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Service/put_service
	 * 
	 * @param serviceId
	 *            The Id of the service to update.
	 * @param serviceData
	 *            The service data to update the existing service with.
	 * @param user
	 *            The user submitting the request
	 * @return 200 OK if success, or an error if exceptions occur.
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Update Service Information", notes = "Updates Service Metadata, to the Service specified by Id.", tags = "Service")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Confirmation of Update.", response = SuccessResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),		
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> updateService(
			@ApiParam(value = "The Id of the Service to Update.", required = true) @PathVariable(value = "serviceId") String serviceId,
			@ApiParam(value = "The Service Metadata. All properties specified in the Service data here will overwrite the existing properties of the Service.", required = true, name = "service") @RequestBody Service serviceData,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested Service update of %s", gatewayUtil.getPrincipalName(user),
					serviceId), PiazzaLogger.INFO);

			// Proxy the request to the Service Controller instance
			HttpHeaders theHeaders = new HttpHeaders();
			// headers.add("Authorization", "Basic " + credentials);
			theHeaders.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Service> request = new HttpEntity<Service>(serviceData, theHeaders);
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate.exchange(String.format("%s/%s/%s", SERVICECONTROLLER_URL, "service", serviceId), HttpMethod.PUT, request, SuccessResponse.class).getBody(), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Updating Service %s Info for user %s: %s", serviceId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);

		}
	}

	/**
	 * Gets the list of all Services held by Piazza.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Service/get_service
	 * 
	 * @param page
	 *            The start page
	 * @param perPage
	 *            The size per page
	 * @param keyword
	 *            The keywords to search on
	 * @param createdBy
	 *            Filter services created by a certain user
	 * @param user
	 *            The user submitting the request
	 * @return The list of services; or an error.
	 */
	@RequestMapping(value = "/service", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Retrieve list of Services", notes = "Retrieves the list of available Services currently registered to this Piazza system.", tags = "Service")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Services registered to Piazza.", response = ServiceListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getServices(
			@ApiParam(value = "A general keyword search to apply to all Services.") @RequestParam(value = "keyword", required = false) String keyword,
			@ApiParam(value = "Paginating large results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			@ApiParam(value = "Filter for the user name that published the service.") @RequestParam(value = "createdBy", required = false) String createdBy,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false, defaultValue = DEFAULT_SORTBY) String sortBy,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Service List.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			
			// Validate params
			String validationError = null;
			if( (order != null && (validationError = gatewayUtil.validateInput("order", order)) != null) || 
				(page != null && (validationError = gatewayUtil.validateInput("page", page)) != null) ||
				(perPage != null && (validationError = gatewayUtil.validateInput("perPage", perPage)) != null) ) {
				return new ResponseEntity<PiazzaResponse>(new ErrorResponse(validationError, "Gateway"), HttpStatus.BAD_REQUEST);
			}
			
			// Proxy the request to the Service Controller
			String url = String.format("%s/%s?page=%s&perPage=%s", SERVICECONTROLLER_URL, "service", page, perPage);
			// Attach keywords if specified
			if ((keyword != null) && (keyword.isEmpty() == false)) {
				url = String.format("%s&keyword=%s", url, keyword);
			}
			// Add username if specified
			if ((createdBy != null) && (createdBy.isEmpty() == false)) {
				url = String.format("%s&userName=%s", url, createdBy);
			}
			// Sort by and order
			if ((order != null) && (order.isEmpty() == false)) {
				url = String.format("%s&order=%s", url, order);
			}
			if ((sortBy != null) && (sortBy.isEmpty() == false)) {
				url = String.format("%s&sortBy=%s", url, sortBy);
			}
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate.getForEntity(url, ServiceListResponse.class).getBody(), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Services by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets the services for the current user.
	 * 
	 * @param page
	 *            The start page
	 * @param perPage
	 *            The size per page
	 * @param keyword
	 *            The keywords to search on
	 * @param createdBy
	 *            Filter services created by a certain user
	 * @param user
	 *            The user submitting the request
	 * @return The list of services; or an error.
	 */
	@RequestMapping(value = "/service/me", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Retrieve list of Services", notes = "Retrieves the list of available Services currently registered to this Piazza system.", tags = "Service")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Services registered to Piazza.", response = ServiceListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getServicesForCurrentUser(
			@ApiParam(value = "A general keyword search to apply to all Services.") @RequestParam(value = "keyword", required = false) String keyword,
			@ApiParam(value = "Paginating large results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = "asc") String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false) String sortBy,
			Principal user) {
		return getServices(keyword, page, perPage, gatewayUtil.getPrincipalName(user), order, sortBy, user);
	}

	/**
	 * Proxies an ElasticSearch DSL query to the Pz-Search component to return a
	 * list of Service items.
	 * 
	 * @see http 
	 *      ://pz-swagger.stage.geointservices.io/#!/Service/post_service_query
	 * 
	 * @return The list of Services matching the query.
	 */
	@RequestMapping(value = "/service/query", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)	
	@ApiOperation(value = "Query Metadata in Piazza Services", notes = "Sends a complex query message to the Piazza Search component, that allow users to search for registered Services. Searching is capable of filtering by keywords, spatial metadata, or other dynamic information.", tags = {
			"Search", "Service" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Search results that match the query string.", response = ServiceListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> searchServices(
			@ApiParam(value = "The Query string for the Search component.", name = "search", required = true) @Valid @RequestBody SearchRequest query,
			@ApiParam(value = "Paginating large datasets. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false, defaultValue = DEFAULT_SORTBY) String sortBy,
			Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s sending a complex query for Search Services.",
							gatewayUtil.getPrincipalName(user)), PiazzaLogger.INFO);
			// Send the query to the Pz-Search component
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Object> entity = new HttpEntity<Object>(query, headers);
			ServiceListResponse searchResponse = restTemplate.postForObject(
					String.format("%s/%s?page=%s&perPage=%s&order=%s&sortBy=%s", SEARCH_URL, SEARCH_ENDPOINT, page, perPage, order, sortBy), entity, ServiceListResponse.class);
			// Respond
			return new ResponseEntity<PiazzaResponse>(searchResponse, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Services by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}