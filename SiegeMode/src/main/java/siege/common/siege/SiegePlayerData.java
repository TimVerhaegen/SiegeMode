package siege.common.siege;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3DPacketDisplayScoreboard;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import siege.common.kit.Kit;
import siege.common.kit.KitDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SiegePlayerData
{
	private Siege theSiege;

	private BackupSpawnPoint backupSpawnPoint;
	private UUID currentKit;
	private UUID chosenKit;
	private boolean clearedLimitedKit = false;
	private String nextTeam;

	// -------------------------
	private int currentPlayerLives;
	private boolean defeated = false;
	private int offlineTicks;
	private boolean online;
	// -------------------------

	private int kills;
	private int deaths;
	private int killstreak;
	private int longestKillstreak;
	
	private ScoreObjective lastSentSiegeObjective = null;

	public SiegePlayerData(Siege siege)
	{
		theSiege = siege;
	}
	
	public void writeToNBT(NBTTagCompound nbt)
	{
		if (backupSpawnPoint != null)
		{
			nbt.setInteger("BSP_Dim", backupSpawnPoint.dimension);
			ChunkCoordinates bspCoords = backupSpawnPoint.spawnCoords;
			nbt.setInteger("BSP_X", bspCoords.posX);
			nbt.setInteger("BSP_Y", bspCoords.posY);
			nbt.setInteger("BSP_Z", bspCoords.posZ);
			nbt.setBoolean("BSP_Forced", backupSpawnPoint.spawnForced);
		}
		
		if (currentKit != null)
		{
			nbt.setString("CurrentKit", currentKit.toString());
		}
		
		if (chosenKit != null)
		{
			nbt.setString("Kit", chosenKit.toString());
		}
		
		nbt.setBoolean("ClearedLimitedKit", clearedLimitedKit);
		
		if (nextTeam != null)
		{
			nbt.setString("NextTeam", nextTeam);
		}
		
		nbt.setInteger("Kills", kills);
		nbt.setInteger("Deaths", deaths);
		nbt.setInteger("Killstreak", killstreak);
		nbt.setInteger("LongestKillstreak", longestKillstreak);
	}
	
	public void readFromNBT(NBTTagCompound nbt)
	{
		backupSpawnPoint = null;
		if (nbt.hasKey("BSP_Dim"))
		{
			int bspDim = nbt.getInteger("BSP_Dim");
			int bspX = nbt.getInteger("BSP_X");
			int bspY = nbt.getInteger("BSP_Y");
			int bspZ = nbt.getInteger("BSP_Z");
			boolean bspForced = nbt.getBoolean("BSP_Forced");
			ChunkCoordinates bspCoords = new ChunkCoordinates(bspX, bspY, bspZ);
			backupSpawnPoint = new BackupSpawnPoint(bspDim, bspCoords, bspForced);
		}
		
		if (nbt.hasKey("CurrentKit"))
		{
			currentKit = UUID.fromString(nbt.getString("CurrentKit"));
		}
		
		if (nbt.hasKey("Kit"))
		{
			chosenKit = UUID.fromString(nbt.getString("Kit"));
		}
		
		clearedLimitedKit = nbt.getBoolean("ClearedLimitedKit");
		
		nextTeam = nbt.getString("NextTeam");
		
		kills = nbt.getInteger("Kills");
		deaths = nbt.getInteger("Deaths");
		killstreak = nbt.getInteger("Killstreak");
		longestKillstreak = nbt.getInteger("LongestKillstreak");
	}

	public BackupSpawnPoint getBackupSpawnPoint()
	{
		return backupSpawnPoint;
	}
	
	public void setBackupSpawnPoint(BackupSpawnPoint bsp)
	{
		backupSpawnPoint = bsp;
		theSiege.markDirty();
	}
	
	public UUID getCurrentKit()
	{
		return currentKit;
	}

	public void setCurrentKit(Kit kit)
	{
		currentKit = kit == null ? null : kit.getKitID();
		theSiege.markDirty();
	}
	
	public UUID getChosenKit()
	{
		return chosenKit;
	}

	public void setChosenKit(Kit kit)
	{
		chosenKit = kit == null ? null : kit.getKitID();
		theSiege.markDirty();
	}
	
	public void setRandomChosenKit()
	{
		setChosenKit(null);
	}
	
	public String getNextTeam()
	{
		return nextTeam;
	}

	public void setNextTeam(String team)
	{
		nextTeam = team;
		theSiege.markDirty();
	}
	
	public int getKills()
	{
		return kills;
	}
	
	public void onKill()
	{
		kills++;
		killstreak++;
		if (killstreak > longestKillstreak)
		{
			longestKillstreak = killstreak;
		}
		theSiege.markDirty();
	}
	
	public int getDeaths()
	{
		return deaths;
	}
	
	public boolean onDeath()
	{
		deaths++;
		killstreak = 0;
		theSiege.markDirty();

		// -------------------------
		if (theSiege.getSiegeType() == SiegeType.PlayerAttempts && currentPlayerLives == 0)
			return false;

		currentPlayerLives--;
		return true;
		// -------------------------

	}
	
	public int getKillstreak()
	{
		return killstreak;
	}
	
	public int getLongestKillstreak()
	{
		return longestKillstreak;
	}
	
	public void onTeamChange()
	{
		kills = 0;
		deaths = 0;
		killstreak = 0;
		longestKillstreak = 0;
		theSiege.markDirty();
	}

	public void onLogin(EntityPlayerMP entityplayer)
	{
		online = true;
		offlineTicks = 0;
		if (clearedLimitedKit)
		{
			clearedLimitedKit = false;
			theSiege.messagePlayer(entityplayer, "Your limited kit was deselected on logout so others may use it!");
			theSiege.messagePlayer(entityplayer, "Switching to random kit selection after death");
			theSiege.markDirty();
		}
	}
	
	public void onLogout(EntityPlayerMP entityplayer)
	{

		lastSentSiegeObjective = null;
		
		SiegeTeam team = theSiege.getPlayerTeam(entityplayer);
		if (team != null)
		{
			Kit kit = KitDatabase.getKit(chosenKit);
			if (kit != null && team.isKitLimited(kit))
			{
				clearedLimitedKit = true;
				setRandomChosenKit();
				theSiege.markDirty();
			}
		}
	}
	
	public void updateSiegeScoreboard(EntityPlayerMP entityplayer, boolean forceClear)
	{
		World world = entityplayer.worldObj;
		SiegeTeam team = theSiege.getPlayerTeam(entityplayer);

		Scoreboard scoreboard = world.getScoreboard();
		ScoreObjective siegeObjective = null;
		
		// TODO: change this to account for when the siege ends: remove scoreboards / start a timer etc.
		boolean inSiege = team != null;
		if (inSiege && !forceClear)
		{
			// create a new siege objective, with a new name, so we can send all the scores one by one, and only then display it
			String newObjName = "siege" + Siege.siegeObjectiveNumber;
			Siege.siegeObjectiveNumber++;
			siegeObjective = new ScoreObjective(scoreboard, newObjName, null);
			String displayName = "SiegeMode: " + theSiege.getSiegeName();
			siegeObjective.setDisplayName(displayName);
			
			String kitName = "";
			Kit currentKit = KitDatabase.getKit(getCurrentKit());
			if (currentKit != null)
			{
				kitName = currentKit.getKitName();
			}
			
			String timeRemaining = theSiege.isActive() ? ("Time: " + Siege.ticksToTimeString(theSiege.getTicksRemaining())) : "Ended";
			
			// clever trick to control the ordering of the objectives: put actual scores in the 'playernames', and put the desired order in the 'scores'!
			
			List<Score> allSiegeStats = new ArrayList();
			allSiegeStats.add(new Score(scoreboard, siegeObjective, timeRemaining));
			allSiegeStats.add(null);

			// -------------------------
			SiegeType t = theSiege.getSiegeType();
			allSiegeStats.add(new Score(scoreboard, siegeObjective, "Type: " + convertSiegeTypeReadable()));
			if(t == SiegeType.PlayerAttempts || t == SiegeType.TeamAttempts) {
				allSiegeStats.add(new Score(scoreboard, siegeObjective, "Max enter: " + Siege.ticksToTimeString(theSiege.getMaxEnterTime())));
				if (t == SiegeType.PlayerAttempts)
					allSiegeStats.add(new Score(scoreboard, siegeObjective, "Lives: " + currentPlayerLives));
				else
					allSiegeStats.add(new Score(scoreboard, siegeObjective, "Lives: " + team.getCurrentTeamLives()));
			}
			// -------------------------

			allSiegeStats.add(null);
			allSiegeStats.add(new Score(scoreboard, siegeObjective, "Team: " + team.getTeamName()));
			allSiegeStats.add(new Score(scoreboard, siegeObjective, "Kit: " + kitName));
			allSiegeStats.add(null);
			allSiegeStats.add(new Score(scoreboard, siegeObjective, "Kills: " + getKills()));
			allSiegeStats.add(new Score(scoreboard, siegeObjective, "Deaths: " + getDeaths()));
			allSiegeStats.add(new Score(scoreboard, siegeObjective, "Killstreak: " + getKillstreak()));
			allSiegeStats.add(null);
			allSiegeStats.add(new Score(scoreboard, siegeObjective, "Team K: " + team.getTeamKills()));
			allSiegeStats.add(new Score(scoreboard, siegeObjective, "Team D: " + team.getTeamDeaths()));
			
			// recreate the siege objective (or create for first time if not sent before)
			Packet pktObjective = new S3BPacketScoreboardObjective(siegeObjective, 0);
			entityplayer.playerNetServerHandler.sendPacket(pktObjective);
			
			int index = allSiegeStats.size();
			int gaps = 0;
			for (Score score : allSiegeStats)
			{
				if (score == null)
				{
					// create a unique gap string, based on how many gaps we've already had
					String gapString = "";
					for (int l = 0; l <= gaps; l++)
					{
						gapString += "-";
					}
					score = new Score(scoreboard, siegeObjective, gapString);
					gaps++;
				}
				
				// avoid string too long in packet
				String scoreName = score.getPlayerName();
				int maxLength = 16;
				if (scoreName.length() > maxLength)
				{
					scoreName = scoreName.substring(0, Math.min(scoreName.length(), maxLength));
				}
				score = new Score(score.getScoreScoreboard(), score.func_96645_d(), scoreName);
				
				score.setScorePoints(index);
				Packet pktScore = new S3CPacketUpdateScore(score, 0);
				entityplayer.playerNetServerHandler.sendPacket(pktScore);
				index--;
			}
		}
		
		// try disabling this to avoid the rare crash when the last objective has failed to send and it tries to remove a nonexistent objective
		// remove last objective only AFTER sending new objective & all scores
		/*if (lastSentSiegeObjective != null)
		{
			Packet pkt = new S3BPacketScoreboardObjective(lastSentSiegeObjective, 1);
			entityplayer.playerNetServerHandler.sendPacket(pkt);
			lastSentSiegeObjective = null;
		}*/
		
		// if a new objective was sent, display it
		if (siegeObjective != null)
		{
			Packet pktDisplay = new S3DPacketDisplayScoreboard(1, siegeObjective);
			entityplayer.playerNetServerHandler.sendPacket(pktDisplay);
			lastSentSiegeObjective = siegeObjective;
		}
	}

	// -------------------------
	public void setCurrentPlayerLives(int i) {
		currentPlayerLives = i;
	}

	public int getCurrentPlayerLives() {
		return currentPlayerLives;
	}

	public boolean decrementCurrentLives() {
		if(currentPlayerLives < 1)
			return false;

		currentPlayerLives--;
		return true;
	}

	public void setDefeated(boolean b) {
		this.defeated = b;
	}

	public boolean isDefeated() {
		return defeated;
	}

	public void incrementOfflineTicks() {
		offlineTicks++;
	}

	private String convertSiegeTypeReadable() {
		switch(theSiege.getSiegeType()) {
			case PlayerAttempts:
				return "Lim. player lives";
			case TeamAttempts:
				return "Lim. team lives";
			case Regular:
				return "Regular";
		}
		return "";
	}
	// -------------------------
}
