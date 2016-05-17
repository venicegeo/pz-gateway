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

import messaging.job.JobMessageFactory;
import model.job.type.AbortJob;
import model.job.type.ExecuteServiceJob;
import model.job.type.RepeatJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * Controller that defines REST end points dealing with Job interactions such as
 * retrieving Job status, or executing Jobs.
 * 
 * @author Patrick.Doody
 * 
 */
@CrossOrigin
@RestController
public class JobController extends PiazzaRestController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${jobmanager.url}")
	private String JOBMANAGER_URL;
	@Value("${SPACE}")
	private String SPACE;

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
	 * @return Contains Job Status, or an appropriate Error.
	 */
	@RequestMapping(value = "/job/{jobId}", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getJobStatus(@PathVariable(value = "jobId") String jobId, Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s requested Job Status for %s.", gatewayUtil.getPrincipalName(user), jobId),
					PiazzaLogger.INFO);
			// Proxy the request to the Job Manager
			PiazzaResponse jobStatusResponse = restTemplate.getForObject(
					String.format("%s/%s/%s", JOBMANAGER_URL, "job", jobId), PiazzaResponse.class);
			HttpStatus status = jobStatusResponse instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
					: HttpStatus.OK;
			// Respond
			return new ResponseEntity<PiazzaResponse>(jobStatusResponse, status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error requesting Job Status for ID %s: %s", jobId, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(jobId, error, "Gateway"),
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
	 * @return No response body if successful, or an appropriate Error
	 */
	@RequestMapping(value = "/job/{jobId}", method = RequestMethod.DELETE)
	public ResponseEntity<PiazzaResponse> abortJob(@PathVariable(value = "jobId") String jobId,
			@RequestParam(value = "reason", required = false) String reason, Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s requested Job Abort for Job ID %s with reason %s",
							gatewayUtil.getPrincipalName(user), jobId, reason), PiazzaLogger.INFO);

			// Create the Request object.
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.userName = gatewayUtil.getPrincipalName(user);
			request.jobType = new AbortJob(jobId, reason);

			// Send the message through Kafka to delete the Job. This message
			// will get picked up by whatever component is running the Job.
			ProducerRecord<String, String> abortMessage = JobMessageFactory.getAbortJobMessage(request,
					gatewayUtil.getUuid(), SPACE);
			gatewayUtil.sendKafkaMessage(abortMessage);

			// Proxy the request to the Job Manager, where the Job Table will be
			// updated.
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<PiazzaJobRequest> entity = new HttpEntity<PiazzaJobRequest>(request, headers);
			ResponseEntity<PiazzaResponse> cancelResponse = restTemplate.postForEntity(
					String.format("%s/%s", JOBMANAGER_URL, "abort"), entity, PiazzaResponse.class);
			// Check if the response was an error. If so, set the status code
			// appropriately.
			HttpStatus status = cancelResponse.getBody() instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
					: HttpStatus.OK;
			// Send back the proxied response to the client
			return new ResponseEntity<PiazzaResponse>(cancelResponse.getBody(), status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error requesting Job Abort for ID %s: %s", jobId, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Repeats a Job that has previously been submitted to Piazza. This will
	 * spawn a new Job with new corresponding ID.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Job/put_job_jobId
	 * 
	 * @param jobId
	 *            The ID of the Job to repeat.
	 * @param user
	 *            User information
	 * @return Response containing the ID of the newly created Job, or
	 *         appropriate error
	 */
	@RequestMapping(value = "/job/{jobId}", method = RequestMethod.PUT)
	public ResponseEntity<PiazzaResponse> repeatJob(@PathVariable(value = "jobId") String jobId, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested to Repeat Job %s", gatewayUtil.getPrincipalName(user), jobId),
					PiazzaLogger.INFO);
			// Create the Request Object from the input parameters
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.userName = gatewayUtil.getPrincipalName(user);
			request.jobType = new RepeatJob(jobId);
			// Proxy the request to the Job Manager
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<PiazzaJobRequest> entity = new HttpEntity<PiazzaJobRequest>(request, headers);
			ResponseEntity<PiazzaResponse> jobResponse = restTemplate.postForEntity(
					String.format("%s/%s", JOBMANAGER_URL, "repeat"), entity, PiazzaResponse.class);
			// Check if the response was an error. If so, set the status code
			// appropriately.
			HttpStatus status = jobResponse.getBody() instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
					: HttpStatus.OK;
			// Send back the proxied response to the client
			return new ResponseEntity<PiazzaResponse>(jobResponse.getBody(), status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Repeating Job ID %s: %s", jobId, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Executes a job with the Piazza service controller. This will create a Job
	 * ID and then return that ID immediately. The job itself will be
	 * long-running and status can be tracked by requesting the status of that
	 * Job ID. When the complete has completed, it will be made available
	 * through the Job result field. The ServiceController handles the execution
	 * of the Job.
	 * 
	 * @param job
	 *            The job to execute
	 * @param user
	 *            The user executing the Job
	 * @return The job ID, or the error if encountered
	 */
	@RequestMapping(value = "/v2/job", method = RequestMethod.POST)
	public ResponseEntity<PiazzaResponse> executeService(@RequestBody ExecuteServiceJob job, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Execute Job for Service %s.",
					gatewayUtil.getPrincipalName(user), job.data.getServiceId()), PiazzaLogger.INFO);
			// Create the Request to send to Kafka
			String newJobId = gatewayUtil.getUuid();
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.jobType = job;
			request.userName = gatewayUtil.getPrincipalName(user);
			ProducerRecord<String, String> message = JobMessageFactory.getRequestJobMessage(request, newJobId, SPACE);
			// Send the message to Kafka
			gatewayUtil.sendKafkaMessage(message);
			// Attempt to wait until the user is able to query the Job ID
			// immediately.
			gatewayUtil.verifyDatabaseInsertion(newJobId);
			// Return the Job ID of the newly created Job
			return new ResponseEntity<PiazzaResponse>(new PiazzaResponse(newJobId), HttpStatus.OK);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Executing for user %s for Service %s: %s",
					gatewayUtil.getPrincipalName(user), job.data.getServiceId(), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
