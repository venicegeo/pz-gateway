package gateway.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsBean implements UserDetailsService {
	
    @Override
    public UserDetails loadUserByUsername(String username) {
    	//TODO: Make callout to pz-security service for user roles, details.
    	
    	List<GrantedAuthority> gas = new ArrayList<GrantedAuthority>();
//        gas.add(new SimpleGrantedAuthority("ROLE_USER"));
        return new User(username, "password", true, true, true, true, gas);
    }
}