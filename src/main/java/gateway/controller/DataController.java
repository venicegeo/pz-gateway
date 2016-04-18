package gateway.controller;

import gateway.controller.util.GatewayUtil;

import java.security.Principal;

import messaging.job.JobMessageFactory;
import model.job.type.IngestJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import util.PiazzaLogger;

/**
 * REST controller serving end points that are related to Piazza data, such as
 * loading or accessing spatial data.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin
@RestController
public class DataController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;

	/**
	 * Process the request to Ingest data.
	 * 
	 * @param job
	 *            The Ingest Job, describing the data to be ingested.
	 * @param user
	 *            The user submitting the request
	 * @return The Response containing the Job ID, or containing the appropriate
	 *         ErrorResponse
	 */
	@RequestMapping(value = "/data", method = RequestMethod.POST)
	public ResponseEntity<PiazzaResponse> ingestData(@RequestBody IngestJob job, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Ingest Job of type %s.", gatewayUtil.getPrincipalName(user),
					job.getData().getDataType()), PiazzaLogger.INFO);
			// Create the Request to send to Kafka
			String newJobId = gatewayUtil.getUuid();
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.jobType = job;
			request.userName = gatewayUtil.getPrincipalName(user);
			ProducerRecord<String, String> message = JobMessageFactory.getRequestJobMessage(request, newJobId);
			// Send the message to Kafka
			gatewayUtil.sendKafkaMessage(message);
			// Return the Job ID of the newly created Job
			return new ResponseEntity<PiazzaResponse>(new PiazzaResponse(newJobId), HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Loading Data for user %s of type %s:  %s",
					gatewayUtil.getPrincipalName(user), job.getData().getDataType(), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
