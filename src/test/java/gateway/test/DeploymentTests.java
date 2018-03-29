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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.amazonaws.services.s3.AmazonS3;

import exception.PiazzaJobException;
import gateway.controller.DeploymentController;
import gateway.controller.util.GatewayUtil;
import model.data.deployment.Deployment;
import model.job.type.AccessJob;
import model.request.PiazzaJobRequest;
import model.response.DeploymentListResponse;
import model.response.DeploymentResponse;
import model.response.ErrorResponse;
import model.response.JobResponse;
import model.response.Pagination;
import model.response.PiazzaResponse;
import model.response.SuccessResponse;
import util.PiazzaLogger;
import util.UUIDFactory;

/**
 * Tests the Deployment Controller
 * 
 * @author Patrick.Doody
 *
 */
public class DeploymentTests {
	@Mock
	private PiazzaLogger logger;
	@Mock
	private UUIDFactory uuidFactory;
	@Mock
	private GatewayUtil gatewayUtil;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	private AmazonS3 s3Client;
	@InjectMocks
	private DeploymentController deploymentController;

	private Principal user;
	private Deployment mockDeployment;

	/**
	 * Initialize mock objects.
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
		MockitoAnnotations.initMocks(gatewayUtil);

		// Mock a user
		user = new JMXPrincipal("Test User");

		// Mock some deployment
		mockDeployment = new Deployment("123456", "654321", "localhost", "8080", "layer",
				"http://localhost:8080/layer?request=GetCapabilities");

		when(gatewayUtil.getErrorResponse(anyString())).thenCallRealMethod();
	}

	/**
	 * Test POST /deployment
	 */
	@Test
	public void testCreate() throws Exception {
		// Mock
		AccessJob accessJob = new AccessJob("123456");
		accessJob.setDeploymentType("geoserver");
		// Generate a UUID that we can reproduce.
		when(gatewayUtil.sendJobRequest(any(PiazzaJobRequest.class), anyString())).thenReturn("654321");

		// Test
		ResponseEntity<PiazzaResponse> entity = deploymentController.createDeployment(accessJob, user);

		// Verify
		assertTrue(((JobResponse) entity.getBody()).data.getJobId().equals("654321"));
		assertTrue(entity.getStatusCode().equals(HttpStatus.CREATED));

		// Test Exception
		Mockito.doThrow(new PiazzaJobException("Message Bus Error")).when(gatewayUtil).sendJobRequest(any(PiazzaJobRequest.class),
				anyString());
		entity = deploymentController.createDeployment(accessJob, user);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
		assertTrue(entity.getBody() instanceof ErrorResponse);
		assertTrue(((ErrorResponse) entity.getBody()).message.contains("Message Bus Error"));
	}

	/**
	 * Test GET /deployment
	 */
	@Test
	public void testGetList() {
		// Mock
		DeploymentListResponse mockResponse = new DeploymentListResponse();
		mockResponse.data = new ArrayList<Deployment>();
		mockResponse.getData().add(mockDeployment);
		mockResponse.pagination = new Pagination(new Long(1), 0, 10, "test", "asc");
		when(restTemplate.getForEntity(anyString(), eq(DeploymentListResponse.class)))
				.thenReturn(new ResponseEntity<DeploymentListResponse>(mockResponse, HttpStatus.OK));

		// Test
		ResponseEntity<PiazzaResponse> entity = deploymentController.getDeployment(null, 0, 10, "asc", "test", user);
		PiazzaResponse response = entity.getBody();

		// Verify
		assertTrue(response instanceof DeploymentListResponse);
		DeploymentListResponse dataList = (DeploymentListResponse) response;
		assertTrue(dataList.getData().size() == 1);
		assertTrue(dataList.getPagination().getCount() == 1);
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));

		// Test Exception
		when(restTemplate.getForEntity(anyString(), eq(DeploymentListResponse.class)))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
		entity = deploymentController.getDeployment(null, 0, 10, "asc", "test", user);
		response = entity.getBody();
		assertTrue(response instanceof ErrorResponse);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
	}

	/**
	 * Test GET /deployment/{deploymentId}
	 */
	@Test
	public void testGetMetadata() {
		// Mock the Response
		DeploymentResponse mockResponse = new DeploymentResponse(mockDeployment, "Now");
		when(restTemplate.getForEntity(anyString(), eq(DeploymentResponse.class)))
				.thenReturn(new ResponseEntity<DeploymentResponse>(mockResponse, HttpStatus.OK));

		// Test
		ResponseEntity<PiazzaResponse> entity = deploymentController.getDeployment("123456", user);
		PiazzaResponse response = entity.getBody();

		// Verify
		assertTrue(response instanceof ErrorResponse == false);
		assertTrue(
				((DeploymentResponse) response).data.getDeployment().getDeploymentId().equalsIgnoreCase(mockDeployment.getDeploymentId()));
		assertTrue(entity.getStatusCode().equals(HttpStatus.OK));

		// Test an Exception
		when(restTemplate.getForEntity(anyString(), eq(DeploymentResponse.class)))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
		entity = deploymentController.getDeployment("123456", user);
		response = entity.getBody();
		assertTrue(response instanceof ErrorResponse);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
	}

	/**
	 * Test DELETE /deployment/{deploymentId}
	 */
	@Test
	public void testDeleteDeployment() {
		// Mock the Response
		when(restTemplate.exchange(anyString(), any(), any(), eq(SuccessResponse.class)))
				.thenReturn(new ResponseEntity<SuccessResponse>(new SuccessResponse("Deleted", "Access"), HttpStatus.OK));

		// Test
		ResponseEntity<PiazzaResponse> entity = deploymentController.deleteDeployment("123456", user);

		// Verify
		assertTrue(entity.getBody() instanceof SuccessResponse);

		// Test an Exception
		when(restTemplate.exchange(anyString(), any(), any(), eq(SuccessResponse.class)))
				.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
		entity = deploymentController.deleteDeployment("123456", user);
		PiazzaResponse response = entity.getBody();
		assertTrue(response instanceof ErrorResponse);
		assertTrue(entity.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR));
	}

	/**
	 * Test deleting a deployment group
	 */
	@Test
	public void testDeleteDeploymentGroup() {
		// Mock
		Mockito.doReturn(new ResponseEntity<PiazzaResponse>(new SuccessResponse(), HttpStatus.OK)).when(restTemplate)
				.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.DELETE), Mockito.any(), Mockito.eq(PiazzaResponse.class));

		// Test
		ResponseEntity<PiazzaResponse> response = deploymentController.deleteDeploymentGroup("123456", user);

		// Verify
		assertTrue(response.getBody() instanceof SuccessResponse);
	}
}
