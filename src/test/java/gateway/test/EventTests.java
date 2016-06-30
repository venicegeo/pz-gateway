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
import gateway.controller.EventController;
import gateway.controller.util.GatewayUtil;

import java.security.Principal;

import javax.management.remote.JMXPrincipal;

import model.response.ErrorResponse;
import model.response.SuccessResponse;
import model.response.WorkflowResponse;
import model.workflow.Event;
import model.workflow.EventType;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Unit tests for the EventController.java Rest Controller, which brokers calls
 * to pz-workflow.
 * 
 * @author Patrick.Doody
 *
 */
public class EventTests {
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
	private EventController eventController;

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
	 * Test GET /event
	 */
	@Test
	public void testGetEvents() {
		// Mock Response
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("event");

		// Test
		ResponseEntity<?> response = eventController.getEvents(null, null, null, null, 0, 10, user);

		// Verify
		assertTrue(response.getBody().toString().equals("event"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class)))
				.thenThrow(new RestClientException("event error"));
		response = eventController.getEvents(null, null, null, null, 0, 10, user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("event error"));
	}

	/**
	 * Test POST /event
	 */
	@Test
	public void testFireEvent() {
		// Mock Response
		when(restTemplate.postForObject(anyString(), any(), eq(WorkflowResponse.class))).thenReturn(any(WorkflowResponse.class));

		// Test
		ResponseEntity<?> response = eventController.fireEvent(new Event(), user);

		// Verify
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.postForObject(anyString(), any(), eq(WorkflowResponse.class))).thenThrow(
				new RestClientException("event error"));
		response = eventController.fireEvent(new Event(), user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("event error"));
	}

	/**
	 * Test GET /event/{eventId}
	 */
	@Test
	public void testGetEvent() {
		// Mock Response
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("event");

		// Test
		ResponseEntity<?> response = eventController.getEventInformation("eventId", user);

		// Verify
		assertTrue(response.getBody().toString().equals("event"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class)))
				.thenThrow(new RestClientException("event error"));
		response = eventController.getEventInformation("eventId", user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("event error"));
	}

	/**
	 * Test DELETE /event/{eventId}
	 */
	@Test
	public void testDeleteEvent() {
		// Mock Response
		Mockito.doNothing().when(restTemplate).delete(anyString(), eq(String.class));

		// Test
		ResponseEntity<?> response = eventController.deleteEvent("eventId", user);

		// Verify
		assertTrue(response.getBody() instanceof SuccessResponse);

		// Test Exception
		Mockito.doThrow(new RestClientException("")).when(restTemplate).delete(anyString(), eq(String.class));
		response = eventController.deleteEvent("eventId", user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test GET /event/eventType
	 */
	@Test
	public void testEventTypes() {
		// Mock Response
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("eventTypes");

		// Test
		ResponseEntity<?> response = eventController.getEventTypes(null, null, null, 0, 10, user);

		// Verify
		assertTrue(response.getBody().toString().equals("eventTypes"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class)))
				.thenThrow(new RestClientException("event error"));
		response = eventController.getEventTypes(null, null, null, 0, 10, user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("event error"));
	}

	/**
	 * Test POST /event/eventType
	 */
//	@Test
	public void testCreateEventType() {
		// Mock Response
		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("OK");

		// Test
		ResponseEntity<?> response = eventController.createEventType(new EventType(), user);

		// Verify
		assertTrue(response.getBody().toString().equals("OK"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenThrow(
				new RestClientException("event type error"));
		response = eventController.createEventType(new EventType(), user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("event type error"));
	}

	/**
	 * Test GET /event/eventType/{eventTypeId}
	 */
	@Test
	public void testGetEventType() {
		// Mock Response
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("eventType");

		// Test
		ResponseEntity<?> response = eventController.getEventType("eventTypeId", user);

		// Verify
		assertTrue(response.getBody().toString().equals("eventType"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(
				new RestClientException("event type error"));
		response = eventController.getEventType("eventTypeId", user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("event type error"));
	}

	/**
	 * Test DELETE /event/eventType/{eventTypeId}
	 */
	@Test
	public void testDeleteEventType() {
		// Mock Response
		Mockito.doNothing().when(restTemplate).delete(anyString());

		// Test
		ResponseEntity<?> response = eventController.deleteEventType("eventTypeId", user);

		// Verify
		assertTrue(response.getBody() instanceof SuccessResponse);

		// Test Exception
		Mockito.doThrow(new RestClientException("")).when(restTemplate).delete(anyString());
		response = eventController.deleteEventType("eventTypeId", user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
	}
}
