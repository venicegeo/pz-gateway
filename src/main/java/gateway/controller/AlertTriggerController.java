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
import model.response.WorkflowResponse;
import model.workflow.Alert;
import model.workflow.Trigger;

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
@CrossOrigin
@RestController
public class AlertTriggerController extends PiazzaRestController {
	@Autowired
	private ObjectMapper om;
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${workflow.url}")
	private String WORKFLOW_URL;

	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";
	private static final String DEFAULT_ORDER = "asc";

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
	 * @return The ID of the Trigger, or an error.
	 */
	@RequestMapping(value = "/trigger", method = RequestMethod.POST, produces = "application/json")
	@ApiOperation(value = "Creates a Trigger", notes = "Creates a new Trigger with the Piazza Workflow component.", tags = {
			"Trigger", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The ID of the newly created Trigger", response = WorkflowResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> createTrigger(
			@ApiParam(value = "The Trigger information to register. This defines the Conditions that must be hit in order for some Action to occur.", required = true) @RequestBody Trigger trigger,
			Principal user) {
		try {
			// Log the message
			logger.log(String.format("User %s has requested a new Trigger to be created.",
					gatewayUtil.getPrincipalName(user)), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v2/%s", WORKFLOW_URL, "trigger");
			WorkflowResponse response = restTemplate.postForObject(url, om.writeValueAsString(trigger),
					WorkflowResponse.class);
			return new ResponseEntity<WorkflowResponse>(response, HttpStatus.OK);
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
	 * Gets the list of Triggers
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Trigger/get_trigger"
	 * 
	 * @return The list of Triggers, or an error.
	 */
	@RequestMapping(value = "/trigger", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "List Triggers", notes = "Lists all of the defined Triggers in the Piazza Workflow component.", tags = {
			"Trigger", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Triggers.", response = TriggerListResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getTriggers(
			@ApiParam(value = "A general keyword search to apply to all triggers.") @RequestParam(value = "key", required = false) String key,
			@ApiParam(value = "Paginating large numbers of results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested a list of Triggers.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Broker the request to Workflow
			String url = String.format("%s/v2/%s?page=%s&per_page=%s&order=%s&sort_by=%s", WORKFLOW_URL, "trigger",
					page, pageSize, order, key != null ? key : "");
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
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
	 * Gets Trigger metadata by the ID of that Trigger
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Trigger/get_trigger_triggerId"
	 * 
	 * @param triggerId
	 *            The trigger ID
	 * @param user
	 *            The user submitting the request
	 * @return Trigger information, or an error
	 */
	@RequestMapping(value = "/trigger/{triggerId}", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "Gets Metadata for a Trigger", notes = "Retrieves the Trigger definition for the Trigger matching the specified Trigger ID.", tags = {
			"Trigger", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The Trigger definition matching the specified Trigger ID.", response = Trigger.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getTrigger(
			@ApiParam(value = "The ID of the Trigger to retrieve.", required = true) @PathVariable(value = "triggerId") String triggerId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested information for Trigger %s",
					gatewayUtil.getPrincipalName(user), triggerId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v2/%s/%s", WORKFLOW_URL, "trigger", triggerId);
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting Trigger ID %s by user %s: %s", triggerId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes a Trigger by its ID
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Trigger/delete_trigger_triggerId"
	 * 
	 * @param triggerId
	 *            The ID of the trigger to delete
	 * @param user
	 *            The user submitting the request
	 * @return 200 OK if deleted, error if exceptions occurred
	 */
	@RequestMapping(value = "/trigger/{triggerId}", method = RequestMethod.DELETE, produces = "application/json")
	@ApiOperation(value = "Deletes a Trigger", notes = "Deletes a Trigger with the Workflow component. This Trigger will no longer listen for conditions for events to fire.", tags = {
			"Trigger", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Message indicating Trigger was deleted successfully", response = SuccessResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> deleteTrigger(
			@ApiParam(value = "The ID of the Trigger to delete.", required = true) @PathVariable(value = "triggerId") String triggerId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested deletion of Trigger %s", gatewayUtil.getPrincipalName(user),
					triggerId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v2/%s/%s", WORKFLOW_URL, "trigger", triggerId);
			restTemplate.delete(url);
			return new ResponseEntity<PiazzaResponse>(
					new SuccessResponse("Trigger " + triggerId + " was deleted successfully", "Gateway"),
					HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Trigger ID %s by user %s: %s", triggerId,
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
	@RequestMapping(value = "/alert", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "Get User Alerts", notes = "Gets all of the Alerts for the currently authenticated user. Alerts occur when a Trigger's conditions are met.", tags = {
			"Alert", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Alerts owned by the current User.", response = AlertListResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getAlerts(
			@ApiParam(value = "Paginating large numbers of results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "A general keyword search to apply to all alerts.") @RequestParam(value = "key", required = false) String key,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested a list of Alerts.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Broker the request to Workflow
			String url = String.format("%s/v2/%s?page=%s&per_page=%s&order=%s&sort_by=%s", WORKFLOW_URL, "alert", page,
					pageSize, order, key != null ? key : "");
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Alerts by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes an Alert by its ID
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Alert/delete_alert_alertId"
	 * 
	 * @param alertId
	 *            The ID of the trigger to delete
	 * @param user
	 *            The user submitting the request
	 * @return 200 OK if deleted, error if exceptions occurred
	 */
	@RequestMapping(value = "/alert/{alertId}", method = RequestMethod.DELETE)
	@ApiOperation(value = "Delete Alert", notes = "Deletes an Alert; referenced by ID.", tags = { "Alert",
			"Workflow" }, produces = "application/json")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Message indicating Alert was deleted successfully", response = SuccessResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> deleteAlert(
			@ApiParam(value = "The ID of the Alert to Delete.", required = true) @PathVariable(value = "alertId") String alertId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested deletion of Alert %s", gatewayUtil.getPrincipalName(user),
					alertId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v2/%s/%s", WORKFLOW_URL, "alert", alertId);
			restTemplate.delete(url);
			return new ResponseEntity<PiazzaResponse>(
					new SuccessResponse("Alert " + alertId + " was deleted successfully", "Gateway"), HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Alert ID %s by user %s: %s", alertId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets Alert metadata
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Alert/get_alert_alertId"
	 * 
	 * @param alertId
	 *            The trigger ID
	 * @param user
	 *            The user submitting the request
	 * @return Trigger information, or an error
	 */
	@RequestMapping(value = "/alert/{alertId}", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "Get Alert Information", notes = "Gets the metadata for a single Alert; referenced by ID.", tags = {
			"Alert", "Workflow" })
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The Alert metadata.", response = Alert.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getAlert(
			@ApiParam(value = "The ID of the Alert to retrieve metadata for.", required = true) @PathVariable(value = "alertId") String alertId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested information for Alert %s",
					gatewayUtil.getPrincipalName(user), alertId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v2/%s/%s", WORKFLOW_URL, "alert", alertId);
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting Alert ID %s by user %s: %s", alertId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
