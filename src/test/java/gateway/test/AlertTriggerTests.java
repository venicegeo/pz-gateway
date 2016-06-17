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
import gateway.controller.AlertTriggerController;
import gateway.controller.util.GatewayUtil;

import java.security.Principal;

import javax.management.remote.JMXPrincipal;

import model.response.ErrorResponse;
import model.workflow.Trigger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
	 */
	@Test
	public void testCreateTrigger() {
		// Mock Response
		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("OK");

		// Test
		ResponseEntity<?> response = alertTriggerController.createTrigger(new Trigger(), user);

		// Verify
		assertTrue(response.getBody().toString().equals("OK"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

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
		ResponseEntity<?> response = alertTriggerController.getTriggers(null, 0, 10, null, user);

		// Verify
		assertTrue(response.getBody().toString().equals("Trigger"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(
				new RestClientException("Trigger Error"));
		response = alertTriggerController.getTriggers(null, 0, 10, null, user);
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
	 * Test DELETE /trigger/{triggerId}
	 */
	@Test
	public void testDeleteTrigger() {
		// Mock Response
		Mockito.doNothing().when(restTemplate).delete(anyString(), eq(String.class));

		// Test
		ResponseEntity<?> response = alertTriggerController.deleteTrigger("triggerId", user);

		// Verify
		assertTrue(response == null);

		// Test Exception
		Mockito.doThrow(new RestClientException("")).when(restTemplate).delete(anyString());
		response = alertTriggerController.deleteTrigger("triggerId", user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test GET /alert
	 */
	@Test
	public void testGetAlerts() {
		// Mock Response
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("Alert");

		// Test
		ResponseEntity<?> response = alertTriggerController.getAlerts(0, 10, null, null, user);

		// Verify
		assertTrue(response.getBody().toString().equals("Alert"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class)))
				.thenThrow(new RestClientException("Alert Error"));
		response = alertTriggerController.getAlerts(0, 10, null, null, user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("Alert Error"));
	}

	/**
	 * Test DELETE /alert/{alertId}
	 */
	@Test
	public void testDeleteAlert() {
		// Mock Response
		Mockito.doNothing().when(restTemplate).delete(anyString(), eq(String.class));

		// Test
		ResponseEntity<?> response = alertTriggerController.deleteAlert("alertId", user);

		// Verify
		assertTrue(response == null);

		// Test Exception
		Mockito.doThrow(new RestClientException("")).when(restTemplate).delete(anyString());
		response = alertTriggerController.deleteAlert("alertId", user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test GET /alert/{alertId}
	 */
	@Test
	public void testGetAlert() {
		// Mock Response
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("Alert");

		// Test
		ResponseEntity<?> response = alertTriggerController.getAlert("AlertID", user);

		// Verify
		assertTrue(response.getBody().toString().equals("Alert"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class)))
				.thenThrow(new RestClientException("Alert Error"));
		response = alertTriggerController.getAlert("AlertID", user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("Alert Error"));
	}
}
