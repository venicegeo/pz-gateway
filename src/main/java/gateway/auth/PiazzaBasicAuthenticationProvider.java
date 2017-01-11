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

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import model.logger.Severity;
import model.response.AuthResponse;
import util.PiazzaLogger;

/**
 * Custom Authentication Provider to authentication the provided username and credential in the 'Authorization' request
 * header field.
 * 
 * @author Russell.Orf
 * 
 */
@Component
public class PiazzaBasicAuthenticationProvider implements AuthenticationProvider {
	@Autowired
	private PiazzaLogger logger;
	@Autowired
	private UserDetailsBean userDetails;

	private final static Logger LOGGER = LoggerFactory.getLogger(PiazzaBasicAuthenticationProvider.class);

	public PiazzaBasicAuthenticationProvider() {
		super();
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		ExtendedRequestDetails details = (ExtendedRequestDetails) authentication.getDetails();
		try {
			// Form the AuthN+AuthZ request to pz-idam.
			AuthResponse response = userDetails.getFullAuthorizationDecision(authentication.getName(), details);
			if (response.getIsAuthSuccess()) {
				PiazzaAuthenticationToken authToken = new PiazzaAuthenticationToken(response.getUserProfile().getUsername(), null,
						new ArrayList<>());
				authToken.setDistinguishedName(response.getUserProfile().getDistinguishedName());
				return authToken;
			}
		} catch (Exception exception) {
			String error = String.format("Error retrieving Api Key: %s.", exception.getMessage());
			logger.log(error, Severity.ERROR);
			LOGGER.error(error, exception);
		}
		return null;
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}
}