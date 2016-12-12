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
package gateway.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import model.logger.AuditElement;
import model.logger.Severity;
import model.response.AuthResponse;
import model.security.authz.AuthorizationCheck;
import model.security.authz.Permission;
import util.PiazzaLogger;

/**
 * Bean that communicates with the pz-security project for authentication information.
 * 
 * @author Russell.Orf
 * 
 */
@Service
public class UserDetailsBean {
	@Value("${security.url}")
	private String SECURITY_URL;
	@Autowired
	private RestTemplate restTemplate;
	@Autowired
	private PiazzaLogger logger;

	/**
	 * Gets a full Authentication/Authorization decision for a user. This checks the validity of the API Key, and, if
	 * successfully authenticated, will also conduct an Authorization check on the User for the requested action.
	 * 
	 * @param apiKey
	 *            The API Key of the user
	 * @param requestDetails
	 *            The details of the request made to the Gateway. Used to populate the AuthorizationCheck payload.
	 * @return The Auth response, containing details and success/failure information.
	 */
	public AuthResponse getFullAuthorizationDecision(String apiKey, ExtendedRequestDetails requestDetails) {
		String url = String.format("%s/%s", SECURITY_URL, "/authz");
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		// Create the Authorization Check based on the Request Details
		AuthorizationCheck authorizationCheck = new AuthorizationCheck();
		authorizationCheck.setApiKey(apiKey);
		Permission permission = new Permission(requestDetails.getRequest().getMethod(), requestDetails.getRequest().getRequestURI());
		authorizationCheck.setAction(permission);
		// Send request to Pz-Idam
		HttpEntity<AuthorizationCheck> request = new HttpEntity<>(authorizationCheck, headers);
		AuthResponse response = restTemplate.postForEntity(url, request, AuthResponse.class).getBody();
		// If the UserProfile was returned, log the Username
		String userName = null;
		if (response.getIsAuthSuccess()) {
			userName = response.getUserProfile().getUsername();
		}
		// Log the Results of the Check
		String actionName = response.getIsAuthSuccess() ? "keyVerified" : "keyDeclined";
		logger.log(
				String.format("Checked Full Authentication and Authorization for Username %s performing Action %s with verified = %s",
						userName, authorizationCheck.toString(), response.getIsAuthSuccess()),
				Severity.INFORMATIONAL, new AuditElement(userName != null ? userName : "gateway", actionName, ""));
		// Return Response
		return response;
	}

	/**
	 * Gets an Authentication Decision for an Authorization check. This determines the validity of a Piazza users API
	 * Key within the pz-IDAM component.
	 * 
	 * @param uuid
	 *            The API Key of the user.
	 * @return The Auth response, containing details and success/failure information.
	 */
	public AuthResponse getAuthenticationDecision(String uuid) {
		String url = String.format("%s/%s", SECURITY_URL, "/authn");
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<PiazzaVerificationRequest> request = new HttpEntity<>(new PiazzaVerificationRequest(uuid), headers);
		AuthResponse response = restTemplate.postForEntity(url, request, AuthResponse.class).getBody();
		String actionName = response.getIsAuthSuccess() ? "keyVerified" : "keyDeclined";
		// If the UserProfile was returned, log the Username
		String userName = null;
		if (response.getIsAuthSuccess()) {
			userName = response.getUserProfile().getUsername();
		}
		// Log
		logger.log(
				String.format("Checked Authentication for Username's %s API Key with verified = %s", userName, response.getIsAuthSuccess()),
				Severity.INFORMATIONAL, new AuditElement(userName != null ? userName : "gateway", actionName, ""));
		return response;
	}

	/**
	 * The model that corresponds with an AuthN request to pz-idam.
	 */
	class PiazzaVerificationRequest {
		private String uuid;

		PiazzaVerificationRequest(String uuid) {
			this.uuid = uuid;
		}

		public String getUuid() {
			return uuid;
		}
	}
}