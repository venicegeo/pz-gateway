package gateway.controller;

import gateway.controller.util.GatewayUtil;

import java.security.Principal;

import messaging.job.JobMessageFactory;
import model.job.type.AccessJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import util.PiazzaLogger;

/**
 * REST controller that handles requests for interacting with the Piazza Access
 * component, and dealing with GeoServer data deployments.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin
@RestController
public class DeploymentController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;

	private RestTemplate restTemplate = new RestTemplate();

	/**
	 * Processes a request to create a GeoServer deployment for Piazza data.
	 * 
	 * @param job
	 *            The job, defining details on the deployment
	 * @param user
	 *            The user executing the request
	 * @return Job ID for the deployment; appropriate ErrorResponse if that call
	 *         fails.
	 */
	@RequestMapping(value = "/deployment", method = RequestMethod.POST)
	public ResponseEntity<PiazzaResponse> createDeployment(AccessJob job, Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s requested Deployment of type %s for Data %s",
							gatewayUtil.getPrincipalName(user), job.getDeploymentType(), job.getDataId()),
					PiazzaLogger.INFO);
			// Create the Job that Kafka will broker
			String jobId = gatewayUtil.getUuid();
			PiazzaJobRequest jobRequest = new PiazzaJobRequest();
			jobRequest.userName = gatewayUtil.getPrincipalName(user);
			jobRequest.jobType = job;
			ProducerRecord<String, String> message = JobMessageFactory.getRequestJobMessage(jobRequest, jobId);
			// Send the message to Kafka
			gatewayUtil.sendKafkaMessage(message);
			// Send the response back to the user
			return new ResponseEntity<PiazzaResponse>(new PiazzaResponse(jobId), HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Loading Data for user %s for ID %s of type %s: %s",
					gatewayUtil.getPrincipalName(user), job.getDataId(), job.getDeploymentType(),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
