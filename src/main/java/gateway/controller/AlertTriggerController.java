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

import gateway.controller.util.GatewayUtil;
import gateway.controller.util.PiazzaRestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import model.response.AlertListResponse;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;
import model.response.SuccessResponse;
import model.response.TriggerListResponse;
import model.response.TriggerResponse;
import model.workflow.Alert;
import model.workflow.Trigger;
import model.workflow.TriggerUpdate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import util.PiazzaLogger;

/**
 * REST controller defining end points that interact with the Piazza workflow
 * service; including alerts and triggers.
 * 
 * @author Patrick.Doody
 *
 */
@Api
@RestController
public class AlertTriggerController extends PiazzaRestController {
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${workflow.url}")
	private String WORKFLOW_URL;

	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";
	private static final String DEFAULT_ORDER = "desc";
	private static final String DEFAULT_SORTBY = "createdOn";

	private RestTemplate restTemplate = new RestTemplate();

	/**
	 * Creates a new Trigger
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Trigger/post_trigger"
	 * 
	 * @param trigger
	 *            The Trigger JSON.
	 * @param user
	 *            The user making the request
	 * @return The Trigger, or an error.
	 */
	@RequestMapping(value = "/trigger", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)	
	@ApiOperation(value = "Creates a Trigger", notes = "Creates a new Trigger", tags = { "Trigger", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "The newly created Trigger", response = TriggerResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),						
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> createTrigger(
			@ApiParam(value = "The Trigger information to register. This defines the Conditions that must be hit in order for some Action to occur.", required = true)  @Valid @RequestBody Trigger trigger,
			Principal user) {
		try {
			// Log the message
			logger.log(
					String.format("User %s has requested a new Trigger to be created.",
							gatewayUtil.getPrincipalName(user)), PiazzaLogger.INFO);
			try {
				// Attempt to set the username of the Job in the Trigger to the
				// submitting username
				trigger.job.createdBy = gatewayUtil.getPrincipalName(user);
				trigger.createdBy = gatewayUtil.getPrincipalName(user);
			} catch (Exception exception) {
				logger.log(
						String.format("Failed to set the username field in Trigger created by User %s: ",
								gatewayUtil.getPrincipalName(user), exception.getMessage()), PiazzaLogger.WARNING);
			}
			
			try {
				// Proxy the request to Workflow
				return new ResponseEntity<String>(restTemplate.postForObject(String.format("%s/%s", WORKFLOW_URL, "trigger"), objectMapper.writeValueAsString(trigger), String.class), HttpStatus.CREATED);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(objectMapper.readValue(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }"), ErrorResponse.class), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Creating Trigger by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Update a Trigger
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Trigger/put_trigger
	 * 
	 * @param dataId
	 *            The Id of the resource
	 * @param user
	 *            the user submitting the request
	 * @return OK if successful; error if not.
	 */
	@RequestMapping(value = "/trigger/{triggerId}", method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Update a Trigger", notes = "This will update the Trigger to either be enabled or disabled.", tags = { "Trigger", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Confirmation that the Trigger has been updated.", response = SuccessResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),			
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> updateMetadata(
			@ApiParam(value = "Id of the Trigger to update", required = true) @PathVariable(value = "triggerId") String triggerId,
			@ApiParam(value = "The object containing the boolean field to enable or disable", required = true) @Valid @RequestBody TriggerUpdate triggerUpdate,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Update of information for %s.", gatewayUtil.getPrincipalName(user), triggerId), PiazzaLogger.INFO);
			
			// Proxy the request to Ingest
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate.exchange(String.format("%s/%s/%s", WORKFLOW_URL, "trigger", triggerId), HttpMethod.PUT, new HttpEntity<TriggerUpdate>(triggerUpdate), SuccessResponse.class).getBody(), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(objectMapper.readValue(hee.getResponseBodyAsString(), ErrorResponse.class), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Updating information for item %s by user %s: %s", triggerId, gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}	
	
	/**
	 * Gets the list of Triggers
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Trigger/get_trigger"
	 * 
	 * @return The list of Triggers, or an error.
	 */
	@RequestMapping(value = "/trigger", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "List Triggers", notes = "Returns an array of Triggers", tags = { "Trigger", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Triggers.", response = TriggerListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getTriggers(
			@ApiParam(value = "Paginating large numbers of results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false, defaultValue = DEFAULT_SORTBY) String sortBy,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested a list of Triggers.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			
			// Validate params
			String validationError = null;
			if( (order != null && (validationError = gatewayUtil.validateInput("order", order)) != null) || 
				(page != null && (validationError = gatewayUtil.validateInput("page", page)) != null) ||
				(perPage != null && (validationError = gatewayUtil.validateInput("perPage", perPage)) != null) ) {
				return new ResponseEntity<PiazzaResponse>(new ErrorResponse(validationError, "Gateway"), HttpStatus.BAD_REQUEST);
			}			
			
			try {
				// Broker the request to Workflow
				String url = String.format("%s/%s?page=%s&perPage=%s&order=%s&sortBy=%s", WORKFLOW_URL, 
						"trigger", page, perPage, order, sortBy != null ? sortBy : "");
				return new ResponseEntity<String>(restTemplate.getForObject(url, String.class), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(objectMapper.readValue(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }"), ErrorResponse.class), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Triggers by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets Trigger by the Id of that Trigger
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Trigger/get_trigger_triggerId"
	 * 
	 * @param triggerId
	 *            The Trigger Id
	 * @param user
	 *            The user submitting the request
	 * @return Trigger information, or an error
	 */
	@RequestMapping(value = "/trigger/{triggerId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Gets a Trigger", notes = "Gets a Trigger by its Id.", tags = { "Trigger", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The Trigger definition matching the specified Trigger Id", response = Trigger.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getTrigger(
			@ApiParam(value = "The Id of the Trigger to retrieve.", required = true) @PathVariable(value = "triggerId") String triggerId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested information for Trigger %s", gatewayUtil.getPrincipalName(user), triggerId), PiazzaLogger.INFO);
			
			try {
				// Proxy the request to Workflow
				return new ResponseEntity<String>(restTemplate.getForObject(String.format("%s/%s/%s", WORKFLOW_URL, "trigger", triggerId), String.class), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(objectMapper.readValue(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }"), ErrorResponse.class), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting Trigger Id %s by user %s: %s", triggerId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes a Trigger by its Id
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Trigger/delete_trigger_triggerId"
	 * 
	 * @param triggerId
	 *            The Id of the trigger to delete
	 * @param user
	 *            The user submitting the request
	 * @return 200 OK if deleted, error if exceptions occurred
	 */
	@RequestMapping(value = "/trigger/{triggerId}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Deletes a Trigger", notes = "Deletes a Trigger. This Trigger will no longer listen for conditions for events to fire.", tags = {
			"Trigger", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Message indicating Trigger was deleted successfully", response = SuccessResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> deleteTrigger(
			@ApiParam(value = "The Id of the Trigger to delete.", required = true) @PathVariable(value = "triggerId") String triggerId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested deletion of Trigger %s",
					gatewayUtil.getPrincipalName(user), triggerId), PiazzaLogger.INFO);
			
			try {
				// Proxy the request to Workflow
				restTemplate.delete(String.format("%s/%s/%s", WORKFLOW_URL, "trigger", triggerId));
				return new ResponseEntity<PiazzaResponse>(new SuccessResponse("Trigger " + triggerId + " was deleted successfully", "Gateway"), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(objectMapper.readValue(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }"), ErrorResponse.class), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Trigger Id %s by user %s: %s", triggerId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Gets the list of Alerts
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Alert/get_alert"
	 * 
	 * @return The list of Alerts, or an error
	 */
	@RequestMapping(value = "/alert", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Get User Alerts", notes = "Gets all of the Alerts", tags = { "Alert", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Alerts.", response = AlertListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getAlerts(
			@ApiParam(value = "Paginating large numbers of results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false, defaultValue = DEFAULT_SORTBY) String sortBy,
			@ApiParam(value = "The Trigger Id by which to filter results.") @RequestParam(value = "triggerId", required = false) String triggerId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested a list of Alerts.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			
			// Validate params
			String validationError = null;
			if( (order != null && (validationError = gatewayUtil.validateInput("order", order)) != null) || 
				(page != null && (validationError = gatewayUtil.validateInput("page", page)) != null) ||
				(perPage != null && (validationError = gatewayUtil.validateInput("perPage", perPage)) != null) ) {
				return new ResponseEntity<PiazzaResponse>(new ErrorResponse(validationError, "Gateway"), HttpStatus.BAD_REQUEST);
			}			
			
			try {
				// Broker the request to Workflow
				String url = String.format("%s/%s?page=%s&perPage=%s&order=%s&sortBy=%s&triggerId=%s",
						WORKFLOW_URL, "alert", page, perPage, order, sortBy != null ? sortBy : "",
						triggerId != null ? triggerId : "");
				return new ResponseEntity<String>(restTemplate.getForObject(url, String.class), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(objectMapper.readValue(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }"), ErrorResponse.class), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error querying Alerts by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets Alert
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Alert/get_alert_alertId"
	 * 
	 * @param alertId
	 *            The trigger Id
	 * @param user
	 *            The user submitting the request
	 * @return Trigger information, or an error
	 */
	@RequestMapping(value = "/alert/{alertId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Get Alert Information", notes = "Gets an Alert by its Id", tags = { "Alert",	"Workflow" })
	@ApiResponses(value = { 
			@ApiResponse(code = 200, message = "The Alert", response = Alert.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getAlert(
			@ApiParam(value = "The Id of the Alert to retrieve data for.", required = true) @PathVariable(value = "alertId") String alertId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested information for Alert %s",
					gatewayUtil.getPrincipalName(user), alertId), PiazzaLogger.INFO);
			
			try {
				// Proxy the request to Workflow
				return new ResponseEntity<String>(restTemplate.getForObject(String.format("%s/%s/%s", WORKFLOW_URL, "alert", alertId), String.class), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(objectMapper.readValue(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }"), ErrorResponse.class), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting Alert Id %s by user %s: %s", alertId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
