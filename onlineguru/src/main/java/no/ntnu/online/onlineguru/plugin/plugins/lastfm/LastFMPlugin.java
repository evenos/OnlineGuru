package no.ntnu.online.onlineguru.plugin.plugins.lastfm;

import java.util.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;

import de.umass.lastfm.Caller;
import de.umass.lastfm.PaginatedResult;
import de.umass.lastfm.Track;
import de.umass.lastfm.User;
import no.fictive.irclib.event.container.Event;
import no.fictive.irclib.event.container.command.PrivMsgEvent;
import no.fictive.irclib.event.model.EventType;
import no.fictive.irclib.model.network.Network;
import no.ntnu.online.onlineguru.plugin.control.EventDistributor;
import no.ntnu.online.onlineguru.plugin.model.Plugin;
import no.ntnu.online.onlineguru.plugin.model.PluginWithDependencies;
import no.ntnu.online.onlineguru.plugin.plugins.flags.model.Flag;
import no.ntnu.online.onlineguru.plugin.plugins.help.HelpPlugin;
import no.ntnu.online.onlineguru.utils.SimpleIO;
import no.ntnu.online.onlineguru.utils.Wand;
import org.apache.log4j.Logger;


public class LastFMPlugin implements PluginWithDependencies {
    static Logger logger = Logger.getLogger(LastFMPlugin.class);

    private String apikey = null;
    private final String settings_folder = "settings/";
    private final String settings_file = settings_folder + "lastfm.conf";
    private final String database_folder = "database/";
    private final String database_file = database_folder + "lastfm.db";

    private Map<String, String> usernameMapping = new HashMap<String, String>();
    private Wand wand;

    public LastFMPlugin() {
        initiate();
        Caller.getInstance().setUserAgent("onlineguru");
    }

    private void initiate() {
        try {
            SimpleIO.createFolder(database_folder);
            SimpleIO.createFile(database_file);
            SimpleIO.createFile(settings_file);
            usernameMapping = SimpleIO.loadConfig(database_file);
            apikey = SimpleIO.loadConfig(settings_file).get("apikey");

            if (apikey == null) {
                SimpleIO.writelineToFile(settings_file, "apikey=");
                logger.error("Lastfm.conf is not configured correctly");
            }
            else if (apikey.isEmpty()) {
                logger.error("Lastfm API key is empty");
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleNowPlaying(Event e) {
        PrivMsgEvent pme = (PrivMsgEvent) e;

        String message = pme.getMessage();
        String target = pme.getTarget();
        String sender = pme.getSender();
        Network network = pme.getNetwork();

        String[] parameters = message.split("\\s+");

        String lookup = sender;
        if (parameters.length == 2) {
            lookup = parameters[1];
        }

        if (usernameMapping.containsKey(lookup)) {
            sendRecentTrack(network, target, usernameMapping.get(lookup));
        }
        else {
            sendRecentTrack(network, target, lookup);
        }
    }

    private void sendRecentTrack(Network network, String target, String username) {
        PaginatedResult<Track> pagedTracks = User.getRecentTracks(username, apikey);
        Collection<Track> tracks = pagedTracks.getPageResults();

        if (tracks.size() > 0) {
            for (Track track : tracks) {
                String artist = track.getArtist();
                String album = track.getAlbum();
                String song = track.getName();

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:kk:ss");
                Date date = track.getPlayedWhen();

                String lastPlayedWhen = "";
                if (date != null) {
                    lastPlayedWhen += " - Last played: " + sdf.format(date);
                }
                if (album != null && !album.isEmpty()) {
                    album = " - Album: " + album;
                }

                wand.sendMessageToTarget(network, target, artist + " - " + song + album + lastPlayedWhen);
                //We only want the last song
                break;
            }
        }
    }

    private void handleRegisterNickname(Event e) {
        PrivMsgEvent pme = (PrivMsgEvent) e;

        String message = pme.getMessage();
        String sender = pme.getSender();
        String[] parameters = message.split("\\s+");

        if (parameters.length == 3) {
            usernameMapping.put(sender, parameters[2]);
            try {
                SimpleIO.saveConfig(database_file, usernameMapping);
                wand.sendMessageToTarget(e.getNetwork(), sender, "Your nickname was registered successfully.");
            } catch (IOException e1) {
                e1.printStackTrace();
                wand.sendMessageToTarget(e.getNetwork(), sender, "Something went wrong with registering your !np nick");
            }
        }
    }

    private void handleUnregisterNickname(Event e) {
        PrivMsgEvent pme = (PrivMsgEvent) e;

        String message = pme.getMessage();
        String sender = pme.getSender();
        String[] parameters = message.split("\\s+");

        if (parameters.length == 2) {
            if (usernameMapping.containsKey(sender)) {
                usernameMapping.remove(sender);
                try {
                    SimpleIO.saveConfig(database_file, usernameMapping);
                    wand.sendMessageToTarget(e.getNetwork(), sender, "Your nickname has been removed.");
                } catch (IOException e1) {
                    e1.printStackTrace();
                    wand.sendMessageToTarget(e.getNetwork(), sender, "Something went wrong with unregistering your !np nick");
                }
            }
            else {
                wand.sendMessageToTarget(e.getNetwork(), sender, "You have not yet registered your nickname with !np");
            }
        }
    }

    public void addEventDistributor(EventDistributor eventDistributor) {
        eventDistributor.addListener(this, EventType.PRIVMSG);

    }

    public void addWand(Wand wand) {
        this.wand = wand;
    }

    public String getDescription() {
        return "Displays last.fm information.";
    }

    public void incomingEvent(Event e) {
        PrivMsgEvent pme = (PrivMsgEvent) e;
        String message = pme.getMessage();

        if (message.startsWith("!np unregister")) {
            handleUnregisterNickname(e);
        }
        else if (message.startsWith("!np register")) {
            handleRegisterNickname(e);
        }
        else if (message.startsWith("!np")) {
            handleNowPlaying(e);
        }
    }

    public String[] getDependencies() {
        return new String[]{"HelpPlugin",};
    }

    public void loadDependency(Plugin plugin) {
        if (plugin instanceof HelpPlugin) {
            HelpPlugin help = (HelpPlugin) plugin;
            help.addHelp(
                    "!np",
                    Flag.ANYONE,
                    "!np <Last.fm username> - Displays the last track played by the supplied Last.fm api.",
                    "!np register <Last.fm username> - Links the Last.fm username to your nick.",
                    "!np unregister <Last.fm username> - Unlinks the Last.fm username from your nick."
            );
        }
    }

}
