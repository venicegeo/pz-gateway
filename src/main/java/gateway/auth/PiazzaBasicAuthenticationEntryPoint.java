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

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import model.logger.Severity;
import model.response.ErrorResponse;
import util.PiazzaLogger;

/**
 * Custom Sprint 'Entry Point' that issues an appropriate response when authentication fails.
 * 
 * @author Russell.Orf
 * 
 */
@Component
public class PiazzaBasicAuthenticationEntryPoint extends BasicAuthenticationEntryPoint {
	@Autowired
	private PiazzaLogger logger;

	private final static Logger LOG = LoggerFactory.getLogger(PiazzaBasicAuthenticationEntryPoint.class);

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authEx)
			throws IOException, ServletException {
		response.addHeader("WWW-Authenticate", "Basic realm=\"" + getRealmName() + "\"");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json");
		PrintWriter writer = response.getWriter();
		// Create a Response Object
		ErrorResponse error = new ErrorResponse("Gateway is unable to authenticate the provided user.", "Gateway");

		try {
			// Log the request
			logger.log(String.format("Unable to authenticate a user with Auth Type %s.", request.getAuthType()), Severity.ERROR);
		} catch (Exception exception) {
			String errorString = String.format("Exception encountered during Authorization check: %s.", exception.getMessage());
			LOG.error(errorString, exception);
			logger.log(errorString, Severity.ERROR);
		}

		// Write back the response
		writer.println(new ObjectMapper().writeValueAsString(error));
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		setRealmName("Piazza");
		super.afterPropertiesSet();
	}
}