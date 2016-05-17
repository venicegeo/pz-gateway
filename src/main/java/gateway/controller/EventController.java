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
 * service; including events and event types.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin
@RestController
public class EventController extends PiazzaRestController {
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
	 * Gets all events from the workflow component.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Event/get_event
	 * 
	 * @param user
	 *            The user submitting the request
	 * @return The list of events, or the appropriate error.
	 */
	@RequestMapping(value = "/event", method = RequestMethod.GET)
	public ResponseEntity<?> getEvents(
			@RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) Boolean order,
			@RequestParam(value = "key", required = false) String key, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s queried for Events.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Broker the request to Workflow
			String url = String.format("%s/v1/%s?from=%s&size=%s&order=%s&key=%s", WORKFLOW_URL, "events", page,
					pageSize, order, key != null ? key : "");
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Events by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Fires a new event to the workflow component.
	 * 
	 * @see pz-swagger.stage.geointservices.io/#!/Event/post_event_eventTypeId
	 * 
	 * @param event
	 *            The event to be fired
	 * @param user
	 *            The user submitting the event
	 * @return The event ID, or an error.
	 */
	@RequestMapping(value = "/event/{eventType}", method = RequestMethod.POST)
	public ResponseEntity<?> fireEvent(@PathVariable(value = "eventType") String eventType, @RequestBody String event,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has fired an event.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Broker the request to Workflow
			String response = restTemplate.postForObject(
					String.format("%s/v1/%s/%s", WORKFLOW_URL, "events", eventType), event, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Submitting Event by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Get all Events for the specified event type.
	 * 
	 * @see "pz-swagger.stage.geointservices.io/#!/Event/get_event_eventTypeId"
	 * 
	 * @param eventType
	 *            The event ID
	 * @param user
	 *            The user executing the request
	 * @return The list of events, or an error
	 */
	@RequestMapping(value = "/event/{eventType}", method = RequestMethod.GET)
	public ResponseEntity<?> getEventsForType(
			@RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) Boolean order,
			@RequestParam(value = "key", required = false) String key,
			@PathVariable(value = "eventType") String eventType, Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s has requested a list of Events for Type %s",
							gatewayUtil.getPrincipalName(user), eventType), PiazzaLogger.INFO);
			// Broker the request to Workflow
			String url = String.format("%s/v1/%s/%s?from=%s&size=%s&order=%s&key=%s", WORKFLOW_URL, "events",
					eventType, page, pageSize, order, key != null ? key : "");
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Events by user %s for Event Type %s: %s",
					gatewayUtil.getPrincipalName(user), eventType, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets the Specific event details for a single event.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event/get_event_eventTypeId_eventId"
	 * 
	 * @param eventType
	 *            The event type of the event
	 * @param eventId
	 *            the unique ID of the event
	 * @param user
	 *            The user executing the request
	 * @return The event metadata, or an error
	 */
	@RequestMapping(value = "/event/{eventType}/{eventId}", method = RequestMethod.GET)
	public ResponseEntity<?> getEventInformation(@PathVariable(value = "eventType") String eventType,
			@PathVariable(value = "eventId") String eventId, Principal user) {
		try {
			// Log the message
			logger.log(
					String.format("User %s requesting information on Event %s under Type %s",
							gatewayUtil.getPrincipalName(user), eventId, eventType), PiazzaLogger.INFO);
			// Broker the request to pz-workflow
			String response = restTemplate.getForObject(
					String.format("%s/v1/%s/%s/%s", WORKFLOW_URL, "events", eventType, eventId), String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Event %s under Type %s by user %: %s", eventId, eventType,
					gatewayUtil.getPrincipalName(user));
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes the Specific Event
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event/delete_event_eventTypeId_eventId"
	 * 
	 * @param eventType
	 *            The event type of the event to delete
	 * @param eventId
	 *            the unique ID of the event to delete
	 * @param user
	 *            The user executing the request
	 * @return 200 OK, or an error
	 */
	@RequestMapping(value = "/event/{eventType}/{eventId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> deleteEvent(@PathVariable(value = "eventType") String eventType,
			@PathVariable(value = "eventId") String eventId, Principal user) {
		try {
			// Log the message
			logger.log(
					String.format("User %s Requesting Deletion for Event %s under Type %s",
							gatewayUtil.getPrincipalName(user), eventId, eventType), PiazzaLogger.INFO);
			// Broker the request to pz-workflow
			restTemplate.delete(String.format("%s/v1/%s/%s/%s", WORKFLOW_URL, "events", eventType, eventId),
					String.class);
			return null;
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Event %s under Type %s by user %: %s", eventId, eventType,
					gatewayUtil.getPrincipalName(user));
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets the list of Event Types.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event_Type/get_eventType"
	 * 
	 * @return The list of event types, or an error.
	 */
	@RequestMapping(value = "/eventType", method = RequestMethod.GET)
	public ResponseEntity<?> getEventTypes(
			@RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) Boolean order,
			@RequestParam(value = "key", required = false) String key, Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s has requested a list of Event Types.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Broker the request to Workflow
			String url = String.format("%s/v1/%s?from=%s&size=%s&order=%s&key=%s", WORKFLOW_URL, "eventtypes", page,
					pageSize, order, key != null ? key : "");
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Event Types by user %s: %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Creates a new Event Type.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event_Type/post_eventType"
	 * 
	 * @param eventType
	 *            The event Type JSON.
	 * @param user
	 *            The user making the request
	 * @return The ID of the event type, or an error.
	 */
	@RequestMapping(value = "/eventType", method = RequestMethod.POST)
	public ResponseEntity<?> createEventType(@RequestBody String eventType, Principal user) {
		try {
			// Log the message
			logger.log(
					String.format("User %s has requested a new Event Type to be created.",
							gatewayUtil.getPrincipalName(user)), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v1/%s", WORKFLOW_URL, "eventtypes");
			String response = restTemplate.postForObject(url, eventType, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Creating Event Type by user %s: %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets Event type metadata by the ID of the event type.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event_Type/get_eventType_eventTypeId"
	 * 
	 * @param eventTypeId
	 *            Event type ID
	 * @param user
	 *            The user submitting the request
	 * @return Event type information, or an error
	 */
	@RequestMapping(value = "/eventType/{eventTypeId}", method = RequestMethod.GET)
	public ResponseEntity<?> getEventType(@PathVariable(value = "eventTypeId") String eventTypeId, Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s has requested information for Event Type %s",
							gatewayUtil.getPrincipalName(user), eventTypeId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v1/%s/%s", WORKFLOW_URL, "eventtypes", eventTypeId);
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting Event Type ID %s by user %s: %s", eventTypeId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes an Event Type
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event_Type/delete_eventType_eventTypeId"
	 * 
	 * @param eventTypeId
	 *            The ID of the event type to delete
	 * @param user
	 *            The user executing the request
	 * @return 200 OK if deleted, error if exceptions occurred
	 */
	@RequestMapping(value = "/eventType/{eventTypeId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> deleteEventType(@PathVariable(value = "eventTypeId") String eventTypeId, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested deletion of Event Type %s",
					gatewayUtil.getPrincipalName(user), eventTypeId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v1/%s/%s", WORKFLOW_URL, "eventtypes", eventTypeId);
			restTemplate.delete(url);
			return null;
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Event Type ID %s by user %s: %s", eventTypeId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
