package net.Indyuce.bountyhunters.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.Indyuce.bountyhunters.BountyHunters;
import net.Indyuce.bountyhunters.api.Bounty;
import net.Indyuce.bountyhunters.api.BountyCause;
import net.Indyuce.bountyhunters.api.BountyManager;
import net.Indyuce.bountyhunters.api.Message;
import net.Indyuce.bountyhunters.api.event.BountyChangeEvent;
import net.Indyuce.bountyhunters.api.event.BountyCreateEvent;
import net.Indyuce.bountyhunters.listener.Alerts;
import net.Indyuce.bountyhunters.util.Utils;

public class AddBountyCommand implements CommandExecutor {
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!sender.hasPermission("bountyhunters.add")) {
			Message.NOT_ENOUGH_PERMS.format(ChatColor.RED).send(sender);
			return true;
		}
		if (args.length < 2) {
			Message.COMMAND_USAGE.format(ChatColor.RED, "%command%", "/bounty <player> <reward>").send(sender);
			return true;
		}
		if (sender instanceof Player)
			if (BountyHunters.plugin.getConfig().getStringList("world-blacklist").contains(((Player) sender).getWorld().getName()))
				return true;

		// check for player
		Player t = Bukkit.getPlayer(args[0]);
		if (t == null) {
			Message.ERROR_PLAYER.format(ChatColor.RED, "%arg%", args[0]).send(sender);
			return true;
		}
		if (!t.isOnline()) {
			Message.ERROR_PLAYER.format(ChatColor.RED, "%arg%", args[0]).send(sender);
			return true;
		}
		if (sender instanceof Player)
			if (t.getName().equals(((Player) sender).getName())) {
				Message.CANT_SET_BOUNTY_ON_YOURSELF.format(ChatColor.RED).send(sender);
				return true;
			}

		// permission
		if (t.hasPermission("bountyhunters.immunity") && !sender.hasPermission("bountyhunters.immunity.bypass")) {
			Message.BOUNTY_IMUN.format(ChatColor.RED).send(sender);
			return true;
		}

		// reward
		double reward = 0;
		try {
			reward = Double.parseDouble(args[1]);
		} catch (Exception e) {
			Message.NOT_VALID_NUMBER.format(ChatColor.RED, "%arg%", args[1]).send(sender);
			return true;
		}
		reward = Utils.truncation(reward, 1);

		// min/max check
		double min = BountyHunters.plugin.getConfig().getDouble("min-reward");
		double max = BountyHunters.plugin.getConfig().getDouble("max-reward");
		if ((reward < min) || (max > 0 && reward > max)) {
			Message.WRONG_REWARD.format(ChatColor.RED, "%max%", Utils.format(max), "%min%", Utils.format(min)).send(sender);
			return true;
		}

		// tax calculation
		double tax = reward * BountyHunters.plugin.getConfig().getDouble("tax") / 100;
		tax = Utils.truncation(tax, 1);

		// set restriction
		if (sender instanceof Player) {
			Player p = (Player) sender;
			long restriction = BountyHunters.plugin.getConfig().getInt("bounty-set-restriction") * 1000;
			long last = BountyHunters.plugin.lastBounty.containsKey(p.getUniqueId()) ? BountyHunters.plugin.lastBounty.get(p.getUniqueId()) : 0;
			long left = last + restriction - System.currentTimeMillis();

			if (left > 0) {
				Message.BOUNTY_SET_RESTRICTION.format(ChatColor.RED, "%left%", "" + left / 1000, "%s%", left / 1000 >= 2 ? "s" : "").send(sender);
				return true;
			}
		}

		// money restriction
		if (sender instanceof Player)
			if (!BountyHunters.getEconomy().has((Player) sender, reward)) {
				Message.NOT_ENOUGH_MONEY.format(ChatColor.RED).send(sender);
				return true;
			}

		// bounty can be created
		BountyManager bountyManager = BountyHunters.getBountyManager();
		reward -= tax;

		// add to existing bounty
		if (bountyManager.hasBounty(t)) {

			// API
			Bounty bounty = bountyManager.getBounty(t);
			BountyChangeEvent e = new BountyChangeEvent(bounty);
			Bukkit.getPluginManager().callEvent(e);
			if (e.isCancelled())
				return true;

			// remove balance
			// set last bounty value
			if (sender instanceof Player) {
				BountyHunters.getEconomy().withdrawPlayer((Player) sender, reward + tax);
				BountyHunters.plugin.lastBounty.put(((Player) sender).getUniqueId(), System.currentTimeMillis());
			}

			bounty.addToReward(sender instanceof Player ? (Player) sender : null, reward);
			for (Player ent : Bukkit.getOnlinePlayers())
				Message.BOUNTY_CHANGE.format(ChatColor.YELLOW, "%player%", t.getName(), "%reward%", Utils.format(bounty.getReward())).send(ent);
			return true;
		}

		// API
		Bounty bounty = new Bounty(sender instanceof Player ? (Player) sender : null, t, reward);
		BountyCreateEvent e = new BountyCreateEvent(bounty, sender instanceof Player ? BountyCause.PLAYER : BountyCause.CONSOLE);
		Bukkit.getPluginManager().callEvent(e);
		reward = e.getBounty().getReward();
		if (e.isCancelled())
			return true;

		// remove balance
		// set last bounty value
		if (sender instanceof Player) {
			BountyHunters.getEconomy().withdrawPlayer((Player) sender, reward + tax);
			BountyHunters.plugin.lastBounty.put(((Player) sender).getUniqueId(), System.currentTimeMillis());
		}
		
		bounty.register();
		Alerts.newBounty(e);

		if (tax > 0)
			Message.TAX_EXPLAIN.format(ChatColor.RED, "%percent%", "" + BountyHunters.plugin.getConfig().getDouble("tax"), "%price%", Utils.format(tax)).send(sender);
		return true;
	}
}