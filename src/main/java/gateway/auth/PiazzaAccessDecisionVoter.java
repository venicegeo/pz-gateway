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
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.FilterInvocation;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import messaging.job.JobMessageFactory;
import model.job.type.RepeatJob;
import model.request.PiazzaJobRequest;
import model.response.JobStatusResponse;

/**
 * Custom class responsible for voting on Authorization decisions
 * 
 * @author Russell.Orf
 * 
 */
public class PiazzaAccessDecisionVoter implements AccessDecisionVoter<Object> {
	
	private RestTemplate restTemplate = new RestTemplate();	
	
	private static String DISPATCHER_HOST = null;
	
	public PiazzaAccessDecisionVoter(String host) {
		DISPATCHER_HOST = host;
	}
	
	@Override
	public boolean supports(ConfigAttribute attribute) {
		return true;
	}

	@Override
	public boolean supports(Class clazz) {
		return true;
	}

	@Override
	public int vote(Authentication authentication, Object object, Collection<ConfigAttribute> attributes) {
		HttpServletRequest req = ((FilterInvocation)object).getRequest();
		
		try {
			String requestedJobType = getRequestedJobType(req);
			
			for( GrantedAuthority ga : authentication.getAuthorities() ) {
				System.out.println("Checking requested " + requestedJobType + " against authorized " + ga.getAuthority());
				if( requestedJobType.equals(ga.getAuthority()) ) {
					return 1;
				}
			}			
		} catch (Exception e) {
			System.out.println("Exception occurred; could not authorize user due to: " + e.getMessage());
			e.printStackTrace();
		}
		
		return -1;
	}
	
	private String getRequestedJobType(HttpServletRequest req) throws JsonParseException, JsonMappingException, IOException {
		String requestPathLower = req.getServletPath().toLowerCase();
		
		if( requestPathLower.startsWith("/admin") ) {
			return "admin-stats"; 
		}
		else if( requestPathLower.startsWith("/file") ) {
			return "access";
		}
		else {
			PiazzaJobRequest pjr = (PiazzaJobRequest)JobMessageFactory.parseRequestJson(req.getParameter("body"));
			String requestedJobType = pjr.jobType.getType();
			
			if( requestedJobType.equalsIgnoreCase("repeat") ) {
				requestedJobType = getOriginalJobTypeForRepeatJob( ((RepeatJob)(pjr.jobType)).jobId );
				System.out.println("Original job type is: " + requestedJobType);
			}
			return requestedJobType;
		}
	}
	
	private String getOriginalJobTypeForRepeatJob(String jobId) {
		return restTemplate.getForObject(
				String.format("http://%s/%s/%s", DISPATCHER_HOST, "job", jobId), JobStatusResponse.class).jobType;
	}
}