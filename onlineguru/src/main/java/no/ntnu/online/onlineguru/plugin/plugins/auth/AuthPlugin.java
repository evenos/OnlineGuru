package no.ntnu.online.onlineguru.plugin.plugins.auth;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import no.ntnu.online.onlineguru.exceptions.MalformedSettingsException;
import no.ntnu.online.onlineguru.exceptions.MissingSettingsException;
import no.ntnu.online.onlineguru.utils.Wand;
import org.apache.log4j.Logger;

import no.fictive.irclib.event.container.Event;
import no.fictive.irclib.event.container.command.ConnectEvent;
import no.fictive.irclib.event.model.EventType;
import no.ntnu.online.onlineguru.plugin.control.EventDistributor;
import no.ntnu.online.onlineguru.plugin.model.Plugin;
import no.ntnu.online.onlineguru.utils.SimpleIO;
import no.ntnu.online.onlineguru.utils.settingsreader.Settings;
import no.ntnu.online.onlineguru.utils.settingsreader.SettingsReader;

/**
 * This plugin will authenticate onlineguru with AuthPlugin on connect.
 * 
 * @author melwil
 */

public class AuthPlugin implements Plugin {
	
	private Wand wand;
	static Logger logger = Logger.getLogger(AuthPlugin.class);
	
	private final String settings_folder = "settings/";
	private final String settings_file = settings_folder + "auth.conf";
	private HashMap<String, AuthEntry> networks = new HashMap<String, AuthEntry>();

	public AuthPlugin() {
        try {
		    initiate();
        } catch (MissingSettingsException mse) {
            logger.warn(mse.getError(), mse.getCause());
        }
	}
	
	private void initiate() throws MissingSettingsException {
		try {
			SimpleIO.createFolder(settings_folder);
			File file = new File(settings_file);
						
			if (!file.exists()) {
				SimpleIO.createFile(settings_file);
				SimpleIO.writelineToFile(settings_file, "[network]\n" +
														"network=\n" +
														"username=\n" +
														"password=\n");
				throw new MissingSettingsException("Missing settings file. Settings file created.");
			}

            try {
                ArrayList<Settings> settingsList = SettingsReader.readSettings(settings_file);
                readSettings(settingsList);
            } catch (MalformedSettingsException mse) {
                logger.warn(mse.getError(), mse.getCause());
            }

        } catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

    private void readSettings(ArrayList<Settings> settingsList) throws MalformedSettingsException {
        for (Settings settings : settingsList) {

            String network, username, password;

            network = settings.getSetting("network");
            username = settings.getSetting("username");
            password = settings.getSetting("password");

            if(
                    (network == null)	|| (network.isEmpty())
                ||	(username == null) 	|| (username.isEmpty())
                || 	(password == null) 	|| (password.isEmpty())
                ) {
                throw new MalformedSettingsException("Some settings were empty.");
            }
            else {
                networks.put(network, new AuthEntry(username, password));
            }
        }
    }

	public String getDescription() {
		return "Identifies this bot with the NickServ service on a network.";
	}

	public void incomingEvent(Event e) {
		if (e instanceof ConnectEvent) {
			AuthEntry ae = networks.get(e.getNetwork().getServerAlias());
			if (ae != null) {
				if (ae.getUsername().equalsIgnoreCase(wand.getMyNick(e.getNetwork()))) {
					wand.sendMessageToTarget(e.getNetwork(), "NickServ", "identify "+ae.getPassword());
				}
			}
		}
	}

	public void addEventDistributor(EventDistributor eventDistributor) {
		eventDistributor.addListener(this, EventType.CONNECT);
	}

	public void addWand(Wand wand) {
		this.wand = wand;
	}
	
}
