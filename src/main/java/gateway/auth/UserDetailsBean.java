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

import util.PiazzaLogger;

/**
 * Bean that communicates with the pz-security project for authentication
 * information.
 * 
 * @author Russell.Orf
 * 
 */
@Service
public class UserDetailsBean {
	@Autowired
	private PiazzaLogger logger;
	@Value("#{'${security.protocol}' + '://' + '${security.prefix}' + '.' + '${DOMAIN}' + ':' + '${security.port}'}")
	private String SECURITY_URL;

	public boolean getAuthenticationDecision(String username, String credential) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);

			HttpEntity<PiazzaVerificationRequest> entity = new HttpEntity<PiazzaVerificationRequest>(
					new PiazzaVerificationRequest(username, credential), headers);
			String url = String.format("%s/%s", SECURITY_URL, "/verification");
			return new RestTemplate().postForEntity(url, entity, Boolean.class).getBody();
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(e.getMessage(), PiazzaLogger.ERROR);
			return false;
		}
	}

	class PiazzaVerificationRequest {
		private String username;
		private String credential;

		PiazzaVerificationRequest(String username, String credential) {
			this.username = username;
			this.credential = credential;
		}

		public String getUsername() {
			return username;
		}

		public String getCredential() {
			return credential;
		}
	}
}