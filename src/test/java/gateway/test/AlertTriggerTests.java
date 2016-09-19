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
package gateway.test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.ArrayList;

import javax.management.remote.JMXPrincipal;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gateway.controller.AlertTriggerController;
import gateway.controller.util.GatewayUtil;
import model.response.AlertListResponse;
import model.response.ErrorResponse;
import model.response.Pagination;
import model.response.PiazzaResponse;
import model.response.TriggerListResponse;
import model.workflow.Alert;
import model.workflow.Trigger;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Tests the Workflow /alert and /trigger endpoints
 * 
 * @author Patrick.Doody
 *
 */
public class AlertTriggerTests {
	@Mock
	private ObjectMapper om;
	@Mock
	private PiazzaLogger logger;
	@Mock
	private UUIDFactory uuidFactory;
	@Mock
	private GatewayUtil gatewayUtil;
	@Mock
	private RestTemplate restTemplate;
	@InjectMocks
	private AlertTriggerController alertTriggerController;

	private Principal user;

	/**
	 * Initialize mock objects.
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		MockitoAnnotations.initMocks(gatewayUtil);

		// Mock a user
		user = new JMXPrincipal("Test User");
	}

	/**
	 * Test POST /trigger
	 * 
	 * @throws JsonProcessingException
	 */
	@Test
	public void testCreateTrigger() throws JsonProcessingException {
		// Mock Response
		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(
				any(String.class));

		// Test
		ResponseEntity<?> response = alertTriggerController.createTrigger(new Trigger(), user);

		// Verify
		assertTrue(response.getStatusCode().equals(HttpStatus.CREATED));

		// Test Exception
		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenThrow(
				new RestClientException("Trigger Error"));
		response = alertTriggerController.createTrigger(new Trigger(), user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("Trigger Error"));
	}

	/**
	 * Test GET /trigger
	 */
	@Test
	public void testListTriggers() {
		// Mock Response
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("Trigger");

		// Test
		ResponseEntity<?> response = alertTriggerController.getTriggers(0, 10, null, "test", user);

		// Verify
		assertTrue(response.getBody().toString().equals("Trigger"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(
				new RestClientException("Trigger Error"));
		response = alertTriggerController.getTriggers(0, 10, null, "test", user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("Trigger Error"));
	}

	/**
	 * Test GET /trigger/{triggerId}
	 */
	@Test
	public void testGetTrigger() {
		// Mock Response
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("Trigger");

		// Test
		ResponseEntity<?> response = alertTriggerController.getTrigger("triggerId", user);

		// Verify
		assertTrue(response.getBody().toString().equals("Trigger"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(
				new RestClientException("Trigger Error"));
		response = alertTriggerController.getTrigger("triggerId", user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("Trigger Error"));
	}

	/**
	 * Test GET /alert
	 */
	@Test
	public void testGetAlerts() {
		// Mock Response
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("Alert");

		// Test
		ResponseEntity<?> response = alertTriggerController.getAlerts(0, 10, null, "test", null, false, user);

		// Verify
		assertTrue(response.getBody().toString().equals("Alert"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class)))
				.thenThrow(new RestClientException("Alert Error"));
		response = alertTriggerController.getAlerts(0, 10, null, "test", null, false, user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("Alert Error"));
	}

	/**
	 * Test GET /alert/{alertId}
	 */
	@Test
	public void testGetAlert() {
		// Mock Response
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("Alert");

		// Test
		ResponseEntity<?> response = alertTriggerController.getAlert("AlertID", false, user);

		// Verify
		assertTrue(response.getBody().toString().equals("Alert"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class)))
				.thenThrow(new RestClientException("Alert Error"));
		response = alertTriggerController.getAlert("AlertID", false, user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("Alert Error"));
	}

	/**
	 * Test POST /alert/query
	 */
	@Test
	public void testQueryAlerts() {
		// Mock
		Alert alert = new Alert();
		alert.alertId = "123456";
		
		AlertListResponse mockResponse = new AlertListResponse();
		mockResponse.data = new ArrayList<Alert>();
		mockResponse.getData().add(alert);
		mockResponse.pagination = new Pagination(1, 0, 10, "test", "asc");
		when(restTemplate.postForObject(anyString(), any(), eq(AlertListResponse.class))).thenReturn(mockResponse);

		// Test
		ResponseEntity<PiazzaResponse> entity = alertTriggerController.searchAlerts(null, 0, 10, null, null, false, user);
		AlertListResponse response = (AlertListResponse) entity.getBody();

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
		assertTrue(response.getData().get(0).alertId.equalsIgnoreCase(alert.alertId));
		assertTrue(response.getPagination().getCount().equals(1));

		// Test an Exception
		when(restTemplate.postForObject(anyString(), any(), eq(AlertListResponse.class)))
				.thenThrow(new RestClientException(""));
		entity = alertTriggerController.searchAlerts(null, 0, 10, null, null, false, user);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}
	
	/**
	 * Test POST /trigger/query
	 */
	@Test
	public void testQueryTriggers() {
		// Mock
		Trigger trigger = new Trigger();
		trigger.triggerId = "123456";
		
		TriggerListResponse mockResponse = new TriggerListResponse();
		mockResponse.data = new ArrayList<Trigger>();
		mockResponse.getData().add(trigger);
		mockResponse.pagination = new Pagination(1, 0, 10, "test", "asc");
		when(restTemplate.postForObject(anyString(), any(), eq(TriggerListResponse.class))).thenReturn(mockResponse);

		// Test
		ResponseEntity<PiazzaResponse> entity = alertTriggerController.searchTriggers(null, 0, 10, null, null, user);
		TriggerListResponse response = (TriggerListResponse) entity.getBody();

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
		assertTrue(response.getData().get(0).triggerId.equalsIgnoreCase(trigger.triggerId));
		assertTrue(response.getPagination().getCount().equals(1));

		// Test an Exception
		when(restTemplate.postForObject(anyString(), any(), eq(TriggerListResponse.class)))
				.thenThrow(new RestClientException(""));
		entity = alertTriggerController.searchTriggers(null, 0, 10, null, null, user);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}	
}
