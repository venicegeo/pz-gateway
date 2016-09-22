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
import model.response.ErrorResponse;
import model.response.PiazzaResponse;
import model.response.UUIDResponse;
import util.PiazzaLogger;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * REST Controller that defines administrative end points that reference
 * logging, administartion, and debugging information related to the Gateway
 * component.
 * 
 * @author Patrick.Doody
 *
 */
@RestController
public class AdminController extends PiazzaRestController {
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private HttpServletRequest request;
	@Autowired
	private GatewayUtil gatewayUtil;	
	@Value("${vcap.services.pz-kafka.credentials.host}")
	private String KAFKA_ADDRESS;
	@Value("${SPACE}")
	private String SPACE;
	@Value("${workflow.url}")
	private String WORKFLOW_URL;
	@Value("${search.url}")
	private String SEARCH_URL;
	@Value("${ingest.url}")
	private String INGEST_URL;
	@Value("${access.url}")
	private String ACCESS_URL;
	@Value("${jobmanager.url}")
	private String JOBMANAGER_URL;
	@Value("${servicecontroller.url}")
	private String SERVICECONTROLLER_URL;
	@Value("${uuid.url}")
	private String UUIDGEN_URL;
	@Value("${logger.url}")
	private String LOGGER_URL;
	@Value("${security.url}")
	private String SECURITY_URL;
	@Value("${release.url}")
	private String RELEASE_URL;

	@Autowired
	private RestTemplate restTemplate;

	/**
	 * Healthcheck required for all Piazza Core Services
	 * 
	 * @return String
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String getHealthCheck() {
		return "Hello, Health Check here for pz-gateway.";
	}

	@RequestMapping(value = "/version", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getVersion() {
		try {
			return new ResponseEntity<String>(restTemplate.getForObject(RELEASE_URL, String.class), HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(String.format("Error retrieving version for Piazza: %s", e.getMessage()), "Gateway"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Returns administrative statistics for this Gateway component.
	 * 
	 * @return Component information
	 */
	@RequestMapping(value = "/admin/stats", method = RequestMethod.GET)
	public ResponseEntity<Map<String, Object>> getAdminStats() {
		Map<String, Object> stats = new HashMap<String, Object>();
		// Write the Kafka configs
		stats.put("Kafka Address", KAFKA_ADDRESS);
		// Write the URL configs
		stats.put("Space", SPACE);
		stats.put("Workflow", WORKFLOW_URL);
		stats.put("Search", SEARCH_URL);
		stats.put("Ingest", INGEST_URL);
		stats.put("Access", ACCESS_URL);
		stats.put("JobManager", JOBMANAGER_URL);
		stats.put("ServiceController", SERVICECONTROLLER_URL);
		stats.put("UUIDGen", UUIDGEN_URL);
		stats.put("Logger", LOGGER_URL);
		stats.put("Security", SECURITY_URL);
		stats.put("Release", RELEASE_URL);
		// Return
		return new ResponseEntity<Map<String, Object>>(stats, HttpStatus.OK);
	}

	/**
	 * Returns a user's UUID for subsequent authentication with pz-gateway
	 * endpoints.
	 * 
	 * @return Component information
	 */
	@RequestMapping(value = "/key", method = RequestMethod.GET)
	public ResponseEntity<PiazzaResponse> getUUIDForUser() {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", request.getHeader("Authorization"));
			try {
				return new ResponseEntity<PiazzaResponse>(
						new RestTemplate().exchange(SECURITY_URL + "/key", HttpMethod.GET, new HttpEntity<String>("parameters", headers), UUIDResponse.class).getBody(), 
						HttpStatus.CREATED);
			} catch (HttpClientErrorException | HttpServerErrorException hee) {
				return new ResponseEntity<PiazzaResponse>(gatewayUtil.getErrorResponse(hee.getResponseBodyAsString()), hee.getStatusCode());
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error retrieving UUID: %s", exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);
			return new ResponseEntity<PiazzaResponse>(new ErrorResponse(error, "Gateway"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Error handling for missing GET request
	 * 
	 * @return Error describing the missing GET end point
	 */
	/*
	 * @RequestMapping(value = "/error", method = RequestMethod.GET) public
	 * ResponseEntity<?> missingEndpoint_GetRequest() { String message =
	 * "Gateway GET endpoint not defined. Please verify the API for the correct call."
	 * ; return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null,
	 * message, "Gateway"), HttpStatus.BAD_REQUEST); }
	 */

	/**
	 * Error handling for missing POST request
	 * 
	 * @return Error describing the missing POST endpoint
	 */
	/*
	 * @RequestMapping(value = "/error", method = RequestMethod.POST) public
	 * ResponseEntity<?> missingEndpoint_PostRequest() { String message =
	 * "Gateway POST endpoint not defined. Please verify the API for the correct call."
	 * ; return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null,
	 * message, "Gateway"), HttpStatus.BAD_REQUEST); }
	 */
}
