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
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import gateway.controller.DataController;
import gateway.controller.util.GatewayUtil;

import java.security.Principal;
import java.util.ArrayList;

import javax.management.remote.JMXPrincipal;

import model.data.DataResource;
import model.data.type.TextDataType;
import model.job.metadata.ResourceMetadata;
import model.response.DataResourceListResponse;
import model.response.ErrorResponse;
import model.response.Pagination;
import model.response.PiazzaResponse;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import util.PiazzaLogger;
import util.UUIDFactory;

import com.amazonaws.services.s3.AmazonS3;

/**
 * Tests the Data Controller, and various Data Access/Load Jobs.
 * 
 * @author Patrick.Doody
 * 
 */
public class DataTests {
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
	private DataController dataController;

	private Principal user;
	private DataResource mockData;

	/**
	 * Initialize mock objects.
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);

		// Mock some Data that we can use in our test cases.
		mockData = new DataResource();
		mockData.dataId = "DataID";
		mockData.dataType = new TextDataType();
		((TextDataType) mockData.dataType).content = "MockData";
		mockData.metadata = new ResourceMetadata();
		mockData.metadata.setName("Test Data");

		// Mock a user
		user = new JMXPrincipal("Test User");
	}

	@Test
	public void testGetData() {
		// When the Gateway asks the Dispatcher for a List of Data, Mock that
		// response here.
		DataResourceListResponse mockResponse = new DataResourceListResponse();
		mockResponse.data = new ArrayList<DataResource>();
		mockResponse.getData().add(mockData);
		mockResponse.pagination = new Pagination(1, 0, 10);
		when(restTemplate.getForObject(anyString(), eq(PiazzaResponse.class))).thenReturn(mockResponse);

		// Get the data
		ResponseEntity<PiazzaResponse> entity = dataController.getData(0, 10, null, null, user);
		PiazzaResponse response = entity.getBody();

		// Verify the results
		assertTrue(response instanceof DataResourceListResponse);
		DataResourceListResponse dataList = (DataResourceListResponse) response;
		assertTrue(dataList.getData().size() == 1);
		assertTrue(dataList.getPagination().getCount() == 1);

		// Mock an Exception being thrown and handled.
		ErrorResponse mockError = new ErrorResponse("JobID", "Error!", "Test");
		when(restTemplate.getForObject(anyString(), eq(PiazzaResponse.class))).thenReturn(mockError);

		// Get the data
		entity = dataController.getData(0, 10, null, null, user);
		response = entity.getBody();

		// Verify that a proper exception was thrown.
		assertTrue(response instanceof ErrorResponse);
	}
}