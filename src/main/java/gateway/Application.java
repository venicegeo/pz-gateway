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
package gateway;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import gateway.auth.PiazzaAccessDecisionVoter;
import gateway.auth.UserDetailsBean;

/**
 * Spring-boot configuration for the Gateway service.
 * 
 * @author Patrick.Doody, Russell.Orf
 * 
 */
@SpringBootApplication
@ComponentScan({ "gateway, util" })
public class Application extends SpringBootServletInitializer {
	
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(Application.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
	
	@Configuration
	protected static class ApplicationSecurity extends WebSecurityConfigurerAdapter {

		@Autowired
		private UserDetailsBean userService;
		
		@Override
		protected void configure(HttpSecurity http) throws Exception {
			http
				.x509().userDetailsService(userService)
				.and()
				.authorizeRequests().accessDecisionManager(accessDecisionManager()).anyRequest().authenticated()
				.and()
				.csrf().disable();
		}

		@Bean
		public AccessDecisionManager accessDecisionManager() {
			return new AffirmativeBased(Arrays.asList((AccessDecisionVoter<?>)new PiazzaAccessDecisionVoter()));
		}	
	}
}