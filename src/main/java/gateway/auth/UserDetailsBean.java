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
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Bean that communicates with the pz-security project for authorization
 * information.
 * 
 * @author Russell.Orf
 * 
 */
@Service
public class UserDetailsBean implements UserDetailsService {

	private RestTemplate restTemplate = new RestTemplate();

	@Value("${pz.security.endpoint:}")
	private String SEC_ENDPOINT;

	@Override
	public UserDetails loadUserByUsername(String username) {
		List<GrantedAuthority> gas = new ArrayList<GrantedAuthority>();

		@SuppressWarnings("rawtypes")
		ResponseEntity<List> roles = restTemplate.getForEntity("http://" + SEC_ENDPOINT + "/users/" + username + "/roles", List.class);

		for (Object role : roles.getBody()) {
			System.out.println("Adding " + username + " role: " + role);
			gas.add(new SimpleGrantedAuthority(role.toString()));
		}

		return new User(username, "password", true, true, true, true, gas);
	}
}