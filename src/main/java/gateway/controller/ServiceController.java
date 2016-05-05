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

import java.security.Principal;

import model.job.type.RegisterServiceJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;
import model.service.metadata.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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
public class ServiceController extends ErrorController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;

	@Value("${servicecontroller.protocol}")
	private String SERVICE_CONTROLLER_PROTOCOL;
	@Value("${servicecontroller.host}")
	private String SERVICE_CONTROLLER_HOST;

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
			PiazzaResponse response = restTemplate.postForObject(String.format("%s://%s/%s",
					SERVICE_CONTROLLER_PROTOCOL, SERVICE_CONTROLLER_HOST, "registerService"), jobRequest,
					PiazzaResponse.class);
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
			PiazzaResponse response = restTemplate.getForObject(String.format("%s://%s/%s/%s",
					SERVICE_CONTROLLER_PROTOCOL, SERVICE_CONTROLLER_HOST, "service", serviceId), PiazzaResponse.class);
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
	public ResponseEntity<PiazzaResponse> deleteService(@PathVariable(value = "serviceId") String serviceId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested Service deletion of %s",
					gatewayUtil.getPrincipalName(user), serviceId), PiazzaLogger.INFO);
			// Proxy the request to the Service Controller instance
			restTemplate.delete(String.format("%s://%s/%s/%s", SERVICE_CONTROLLER_PROTOCOL, SERVICE_CONTROLLER_HOST,
					"service", serviceId));
			return null;
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
			restTemplate.put(String.format("%s://%s/%s/%s", SERVICE_CONTROLLER_PROTOCOL, SERVICE_CONTROLLER_HOST,
					"service", serviceId), serviceData);
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
			String url = String.format("%s://%s/%s?page=%s&per_page=%s", SERVICE_CONTROLLER_PROTOCOL,
					SERVICE_CONTROLLER_HOST, "service", page, pageSize);
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
}
