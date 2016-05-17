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

import gateway.controller.util.PiazzaRestController;

import java.util.HashMap;
import java.util.Map;

import model.response.ErrorResponse;
import model.response.PiazzaResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller that defines administrative end points that reference
 * logging, administartion, and debugging information related to the Gateway
 * component.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin
@RestController
public class AdminController extends PiazzaRestController {
	@Value("${vcap.services.pz-kafka.credentials.host}")
	private String KAFKA_ADDRESS;
	@Value("${SPACE}")
	private String SPACE;
	@Value("#{'${workflow.protocol}' + '://' + '${workflow.prefix}' + '.' + '${DOMAIN}' + ':' + '${workflow.port}'}")
	private String WORKFLOW_URL;
	@Value("#{'${search.protocol}' + '://' + '${search.prefix}' + '.' + '${DOMAIN}' + ':' + '${search.port}'}")
	private String SEARCH_URL;
	@Value("#{'${ingest.protocol}' + '://' + '${ingest.prefix}' + '.' + '${DOMAIN}' + ':' + '${ingest.port}'}")
	private String INGEST_URL;
	@Value("#{'${access.protocol}' + '://' + '${access.prefix}' + '.' + '${DOMAIN}' + ':' + '${access.port}'}")
	private String ACCESS_URL;
	@Value("#{'${jobmanager.protocol}' + '://' + '${jobmanager.prefix}' + '.' + '${DOMAIN}' + ':' + '${jobmanager.port}'}")
	private String JOBMANAGER_URL;
	@Value("#{'${dispatcher.protocol}' + '://' + '${dispatcher.prefix}' + '.' + '${DOMAIN}' + ':' + '${dispatcher.port}'}")
	private String DISPATCHER_URL;
	@Value("#{'${servicecontroller.protocol}' + '://' + '${servicecontroller.prefix}' + '.' + '${DOMAIN}' + ':' + '${servicecontroller.port}'}")
	private String SERVICECONTROLLER_URL;
	@Value("#{'${uuid.protocol}' + '://' + '${uuid.prefix}' + '.' + '${DOMAIN}' + ':' + '${uuid.port}'}")
	private String UUIDGEN_URL;
	@Value("#{'${logger.protocol}' + '://' + '${logger.prefix}' + '.' + '${DOMAIN}' + ':' + '${logger.port}'}")
	private String LOGGER_URL;
	@Value("#{'${security.protocol}' + '://' + '${security.prefix}' + '.' + '${DOMAIN}' + ':' + '${security.port}'}")
	private String SECURITY_URL;

	/**
	 * Healthcheck required for all Piazza Core Services
	 * 
	 * @return String
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String getHealthCheck() {
		return "Hello, Health Check here for pz-gateway.";
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
		stats.put("Dispatcher", DISPATCHER_URL);
		stats.put("ServiceController", SERVICECONTROLLER_URL);
		stats.put("UUIDGen", UUIDGEN_URL);
		stats.put("Logger", LOGGER_URL);
		stats.put("Security", SECURITY_URL);
		// Return
		return new ResponseEntity<Map<String, Object>>(stats, HttpStatus.OK);
	}

	/**
	 * Error handling for missing GET request
	 * 
	 * @return Error describing the missing GET end point
	 */
	/*
	@RequestMapping(value = "/error", method = RequestMethod.GET)
	public ResponseEntity<?> missingEndpoint_GetRequest() {
		String message = "Gateway GET endpoint not defined. Please verify the API for the correct call.";
		return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, message, "Gateway"), HttpStatus.BAD_REQUEST);
	}*/

	/**
	 * Error handling for missing POST request
	 * 
	 * @return Error describing the missing POST endpoint
	 */
	/*
	@RequestMapping(value = "/error", method = RequestMethod.POST)
	public ResponseEntity<?> missingEndpoint_PostRequest() {
		String message = "Gateway POST endpoint not defined. Please verify the API for the correct call.";
		return new ResponseEntity<PiazzaResponse>(new ErrorResponse(null, message, "Gateway"), HttpStatus.BAD_REQUEST);
	}*/
}
