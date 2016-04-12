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

import gateway.auth.PiazzaAccessDecisionVoter;
import gateway.auth.UserDetailsBean;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

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
	@Profile({ "ssl" })
	protected static class ApplicationSecurity extends WebSecurityConfigurerAdapter {

		@Autowired
		private UserDetailsBean userService;

		@Value("${dispatcher.port}")
		private String DPORT;

		@Value("${dispatcher.host}")
		private String DHOST;

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			http.addFilterBefore(corsFilter(), ChannelProcessingFilter.class).x509().userDetailsService(userService)
					.and().authorizeRequests().accessDecisionManager(accessDecisionManager()).antMatchers("/job")
					.authenticated().antMatchers("/file").authenticated().antMatchers("/admin/**").authenticated()
					.and().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.NEVER).and().csrf()
					.disable();
		}

		@Bean
		public AccessDecisionManager accessDecisionManager() {
			String dispatcher = (DPORT == null || DPORT.trim().length() == 0) ? DHOST : DHOST + ":" + DPORT;

			return new AffirmativeBased(
					Arrays.asList((AccessDecisionVoter<?>) new PiazzaAccessDecisionVoter(dispatcher)));
		}

		@Bean
		public CorsFilter corsFilter() {
			UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
			CorsConfiguration config = new CorsConfiguration();
			config.addAllowedOrigin("*");
			config.addAllowedHeader("*");
			config.addAllowedMethod("OPTIONS");
			config.addAllowedMethod("GET");
			config.addAllowedMethod("PUT");
			config.addAllowedMethod("POST");
			config.addAllowedMethod("DELETE");
			source.registerCorsConfiguration("/**", config);
			return new CorsFilter(source);
		}
	}
}