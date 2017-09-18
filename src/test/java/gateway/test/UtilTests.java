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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javax.management.remote.JMXPrincipal;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.amazonaws.services.s3.AmazonS3;

import gateway.controller.util.GatewayUtil;
import model.job.type.AbortJob;
import model.request.PiazzaJobRequest;
import model.response.JobResponse;
import model.response.PiazzaResponse;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Tests the Gateway Utility class.
 * 
 * @author Patrick.Doody
 *
 */
public class UtilTests {
	@Mock
	private PiazzaLogger logger;
	@Mock
	private UUIDFactory uuidFactory;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	private AmazonS3 s3Client;
	@Mock
	private Producer<String, String> producer;

	@InjectMocks
	private GatewayUtil gatewayUtil;

	/**
	 * Initialize mock objects.
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

		// Mock the Kafka response that Producers will send. This will always
		// return a Future that completes immediately and simply returns true.
		when(producer.send(isA(ProducerRecord.class))).thenAnswer(new Answer<Future<Boolean>>() {
			@Override
			public Future<Boolean> answer(InvocationOnMock invocation) throws Throwable {
				Future<Boolean> future = mock(FutureTask.class);
				when(future.isDone()).thenReturn(true);
				when(future.get()).thenReturn(true);
				return future;
			}
		});
	}

	/**
	 * Tests getting the username from the Principal.
	 */
	@Test
	public void testUserName() {
		assertEquals("Test User", gatewayUtil.getPrincipalName(new JMXPrincipal("Test User")));
		assertEquals("UNAUTHENTICATED", gatewayUtil.getPrincipalName(null));
	}

	/**
	 * Tests sending a Job Request
	 */
	@Test
	public void testJobRequest() throws Exception {
		// Mock
		PiazzaJobRequest mockRequest = new PiazzaJobRequest();
		mockRequest.createdBy = "tester";
		mockRequest.jobType = new AbortJob("123456");
		Mockito.doReturn(new ResponseEntity<PiazzaResponse>(new JobResponse("123456"), HttpStatus.OK)).when(restTemplate)
				.postForEntity(Mockito.anyString(), Mockito.any(), Mockito.eq(PiazzaResponse.class));

		// Test
		String jobId = gatewayUtil.sendJobRequest(mockRequest, "123456");

		// Verify
		assertEquals(jobId, "123456");
	}

	/**
	 * Tests input validation for Pagination parameters
	 */
	@Test
	public void testValidateInput() {
		assertTrue(gatewayUtil.validateInput("order", "asc") == null);
		assertTrue(gatewayUtil.validateInput("order", "desc") == null);
		assertTrue(gatewayUtil.validateInput("order", "ascending").isEmpty() == false);

		assertTrue(gatewayUtil.validateInput("perPage", 5) == null);
		assertTrue(gatewayUtil.validateInput("perPage", 10) == null);
		assertTrue(gatewayUtil.validateInput("perPage", -50).isEmpty() == false);

		assertTrue(gatewayUtil.validateInput("page", 5) == null);
		assertTrue(gatewayUtil.validateInput("page", 10) == null);
		assertTrue(gatewayUtil.validateInput("page", -50).isEmpty() == false);
	}
	
	/**
	 * Tests input concatenation
	 */
	@Test
	public void testJoinValidationErrors() {
		assertEquals("", gatewayUtil.joinValidationErrors((String)null)); // This mimics a null return from validateInput.
		assertEquals("", gatewayUtil.joinValidationErrors(null, null, null));
		assertEquals("foo ", gatewayUtil.joinValidationErrors("foo"));
		assertEquals("foo ", gatewayUtil.joinValidationErrors(null, "foo", null));
		assertEquals("bar foo baz ", gatewayUtil.joinValidationErrors("bar", "foo", "baz"));
		assertEquals("bar foo baz ", gatewayUtil.joinValidationErrors("bar", null, "foo", null, "baz"));
	}
	
}
