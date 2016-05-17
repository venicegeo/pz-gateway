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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
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
public class DeploymentController extends PiazzaRestController {
	@Autowired
	private GatewayUtil gatewayUtil;
	@Autowired
	private PiazzaLogger logger;
	@Value("${access.url}")
	private String ACCESS_URL;
	@Value("${SPACE}")
	private String SPACE;

	private RestTemplate restTemplate = new RestTemplate();
	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";

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
	public ResponseEntity<PiazzaResponse> createDeployment(@RequestBody AccessJob job, Principal user) {
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
			ProducerRecord<String, String> message = JobMessageFactory.getRequestJobMessage(jobRequest, jobId, SPACE);
			// Send the message to Kafka
			gatewayUtil.sendKafkaMessage(message);
			// Attempt to wait until the user is able to query the Job ID
			// immediately.
			gatewayUtil.verifyDatabaseInsertion(jobId);
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
	 * Returns a list of Deployments held by the Access component
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Deployment/get_deployment"
	 * 
	 * @param user
	 *            The user making the request
	 * @return The list of results, with pagination information included.
	 *         ErrorResponse if something goes wrong.
	 */
	@RequestMapping(value = "/deployment", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getDeployment(
			@RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			@RequestParam(value = "keyword", required = false) String keyword, Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Deployment List query.", gatewayUtil.getPrincipalName(user)),
					PiazzaLogger.INFO);
			// Proxy the request to Pz-Access
			String url = String.format("%s/%s?page=%s&pageSize=%s", ACCESS_URL, "deployment", page, pageSize);
			// Attach keywords if specified
			if ((keyword != null) && (keyword.isEmpty() == false)) {
				url = String.format("%s&keyword=%s", url, keyword);
			}
			PiazzaResponse dataResponse = restTemplate.getForObject(url, PiazzaResponse.class);
			HttpStatus status = dataResponse instanceof ErrorResponse ? HttpStatus.INTERNAL_SERVER_ERROR
					: HttpStatus.OK;
			// Respond
			return new ResponseEntity<PiazzaResponse>(dataResponse, status);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Listing Deployments by user %s: %s",
					gatewayUtil.getPrincipalName(user), exception.getMessage());
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
			PiazzaResponse deploymentResponse = restTemplate.getForObject(
					String.format("%s/%s/%s", ACCESS_URL, "deployment", deploymentId), PiazzaResponse.class);
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

	/**
	 * Deletes Deployment information for an active deployment.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Deployment/
	 *      delete_deployment_deploymentId
	 * 
	 * @param deploymentId
	 *            The ID of the deployment to delete.
	 * @param user
	 *            The user requesting the deployment information
	 * @return OK confirmation if deleted, or an ErrorResponse if exceptions
	 *         occur
	 */
	@RequestMapping(value = "/deployment/{deploymentId}", method = RequestMethod.DELETE)
	public ResponseEntity<PiazzaResponse> deleteDeployment(@PathVariable(value = "deploymentId") String deploymentId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Deletion for Deployment %s",
					gatewayUtil.getPrincipalName(user), deploymentId), PiazzaLogger.INFO);
			// Broker the request to Pz-Access
			restTemplate.delete(String.format("%s/%s/%s", ACCESS_URL, "deployment", deploymentId));
			return null;
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Deployment by ID %s by user %s: %s", deploymentId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
