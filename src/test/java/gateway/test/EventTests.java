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
import java.util.ArrayList;

import javax.management.remote.JMXPrincipal;

import model.response.ErrorResponse;
import model.response.EventListResponse;
import model.response.EventTypeListResponse;
import model.response.Pagination;
import model.response.PiazzaResponse;
import model.workflow.Event;
import model.workflow.EventType;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
		when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(new ResponseEntity<String>("event", HttpStatus.OK));

		// Test
		ResponseEntity<?> response = eventController.getEvents(null, null, null, null, 0, 10, user);

		// Verify
		assertTrue(response.getBody().toString().equals("event"));
		assertTrue(response.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForEntity(anyString(), eq(String.class)))
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
		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(any(String.class));

		// Test
		ResponseEntity<?> response = eventController.fireEvent(new Event(), user);

		// Verify
		assertTrue(response.getStatusCode().equals(HttpStatus.CREATED));

		// Test Exception
		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenThrow(
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
	 * Test GET /eventType
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
	 * Test POST /eventType
	 */
//	@Test
	public void testCreateEventType() {
		// Mock Response
		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("OK");

		// Test
		ResponseEntity<?> response = eventController.createEventType(new EventType(), user);

		// Verify
		assertTrue(response.getBody().toString().equals("OK"));
		assertTrue(response.getStatusCode().equals(HttpStatus.CREATED));

		// Test Exception
		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenThrow(
				new RestClientException("event type error"));
		response = eventController.createEventType(new EventType(), user);
		assertTrue(response.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(response.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) response.getBody()).message.contains("event type error"));
	}

	/**
	 * Test GET /eventType/{eventTypeId}
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
	 * Test POST /event/query
	 */
	@Test
	public void testQueryEvents() {
		// Mock
		Event event = new Event();
		event.eventId = "123456";
		
		EventListResponse mockResponse = new EventListResponse();
		mockResponse.data = new ArrayList<Event>();
		mockResponse.getData().add(event);
		mockResponse.pagination = new Pagination(1, 0, 10, "test", "asc");
		when(restTemplate.postForObject(anyString(), any(), eq(EventListResponse.class))).thenReturn(mockResponse);

		// Test
		ResponseEntity<PiazzaResponse> entity = eventController.searchEvents(null, 0, 10, null, null, user);
		EventListResponse response = (EventListResponse) entity.getBody();

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
		assertTrue(response.getData().get(0).eventId.equalsIgnoreCase(event.eventId));
		assertTrue(response.getPagination().getCount().equals(1));

		// Test an Exception
		when(restTemplate.postForObject(anyString(), any(), eq(EventListResponse.class)))
				.thenThrow(new RestClientException(""));
		entity = eventController.searchEvents(null, 0, 10, null, null, user);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}
	
	/**
	 * Test POST /eventType/query
	 */
	@Test
	public void testQueryEventTypes() {
		// Mock
		EventType eventType = new EventType();
		eventType.eventTypeId = "123456";
		
		EventTypeListResponse mockResponse = new EventTypeListResponse();
		mockResponse.data = new ArrayList<EventType>();
		mockResponse.getData().add(eventType);
		mockResponse.pagination = new Pagination(1, 0, 10, "test", "asc");
		when(restTemplate.postForObject(anyString(), any(), eq(EventTypeListResponse.class))).thenReturn(mockResponse);

		// Test
		ResponseEntity<PiazzaResponse> entity = eventController.searchEventTypes(null, 0, 10, null, null, user);
		EventTypeListResponse response = (EventTypeListResponse) entity.getBody();

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
		assertTrue(response.getData().get(0).eventTypeId.equalsIgnoreCase(eventType.eventTypeId));
		assertTrue(response.getPagination().getCount().equals(1));

		// Test an Exception
		when(restTemplate.postForObject(anyString(), any(), eq(EventTypeListResponse.class)))
				.thenThrow(new RestClientException(""));
		entity = eventController.searchEventTypes(null, 0, 10, null, null, user);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}	
}
