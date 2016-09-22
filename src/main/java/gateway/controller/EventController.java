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

import model.request.SearchRequest;
import model.response.ErrorResponse;
import model.response.EventListResponse;
import model.response.EventResponse;
import model.response.EventTypeListResponse;
import model.response.EventTypeResponse;
import model.response.PiazzaResponse;
import model.response.SuccessResponse;
import model.workflow.Event;
import model.workflow.EventType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
 * service; including events and EventTypes.
 * 
 * @author Patrick.Doody
 *
 */
@Api
@RestController
public class EventController extends PiazzaRestController {
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

	@Autowired
	private RestTemplate restTemplate;

	/**
	 * Gets all Events from the workflow component.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Event/get_event
	 * 
	 * @param user
	 *            The user submitting the request
	 * @return The list of Events, or the appropriate error.
	 */
	@RequestMapping(value = "/event", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Get all Events", notes = "Retrieves a list of all Events", tags = { "Event", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Events.", response = EventListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getEvents(
			@ApiParam(value = "The name of the EventType to filter by.") @RequestParam(value = "eventTypeName", required = false) String eventTypeName,
			@ApiParam(value = "The Id of the EventType to filter by.") @RequestParam(value = "eventTypeId", required = false) String eventTypeId,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false, defaultValue = DEFAULT_SORTBY) String sortBy,
			@ApiParam(value = "Paginating large numbers of results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s queried for Events.", gatewayUtil.getPrincipalName(user)),
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
				String url = String.format("%s/%s?page=%s&perPage=%s&order=%s&sortBy=%s&eventTypeName=%s&eventTypeId=%s", WORKFLOW_URL,
						"event", page, perPage, order, sortBy != null ? sortBy : "", eventTypeName != null ? eventTypeName : "", eventTypeId != null ? eventTypeId : "");				
				return new ResponseEntity<String>(restTemplate.getForEntity(url, String.class).getBody(), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }")), hee.getStatusCode());
			}		
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
	 * Fires a new Event to the workflow component.
	 * 
	 * @see pz-swagger.stage.geointservices.io/#!/Event/post_event_eventTypeId
	 * 
	 * @param event
	 *            The Event to be fired
	 * @param user
	 *            The user submitting the Event
	 * @return The Event Id, or an error.
	 */
	@RequestMapping(value = "/event", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)	
	@ApiOperation(value = "Creates an Event for the EventType", notes = "Sends an Event to the Piazza workflow component", tags = {	"Event", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "The created Event", response = EventResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),			
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> fireEvent(
			@ApiParam(value = "The Event JSON object.", required = true) @Valid @RequestBody Event event, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has fired an event.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			
			try {
				// Attempt to set the createdBy field
				event.createdBy = gatewayUtil.getPrincipalName(user);
			} catch (Exception exception) {
				logger.log(
						String.format("Failed to set the createdBy field in Event created by User %s: ",
								gatewayUtil.getPrincipalName(user), exception.getMessage()), PiazzaLogger.WARNING);
			}
			
			try {
				// Broker the request to Workflow
				return new ResponseEntity<String>(restTemplate.postForObject(String.format("%s/%s", WORKFLOW_URL, "event"), objectMapper.writeValueAsString(event), String.class), HttpStatus.CREATED);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }")), hee.getStatusCode());
			}			
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
	 * Gets the specific Event details for a single Event.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event/get_event_eventTypeId_eventId"
	 * 
	 * @param eventType
	 *            The EventType of the Event
	 * @param eventId
	 *            The unique Id of the Event
	 * @param user
	 *            The user executing the request
	 * @return The Event, or an error
	 */
	@RequestMapping(value = "/event/{eventId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Get a Event", notes = "Gets an Event by its Id, that corresponds with the EventType", tags = { "Event", "Workflow" })
	@ApiResponses(value = { 
			@ApiResponse(code = 200, message = "The requested Event", response = Event.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getEventInformation(
			@ApiParam(value = "The Event Id for the event to retrieve.", required = true) @PathVariable(value = "eventId") String eventId,
			Principal user) {
		try {
			// Log the message
			logger.log(String.format("User %s requesting information on Event %s", gatewayUtil.getPrincipalName(user),
					eventId), PiazzaLogger.INFO);

			try {
				// Broker the request to pz-workflow
				return new ResponseEntity<String>(restTemplate.getForObject(String.format("%s/%s/%s", WORKFLOW_URL, "event", eventId), String.class), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }")), hee.getStatusCode());
			}
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
	 * Gets the list of EventTypes.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event_Type/get_eventType"
	 * 
	 * @return The list of EventTypes, or an error.
	 */
	@RequestMapping(value = "/eventType", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "List EventTypes", notes = "Returns all EventTypes", tags = {	"EventType", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of EventTypes.", response = EventTypeListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getEventTypes(
			@ApiParam(value = "The EventType name to select on.") @RequestParam(value = "name", required = false) String name,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false, defaultValue = DEFAULT_SORTBY) String sortBy,
			@ApiParam(value = "Paginating large numbers of results. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s has requested a list of EventTypes.", gatewayUtil.getPrincipalName(user)),
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
				String url = String.format("%s/%s?page=%s&perPage=%s&order=%s&sortBy=%s&name=%s", WORKFLOW_URL, "eventType",
						page, perPage, order, sortBy != null ? sortBy : "", name != null ? name : "");
				return new ResponseEntity<String>(restTemplate.getForObject(url, String.class), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }")), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying EventTypes by user %s: %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Creates a new EventType.
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event_Type/post_eventType"
	 * 
	 * @param eventType
	 *            The EventType JSON.
	 * @param user
	 *            The user making the request
	 * @return The EventType, or an error.
	 */
	@RequestMapping(value = "/eventType", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)	
	@ApiOperation(value = "Register an EventType", notes = "Defines an EventType", tags = {	"EventType", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "The created EventType", response = EventTypeResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> createEventType(
			@ApiParam(value = "The EventType information. This defines the schema for the Events that must be followed.", required = true) @Valid @RequestBody EventType eventType,
			Principal user) {
		try {
			// Log the message
			logger.log(
					String.format("User %s has requested a new EventType to be created.",
							gatewayUtil.getPrincipalName(user)), PiazzaLogger.INFO);
			
			try {
				// Attempt to set the createdBy field
				eventType.createdBy = gatewayUtil.getPrincipalName(user);
			} catch (Exception exception) {
				logger.log(
						String.format("Failed to set the createdBy field in EventType created by User %s: ",
								gatewayUtil.getPrincipalName(user), exception.getMessage()), PiazzaLogger.WARNING);
			}
			
			try {
				// Proxy the request to Workflow
				return new ResponseEntity<String>(restTemplate.postForObject(String.format("%s/%s", WORKFLOW_URL, "eventType"), 
						objectMapper.writeValueAsString(eventType), String.class), HttpStatus.CREATED);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }")), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Creating EventType by user %s: %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets EventType by Id
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event_Type/get_eventType_eventTypeId"
	 * 
	 * @param eventTypeId
	 *            EventType Id
	 * @param user
	 *            The user submitting the request
	 * @return EventType information, or an error
	 */
	@RequestMapping(value = "/eventType/{eventTypeId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Get an EventType", notes = "Gets an EventType by Id", tags = { "EventType", "Workflow" })
	@ApiResponses(value = { 
			@ApiResponse(code = 200, message = "The EventType", response = EventType.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> getEventType(
			@ApiParam(value = "The unique Id for the EventType.", required = true) @PathVariable(value = "eventTypeId") String eventTypeId,
			Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s has requested information for EventType %s",
							gatewayUtil.getPrincipalName(user), eventTypeId), PiazzaLogger.INFO);
			
			try {
				// Proxy the request to Workflow
				return new ResponseEntity<String>(restTemplate.getForObject(String.format("%s/%s/%s", WORKFLOW_URL, "eventType", eventTypeId), String.class), HttpStatus.OK);			
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }")), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Getting EventType Id %s by user %s: %s", eventTypeId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Deletes an EventType
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Event_Type/delete_eventType_eventTypeId"
	 * 
	 * @param eventTypeId
	 *            The Id of the EventType to delete
	 * @param user
	 *            The user executing the request
	 * @return 200 OK if deleted, error if exceptions occurred
	 */
	@RequestMapping(value = "/eventType/{eventTypeId}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Delete an EventType", notes = "Deletes a specific EventType by Id", tags = { "EventType", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Confirmation of EventType deletion.", response = SuccessResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 403, message = "Forbidden", response = ErrorResponse.class),						
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),			
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> deleteEventType(
			@ApiParam(value = "The unique identifier for the EventType to delete.", required = true) @PathVariable(value = "eventTypeId") String eventTypeId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested deletion of EventType %s",
					gatewayUtil.getPrincipalName(user), eventTypeId), PiazzaLogger.INFO);
			
			try {
				// Proxy the request to Workflow
				restTemplate.delete(String.format("%s/%s/%s", WORKFLOW_URL, "eventType", eventTypeId));
				return new ResponseEntity<PiazzaResponse>(new SuccessResponse("EventType " + eventTypeId + " was deleted successfully", "Gateway"), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }")), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting EventType Id %s by user %s: %s", eventTypeId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}	
	
	/**
	 * Deletes an Event

	 * @param eventId
	 *            The Id of the Event to delete
	 * @param user
	 *            The user executing the request
	 * @return 200 OK if deleted, error if exceptions occurred
	 */
	@RequestMapping(value = "/event/{eventId}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Delete an Event", notes = "Deletes a specific Event by Id", tags = { "Event", "Workflow" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Confirmation of Event deletion.", response = SuccessResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 403, message = "Forbidden", response = ErrorResponse.class),						
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),			
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<?> deleteEvent(
			@ApiParam(value = "The unique identifier for the Event to delete.", required = true) @PathVariable(value = "eventId") String eventId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s has requested deletion of Event %s",
					gatewayUtil.getPrincipalName(user), eventId), PiazzaLogger.INFO);
			
			try {
				// Proxy the request to Workflow
				restTemplate.delete(String.format("%s/%s/%s", WORKFLOW_URL, "event", eventId));
				return new ResponseEntity<PiazzaResponse>(new SuccessResponse("Event " + eventId + " was deleted successfully", "Gateway"), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString().replaceAll("}", " ,\"type\":\"error\" }")), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Event Id %s by user %s: %s", eventId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Proxies an ElasticSearch DSL query to the Pz-Workflow component to return a
	 * list of Event items.
	 * 
	 * @see TBD
	 * 
	 * @return The list of Event items matching the query.
	 */
	@RequestMapping(value = "/event/query", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)	
	@ApiOperation(value = "Query Events in Piazza Workflow", notes = "Sends a complex query message to the Piazza Workflow component, that allow users to search for Events. Searching is capable of filtering by keywords or other dynamic information.", tags = {
			"Event", "Workflow", "Search" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Event results that match the query string.", response = EventListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> searchEvents(
			@ApiParam(value = "The Query string for the Workflow component.", required = true) @Valid @RequestBody SearchRequest query,
			@ApiParam(value = "Paginating large datasets. This will determine the starting page for the query.") @RequestParam(value = "page", required = false) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false) Integer perPage,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false) String sortBy,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s sending a complex query for Workflow.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			
			// Send the query to the Pz-Workflow component
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Object> entity = new HttpEntity<Object>(query, headers);
			
			String paramPage = (page == null) ? "" : "page=" + page.toString();
			String paramPerPage = (perPage == null) ? "" : "perPage=" + perPage.toString();
			String paramOrder = (order == null) ? "" : "order=" + order;
			String paramSortBy = (sortBy == null) ? "" : "sortBy=" + sortBy;
			
			EventListResponse searchResponse = restTemplate.postForObject(
					String.format("%s/%s/%s?%s&%s&%s&%s", WORKFLOW_URL, "event", "query", paramPage, paramPerPage, paramOrder, paramSortBy), entity, EventListResponse.class);
			// Respond
			return new ResponseEntity<PiazzaResponse>(searchResponse, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Data by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * Proxies an ElasticSearch DSL query to the Pz-Workflow component to return a
	 * list of EventType items.
	 * 
	 * @see TBD
	 * 
	 * @return The list of EventType items matching the query.
	 */
	@RequestMapping(value = "/eventType/query", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)	
	@ApiOperation(value = "Query EventTypes in Piazza Workflow", notes = "Sends a complex query message to the Piazza Workflow component, that allow users to search for EventTypes. Searching is capable of filtering by keywords or other dynamic information.", tags = {
			"EventType", "Workflow", "Search" })
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of EventType results that match the query string.", response = EventTypeListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> searchEventTypes(
			@ApiParam(value = "The Query string for the Workflow component.", required = true) @Valid @RequestBody SearchRequest query,
			@ApiParam(value = "Paginating large datasets. This will determine the starting page for the query.") @RequestParam(value = "page", required = false) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false) Integer perPage,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false) String sortBy,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s sending a complex query for Workflow.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			
			// Send the query to the Pz-Workflow component
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Object> entity = new HttpEntity<Object>(query, headers);
			
			String paramPage = (page == null) ? "" : "page=" + page.toString();
			String paramPerPage = (perPage == null) ? "" : "perPage=" + perPage.toString();
			String paramOrder = (order == null) ? "" : "order=" + order;
			String paramSortBy = (sortBy == null) ? "" : "sortBy=" + sortBy;
			
			EventTypeListResponse searchResponse = restTemplate.postForObject(
					String.format("%s/%s/%s?%s&%s&%s&%s", WORKFLOW_URL, "eventType", "query", paramPage, paramPerPage, paramOrder, paramSortBy), entity, EventTypeListResponse.class);
			// Respond
			return new ResponseEntity<PiazzaResponse>(searchResponse, HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Querying Data by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}	
}
