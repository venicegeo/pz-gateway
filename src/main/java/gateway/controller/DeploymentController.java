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

import java.security.Principal;

import messaging.job.JobMessageFactory;
import model.job.type.AccessJob;
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
	@Value("${access.host}")
	private String ACCESS_HOST;
	@Value("${access.port}")
	private String ACCESS_PORT;
	@Value("${access.protocol}")
	private String ACCESS_PROTOCOL;
	@Value("${space}")
	private String space;

	private RestTemplate restTemplate = new RestTemplate();

	/**
	 * Processes a request to create a GeoServer deployment for Piazza data.
	 * 
	 * @see http 
	 *      ://pz-swagger.stage.geointservices.io/#!/Deployment/post_deployment
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
			ProducerRecord<String, String> message = JobMessageFactory.getRequestJobMessage(jobRequest, jobId, space);
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

	/**
	 * Gets Deployment information for an active deployment, including URL and
	 * Data ID.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Deployment/
	 *      get_deployment_deploymentId
	 * 
	 * @param deploymentId
	 *            The ID of the deployment to fetch
	 * @param user
	 *            The user requesting the deployment information
	 * @return The deployment information, or an ErrorResponse if exceptions
	 *         occur
	 */
	@RequestMapping(value = "/deployment/{deploymentId}", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getDeployment(@PathVariable(value = "deploymentId") String deploymentId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Deployment Data for %s", gatewayUtil.getPrincipalName(user),
					deploymentId), PiazzaLogger.INFO);
			// Broker the request to Pz-Access
			PiazzaResponse deploymentResponse = restTemplate.getForObject(String.format("%s://%s:%s/%s/%s",
					ACCESS_PROTOCOL, ACCESS_HOST, ACCESS_PORT, "deployment", deploymentId), PiazzaResponse.class);
			HttpStatus status = deploymentResponse instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
					: HttpStatus.OK;
			// Respond
			return new ResponseEntity<PiazzaResponse>(deploymentResponse, status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error fetching Deployment for ID %s by user %s: %s", deploymentId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
