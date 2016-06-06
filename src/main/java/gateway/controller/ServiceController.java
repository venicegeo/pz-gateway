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

import model.job.metadata.ResourceMetadata;
import model.job.type.RegisterServiceJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;
import model.response.ServiceListResponse;
import model.response.ServiceResponse;
import model.service.metadata.Service;

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

import util.PiazzaLogger;

/**
 * REST controller that handles requests for interacting with the Piazza Service
 * Controller component.
 * 
 * @author Patrick.Doody
 *
 */
@Api
@CrossOrigin
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

	private RestTemplate restTemplate = new RestTemplate();
	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";

	/**
	 * Registers an external service with the Piazza Service Controller.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Service/post_service
	 * 
	 * @param service
	 *            The service to register.
	 * @param user
	 *            The user submitting the request
	 * @return The Service ID, or appropriate error.
	 */
	@RequestMapping(value = "/service", method = RequestMethod.POST, produces = "application/json")
	@ApiOperation(value = "Register new Service definition", notes = "Creates a new Service with the Piazza Service Controller; that can be invoked through Piazza jobs with Piazza data.", tags = "Service", response = ServiceResponse.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The ID of the newly created Service") })
	public ResponseEntity<PiazzaResponse> registerService(
			@ApiParam(value = "The metadata for the service. This includes the URL, parameters, inputs and outputs. It also includes other release metadata such as classification and availability.") @RequestBody(required = true) Service service,
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

			// Create the Service Job to forward
			PiazzaJobRequest jobRequest = new PiazzaJobRequest();
			jobRequest.userName = gatewayUtil.getPrincipalName(user);
			jobRequest.jobType = new RegisterServiceJob(service);
			// Proxy the request to the Service Controller
			PiazzaResponse response = restTemplate.postForObject(
					String.format("%s/%s", SERVICECONTROLLER_URL, "registerService"), jobRequest, PiazzaResponse.class);
			HttpStatus status = response instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
			// Respond
			return new ResponseEntity<PiazzaResponse>(response, status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Registering Service by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets the metadata for a single service.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Service/get_service
	 * 
	 * @param serviceId
	 *            The ID of the service to retrieve the data for.
	 * @param user
	 *            The user submitting the request
	 * @return Service metadata, or an error.
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "Retrieve Service information", notes = "Retrieves the information and metadata for the specified Service matching the ID.", tags = "Service", response = Service.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The Service object.") })
	public ResponseEntity<PiazzaResponse> getService(
			@ApiParam(value = "The ID of the Service to retrieve.", required = true) @PathVariable(value = "serviceId") String serviceId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested Service metadata for %s",
					gatewayUtil.getPrincipalName(user), serviceId), PiazzaLogger.INFO);
			// Proxy the request to the Service Controller instance
			PiazzaResponse response = restTemplate.getForObject(
					String.format("%s/%s/%s", SERVICECONTROLLER_URL, "service", serviceId), PiazzaResponse.class);
			HttpStatus status = response instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
			// Respond
			return new ResponseEntity<PiazzaResponse>(response, status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting Service %s Info for user %s: %s", serviceId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * De-registers a single service with Piazza.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Service/delete_service
	 * 
	 * @param serviceId
	 *            The ID of the service to delete.
	 * @param user
	 *            The user submitting the request
	 * @return Service metadata, or an error.
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.DELETE, produces = "application/json")
	@ApiOperation(value = "Unregister a Service", notes = "Unregisters a service by its ID.", tags = "Service")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Confirmation of Deleted.") })
	public ResponseEntity<?> deleteService(
			@ApiParam(value = "The ID of the Service to unregister.", required = true) @PathVariable(value = "serviceId") String serviceId,
			@ApiParam(hidden = true) @RequestParam(value = "softDelete", required = false) boolean softDelete,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested Service deletion of %s", gatewayUtil.getPrincipalName(user),
					serviceId), PiazzaLogger.INFO);

			// Proxy the request to the Service Controller instance
			String url = String.format("%s/%s/%s", SERVICECONTROLLER_URL, "service", serviceId);
			url = (softDelete) ? (String.format("%s?softDelete=%s", url, softDelete)) : (url);
			restTemplate.delete(url);

			return new ResponseEntity<PiazzaResponse>(new ServiceResponse(serviceId), HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Service %s Info for user %s: %s", serviceId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Updates an existing service with Piazza's Service Controller.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Service/put_service
	 * 
	 * @param serviceId
	 *            The ID of the service to update.
	 * @param serviceData
	 *            The service data to update the existing service with.
	 * @param user
	 *            The user submitting the request
	 * @return 200 OK if success, or an error if exceptions occur.
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.PUT, produces = "application/json")
	@ApiOperation(value = "Update Service Information", notes = "Updates a Service Metadata, with the Service to updated specified by its ID.", tags = "Service")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Confirmation of Update.") })
	public ResponseEntity<PiazzaResponse> updateService(
			@ApiParam(value = "The ID of the Service to Update.", required = true) @PathVariable(value = "serviceId") String serviceId,
			@ApiParam(value = "The Service Metadata. All properties specified in the Service data here will overwrite the existing properties of the Service.", required = true) @RequestBody Service serviceData,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested Service update of %s", gatewayUtil.getPrincipalName(user),
					serviceId), PiazzaLogger.INFO);
			// Proxy the request to the Service Controller instance
			restTemplate.put(String.format("%s/%s/%s", SERVICECONTROLLER_URL, "service", serviceId), serviceData);
			return null;
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Updating Service %s Info for user %s: %s", serviceId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
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
	 * @param pageSize
	 *            The size per page
	 * @param keyword
	 *            The keywords to search on
	 * @param userName
	 *            Filter services created by a certain user
	 * @param user
	 *            The user submitting the request
	 * @return The list of services; or an error.
	 */
	@RequestMapping(value = "/service", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "Retrieve list of Services", notes = "Retrieves the list of available Services currently registered to this Piazza system.", tags = "Service", response = ServiceListResponse.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The list of Services registered to Piazza.") })
	public ResponseEntity<PiazzaResponse> getServices(
			@ApiParam(value = "A general keyword search to apply to all Services.") @RequestParam(value = "keyword", required = false) String keyword,
			@ApiParam(value = "Paginating large results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@RequestParam(value = "userName", required = false) String userName, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Service List.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Proxy the request to the Service Controller
			String url = String.format("%s/%s?page=%s&per_page=%s", SERVICECONTROLLER_URL, "service", page, pageSize);
			// Attach keywords if specified
			if ((keyword != null) && (keyword.isEmpty() == false)) {
				url = String.format("%s&keyword=%s", url, keyword);
			}
			// Add username if specified
			if ((userName != null) && (userName.isEmpty() == false)) {
				url = String.format("%s&userName=%s", url, userName);
			}
			PiazzaResponse servicesResponse = restTemplate.getForObject(url, PiazzaResponse.class);
			HttpStatus status = servicesResponse instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
					: HttpStatus.OK;
			// Respond
			return new ResponseEntity<PiazzaResponse>(servicesResponse, status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Services by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets the services for the current user.
	 * 
	 * @param page
	 *            The start page
	 * @param pageSize
	 *            The size per page
	 * @param keyword
	 *            The keywords to search on
	 * @param userName
	 *            Filter services created by a certain user
	 * @param user
	 *            The user submitting the request
	 * @return The list of services; or an error.
	 */
	@RequestMapping(value = "/service/me", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getServicesForCurrentUser(
			@ApiParam(value = "A general keyword search to apply to all Services.") @RequestParam(value = "keyword", required = false) String keyword,
			@ApiParam(value = "Paginating large results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			Principal user) {
		return getServices(keyword, page, pageSize, gatewayUtil.getPrincipalName(user), user);
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
	@RequestMapping(value = "/service/query", method = RequestMethod.POST, produces = "application/json")
	@ApiOperation(value = "Query Metadata in Piazza Services", notes = "Sends a complex query message to the Piazza Search component, that allow users to search for registered Services. Searching is capable of filtering by keywords, spatial metadata, or other dynamic information.", tags = {
			"Search", "Service" }, response = ServiceListResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Search results that match the query string.") })
	public ResponseEntity<PiazzaResponse> searchServices(
			@ApiParam(value = "The Query string for the Search component.", name = "search", required = true) @RequestBody Object query,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s sending a complex query for Search Services.",
					gatewayUtil.getPrincipalName(user)), PiazzaLogger.INFO);
			// Send the query to the Pz-Search component
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Object> entity = new HttpEntity<Object>(query, headers);
			ServiceListResponse searchResponse = restTemplate.postForObject(
					String.format("%s/%s", SEARCH_URL, SEARCH_ENDPOINT), entity, ServiceListResponse.class);
			// Respond
			return new ResponseEntity<PiazzaResponse>(searchResponse, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Services by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}