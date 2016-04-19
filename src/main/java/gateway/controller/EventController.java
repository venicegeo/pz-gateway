package gateway.controller;

import gateway.controller.util.GatewayUtil;

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
public class EventController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${workflow.host}")
	private String WORKFLOW_HOST;
	@Value("${workflow.protocol}")
	private String WORKFLOW_PROTOCOL;

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
			String url = String.format("%s://%s/v1/%s?from=%s&size=%s&order=%s&key=%s", WORKFLOW_PROTOCOL,
					WORKFLOW_HOST, "events", page, pageSize, order, key != null ? key : "");
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
					String.format("%s://%s/v1/%s/%s", WORKFLOW_PROTOCOL, WORKFLOW_HOST, "events", eventType), event,
					String.class);
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
			String url = String.format("%s://%s/v1/%s/%s?from=%s&size=%s&order=%s&key=%s", WORKFLOW_PROTOCOL,
					WORKFLOW_HOST, "events", eventType, page, pageSize, order, key != null ? key : "");
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
			String response = restTemplate.getForObject(String.format("%s://%s/v1/%s/%s/%s", WORKFLOW_PROTOCOL,
					WORKFLOW_HOST, "events", eventType, eventId), String.class);
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
}
