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
			String url = String.format("%s://%s/%s?from=%s&size=%s&order=%s&key=%s", WORKFLOW_PROTOCOL, WORKFLOW_HOST,
					"v1/events", page, pageSize, order, key != null ? key : "");
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
}
