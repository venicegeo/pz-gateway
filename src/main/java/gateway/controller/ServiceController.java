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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
public class ServiceController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;

	@Value("${servicecontroller.protocol}")
	private String SERVICE_CONTROLLER_PROTOCOL;
	@Value("${servicecontroller.host}")
	private String SERVICE_CONTROLLER_HOST;

	private RestTemplate restTemplate = new RestTemplate();

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
			logger.log(String.format("User %s requested registration of service %s",
					gatewayUtil.getPrincipalName(user), service.getName()), PiazzaLogger.INFO);
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
}
