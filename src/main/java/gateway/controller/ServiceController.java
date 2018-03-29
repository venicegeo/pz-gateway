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
import java.util.HashMap;
import java.util.Map;

import javax.validation.Valid;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
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

import gateway.controller.util.GatewayUtil;
import gateway.controller.util.PiazzaRestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import model.job.metadata.ResourceMetadata;
import model.job.type.RegisterServiceJob;
import model.logger.AuditElement;
import model.logger.Severity;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;
import model.response.ServiceIdResponse;
import model.response.ServiceJobResponse;
import model.response.ServiceListResponse;
import model.response.ServiceResponse;
import model.response.SuccessResponse;
import model.service.metadata.Service;
import model.status.StatusUpdate;
import util.PiazzaLogger;

/**
 * REST controller that handles requests for interacting with the Piazza Service Controller component.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@Api
@RestController
public class ServiceController extends PiazzaRestController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${servicecontroller.url}")
	private String SERVICECONTROLLER_URL;

	@Autowired
	private RestTemplate restTemplate;
	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";
	private static final String DEFAULT_SORTBY = "resourceMetadata.createdOn";
	private static final String DEFAULT_SERVICE_SORTBY = "service.resourceMetadata.createdOn";
	private static final String DEFAULT_ORDER = "desc";
	private static final String GATEWAY = "Gateway";
	private static final String URL_FORMAT = "%s/%s/%s";
	private static final String SERVICE = "service";

	private final static Logger LOG = LoggerFactory.getLogger(ServiceController.class);

	/**
	 * Registers an external service with the Piazza Service Controller.
	 * 
	 * @see http://pz-swagger/#!/Service/post_service
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
	@ApiResponses(value = { @ApiResponse(code = 201, message = "The Id of the newly created Service", response = ServiceIdResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> registerService(
			@ApiParam(value = "The metadata for the service. This includes the URL, parameters, inputs and outputs. It also includes other release metadata such as classification and availability.", required = true) @Valid @RequestBody Service service,
			Principal user) {
		try {
			// Log the request
			String userName = gatewayUtil.getPrincipalName(user);
			String dn = gatewayUtil.getDistinguishedName(SecurityContextHolder.getContext().getAuthentication());
			logger.log(String.format("User %s requested Service registration.", userName), Severity.INFORMATIONAL,
					new AuditElement(dn, "requestServiceRegistration", ""));

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
				ResponseEntity<PiazzaResponse> response = new ResponseEntity<PiazzaResponse>(
						restTemplate.postForEntity(String.format("%s/%s", SERVICECONTROLLER_URL, "registerService"), jobRequest,
								ServiceIdResponse.class).getBody(),
						HttpStatus.CREATED);
				logger.log(String.format("User %s Registered Service.", userName), Severity.INFORMATIONAL, new AuditElement(dn,
						"completeServiceRegistration", ((ServiceIdResponse) response.getBody()).data.getServiceId()));
				return response;
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error("Error Registering Service", hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error Registering Service by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets the metadata for a single service.
	 * 
	 * @see http://pz-swagger/#!/Service/get_service
	 * 
	 * @param serviceId
	 *            The Id of the service to retrieve the data for.
	 * @param user
	 *            The user submitting the request
	 * @return Service metadata, or an error.
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Retrieve Service information", notes = "Gets a Service by its Id", tags = "Service")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The Service object.", response = ServiceResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getService(
			@ApiParam(value = "The Id of the Service to retrieve.", required = true) @PathVariable(value = "serviceId") String serviceId,
			Principal user) {
		try {
			// Log the request
			String dn = gatewayUtil.getDistinguishedName(SecurityContextHolder.getContext().getAuthentication());
			logger.log(String.format("User %s has requested Service metadata for %s", gatewayUtil.getPrincipalName(user), serviceId),
					Severity.INFORMATIONAL, new AuditElement(dn, "requestServiceMetadataFetch", serviceId));
			// Proxy the request to the Service Controller instance
			try {
				ResponseEntity<PiazzaResponse> response = new ResponseEntity<PiazzaResponse>(restTemplate
						.getForEntity(String.format(URL_FORMAT, SERVICECONTROLLER_URL, SERVICE, serviceId), ServiceResponse.class)
						.getBody(), HttpStatus.OK);
				logger.log(String.format("User %s Retrieved Service metadata for %s", gatewayUtil.getPrincipalName(user), serviceId),
						Severity.INFORMATIONAL, new AuditElement(dn, "completeServiceMetadataFetch", serviceId));
				return response;
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error("Error Getting Service Info", hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error Getting Service %s Info for user %s: %s", serviceId, gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * De-registers a single service with Piazza.
	 * 
	 * @see http://pz-swagger/#!/Service/delete_service
	 * 
	 * @param serviceId
	 *            The Id of the service to delete.
	 * @param user
	 *            The user submitting the request
	 * @return Service metadata, or an error.
	 */
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Unregister a Service", notes = "Unregisters a Service by its Id.", tags = "Service")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Confirmation of Deleted.", response = SuccessResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> deleteService(
			@ApiParam(value = "The Id of the Service to unregister.", required = true) @PathVariable(value = "serviceId") String serviceId,
			@ApiParam(value = "Determines if the Service should be completely removed, or just disabled. If set to false, the Service will be entirely deleted.", hidden = true) @RequestParam(value = "softDelete", required = false) boolean softDelete,
			Principal user) {
		try {
			// Log the request
			String userName = gatewayUtil.getPrincipalName(user);
			String dn = gatewayUtil.getDistinguishedName(SecurityContextHolder.getContext().getAuthentication());
			logger.log(String.format("User %s has requested Service deletion of %s", userName, serviceId), Severity.INFORMATIONAL,
					new AuditElement(dn, "requestServiceDelete", serviceId));

			// Proxy the request to the Service Controller instance
			String url = String.format(URL_FORMAT, SERVICECONTROLLER_URL, SERVICE, serviceId);
			url = (softDelete) ? (String.format("%s?softDelete=%s", url, softDelete)) : (url);
			try {
				ResponseEntity<PiazzaResponse> response = new ResponseEntity<PiazzaResponse>(
						restTemplate.exchange(url, HttpMethod.DELETE, null, SuccessResponse.class).getBody(), HttpStatus.OK);
				logger.log(String.format("User %s has Deleted Service %s", userName, serviceId), Severity.INFORMATIONAL,
						new AuditElement(dn, "completeServiceDelete", serviceId));
				return response;
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error("Error Deleting Service", hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error Deleting Service %s Info for user %s: %s", serviceId, gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Updates an existing service with Piazza's Service Controller.
	 * 
	 * @see http://pz-swagger/#!/Service/put_service
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
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Confirmation of Update.", response = SuccessResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> updateService(
			@ApiParam(value = "The Id of the Service to Update.", required = true) @PathVariable(value = "serviceId") String serviceId,
			@ApiParam(value = "The Service Metadata. All properties specified in the Service data here will overwrite the existing properties of the Service.", required = true, name = SERVICE) @RequestBody Service serviceData,
			Principal user) {
		try {
			// Log the request
			String userName = gatewayUtil.getPrincipalName(user);
			String dn = gatewayUtil.getDistinguishedName(SecurityContextHolder.getContext().getAuthentication());
			logger.log(String.format("User %s has requested Service update of %s", userName, serviceId), Severity.INFORMATIONAL,
					new AuditElement(dn, "requestUpdateService", serviceId));

			// Proxy the request to the Service Controller instance
			HttpHeaders theHeaders = new HttpHeaders();
			// headers.add("Authorization", "Basic " + credentials);
			theHeaders.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Service> request = new HttpEntity<Service>(serviceData, theHeaders);
			try {
				ResponseEntity<PiazzaResponse> response = new ResponseEntity<PiazzaResponse>(
						restTemplate.exchange(String.format(URL_FORMAT, SERVICECONTROLLER_URL, SERVICE, serviceId), HttpMethod.PUT,
								request, SuccessResponse.class).getBody(),
						HttpStatus.OK);
				logger.log(String.format("User %s has Updated Service %s", userName, serviceId), Severity.INFORMATIONAL,
						new AuditElement(dn, "completeUpdateService", serviceId));
				return response;
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error("Error Updating Service", hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error Updating Service %s Info for user %s: %s", serviceId, gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);

		}
	}

	/**
	 * Gets the list of all Services held by Piazza.
	 * 
	 * @see http://pz-swagger/#!/Service/get_service
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
			String userName = gatewayUtil.getPrincipalName(user);
			String dn = gatewayUtil.getDistinguishedName(SecurityContextHolder.getContext().getAuthentication());
			logger.log(String.format("User %s requested Service List.", userName), Severity.INFORMATIONAL,
					new AuditElement(dn, "requestServiceList", ""));

			// Validate params
			String validationError = gatewayUtil.joinValidationErrors(
					gatewayUtil.validateInput("order", order),
					gatewayUtil.validateInput("page", page),
					gatewayUtil.validateInput("perPage", perPage)
					);
			if ( validationError != null ) {
				return new ResponseEntity<PiazzaResponse>(new ErrorResponse(validationError, GATEWAY), HttpStatus.BAD_REQUEST);
			}

			// Proxy the request to the Service Controller
			String url = String.format("%s/%s?page=%s&perPage=%s", SERVICECONTROLLER_URL, SERVICE, page, perPage);
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
				ResponseEntity<PiazzaResponse> response = new ResponseEntity<PiazzaResponse>(
						restTemplate.getForEntity(url, ServiceListResponse.class).getBody(), HttpStatus.OK);
				logger.log(String.format("User %s Retrieved Service List.", userName), Severity.INFORMATIONAL,
						new AuditElement(dn, "completeServiceList", ""));
				return response;
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error("Error Querying Services", hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error Querying Services by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);
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
	 * Gets the next Job in the Service ID queue.
	 * 
	 * @param serviceId
	 *            The ID of the service.
	 * @return The Job information (perhaps null, if no jobs available) or an Error.
	 */
	@RequestMapping(value = { "/service/{serviceId}/task" }, method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Get Next Job in Service's Job Queue", notes = "For the specified Service, assuming it was registed as a Task-Managed Service, this will retrieve the next Job off of that Service's Jobs Queue.", tags = {
			"Service" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The payload containing the Job information, including input and Id.", response = ServiceJobResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getNextJobInQueue(
			@ApiParam(value = "The Id of the Service whose Queue to retrieve a Job from.") @PathVariable(value = "serviceId") String serviceId,
			Principal user) {
		try {
			// Log the request
			String userName = gatewayUtil.getPrincipalName(user);
			String dn = gatewayUtil.getDistinguishedName(SecurityContextHolder.getContext().getAuthentication());
			logger.log(String.format("User %s has requested Retrieve Task-Managed Service Job for Service %s", userName, serviceId),
					Severity.INFORMATIONAL, new AuditElement(dn, "requestRetrieveTaskManagedJob", serviceId));

			// Proxy the request to the Service Controller instance
			HttpHeaders theHeaders = new HttpHeaders();
			theHeaders.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity request = new HttpEntity(theHeaders);
			try {
				String url = String.format("%s/service/%s/task?userName=%s", SERVICECONTROLLER_URL, serviceId, userName);
				ResponseEntity<PiazzaResponse> response = new ResponseEntity<PiazzaResponse>(
						restTemplate.exchange(url, HttpMethod.POST, request, ServiceJobResponse.class).getBody(), HttpStatus.OK);
				logger.log(String.format("User %s has Retrieve Service Job information for Service %s", userName, serviceId),
						Severity.INFORMATIONAL, new AuditElement(dn, "completeRetrieveTaskManagedJob", serviceId));
				return response;
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error("Error Fetching Job from Task Managed Service Queue.", hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error Retrieving Task-Managed Service's Job by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Updates Job Status for a Job on a Task-Managed Service
	 * 
	 * @param serviceId
	 *            The Service ID
	 * @param jobId
	 *            The Job ID
	 * @param statusUpdate
	 *            The contents of the Update
	 * @param user
	 *            The user requesting the Update
	 * @return OK if updated, Error if not.
	 */
	@RequestMapping(value = {
			"/service/{serviceId}/task/{jobId}" }, method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Update Job information for the specified Service.", notes = "Allows for updating of Status (including Results) for Jobs handled by a Task-Managed User Service", tags = {
			"Service" })
	@ApiResponses(value = { @ApiResponse(code = 200, message = "OK message stating the update succeeded", response = SuccessResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> updateServiceJobStatus(
			@ApiParam(value = "The Id of the Service whose Job to Update.") @PathVariable(value = "serviceId") String serviceId,
			@ApiParam(value = "The Id of the Job whose Status to Update.") @PathVariable(value = "jobId") String jobId,
			@ApiParam(value = "The contents of the Status Update.") @Valid @RequestBody StatusUpdate statusUpdate, Principal user) {
		try {
			// Log the request
			String userName = gatewayUtil.getPrincipalName(user);
			String dn = gatewayUtil.getDistinguishedName(SecurityContextHolder.getContext().getAuthentication());
			logger.log(String.format("User %s has requested Update Task-Managed Service Job for Job %s", userName, jobId),
					Severity.INFORMATIONAL, new AuditElement(dn, "requestUpdateTaskManagedJob", jobId));

			// Proxy the request to the Service Controller instance
			HttpHeaders theHeaders = new HttpHeaders();
			theHeaders.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<StatusUpdate> request = new HttpEntity<>(statusUpdate, theHeaders);
			try {
				String url = String.format("%s/service/%s/task/%s?userName=%s", SERVICECONTROLLER_URL, serviceId, jobId, userName);
				ResponseEntity<PiazzaResponse> response = new ResponseEntity<PiazzaResponse>(
						restTemplate.exchange(url, HttpMethod.POST, request, SuccessResponse.class).getBody(), HttpStatus.OK);
				logger.log(String.format("User %s has Updated Service Job %s information for Service %s", userName, jobId, serviceId),
						Severity.INFORMATIONAL, new AuditElement(dn, "completeUpdatedTaskManagedJob", jobId));
				return response;
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error("Error Updating Job from Task Managed Service Queue.", hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error Updating Job Status for Job %s in Service %s by User %s : %s", jobId, serviceId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets Metadata for a specific Task-Managed Service.
	 * 
	 * @param serviceId
	 *            The ID of the Service
	 * @return Map containing information regarding the Task-Managed Service
	 */
	@SuppressWarnings("rawtypes")
	@RequestMapping(value = {
			"/service/{serviceId}/task/metadata" }, method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Get Task-Managed Service Metadata", notes = "Returns specific metadata on the current Job Queue for a Task-Managed Service, such as the number of jobs currently in the queue.", tags = {
			"Service" })
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Metadata information", response = Map.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity getServiceQueueData(@PathVariable(value = "serviceId") String serviceId, Principal user) {
		try {
			// Log the Request
			String userName = gatewayUtil.getPrincipalName(user);
			String dn = gatewayUtil.getDistinguishedName(SecurityContextHolder.getContext().getAuthentication());
			logger.log(String.format("User %s has requested Task-Managed Service Metadata for Service %s", userName, serviceId),
					Severity.INFORMATIONAL, new AuditElement(dn, "requestTaskManagedMetadata", serviceId));

			// Proxy the request to the Service Controller instance
			HttpHeaders theHeaders = new HttpHeaders();
			theHeaders.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity request = new HttpEntity(theHeaders);
			try {
				String url = String.format("%s/service/%s/task/metadata?userName=%s", SERVICECONTROLLER_URL, serviceId, userName);
				ResponseEntity<Map<String, Object>> response = new ResponseEntity<>(
						restTemplate.exchange(url, HttpMethod.GET, request, HashMap.class).getBody(), HttpStatus.OK);
				logger.log(String.format("User %s has Retrieve Service Job information for Service %s", userName, serviceId),
						Severity.INFORMATIONAL, new AuditElement(dn, "completeRetrieveTaskManagedMetadata", serviceId));
				return response;
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				LOG.error("Error Fetching Job from Task Managed Service Queue.", hee);
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			String error = String.format("Error Retrieving Task-Managed Service Metadata Information user %s: %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			LOG.error(error, exception);
			logger.log(error, Severity.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, GATEWAY), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}