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
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

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

import util.PiazzaLogger;

/**
 * REST controller defining end points that interact with the Piazza workflow
 * service; including alerts and triggers.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin
@RestController
public class AlertTriggerController extends PiazzaRestController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${workflow.url}")
	private String WORKFLOW_URL;

	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";
	private static final String DEFAULT_ORDER = "true";

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
	@RequestMapping(value = "/trigger", method = RequestMethod.POST)
	public ResponseEntity<?> createTrigger(@RequestBody String trigger, Principal user) {
		try {
			// Log the message
			logger.log(
					String.format("User %s has requested a new Trigger to be created.",
							gatewayUtil.getPrincipalName(user)), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v1/%s", WORKFLOW_URL, "triggers");
			String response = restTemplate.postForObject(url, trigger, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Creating Trigger by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
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
	@RequestMapping(value = "/trigger", method = RequestMethod.GET)
	public ResponseEntity<?> getTriggers(
			@RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) Boolean order,
			@RequestParam(value = "key", required = false) String key, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested a list of Triggers.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Broker the request to Workflow
			String url = String.format("%s/v1/%s?from=%s&size=%s&order=%s&key=%s", WORKFLOW_URL, "triggers", page,
					pageSize, order, key != null ? key : "");
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Triggers by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
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
	@RequestMapping(value = "/trigger/{triggerId}", method = RequestMethod.GET)
	public ResponseEntity<?> getTrigger(@PathVariable(value = "triggerId") String triggerId, Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s has requested information for Trigger %s",
							gatewayUtil.getPrincipalName(user), triggerId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v1/%s/%s", WORKFLOW_URL, "triggers", triggerId);
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting Trigger ID %s by user %s: %s", triggerId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
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
	@RequestMapping(value = "/trigger/{triggerId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> deleteTrigger(@PathVariable(value = "triggerId") String triggerId, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested deletion of Trigger %s",
					gatewayUtil.getPrincipalName(user), triggerId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v1/%s/%s", WORKFLOW_URL, "triggers", triggerId);
			restTemplate.delete(url);
			return null;
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Trigger ID %s by user %s: %s", triggerId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
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
	@RequestMapping(value = "/alert", method = RequestMethod.GET)
	public ResponseEntity<?> getAlerts(
			@RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) Boolean order,
			@RequestParam(value = "key", required = false) String key, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested a list of Alerts.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Broker the request to Workflow
			String url = String.format("%s/v1/%s?from=%s&size=%s&order=%s&key=%s", WORKFLOW_URL, "alerts", page,
					pageSize, order, key != null ? key : "");
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Alerts by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
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
	public ResponseEntity<?> deleteAlert(@PathVariable(value = "alertId") String alertId, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested deletion of Alert %s", gatewayUtil.getPrincipalName(user),
					alertId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v1/%s/%s", WORKFLOW_URL, "alerts", alertId);
			restTemplate.delete(url);
			return null;
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Alert ID %s by user %s: %s", alertId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
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
	@RequestMapping(value = "/alert/{alertId}", method = RequestMethod.GET)
	public ResponseEntity<?> getAlert(@PathVariable(value = "alertId") String alertId, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested information for Alert %s",
					gatewayUtil.getPrincipalName(user), alertId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v1/%s/%s", WORKFLOW_URL, "alerts", alertId);
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting Alert ID %s by user %s: %s", alertId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

}
