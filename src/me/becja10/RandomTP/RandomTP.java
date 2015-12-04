package me.becja10.RandomTP;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.minecraft.server.v1_8_R3.Material;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class RandomTP extends JavaPlugin implements Listener{
	public final Logger logger = Logger.getLogger("Minecraft");
	private static RandomTP plugin;
	private static final int MAX_BORDER = 500000;
	private static Random gen;
	private static final int SPINNER = 5000;
	private static boolean gotWorld = true;
	
	private static Location spawn;
	private static World world;
	private boolean hasGP;
	
	//config
	public int config_border;							//How far out to check for teleport. Assumes set from spawn location
	public String config_world;							//World that players will be rTPd upon joining
	public int config_startX;							//where to start the search from
	public int config_startY;
	public int config_startZ;
	public boolean config_setBed;						//Whether or not to set the bed location
	public boolean config_setHome;						//Whether or not to run /sethome
	public boolean config_checkClaims;					//Check for claims or not
	
	private String configPath;
	private FileConfiguration config;
	private FileConfiguration outConfig;
	
	
	private void loadConfig()
	{		
		//get stored values, or defaults
		config_border = config.getInt("border", -1);
		config_world  = config.getString("world", "someworldthatwontexist");
		config_startX = config.getInt("start.x", 0);
		config_startY = config.getInt("start.y", 0);
		config_startZ = config.getInt("start.z", 0);
		config_setBed = config.getBoolean("bed", true);
		config_setHome = config.getBoolean("sethome", true);
		config_checkClaims = config.getBoolean("claims", hasGP);
		
		//store them in output.
		outConfig.set("border", config_border);
		outConfig.set("world", config_world);
		outConfig.set("start.x", config_startX);
		outConfig.set("start.y", config_startY);
		outConfig.set("start.z", config_startZ);
		outConfig.set("bed", config_setBed);
		outConfig.set("setHome", config_setHome);
		outConfig.set("claims", config_checkClaims);
		save();
	}

	@Override
	public void onDisable(){
		PluginDescriptionFile pdfFile = this.getDescription();
		this.logger.info(pdfFile.getName() + " Has Been Disabled!");
		save();
	}
	
	@Override
	public void onEnable(){
		PluginDescriptionFile pdfFile = this.getDescription();
		this.logger.info(pdfFile.getName() + " Version "+ pdfFile.getVersion() + " Has Been Enabled!");
	    getServer().getPluginManager().registerEvents(this, this); //register events
		plugin = this;
		gen = new Random();
		gen.setSeed(System.currentTimeMillis());
		
		hasGP = getServer().getPluginManager().getPlugin("GriefPrevention") != null;
		if(!hasGP)
			this.logger.info(pdfFile.getName() + " GriefPrevention not detected! Will not check for claims.");
		
		configPath = plugin.getDataFolder().getAbsolutePath() + File.separator + "config.yml";
		config = YamlConfiguration.loadConfiguration(new File(configPath));
		outConfig = new YamlConfiguration();

		
	    //Save the files
		loadConfig();
	    FileManager.saveDefaultPlayers();
	    
	    world = Bukkit.getWorld(config_world);
	    //make sure a valid world is loaded
	    if(world == null)
	    {
			this.logger.info(pdfFile.getName() + " The world specified in RandomTP/config does not exist");
			this.logger.info(pdfFile.getName() + " Will not check for first time joiners to teleport");
			gotWorld = false;
	    }
	    else
    	{
	    	//it hasn't been configured yet
	    	if(config_startX == 0 && config_startY == 0 && config_startZ == 0)
	    		setSpawn(world.getSpawnLocation());
	    	else
	    		spawn = new Location(world, config_startX, config_startY, config_startZ);
    	}    
	}
	
	
	
	/*
	 * If the player is joining for the first time, randomly teleport them somewhere
	 */
	@EventHandler
	public void onJoin(PlayerJoinEvent e)
	{
		if(!gotWorld) return;
		Player p = e.getPlayer();
		doCheck(p);
	}
	@EventHandler
	public void onChangeWorld(PlayerChangedWorldEvent e)
	{
		//make sure we have a world
		if(!gotWorld) return;
		Player p = e.getPlayer();
		doCheck(p);

	}
	
	//code to run to check if should random tp
	private void doCheck(final Player p)
	{
		final World w = p.getWorld();
		String id = p.getUniqueId().toString();
				
		//see if we care about this world
		if (world.equals(w))
		{
			//check to see if the player has joined this world before
			if (!FileManager.getPlayers().contains(w.getName()+"."+id)) 
			{
				//schedule the teleport to launch after ~a second. This way if they are joining for the first time
				//they are foreced to spawn THEN teleported.
				Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
				{public void run()
					{
						//make sure they are still in the right world. can happen if the world players
						//spawn in is not the same as the world in server.properties, and players can be TP'd
						//without ever actually joining that first world
						if(!p.getWorld().equals(world))
							return;
						doTeleport(p,w);
					}
				}
				, 20L);
			}
		}
	}
	
	/*
	 * Helper function to actually do the teleport. YAY refactoring
	 */
	private void doTeleport(Player p, World w)
	{
		//just to make sure we don't teleport them somewhere bad, ie over lava...
		boolean successful = false;
		int count = 0;
		while(!successful & count < SPINNER)
		{
			successful = randomTeleport(p, w);
			count++; //just to make sure we don't spin indefinitely
		}
		
		//let people know what did/n't happen
		if(successful)
		{
			this.logger.info("[RandomTP] Successfully teleported "+p.getName());
			p.sendMessage(ChatColor.BLUE + "[RandomTP]"+ChatColor.GREEN+" You have been randomly teleported!");
			String id = p.getUniqueId().toString();
			
			//Save the information
			
			//    world:
			//      UUID: name
			//        x:
			//        y:
			//        z:
			FileManager.getPlayers().set(w.getName()+"."+id+".name", p.getName());
			FileManager.getPlayers().set(w.getName()+"."+id+".x", p.getLocation().getX());
			FileManager.getPlayers().set(w.getName()+"."+id+".y", p.getLocation().getY());
			FileManager.getPlayers().set(w.getName()+"."+id+".z", p.getLocation().getZ());
			FileManager.savePlayers();
			
			//set this as their bed spawn location (for other plugins without having to hook)
			if(config_setBed)
				p.setBedSpawnLocation(p.getLocation(), true);
			//for servers with /sethome
			if(config_setHome)
				p.performCommand("sethome");
		}
		else
		{
			this.logger.info("[RandomTP] Failed to teleport "+p.getName());
			p.sendMessage(ChatColor.BLUE + "[RandomTP]"+ChatColor.RED+" Random teleportation failed!");
		}
	}
	
	/*
	 * Teleports the player somewhere random in the world, checking for claims
	 */
	private boolean randomTeleport(Player player, World world)
	{
		//just as sanity check, in case for some reason my logic breaks
		if(spawn == null)
			spawn = player.getLocation();
		Location possible = spawn;
		
		//how far away the can be teleported to
		int border = config_border;
		if(border == -1)
			border = MAX_BORDER;
		
		boolean foundSpot = false;
		//keep spinning until we find an unclaimed place to teleport them too
		int spawnX = (int)spawn.getX();
		int spawnZ = (int)spawn.getZ();
		int count = 0;
		while(!foundSpot & count < SPINNER)
		{
			int x = getRandomCoord(spawnX - border + 10, spawnX + border - 10);
			int z = getRandomCoord(spawnZ - border + 10, spawnZ + border - 10);
			int y = world.getHighestBlockYAt(x, z);
			possible = new Location(world, x,y,z);
			foundSpot = checkLocation(possible);
			count++; //make sure that it stops at some point...
		}
		if(foundSpot) //if we found a spot and didn't run out of spinning
		{
			world.loadChunk(possible.getChunk()); //load the chunk so that the player doesn't die in the void.
			return player.teleport(possible);
		}
		return false;
	}
	
	/*
	 * checks whether or not this is unclaimed
	 */
	private boolean checkLocation(Location l)
	{
		Block b = l.getBlock();
		if(b.getType().equals(Material.WATER) && (b.getBiome().equals(Biome.OCEAN) || b.getBiome().equals(Biome.DEEP_OCEAN) )) return false;
		if(!hasGP || !config_checkClaims) return true;
		
		Claim claim = GriefPrevention.instance.dataStore.getClaimAt(l, true, null);
		
		//if we've landed in a claim, that's bad
		if(claim != null) return false;
		
		//else, see if there's a claim nearby
		for(int x = -10; x <= 10; x++)
			for(int z = -10; z <= 10; z++)
			{
				if(GriefPrevention.instance.dataStore.getClaimAt(new Location(l.getWorld(), (int)l.getX()+ x, 65, (int)l.getZ()+ z),true, null) != null)
					return false;
			}
		
		//otherwise there aren't any claims nearby, and this is a valid location
		return true;		
	}
	
	/*
	 * Generates a random location
	 */
	private int getRandomCoord(int min, int max)
	{		
		return gen.nextInt((max - min)+1) + min;		
	}
	
	/*
	 * To manually teleport someone randomly
	 */
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{
		//tprreload
		if(cmd.getName().equalsIgnoreCase("tprreload"))
		{
			//if is player, and doesn't have permission
			if((sender instanceof Player) && !(sender.hasPermission("randomtp.reload")))
					sender.sendMessage(ChatColor.DARK_RED+"No permission.");
			else
			{
				FileManager.reloadPlayers();
				loadConfig();
			}
		}
		
		//TPRB
		else if(cmd.getName().equalsIgnoreCase("tprb"))
		{
			if (!(sender instanceof Player))
				sender.sendMessage("This command can only be run by a player.");
			else 
			{
				Player p = (Player) sender;
				if(!(p.hasPermission("randomtp.back")))
					p.sendMessage(ChatColor.DARK_RED+"You do not have permission to use this command!");
				else
				{
					String id = p.getUniqueId().toString();
					//make sure they have one
					if (FileManager.getPlayers().contains(world.getName()+"."+id+".x")) 
					{
						//get the stored location
						World w = world;
						double x = FileManager.getPlayers().getDouble(w.getName()+"."+id+".x");
						double y = FileManager.getPlayers().getDouble(w.getName()+"."+id+".y");
						double z = FileManager.getPlayers().getDouble(w.getName()+"."+id+".z");
						
						Location back = new Location(w, x, y, z);
						p.teleport(back);
						p.sendMessage(ChatColor.GOLD+"You've been sent back");
					}
					else
						p.sendMessage(ChatColor.RED+"No origin available");
				}
			}
			return true;
		}
		
		//TPR
		else if(cmd.getName().equalsIgnoreCase("tpr"))
		{
			boolean isPlayer = sender instanceof Player;
			switch (args.length)
			{
				case 0:
					if(isPlayer)
						tpr((Player) sender, null);
					else //console
						sender.sendMessage("This command can only be run by a player.");
					break;
				case 1:
					Player t = Bukkit.getPlayer(args[0]);
					if(t == null)
						sender.sendMessage(ChatColor.BLUE+"[RandomTP]"+ChatColor.RED+"Player not found.");
					else 
						if(isPlayer)
							tpr((Player) sender, t);
						else //console, no need to check for permission
							doTeleport(t, t.getWorld());
					break;
				default: //greater than 1
					sender.sendMessage(ChatColor.BLUE+"[RandomTP]"+ChatColor.RED+"Wrong usage. Try /tpr <player>");
					break;					
			}
			return true;
		}
		//TPRS
		else if(cmd.getName().equalsIgnoreCase("tprs"))
		{
			if (!(sender instanceof Player))
				sender.sendMessage("This command can only be run by a player.");
			else 
			{
				Player p = (Player) sender;
				if(!(p.hasPermission("randomtp.admin")))
					p.sendMessage(ChatColor.DARK_RED+"You do not have permission to use this command!");
				else
				{
					if(p.getWorld().equals(world))
					{
						setSpawn(p.getLocation());
						p.sendMessage(ChatColor.BLUE + "[RandomTP]"+ChatColor.GREEN+" Starting point set!");
					}
					else
						p.sendMessage(ChatColor.BLUE + "[RandomTP]"+ChatColor.RED+" Wrong world!");

				}
			}
			return true;
		}			
		return false;
	}
	
	/*
	 * tpr command
	 */
	private void tpr(Player p, Player target)
	{
		//no target, therefore just check player
		if(target == null)
		{
			if(!(p.hasPermission("randomtp.player")))
				p.sendMessage(ChatColor.DARK_RED+"You do not have permission to use this command!");
			else
				doTeleport(p, p.getWorld());
		}
		else
		{
			if(!(p.hasPermission("randomtp.admin")))
				p.sendMessage(ChatColor.DARK_RED+"You do not have permission to use this command!");
			else
				doTeleport(target, target.getWorld());
		}
	}
	
	/*
	 * tprspawn command
	 */
	private void setSpawn(Location l)
	{	
		outConfig.set("start.x", l.getBlockX());
		outConfig.set("start.y", l.getBlockY());
		outConfig.set("start.z", l.getBlockZ());
		spawn = l;
		save();
	}
	
	public static RandomTP getInstance()
	{
		return plugin;
	}
	
	private void save()
	{
        try
        {
            outConfig.save(configPath);
        }
        catch(IOException exception)
        {
            logger.info("Unable to write to the configuration file at \"" + configPath + "\"");
        }		
	}
}
