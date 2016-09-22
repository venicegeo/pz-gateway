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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
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
import model.job.type.AccessJob;
import model.request.PiazzaJobRequest;
import model.response.DeploymentGroupResponse;
import model.response.DeploymentListResponse;
import model.response.DeploymentResponse;
import model.response.ErrorResponse;
import model.response.JobResponse;
import model.response.PiazzaResponse;
import model.response.SuccessResponse;
import util.PiazzaLogger;

/**
 * REST controller that handles requests for interacting with the Piazza Access component, and dealing with GIS Server
 * data deployments.
 * 
 * @author Patrick.Doody
 *
 */
@Api
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

	@Autowired
	private RestTemplate restTemplate;
	private static final String DEFAULT_PAGE_SIZE = "10";
	private static final String DEFAULT_PAGE = "0";
	private static final String DEFAULT_ORDER = "desc";

	/**
	 * Processes a request to create a GIS Server deployment for Piazza data.
	 * 
	 * @see http ://pz-swagger.stage.geointservices.io/#!/Deployment/post_deployment
	 * 
	 * @param job
	 *            The job, defining details on the deployment
	 * @param user
	 *            The user executing the request
	 * @return Job Id for the deployment; appropriate ErrorResponse if that call fails.
	 */
	@RequestMapping(value = "/deployment", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)	
	@ApiOperation(value = "Obtain a GIS Server Deployment for a Data Resource object", notes = "Data that has been loaded into Piazza can be deployed to the GIS Server. This will copy the data to the GIS Server data directory (if needed), or point to the Piazza PostGIS; and then create a WMS/WCS/WFS layer (as available) for the service. Only data that has been internally hosted within Piazza can be deployed.", tags = {
			"Deployment", "Data" })
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "The Job Id for the specified Deployment. This could be a long-running process to copy the data over to the GIS Server, so a new Job is spawned.", response = JobResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> createDeployment(
			@ApiParam(value = "The Data Id and deployment information for creating the Deployment", name = "data", required = true) @Valid @RequestBody AccessJob job,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Deployment of type %s for Data %s", gatewayUtil.getPrincipalName(user),
					job.getDeploymentType(), job.getDataId()), PiazzaLogger.INFO);
			PiazzaJobRequest jobRequest = new PiazzaJobRequest();
			jobRequest.createdBy = gatewayUtil.getPrincipalName(user);
			jobRequest.jobType = job;
			String jobId = gatewayUtil.sendJobRequest(jobRequest, null);
			// Send the response back to the user
			return new ResponseEntity<PiazzaResponse>(new JobResponse(jobId), HttpStatus.CREATED);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Loading Data for user %s for Id %s of type %s: %s", gatewayUtil.getPrincipalName(user),
					job.getDataId(), job.getDeploymentType(), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Returns a list of Deployments held by the Access component
	 * 
	 * @see "http://pz-swagger.stage.geointservices.io/#!/Deployment/get_deployment"
	 * 
	 * @param user
	 *            The user making the request
	 * @return The list of results, with pagination information included. ErrorResponse if something goes wrong.
	 */
	@RequestMapping(value = "/deployment", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Obtain a list of all GIS Server Deployments held by Piazza.", notes = "Data can be made available through the Piazza GIS Server as WMS/WCS/WFS. This must be done through POSTing to the /deployment endpoint. This endpoint will return a list of all Deployed resources.", tags = "Deployment")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The list of Search results that match the query string.", response = DeploymentListResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getDeployment(
			@ApiParam(value = "A general keyword search to apply to all Deployments.") @RequestParam(value = "keyword", required = false) String keyword,
			@ApiParam(value = "Paginating large datasets. This will determine the starting page for the query.") @RequestParam(value = "page", required = false, defaultValue = DEFAULT_PAGE) Integer page,
			@ApiParam(value = "The number of results to be returned per query.") @RequestParam(value = "perPage", required = false, defaultValue = DEFAULT_PAGE_SIZE) Integer perPage,
			@ApiParam(value = "Indicates ascending or descending order.") @RequestParam(value = "order", required = false, defaultValue = DEFAULT_ORDER) String order,
			@ApiParam(value = "The data field to sort by.") @RequestParam(value = "sortBy", required = false) String sortBy,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Deployment List query.", gatewayUtil.getPrincipalName(user)), PiazzaLogger.INFO);
			
			// Validate params
			String validationError = null;
			if( (order != null && (validationError = gatewayUtil.validateInput("order", order)) != null) || 
				(page != null && (validationError = gatewayUtil.validateInput("page", page)) != null) ||
				(perPage != null && (validationError = gatewayUtil.validateInput("perPage", perPage)) != null) ) {
				return new ResponseEntity<PiazzaResponse>(new ErrorResponse(validationError, "Gateway"), HttpStatus.BAD_REQUEST);
			}			
			
			// Proxy the request to Pz-Access
			String url = String.format("%s/%s?page=%s&perPage=%s", ACCESS_URL, "deployment", page, perPage);
			// Attach keywords if specified
			if ((keyword != null) && (keyword.isEmpty() == false)) {
				url = String.format("%s&keyword=%s", url, keyword);
			}
			if ((order != null) && (order.isEmpty() == false)) {
				url = String.format("%s&order=%s", url, order);
			}
			if ((sortBy != null) && (sortBy.isEmpty() == false)) {
				url = String.format("%s&sortBy=%s", url, sortBy);
			}
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate.getForEntity(url, DeploymentListResponse.class).getBody(),
						HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Listing Deployments by user %s: %s", gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Gets Deployment information for an active deployment, including URL and Data Id.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Deployment/ get_deployment_deploymentId
	 * 
	 * @param deploymentId
	 *            The Id of the deployment to fetch
	 * @param user
	 *            The user requesting the deployment information
	 * @return The deployment information, or an ErrorResponse if exceptions occur
	 */
	@RequestMapping(value = "/deployment/{deploymentId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Get Deployment Metadata", notes = "Fetches the Metadata for a Piazza Deployment.", tags = "Deployment")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "The metadata about the Deployment. Contains the unique Id of the Deployment; the Data Id that it represents; and server information regarding the access of the deployed service including the GetCapabilities document.", response = DeploymentResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> getDeployment(
			@ApiParam(value = "Id of the Deployment to Fetch", required = true) @PathVariable(value = "deploymentId") String deploymentId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Deployment Data for %s", gatewayUtil.getPrincipalName(user), deploymentId),
					PiazzaLogger.INFO);
			// Broker the request to Pz-Access
			try {
				return new ResponseEntity<PiazzaResponse>(restTemplate
						.getForEntity(String.format("%s/%s/%s", ACCESS_URL, "deployment", deploymentId), DeploymentResponse.class)
						.getBody(), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error fetching Deployment for Id %s by user %s: %s", deploymentId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes Deployment information for an active deployment.
	 * 
	 * @see http://pz-swagger.stage.geointservices.io/#!/Deployment/ delete_deployment_deploymentId
	 * 
	 * @param deploymentId
	 *            The Id of the deployment to delete.
	 * @param user
	 *            The user requesting the deployment information
	 * @return OK confirmation if deleted, or an ErrorResponse if exceptions occur
	 */
	@RequestMapping(value = "/deployment/{deploymentId}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Remove an active Deployment", notes = "If a user wishes to delete a Deployment before its lease time is up (and automatic deletion could take place) then this endpoint provides a way to do so manually.", tags = "Deployment")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Confirmation that the deployment has been deleted.", response = SuccessResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> deleteDeployment(
			@ApiParam(value = "Id of the Deployment to Delete.", required = true) @PathVariable(value = "deploymentId") String deploymentId,
			Principal user) {
		try {
			// Log the request
			logger.log(String.format("User %s requested Deletion for Deployment %s", gatewayUtil.getPrincipalName(user), deploymentId),
					PiazzaLogger.INFO);
			// Broker the request to Pz-Access
			try {
				return new ResponseEntity<PiazzaResponse>(
						restTemplate.exchange(String.format("%s/%s/%s", ACCESS_URL, "deployment", deploymentId), HttpMethod.DELETE, null,
								SuccessResponse.class).getBody(),
						HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting Deployment by Id %s by user %s: %s", deploymentId,
					gatewayUtil.getPrincipalName(user), exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Creates a new Deployment Group.
	 * 
	 * @return Deployment Group information, or an ErrorResonse if exceptions occur.
	 */
	@RequestMapping(value = "/deployment/group", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)	
	@ApiOperation(value = "Create a DeploymentGroup.", notes = "Creates a new DeploymentGroup Id that can be used in order to add some future set of Deployments into a single WMS layer.", tags = "Deployment")
	@ApiResponses(value = {
			@ApiResponse(code = 201, message = "Metadata about for the DeploymentGroup that has been created.", response = DeploymentGroupResponse.class),
			@ApiResponse(code = 400, message = "Bad Request", response = ErrorResponse.class),		
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> createDeploymentGroup(Principal user) {
		try {
			// Log the request
			String createdBy = gatewayUtil.getPrincipalName(user);
			logger.log(String.format("User %s requested Creation of DeploymentGroup.", createdBy), PiazzaLogger.INFO);
			// Broker to pz-access
			return new ResponseEntity<PiazzaResponse>(restTemplate
					.postForEntity(String.format("%s/deployment/group?createdBy=%s", ACCESS_URL, createdBy), null, PiazzaResponse.class)
					.getBody(), HttpStatus.CREATED);
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error creating DeploymentGroup: %s", exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Deletes a Deployment Group. This will delete the Group in the Mongo DB holdings, and also in the GIS Server
	 * instance. Unrecoverable once deleted.
	 * 
	 * @param deploymentGroupId
	 *            The Id of the group to delete.
	 * @param user
	 *            The user requesting deletion.
	 * @return OK if deleted, Error if not.
	 */
	@RequestMapping(value = "/deployment/group/{deploymentGroupId}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Delete a DeploymentGroup.", notes = "Deletes a DeploymentGroup from the Piazza metadata, and the GIS server.", tags = "Deployment")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Successful deletion of DeploymentGroup.", response = SuccessResponse.class),
			@ApiResponse(code = 401, message = "Unauthorized", response = ErrorResponse.class),
			@ApiResponse(code = 404, message = "Not Found", response = ErrorResponse.class),
			@ApiResponse(code = 500, message = "Internal Error", response = ErrorResponse.class) })
	public ResponseEntity<PiazzaResponse> deleteDeploymentGroup(@PathVariable(value = "deploymentGroupId") String deploymentGroupId,
			Principal user) {
		try {
			// Log the request
			logger.log(
					String.format("User %s requested delete of DeploymentGroup %s", gatewayUtil.getPrincipalName(user), deploymentGroupId),
					PiazzaLogger.INFO);
			// Broker to access
			String url = String.format("%s/deployment/group/%s", ACCESS_URL, deploymentGroupId);
			try {
				return new ResponseEntity<PiazzaResponse>(
						restTemplate.exchange(url, HttpMethod.DELETE, null, PiazzaResponse.class).getBody(), HttpStatus.OK);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error Deleting DeploymentGroup %s : %s", deploymentGroupId, gatewayUtil.getPrincipalName(user),
					exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}