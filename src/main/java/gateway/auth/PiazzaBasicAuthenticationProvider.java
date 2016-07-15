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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import model.response.AuthenticationResponse;
import util.PiazzaLogger;

/**
 * Custom Authentication Provider to authentication the provided username and
 * credential in the 'Authorization' request header field.
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

	public PiazzaBasicAuthenticationProvider() {
		super();
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		try {
			AuthenticationResponse response = userDetails.getAuthenticationDecision(authentication.getName());
			if( response.getAuthenticated() ) {
				return new UsernamePasswordAuthenticationToken(response.getUsername(), null, new ArrayList<>());
			}
		} catch(Exception exception) {
			exception.printStackTrace();
			String error = String.format("Error retrieving UUID: %s", exception.getMessage());
			logger.log(error, PiazzaLogger.ERROR);			
		}
		return null;		
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}
}