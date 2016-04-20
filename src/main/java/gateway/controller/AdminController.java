package gateway.controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller that defines administrative end points that reference
 * logging, administartion, and debugging information related to the Gateway
 * component.
 * 
 * @author Patrick.Doody
 *
 */
@CrossOrigin
@RestController
public class AdminController {
	/**
	 * Gets administrative statistics for the Gateway service.
	 * 
	 * @param user
	 *            The user making this request
	 * @return Administrative statistics
	 */
	@RequestMapping(value = "/admin/stats", method = RequestMethod.GET)
	public Map<String, String> getAdminStats(Principal user) {
		return new HashMap<String, String>();
	}
}
