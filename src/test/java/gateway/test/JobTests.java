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

import javax.management.remote.JMXPrincipal;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import exception.PiazzaJobException;
import gateway.controller.JobController;
import gateway.controller.ServiceController;
import gateway.controller.util.GatewayUtil;
import model.job.Job;
import model.job.JobProgress;
import model.job.metadata.ResourceMetadata;
import model.job.type.ExecuteServiceJob;
import model.job.type.RepeatJob;
import model.request.PiazzaJobRequest;
import model.response.ErrorResponse;
import model.response.JobResponse;
import model.response.JobStatusResponse;
import model.response.PiazzaResponse;
import model.response.ServiceResponse;
import model.response.SuccessResponse;
import model.service.metadata.ExecuteServiceData;
import model.service.metadata.Service;
import model.status.StatusUpdate;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Tests the Job Controller
 * 
 * @author Patrick.Doody
 *
 */
public class JobTests {
	@Spy
	private ObjectMapper mapper;
	@Mock
	private PiazzaLogger logger;
	@Mock
	private UUIDFactory uuidFactory;
	@Mock
	private GatewayUtil gatewayUtil;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	private RabbitTemplate rabbitTemplate;
	@Mock
	private Queue abortJobsQueue;
	@Mock
	private ServiceController serviceController;
	@InjectMocks
	private JobController jobController;

	private Principal user;
	private Job mockJob;

	/**
	 * Initialize mock objects.
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		MockitoAnnotations.initMocks(gatewayUtil);

		// Mock a Job
		mockJob = new Job();
		mockJob.setJobId("123456");
		mockJob.setJobType(new RepeatJob("654321"));
		mockJob.setProgress(new JobProgress(50));
		mockJob.setCreatedBy("Test User 2");
		mockJob.setCreatedOnString(new DateTime().toString());
		mockJob.setStatus(StatusUpdate.STATUS_RUNNING);

		// Mock a user
		user = new JMXPrincipal("Test User");

		when(gatewayUtil.getErrorResponse(anyString())).thenCallRealMethod();
	}

	/**
	 * Test GET /job/{jobId}
	 */
	@Test
	public void testGetStatus() {
		// Mock
		ResponseEntity<JobStatusResponse> mockResponse = new ResponseEntity<JobStatusResponse>(new JobStatusResponse(mockJob),
				HttpStatus.OK);
		when(restTemplate.getForEntity(anyString(), eq(JobStatusResponse.class))).thenReturn(mockResponse);

		// Test
		ResponseEntity<PiazzaResponse> entity = jobController.getJobStatus("123456", user);
		JobStatusResponse response = (JobStatusResponse) entity.getBody();

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));
		assertTrue(response.data.jobId.equals("123456"));
		assertTrue(response.data.status.equalsIgnoreCase(StatusUpdate.STATUS_RUNNING));
		assertTrue(response.data.progress.getPercentComplete().equals(50));
		assertTrue(response.data.createdBy.equals("Test User 2"));

		// Test Exception
		when(restTemplate.getForEntity(anyString(), eq(JobStatusResponse.class)))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		entity = jobController.getJobStatus("123456", user);
		assertTrue(entity.getBody() instanceof ErrorResponse);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
	}

	/**
	 * Test DELETE /job/{jobId}
	 */
	@Test
	public void testAbort() {
		// Mock
		ResponseEntity<SuccessResponse> mockEntity = new ResponseEntity<SuccessResponse>(new SuccessResponse("Deleted", "Job Manager"),
				HttpStatus.OK);
		when(restTemplate.postForEntity(anyString(), any(), eq(SuccessResponse.class))).thenReturn(mockEntity);
		when(abortJobsQueue.getName()).thenReturn("AbortJobQueue");

		// Test
		ResponseEntity<PiazzaResponse> entity = jobController.abortJob("123456", "Not Needed", user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.postForEntity(anyString(), any(), eq(SuccessResponse.class)))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		entity = jobController.abortJob("123456", "Not Needed", user);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test PUT /job/{jobId}
	 */
	@Test
	public void testRepeat() {
		// Mock
		ResponseEntity<JobResponse> mockEntity = new ResponseEntity<JobResponse>(new JobResponse("Updated"), HttpStatus.OK);
		when(restTemplate.postForEntity(anyString(), any(), eq(JobResponse.class))).thenReturn(mockEntity);

		// Test
		ResponseEntity<PiazzaResponse> entity = jobController.repeatJob("123456", user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.CREATED));

		// Test Exception
		when(restTemplate.postForEntity(anyString(), any(), eq(JobResponse.class)))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

		entity = jobController.repeatJob("123456", user);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
	}

	/**
	 * Test POST /v2/job
	 */
	@Test
	public void testExecute() throws Exception {
		// Mock
		ExecuteServiceJob executeJob = new ExecuteServiceJob("123456");
		executeJob.data = new ExecuteServiceData();
		executeJob.data.setServiceId("654321");

		ServiceResponse serviceResponse = new ServiceResponse();
		Service service = new Service();
		service.setServiceId("654321");
		service.setResourceMetadata(new ResourceMetadata());
		service.getResourceMetadata().availability = "ONLINE";
		serviceResponse.data = service;

		Mockito.doNothing().when(logger).log(anyString(), any());
		when(serviceController.getService("654321", user)).thenReturn(new ResponseEntity<PiazzaResponse>(serviceResponse, HttpStatus.OK));

		// Test
		ResponseEntity<PiazzaResponse> entity = jobController.executeService(executeJob, user);

		// Verify
		assertTrue(entity.getStatusCode().equals(HttpStatus.CREATED));

		// Test Exception
		Mockito.doThrow(new PiazzaJobException("REST Broke")).when(gatewayUtil).sendJobRequest(any(PiazzaJobRequest.class), anyString());
		entity = jobController.executeService(executeJob, user);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
		ErrorResponse error = (ErrorResponse) entity.getBody();
		assertTrue(error.message.contains("REST Broke"));
	}
}
