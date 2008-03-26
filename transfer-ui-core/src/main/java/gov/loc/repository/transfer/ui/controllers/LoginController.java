package gov.loc.repository.transfer.ui.controllers;

import gov.loc.repository.transfer.ui.UIConstants;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class LoginController {

	@RequestMapping("/login/login_*.html")
	@SuppressWarnings("unchecked")	
	public ModelAndView login_form(HttpServletRequest req) {		
		ModelAndView mav = new ModelAndView();
		mav.addObject("contextPath", req.getContextPath());
		mav.setViewName("login");
		return mav;
	}
	
	@RequestMapping("/login/login.html")
	@SuppressWarnings("unchecked")	
	public String login(HttpServletRequest req) {		
		
		if (req.getParameter(UIConstants.PARAMETER_REFERER) != null)
		{
			return "redirect:" + req.getParameter(UIConstants.PARAMETER_REFERER);
		}
		return "redirect:/index.html";
	}

	@RequestMapping("/login/logout.html")
	public String logout(HttpServletRequest req) throws Exception {
		req.getSession().invalidate();
		return "redirect:/index.html";		
	}
}
