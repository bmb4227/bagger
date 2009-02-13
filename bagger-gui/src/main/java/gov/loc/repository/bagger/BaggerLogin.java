
package gov.loc.repository.bagger;

import gov.loc.repository.bagger.bag.BaggerBag;

import org.acegisecurity.Authentication;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.richclient.application.Application;
import org.springframework.richclient.application.ApplicationWindow;
import org.springframework.richclient.application.config.ApplicationWindowConfigurer;
import org.springframework.richclient.command.ActionCommand;
import org.springframework.richclient.security.LoginCommand;

/**
 * Custom application lifecycle implementation that configures the app at well defined points within
 * its lifecycle.
 *
 * @author Jon Steinbach
 */
public class BaggerLogin extends LoginCommand
{
	public void doExecuteCommand() {
		super.doExecuteCommand();
	}
}