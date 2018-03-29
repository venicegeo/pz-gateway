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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.servlet.ServletException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import gateway.auth.PiazzaAuthenticationToken;
import gateway.auth.PiazzaBasicAuthenticationEntryPoint;
import gateway.auth.PiazzaBasicAuthenticationProvider;
import gateway.auth.UserDetailsBean;
import model.response.AuthResponse;
import model.security.authz.UserProfile;
import util.PiazzaLogger;

public class AuthenticationTests {
	@Mock
	private PiazzaLogger logger;
	@Mock
	private UserDetailsBean userDetails;
	@InjectMocks
	private PiazzaBasicAuthenticationEntryPoint authPoint;
	@InjectMocks
	private PiazzaBasicAuthenticationProvider provider;

	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testEntryPoint() throws IOException, ServletException {
		MockHttpServletResponse mockResponse = new MockHttpServletResponse();
		authPoint.commence(new MockHttpServletRequest(), mockResponse, null);
	}

	@Test
	public void testProviderAuth() {
		// Test Auth types supported
		boolean supports = provider.supports(UsernamePasswordAuthenticationToken.class);
		assertTrue(supports);
		supports = provider.supports(AnonymousAuthenticationToken.class);
		assertFalse(supports);
	}

	@Test
	public void testAuthenticate() {
		// Mock
		TestingAuthenticationToken mockAuthentication = new TestingAuthenticationToken("Tester", "TestPass");
		UserProfile mockProfile = new UserProfile();
		mockProfile.setDistinguishedName("TestDN");
		mockProfile.setUsername("Tester");
		AuthResponse mockResponse = new AuthResponse(true, mockProfile);
		Mockito.doReturn(mockResponse).when(userDetails).getFullAuthorizationDecision(Mockito.eq("Tester"), Mockito.any());
		// Test Success
		Authentication authentication = provider.authenticate(mockAuthentication);
		// Verify
		assertNotNull(authentication);
		assertTrue(authentication instanceof PiazzaAuthenticationToken);
		PiazzaAuthenticationToken token = (PiazzaAuthenticationToken) authentication;
		assertEquals(token.getDistinguishedName(), mockProfile.getDistinguishedName());
		assertEquals(token.getName(), mockProfile.getUsername());

		// Test Failure
		mockResponse = new AuthResponse(false, mockProfile);
		Mockito.doReturn(mockResponse).when(userDetails).getFullAuthorizationDecision(Mockito.eq("Tester"), Mockito.any());
		authentication = provider.authenticate(mockAuthentication);
		assertNull(authentication);
	}
}
