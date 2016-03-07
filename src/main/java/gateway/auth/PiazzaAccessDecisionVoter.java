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

import messaging.job.JobMessageFactory;
import model.request.PiazzaJobRequest;

/**
 * Custom class responsible for voting on Authorization decisions
 * 
 * @author Russell.Orf
 * 
 */
public class PiazzaAccessDecisionVoter implements AccessDecisionVoter<Object> {
	
	@Override
	public boolean supports(ConfigAttribute attribute) {
		return true;
	}

	@Override
	public boolean supports(Class clazz) {
		return true;
	}

	@Override
	public int vote(Authentication authentication, Object object, Collection attributes) {
		HttpServletRequest req = ((FilterInvocation)object).getRequest();
		
		String requestedJobType;
		try {
			requestedJobType = ((PiazzaJobRequest)JobMessageFactory.parseRequestJson(req.getParameter("body"))).jobType.getType();
			
			for( GrantedAuthority ga : authentication.getAuthorities() ) {
				System.out.println("Checking requested " + requestedJobType + " against authorized " + ga.getAuthority());
				if( requestedJobType.equals(ga.getAuthority()) ) {
					return 1;
				}
			}			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return 0;
	}
}