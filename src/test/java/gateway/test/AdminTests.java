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

import java.util.Map;

import gateway.controller.AdminController;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

/**
 * Tests the Admin controller.
 * 
 * @author Patrick.Doody
 *
 */
public class AdminTests {
	@InjectMocks
	private AdminController adminController;

	/**
	 * Initialize mock objects.
	 */
	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
	}

	/**
	 * Tests root endpoint
	 */
	@Test
	public void testHealthCheck() {
		String result = adminController.getHealthCheck();
		assertTrue(result.contains("Hello"));
	}

	/**
	 * Test /admin/stats
	 */
	@Test
	public void testStats() {
		ResponseEntity<Map<String, Object>> response = adminController.getAdminStats();
		Map<String, Object> stats = response.getBody();

		// Ensure proper keys
		assertTrue(stats.containsKey("Kafka Address"));
		assertTrue(stats.containsKey("Space"));
		assertTrue(stats.containsKey("Workflow"));
		assertTrue(stats.containsKey("Search"));
		assertTrue(stats.containsKey("Ingest"));
		assertTrue(stats.containsKey("Access"));
		assertTrue(stats.containsKey("JobManager"));
		assertTrue(stats.containsKey("ServiceController"));
		assertTrue(stats.containsKey("UUIDGen"));
		assertTrue(stats.containsKey("Logger"));
		assertTrue(stats.containsKey("Space"));
		assertTrue(stats.containsKey("Space"));
		assertTrue(stats.containsKey("Security"));
	}
}
