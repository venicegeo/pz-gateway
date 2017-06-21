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

import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Simple token child class that is capable of also storing a Distinguished Name (DN) from Authorization Responses.
 * 
 * @author Patrick.Doody
 *
 */
public class PiazzaAuthenticationToken extends UsernamePasswordAuthenticationToken {
	private static final long serialVersionUID = 2019443234708187593L;
	private String distinguishedName;

	public PiazzaAuthenticationToken(Object principal, Object credentials, Collection<? extends GrantedAuthority> authorities) {
		super(principal, credentials, authorities);
	}

	public String getDistinguishedName() {
		return distinguishedName;
	}

	public void setDistinguishedName(String distinguishedName) {
		this.distinguishedName = distinguishedName;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((distinguishedName == null) ? 0 : distinguishedName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		PiazzaAuthenticationToken other = (PiazzaAuthenticationToken) obj;
		if (distinguishedName == null) {
			if (other.distinguishedName != null)
				return false;
		} else if (!distinguishedName.equals(other.distinguishedName))
			return false;
		return true;
	}

}
