package me.becja10.RandomTP;

import java.io.File;
import java.io.IOException;
import me.becja10.RandomTP.RandomTP;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class PlayerManager
{
	private static FileConfiguration config = null;
	private static File players = null;
	private static String path = RandomTP.plugin.getDataFolder().getAbsolutePath() 
			+ File.separator + "players.yml";

	public static FileConfiguration getPlayers() {
		/*
		 * <world>:
		 *   <UUID>:
		 *     name:
		 *     x:
		 *     y:
		 *     z:
		 *     hasTeleported:
		 */
		if (config == null)
			reloadPlayers();
		return config;
	}

	public static void reloadPlayers() {
		if (players == null)
			players = new File(path);
		config = YamlConfiguration.loadConfiguration(players);
	}
	
	public static void savePlayers() {
		if ((config == null) || (players == null))
			return;
		try {
			getPlayers().save(players);
		} catch (IOException ex) {
			RandomTP.logger.warning("Unable to write to the player file at \"" + path + "\"");
		}
	}
	
	public static void setUpManager(){
		reloadPlayers();
	}
}
