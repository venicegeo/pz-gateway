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

import java.security.Principal;

import javax.validation.Valid;

import org.apache.kafka.clients.producer.ProducerRecord;
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

import gateway.controller.util.GatewayUtil;
import gateway.controller.util.PiazzaRestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import messaging.job.JobMessageFactory;
import model.job.metadata.ResourceMetadata;
import model.job.type.AbortJob;
import model.job.type.ExecuteServiceJob;
import model.job.type.RepeatJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.JobErrorResponse;
import model.response.JobResponse;
import model.response.JobStatusResponse;
import model.response.PiazzaResponse;
import model.response.ServiceResponse;
import model.response.SuccessResponse;
import model.service.metadata.Service;
import util.PiazzaLogger;

/**
 * Controller that defines REST end points dealing with Job interactions such as retrieving Job status, or executing
 * Jobs.
 * 
 * @author Patrick.Doody
 * 
 */
@Api
@RestController
public class JobController extends PiazzaRestController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private ServiceController serviceController;
	@Value("${jobmanager.url}")
	private String JOBMANAGER_URL;
	@Value("${SPACE}")
	private String SPACE;

	@Autowired
	private RestTemplate restTemplate;

	/**
	 * Returns the Status of a Job.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Job/get_job_jobId
	 * 
	 * @param jobId
	 *            The Id of the Job.
	 * @param user
	 *            User information
	 * @return Contains Job Status, or an appropriate Error.
	 */
	@RequestMapping(value = "/job/{jobId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Get Job Status", notes = "Gets the Status for a Piazza Job by its Id.", tags = "Job")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Information regarding the requested Job. At bare minimum, this will contain the Job Id of the Job that has been spawned as a result of the POSTed message. If more information is available, such as Status, it will also be included.", response = JobStatusResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = JobErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getJobStatus(
			@ApiParam(value = "Id of the Job to Fetch", required = true) @PathVariable(value = "jobId") String jobId, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Job Status for %s.", gatewayUtil.getPrincipalName(user), jobId), PiazzaLogger.INFO);
			// Proxy the request to the Job Manager
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate
						.getForEntity(String.format("%s/%s/%s", JOBMANAGER_URL, "job", jobId), JobStatusResponse.class).getBody(),
						HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error requesting Job Status for Id %s: %s", jobId, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new JobErrorResponse(jobId, error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Cancels a running Job, specified by it's Job Id.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Job/delete_job_jobId
	 * 
	 * @param jobId
	 *            The Id of the Job to delete.
	 * @param user
	 *            User information
	 * @return No response body if successful, or an appropriate Error
	 */
	@RequestMapping(value = "/job/{jobId}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Abort Job", notes = "Requests a Running Job to be cancelled. If the Job is already completed in some way, then cancellation will not occur.", tags = "Job")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The Job has requested to be cancelled. This may take some time, as the process may not be in an easily cancelled state at the time the request is made.", response = SuccessResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = JobErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> abortJob(
			@ApiParam(value = "Id of the Job to cancel.", required = true) @PathVariable(value = "jobId") String jobId,
			@ApiParam(value = "Details for the cancellation of the Job.") @RequestParam(value = "reason", required = false) String reason,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Job Abort for Job Id %s with reason %s", gatewayUtil.getPrincipalName(user), jobId,
					reason), PiazzaLogger.INFO);

			// Create the Request object.
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.createdBy = gatewayUtil.getPrincipalName(user);
			request.jobType = new AbortJob(jobId, reason);

			// Send the message through Kafka to delete the Job. This message
			// will get picked up by whatever component is running the Job.
			ProducerRecord<String, String> abortMessage = JobMessageFactory.getAbortJobMessage(request, gatewayUtil.getUuid(), SPACE);
			gatewayUtil.sendKafkaMessage(abortMessage);

			// Proxy the request to the Job Manager, where the Job Table will be
			// updated.
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<PiazzaJobRequest> entity = new HttpEntity<PiazzaJobRequest>(request, headers);
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate
						.postForEntity(String.format("%s/%s", JOBMANAGER_URL, "abort"), entity, SuccessResponse.class).getBody(),
						HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error requesting Job Abort for Id %s: %s", jobId, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new JobErrorResponse(jobId, error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Repeats a Job that has previously been submitted to Piazza. This will spawn a new Job with new corresponding Id.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Job/put_job_jobId
	 * 
	 * @param jobId
	 *            The Id of the Job to repeat.
	 * @param user
	 *            User information
	 * @return Response containing the Id of the newly created Job, or appropriate error
	 */
	@RequestMapping(value = "/job/{jobId}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@ApiOperation(value = "Repeat Job", notes = "Repeats a previously submitted Job. This will clone the original Job, and run it again with identical parameters, using the requesting users authentication in the new Job.", tags = "Job")
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "A new Job Id that corresponds to the cloned Job in Piazza.", response = JobResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> repeatJob(
			@ApiParam(value = "Id of the Job to Repeat", required = true) @PathVariable(value = "jobId") String jobId, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested to Repeat Job %s", gatewayUtil.getPrincipalName(user), jobId), PiazzaLogger.INFO);
			// Create the Request Object from the input parameters
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.createdBy = gatewayUtil.getPrincipalName(user);
			request.jobType = new RepeatJob(jobId);
			// Proxy the request to the Job Manager
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<PiazzaJobRequest> entity = new HttpEntity<PiazzaJobRequest>(request, headers);
			try {
				return new ResponseEntity<PiazzaResponse>(
						restTemplate.postForEntity(String.format("%s/%s", JOBMANAGER_URL, "repeat"), entity, JobResponse.class).getBody(),
						HttpStatus.CREATED);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Repeating Job Id %s: %s", jobId, exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Executes a job with the Piazza service controller. This will create a Job Id and then return that Id immediately.
	 * The job itself will be long-running and status can be tracked by requesting the status of that Job Id. When the
	 * complete has completed, it will be made available through the Job result field. The ServiceController handles the
	 * execution of the Job.
	 * 
	 * @param job
	 *            The job to execute
	 * @param user
	 *            The user executing the Job
	 * @return The job Id, or the error if encountered
	 */
	@RequestMapping(value = { "/job" }, method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@ApiOperation(value = "Executes a registered Service", notes = "Creates a Piazza Job to execute a registered service in the system, with the specified parameters.", tags = {
			"Job", "Service" })
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "The Job Id for the execution of the Service. This can be queried using Job Status to track progress and, when available, fetch the result object.", response = JobResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> executeService(
			@ApiParam(value = "The Payload that describes the Service to be executed, and the inputs for that service.", required = true, name = "body") @Valid @RequestBody ExecuteServiceJob job,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Execute Job for Service %s.", gatewayUtil.getPrincipalName(user),
					job.data.getServiceId()), PiazzaLogger.INFO);

			// Check that Service is not offline or unavailable
			try {
				Service service = ((ServiceResponse) serviceController.getService(job.data.getServiceId(), user).getBody()).data;
				if ((service != null) && (service.getResourceMetadata() != null)
						&& (service.getResourceMetadata().getAvailability().equals(ResourceMetadata.STATUS_TYPE.OFFLINE.toString()))) {
					return new ResponseEntity<PiazzaResponse>(
							new ErrorResponse("Cannot Execute Service with Service Availability set as Offline.", "Gateway"),
							HttpStatus.BAD_REQUEST);
				}
			} catch (Exception exception) {
				logger.log(String.format(
						"Attempted to check Service Availability for %s but received an error %s. Continued with Job Request.",
						job.getData().getServiceId(), exception.getMessage()), PiazzaLogger.WARNING);
			}

			// Create the Request to send to the Job Manager.
			PiazzaJobRequest request = new PiazzaJobRequest();
			request.jobType = job;
			request.createdBy = gatewayUtil.getPrincipalName(user);

			String jobId = gatewayUtil.sendJobRequest(request, null);
			return new ResponseEntity<PiazzaResponse>(new JobResponse(jobId), HttpStatus.CREATED);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Executing for user %s for Service %s: %s", gatewayUtil.getPrincipalName(user),
					job.data.getServiceId(), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}