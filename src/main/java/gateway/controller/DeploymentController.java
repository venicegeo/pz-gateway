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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import java.security.Principal;

import model.data.deployment.Deployment;
import model.job.type.AccessJob;
import model.request.PiazzaJobRequest;
import model.response.DeploymentListResponse;
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
 * REST controller that handles requests for interacting with the Piazza Access
 * component, and dealing with GeoServer data deployments.
 * 
 * @author Patrick.Doody
 *
 */
@Api
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
	@RequestMapping(value = "/deployment", method = RequestMethod.POST, produces = "application/json")
	@ApiOperation(value = "Obtain a GeoServer deployment for a Data Resource object", notes = "Data that has been loaded into Piazza can be deployed to GeoServer. This will copy the data to the GeoServer data directory (if needed), or point to the Piazza PostGIS; and then create a WMS/WCS/WFS layer (as available) for the service. Only data that has been internally hosted within Piazza can be deployed.", tags = {
			"Deployment", "Data" })
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The Job ID for the specified Deployment. This could be a long-running process to copy the data over to GeoServer, so a new Job is spawned.") })
	public ResponseEntity<PiazzaResponse> createDeployment(
			@ApiParam(value = "The Data ID and deployment information for creating the Deployment", name = "data", required = true) @RequestBody AccessJob job,
			Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s requested Deployment of type %s for Data %s",
							gatewayUtil.getPrincipalName(user), job.getDeploymentType(), job.getDataId()),
					PiazzaLogger.INFO);
			PiazzaJobRequest jobRequest = new PiazzaJobRequest();
			jobRequest.userName = gatewayUtil.getPrincipalName(user);
			jobRequest.jobType = job;
			String jobId = gatewayUtil.sendJobRequest(jobRequest, null);
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
	@RequestMapping(value = "/deployment", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "Obtain a list of all GeoServer deployments held by Piazza.", notes = "Data can be made available through the Piazza GeoServer as WMS/WCS/WFS. This must be done through POSTing to the /deployment endpoint. This endpoint will return a list of all Deployed resources.", tags = "Deployment", response = DeploymentListResponse.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The list of Search results that match the query string.") })
	public ResponseEntity<PiazzaResponse> getDeployment(
			@ApiParam(value = "A general keyword search to apply to all Deployments.") @RequestParam(value = "keyword", required = false) String keyword,
			@ApiParam(value = "Paginating large datasets. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "per_page", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer pageSize,
			Principal user) {
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
	@RequestMapping(value = "/deployment/{deploymentId}", method = RequestMethod.GET, produces = "application/json")
	@ApiOperation(value = "Get Deployment Metadata", notes = "Fetches the Metadata for a Piazza Deployment.", tags = "Deployment", response = Deployment.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "The metadata about the Deployment. Contains the unique ID of the deployment; the Data ID that it represents; and server information regarding the access of the deployed service (likely GeoServer) including the GetCapabilities document.") })
	public ResponseEntity<PiazzaResponse> getDeployment(
			@ApiParam(value = "ID of the Deployment to Fetch", required = true) @PathVariable(value = "deploymentId") String deploymentId,
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
	@ApiOperation(value = "Remove an active deployment", notes = "If a user wishes to delete a deployment before its lease time is up (and automatic deletion could take place) then this endpoint provides a way to do so manually.", tags = "Deployment")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Confirmation that the deployment has been deleted.") })
	public ResponseEntity<PiazzaResponse> deleteDeployment(
			@ApiParam(value = "ID of the Deployment to Delete.", required = true) @PathVariable(value = "deploymentId") String deploymentId,
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