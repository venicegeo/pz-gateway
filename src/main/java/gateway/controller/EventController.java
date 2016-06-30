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

import model.response.ErrorResponse;
import model.response.EventListResponse;
import model.response.EventTypeListResponse;
import model.response.PiazzaResponse;
import model.response.SuccessResponse;
import model.response.WorkflowResponse;
import model.workflow.Event;
import model.workflow.EventType;

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
 * service; including events and event types.
 * 
 * @author Patrick.Doody
 *
 */
@Api
@CrossOrigin
@RestController
public class EventController extends PiazzaRestController {
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
	 * Gets all events from the workflow component.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Event/get_event
	 * 
	 * @param user
	 *            The user submitting the request
	 * @return The list of events, or the appropriate error.
	 */
	@RequestMapping(value = "/event", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "Get all Events", notes = "Retrieves a list of all Events", tags = { "Event", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Events.", response = EventListResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getEvents(
			@ApiParam(value = "The name of the event type to filter by.") @RequestParam(value = "eventType", required = false) String eventType,
			@ApiParam(value = "The field to use for sorting.") @RequestParam(value = "key", required = false) String key,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false) String sortBy,
			@ApiParam(value = "Paginating large numbers of results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s queried for Events.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Broker the request to Workflow
			String url = String.format("%s/v2/%s?page=%s&per_page=%s&order=%s&sort_by=%s&eventType=%s", WORKFLOW_URL,
					"event", page, perPage, order, sortBy != null ? sortBy : "", eventType != null ? eventType : "");
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Events by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
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
	@RequestMapping(value = "/event", method = RequestMethod.POST, produces = "application/json")
	@ApiOperation(value = "Creates an Event for the Event Type", notes = "Sends an event to the Piazza workflow component", tags = {
			"Event", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The ID of the newly created Event", response = WorkflowResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> fireEvent(
			@ApiParam(value = "The Event JSON object.", required = true) @RequestBody Event event, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has fired an event.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Broker the request to Workflow
			WorkflowResponse response = restTemplate.postForObject(String.format("%s/v2/%s", WORKFLOW_URL, "event"),
					om.writeValueAsString(event), WorkflowResponse.class);
			return new ResponseEntity<WorkflowResponse>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Submitting Event by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
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
	@RequestMapping(value = "/event/{eventId}", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "Get a specifc Event", notes = "Gets a specific Event by it's ID, that corresponds with the Event Type", tags = {
			"Event", "Workflow" })
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The requested Event.", response = Event.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getEventInformation(
			@ApiParam(value = "The Event ID for the event to retrieve.", required = true) @PathVariable(value = "eventId") String eventId,
			Principal user) {
		try {
			// Log the message
			logger.log(String.format("User %s requesting information on Event %s", gatewayUtil.getPrincipalName(user),
					eventId), PiazzaLogger.INFO);

			// Broker the request to pz-workflow
			String response = restTemplate.getForObject(String.format("%s/v2/%s/%s", WORKFLOW_URL, "event", eventId),
					String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Event %s by user %s: %s", eventId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
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
	@RequestMapping(value = "/event/{eventId}", method = RequestMethod.DELETE, produces = "application/json")
	@ApiOperation(value = "Delete a specific Event", notes = "Deletes the event with the given id", tags = { "Event",
			"Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Confirmation of delete", response = SuccessResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> deleteEvent(
			@ApiParam(value = "The Event ID for the event to delete.", required = true) @PathVariable(value = "eventId") String eventId,
			Principal user) {
		try {
			// Log the message
			logger.log(String.format("User %s Requesting Deletion for Event %s", gatewayUtil.getPrincipalName(user),
					eventId), PiazzaLogger.INFO);

			// Broker the request to pz-workflow
			restTemplate.delete(String.format("%s/v2/%s/%s", WORKFLOW_URL, "event", eventId), String.class);
			return new ResponseEntity<PiazzaResponse>(new SuccessResponse("Event " + eventId
					+ " was deleted successfully", "Gateway"), HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Event %s by user %s: %s", eventId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
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
	@RequestMapping(value = "/eventType", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "List Event Types", notes = "Returns all event types that have been registered with the Piazza Workflow service", tags = {
			"Event Type", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Event Types.", response = EventTypeListResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getEventTypes(
			@ApiParam(value = "The field to use for sorting.") @RequestParam(value = "key", required = false) String key,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false) String sortBy,
			@ApiParam(value = "Paginating large numbers of results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s has requested a list of Event Types.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Broker the request to Workflow
			String url = String.format("%s/v2/%s?page=%s&per_page=%s&order=%s&sort_by=%s", WORKFLOW_URL, "eventType",
					page, perPage, order, sortBy != null ? sortBy : "");
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Event Types by user %s: %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
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
	@RequestMapping(value = "/eventType", method = RequestMethod.POST, produces = "application/json")
	@ApiOperation(value = "Register an Event Type", notes = "Defines an Event Type with the Workflow component", tags = {
			"Event Type", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The ID of the newly created Event Type", response = WorkflowResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> createEventType(
			@ApiParam(value = "The Event Type information. This defines the Schema for the Events that must be followed.", required = true) @RequestBody EventType eventType,
			Principal user) {
		try {
			// Log the message
			logger.log(
					String.format("User %s has requested a new Event Type to be created.",
							gatewayUtil.getPrincipalName(user)), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v2/%s", WORKFLOW_URL, "eventType");
			WorkflowResponse response = restTemplate.postForObject(url, om.writeValueAsString(eventType),
					WorkflowResponse.class);
			return new ResponseEntity<WorkflowResponse>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Creating Event Type by user %s: %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
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
	@RequestMapping(value = "/eventType/{eventTypeId}", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "Get an Event Type", notes = "Returns the metadata for a specific Event Type", tags = {
			"Event Type", "Workflow" })
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The Event Type metadata.", response = EventType.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getEventType(
			@ApiParam(value = "The unique identifier for the Event Type.", required = true) @PathVariable(value = "eventTypeId") String eventTypeId,
			Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s has requested information for Event Type %s",
							gatewayUtil.getPrincipalName(user), eventTypeId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v2/%s/%s", WORKFLOW_URL, "eventType", eventTypeId);
			String response = restTemplate.getForObject(url, String.class);
			return new ResponseEntity<String>(response, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting Event Type ID %s by user %s: %s", eventTypeId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
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
	@RequestMapping(value = "/eventType/{eventTypeId}", method = RequestMethod.DELETE, produces = "application/json")
	@ApiOperation(value = "Delete an Event Type", notes = "Deletes a specific Event Type using the specified by the Id", tags = {
			"Event Type", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Confirmation of Event Type deletion.", response = SuccessResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> deleteEventType(
			@ApiParam(value = "The unique identifier for the Event Type to delete.", required = true) @PathVariable(value = "eventTypeId") String eventTypeId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested deletion of Event Type %s",
					gatewayUtil.getPrincipalName(user), eventTypeId), PiazzaLogger.INFO);
			// Proxy the request to Workflow
			String url = String.format("%s/v2/%s/%s", WORKFLOW_URL, "eventType", eventTypeId);
			restTemplate.delete(url);
			return new ResponseEntity<PiazzaResponse>(new SuccessResponse("EventType " + eventTypeId
					+ " was deleted successfully", "Gateway"), HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Event Type ID %s by user %s: %s", eventTypeId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
