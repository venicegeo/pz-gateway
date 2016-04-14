package gateway.controller;

import gateway.controller.util.GatewayUtil;

import java.security.Principal;

import messaging.job.JobMessageFactory;
import model.job.type.AbortJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import util.PiazzaLogger;

/**
 * Controller that defines REST end points dealing with Job interactions such as
 * retrieving Job status, or executing Jobs.
 * 
 * @author Patrick.Doody
 *
 *
 */
@CrossOrigin
@RestController
public class JobController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${dispatcher.host}")
	private String DISPATCHER_HOST;
	@Value("${dispatcher.port}")
	private String DISPATCHER_PORT;
	@Value("${dispatcher.protocol}")
	private String DISPATCHER_PROTOCOL;

	private RestTemplate restTemplate = new RestTemplate();

	/**
	 * Returns the Status of a Job.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Job/get_job_jobId
	 * 
	 * @param jobId
	 *            The ID of the Job.
	 * @param user
	 *            User information
	 * @return The response. Contains Job Status, or an appropriate Error.
	 */
	@RequestMapping(value = "/job/{jobId}", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getJobStatus(@PathVariable(value = "jobId") String jobId, Principal user) {
		try {
			// Proxy the request to the Dispatcher
			PiazzaResponse jobStatusResponse = restTemplate.getForObject(String.format("%s://%s:%s/%s/%s",
					DISPATCHER_PROTOCOL, DISPATCHER_HOST, DISPATCHER_PORT, "job", jobId), PiazzaResponse.class);
			HttpStatus status = jobStatusResponse instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
					: HttpStatus.OK;
			// Log the request
			logger.log(
					String.format("User %s requested Job Status for %s.", gatewayUtil.getPrincipalName(user), jobId),
					PiazzaLogger.INFO);
			// Respond
			return new ResponseEntity<PiazzaResponse>(jobStatusResponse, status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error requesting Job Status for ID %s: %s", jobId, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Cancels a running Job, specified by it's Job ID.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Job/delete_job_jobId
	 * 
	 * @param jobId
	 *            The ID of the Job to delete.
	 * @param user
	 *            User information
	 * @return
	 */
	@RequestMapping(value = "/job/{jobId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> abortJob(@PathVariable(value = "jobId") String jobId,
			@RequestParam(value = "reason", required = false) String reason, Principal user) {
		try {
			// Create the Kafka request object
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.userName = gatewayUtil.getPrincipalName(user);
			request.jobType = new AbortJob(jobId, reason);
			ProducerRecord<String, String> message = JobMessageFactory.getRequestJobMessage(request, jobId);
			// Send the Message
			try {
				gatewayUtil.sendKafkaMessage(message);
			} catch (Exception exception) {
				exception.printStackTrace();
				// Handle Kafka errors
				String error = String.format("Error Sending Kafka Message for %s Job %s: %s", "Abort", jobId,
						exception.getMessage());
				logger.log(error, PiazzaLogger.ERROR);
				return new ResponseEntity<PiazzaResponse>(new ErrorResponse(jobId, error, "Gateway"),
						HttpStatus.INTERNAL_SERVER_ERROR);
			}
			// Log the request
			logger.log(
					String.format("User %s requested Job Abort for Job ID %s with reason %s",
							gatewayUtil.getPrincipalName(user), jobId, reason), PiazzaLogger.INFO);
			// Respond
			return new ResponseEntity<Void>(HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error requesting Job Abort for ID %s: %s", jobId, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
