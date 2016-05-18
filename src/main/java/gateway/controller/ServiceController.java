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
	@RequestMapping(value = "/service", method = RequestMethod.POST)
	public ResponseEntity<PiazzaResponse> registerService(@RequestBody Service service, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Service registration.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
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
			String error = String.format("Error Registering Service by user %s: %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
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
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getService(@PathVariable(value = "serviceId") String serviceId, Principal user) {
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
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> deleteService(@PathVariable(value = "serviceId") String serviceId,
			@RequestParam(value = "softDelete", required = false) boolean softDelete, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested Service deletion of %s",
					gatewayUtil.getPrincipalName(user), serviceId), PiazzaLogger.INFO);

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
	@RequestMapping(value = "/service/{serviceId}", method = RequestMethod.PUT)
	public ResponseEntity<PiazzaResponse> updateService(@PathVariable(value = "serviceId") String serviceId,
			@RequestBody Service serviceData, Principal user) {
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
	 * @param user
	 *            The user submitting the request
	 * @return The list of services; or an error.
	 */
	@RequestMapping(value = "/service", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getServices(
			@RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@RequestParam(value = "keyword", required = false) String keyword, Principal user) {
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
	 * Proxies an ElasticSearch DSL query to the Pz-Search component to return a
	 * list of Service items.
	 * 
	 * @see http 
	 *      ://pz-swagger.stage.geointservices.io/#!/Service/post_service_query
	 * 
	 * @return The list of Services matching the query.
	 */
	@RequestMapping(value = "/service/query", method = RequestMethod.POST)
	public ResponseEntity<PiazzaResponse> searchServices(@RequestBody Object query, Principal user) {
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
