package siege.common.siege;

import cpw.mods.fml.common.event.FMLInterModComms;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.common.UsernameCache;
import net.minecraftforge.common.util.Constants;
import siege.common.SiegeMode;
import siege.common.kit.Kit;
import siege.common.kit.KitDatabase;

import java.util.*;
import java.util.Map.Entry;

public class Siege {
    private boolean needsSave = false;
    private boolean deleted = false;

    private UUID siegeID;
    private String siegeName;

    // -------------------------
    private SiegeType siegeType = SiegeType.Regular;
    private int maxPlayerLives = 5;
    private int maxEnterTime;
    private int initialDuration;
    private int maxTimeOffline;
    private List<SiegePlayerData> toClear = new ArrayList<SiegePlayerData>();
    private Map<UUID, BackupSpawnPoint> previousSpawnLocations = new HashMap<UUID, BackupSpawnPoint>();
    // -------------------------

    private boolean isLocationSet = false;
    private int dimension;
    private int xPos;
    private int zPos;
    private int radius;
    public static final int MAX_RADIUS = 2000;
    private static final double EDGE_PUT_RANGE = 2D;

    private int ticksRemaining = 0;
    private static final int SCORE_INTERVAL = 30 * 20;
    private boolean announceActive = true;
    private static final int ANNOUNCE_ACTIVE_INTERVAL = 60 * 20;

    private List<SiegeTeam> siegeTeams = new ArrayList();
    private int maxTeamDifference = 3;
    private boolean friendlyFire = false;
    private boolean mobSpawning = false;
    private boolean terrainProtect = true;
    private boolean terrainProtectInactive = false;
    private boolean dispelOnEnd = false;

    private Map<UUID, SiegePlayerData> playerDataMap = new HashMap();
    private static final int KILLSTREAK_ANNOUNCE = 3;
    private int respawnImmunity = 5;

    // required to ensure each sent scoreboard objective is a unique objective
    public static int siegeObjectiveNumber = 0;

    public Siege(String s) {
        siegeID = UUID.randomUUID();
        siegeName = s;
    }

    public UUID getSiegeID() {
        return siegeID;
    }

    public String getSiegeName() {
        return siegeName;
    }

    public void rename(String s) {
        String oldName = siegeName;
        siegeName = s;
        markDirty();
        SiegeDatabase.renameSiege(this, oldName);
    }

    public void setCoords(int dim, int x, int z, int r) {
        dimension = dim;
        xPos = x;
        zPos = z;
        radius = r;
        isLocationSet = true;
        markDirty();
    }

    public boolean isLocationInSiege(double x, double y, double z) {
        double dx = x - (xPos + 0.5D);
        double dz = z - (zPos + 0.5D);
        double dSq = dx * dx + dz * dz;
        return dSq <= (double) radius * (double) radius;
    }

    public SiegeTeam getTeam(String teamName) {
        for (SiegeTeam team : siegeTeams) {
            if (team.getTeamName().equals(teamName)) {
                return team;
            }
        }
        return null;
    }

    public void createNewTeam(String teamName) {
        SiegeTeam team = new SiegeTeam(this, teamName);
        siegeTeams.add(team);
        markDirty();
    }

    public boolean removeTeam(String teamName) {
        SiegeTeam team = getTeam(teamName);
        if (team != null) {
            siegeTeams.remove(team);
            team.remove();
            markDirty();
            return true;
        }
        return false;
    }

    public List<String> listTeamNames() {
        List<String> names = new ArrayList();
        for (SiegeTeam team : siegeTeams) {
            names.add(team.getTeamName());
        }
        return names;
    }

    public int getSmallestTeamSize() {
        boolean flag = false;
        int smallestSize = -1;
        for (SiegeTeam team : siegeTeams) {
            int size = team.onlinePlayerCount();
            if (!flag || size < smallestSize) {
                smallestSize = size;
            }
            flag = true;
        }
        return smallestSize;
    }

    public boolean hasPlayer(EntityPlayer entityplayer) {
        return getPlayerTeam(entityplayer) != null;
    }

    public SiegeTeam getPlayerTeam(EntityPlayer entityplayer) {
        return getPlayerTeam(entityplayer.getUniqueID());
    }

    public SiegeTeam getPlayerTeam(UUID playerID) {
        for (SiegeTeam team : siegeTeams) {
            if (team.containsPlayer(playerID)) {
                return team;
            }
        }
        return null;
    }

    public SiegePlayerData getPlayerData(EntityPlayer entityplayer) {
        return getPlayerData(entityplayer.getUniqueID());
    }

    public SiegePlayerData getPlayerData(UUID player) {
        SiegePlayerData data = playerDataMap.get(player);
        if (data == null) {
            data = new SiegePlayerData(this);

            data.setCurrentPlayerLives(maxPlayerLives);

            playerDataMap.put(player, data);
        }
        return data;
    }

    public List<String> listAllPlayerNames() {
        List<String> names = new ArrayList();
        List playerList = MinecraftServer.getServer().getConfigurationManager().playerEntityList;
        for (Object player : playerList) {
            EntityPlayer entityplayer = (EntityPlayer) player;
            if (hasPlayer(entityplayer)) {
                names.add(entityplayer.getCommandSenderName());
            }
        }
        return names;
    }

    public int getMaxTeamDifference() {
        return maxTeamDifference;
    }

    public void setMaxTeamDifference(int d) {
        maxTeamDifference = d;
        markDirty();
    }

    public int getRespawnImmunity() {
        return respawnImmunity;
    }

    public void setRespawnImmunity(int seconds) {
        respawnImmunity = seconds;
        markDirty();
    }

    public boolean getFriendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean flag) {
        friendlyFire = flag;
        markDirty();
    }

    public boolean getMobSpawning() {
        return mobSpawning;
    }

    public void setMobSpawning(boolean flag) {
        mobSpawning = flag;
        markDirty();
    }

    public boolean getTerrainProtect() {
        return terrainProtect;
    }

    public void setTerrainProtect(boolean flag) {
        terrainProtect = flag;
        markDirty();
    }

    public boolean getTerrainProtectInactive() {
        return terrainProtectInactive;
    }

    public void setTerrainProtectInactive(boolean flag) {
        terrainProtectInactive = flag;
        markDirty();
    }

    public boolean getDispelEnd() {
        return dispelOnEnd;
    }

    public void setDispelOnEnd(boolean flag) {
        dispelOnEnd = flag;
        markDirty();
    }

    public boolean isSiegeWorld(World world) {
        return world.provider.dimensionId == dimension;
    }

    public boolean canBeStarted() {
        return isLocationSet && !siegeTeams.isEmpty();
    }

    public void startSiege(int duration) {
        playerDataMap.clear();
        toClear.clear();
        previousSpawnLocations.clear();

        for (SiegeTeam team : siegeTeams) {
            team.clearPlayers();

            // -------------------------
            team.setCurrentTeamLives(team.getMaxTeamLives());
            team.resetSpectators();
            // -------------------------
        }

        initialDuration = duration;
        ticksRemaining = duration;
        markDirty();

        announceActiveSiege();
    }

    public void extendSiege(int duration) {
        if (isActive()) {
            ticksRemaining += duration;
            markDirty();
        }
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public static String ticksToTimeString(int ticks) {
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        seconds %= 60;

        String sSeconds = String.valueOf(seconds);
        if (sSeconds.length() < 2) {
            sSeconds = "0" + sSeconds;
        }

        String sMinutes = String.valueOf(minutes);

        String timeDisplay = sMinutes + ":" + sSeconds;
        return timeDisplay;
    }

    public void endSiege() {
        ticksRemaining = 0;

        messageAllSiegePlayers("The siege has ended!");

        List<SiegeTeam> winningTeams = new ArrayList();
        int winningScore = -1;
        for (SiegeTeam team : siegeTeams) {
            int score = team.getTeamKills();
            if (score > winningScore) {
                winningScore = score;
                winningTeams.clear();
                winningTeams.add(team);
            } else if (score == winningScore) {
                winningTeams.add(team);
            }
        }
        String winningTeamName = "";
        if (!winningTeams.isEmpty()) {
            if (winningTeams.size() == 1) {
                SiegeTeam team = winningTeams.get(0);
                winningTeamName = team.getTeamName();
            } else {
                for (SiegeTeam team : winningTeams) {
                    if (!winningTeamName.isEmpty()) {
                        winningTeamName += ", ";
                    }
                    winningTeamName += team.getTeamName();
                }
            }
        }

        if (winningTeams.size() == 1) {
            messageAllSiegePlayers("Team " + winningTeamName + " won with " + winningScore + " kills!");
        } else {
            messageAllSiegePlayers("Teams " + winningTeamName + " tied with " + winningScore + " kills each!");
        }

        messageAllSiegePlayers("---");
        for (SiegeTeam team : siegeTeams) {
            String teamMsg = team.getSiegeEndMessage();
            messageAllSiegePlayers(teamMsg);
        }
        messageAllSiegePlayers("---");

        UUID mvpID = null;
        int mvpKills = 0;
        int mvpDeaths = 0;
        int mvpScore = Integer.MIN_VALUE;
        UUID longestKillstreakID = null;
        int longestKillstreak = 0;
        for (SiegeTeam team : siegeTeams) {
            List<UUID> participants = team.getPlayerList();
            participants.addAll(team.getSpectators());

            for (UUID player : participants) {
                SiegePlayerData playerData = getPlayerData(player);

                int kills = playerData.getKills();
                int deaths = playerData.getDeaths();
                int score = kills - deaths;
                if (score > mvpScore || (score == mvpScore && deaths < mvpDeaths)) {
                    mvpID = player;
                    mvpKills = kills;
                    mvpDeaths = deaths;
                    mvpScore = score;
                }

                int streak = playerData.getLongestKillstreak();
                if (streak > longestKillstreak) {
                    longestKillstreakID = player;
                    longestKillstreak = streak;
                }
            }
        }
        if (mvpID != null) {
            String mvp = UsernameCache.getLastKnownUsername(mvpID);
            messageAllSiegePlayers("MVP was " + mvp + " (" + getPlayerTeam(mvpID).getTeamName() + ") with " + mvpKills + " kills / " + mvpDeaths + " deaths");
        }
        if (longestKillstreakID != null) {
            String streakPlayer = UsernameCache.getLastKnownUsername(longestKillstreakID);
            messageAllSiegePlayers("Longest killstreak was " + streakPlayer + " (" + getPlayerTeam(longestKillstreakID).getTeamName() + ") with a killstreak of " + longestKillstreak);
        }
        messageAllSiegePlayers("---");

        messageAllSiegePlayers("Congratulations to " + winningTeamName + ", and well played by all!");

        List playerList = MinecraftServer.getServer().getConfigurationManager().playerEntityList;
        for (Object player : playerList) {
            EntityPlayerMP entityplayer = (EntityPlayerMP) player;
            if (hasPlayer(entityplayer)) {
                leavePlayer(entityplayer, false);
            }
        }
        playerDataMap.clear();

        for (SiegeTeam team : siegeTeams) {
            team.onSiegeEnd();
        }

        markDirty();
    }

    public boolean isActive() {
        return ticksRemaining > 0;
    }

    public void updateSiege(World world) {
        if (isActive()) {
            ticksRemaining--;
            if (MinecraftServer.getServer().getTickCounter() % 100 == 0) {
                markDirty();
            }

            for (SiegePlayerData data : playerDataMap.values())
                if (!data.onUpdate())
                    toClear.add(data);

            if (ticksRemaining <= 0) {
                endSiege();
            } else {
                if (announceActive && ticksRemaining % ANNOUNCE_ACTIVE_INTERVAL == 0) {
                    announceActiveSiege();
                }

                List playerList = world.playerEntities;
                for (Object player : playerList) {
                    EntityPlayerMP entityplayer = (EntityPlayerMP) player;
                    boolean inSiege = hasPlayer(entityplayer);
                    updatePlayer(entityplayer, inSiege);
                }

                if (ticksRemaining % SCORE_INTERVAL == 0) {
                    List<SiegeTeam> teamsSorted = new ArrayList();
                    teamsSorted.addAll(siegeTeams);
                    Collections.sort(teamsSorted, new Comparator<SiegeTeam>() {
                        @Override
                        public int compare(SiegeTeam team1, SiegeTeam team2) {
                            int score1 = team1.getTeamKills();
                            int score2 = team2.getTeamKills();
                            if (score1 > score2) {
                                return -1;
                            } else if (score1 < score2) {
                                return 1;
                            } else {
                                return team1.getTeamName().compareTo(team2.getTeamName());
                            }
                        }
                    });

                    for (SiegeTeam team : teamsSorted) {
                        messageAllSiegePlayers(team.getSiegeOngoingScore());
                    }
                }
            }
        }
    }

    public boolean isPlayerInDimension(EntityPlayer entityplayer) {
        return entityplayer.dimension == dimension;
    }

    public boolean joinPlayer(EntityPlayer entityplayer, SiegeTeam team, Kit kit) {
        boolean hasAnyItems = false;
        checkForItems:
        for (int i = 0; i < entityplayer.inventory.getSizeInventory(); i++) {
            ItemStack itemstack = entityplayer.inventory.getStackInSlot(i);
            if (itemstack != null) {
                hasAnyItems = true;
                break checkForItems;
            }
        }

        if (hasAnyItems) {
            messagePlayer(entityplayer, "Your inventory must be clear before joining the siege!");
            messagePlayer(entityplayer, "Put your items somewhere safe");
            return false;
        } else {
            SiegePlayerData playerData = getPlayerData(entityplayer);

            // -------------------------
            if (siegeType == SiegeType.PlayerAttempts) {
                if (!playerData.decrementCurrentLives()) {
                    messagePlayer(entityplayer, "You do not have any lives left...");
                    return false;
                }
            }
            if (siegeType == SiegeType.TeamAttempts) {
                if (!team.decrementCurrentLives()) {
                    messagePlayer(entityplayer, "This team doesn't have any lives left...");
                    return false;
                }
            }
            if (maxEnterTime < initialDuration - ticksRemaining) {
                messagePlayer(entityplayer, "You're too late to join this siege.");
                return false;
            }
            // -------------------------

            team.joinPlayer(entityplayer);

            ChunkCoordinates teamSpawn = team.getRespawnPoint();

            int dim = entityplayer.dimension;
            ChunkCoordinates coords = entityplayer.getPlayerCoordinates();
            boolean forced = entityplayer.isSpawnForced(dim);

            previousSpawnLocations.put(entityplayer.getUniqueID(), new BackupSpawnPoint(dim, coords, forced));

            entityplayer.setPositionAndUpdate(teamSpawn.posX + 0.5D, teamSpawn.posY, teamSpawn.posZ + 0.5D);

            if (kit != null && team.isKitAvailable(kit)) {
                playerData.setChosenKit(kit);
            }
            applyPlayerKit(entityplayer);

            return true;
        }
    }

    public void leavePlayer(EntityPlayerMP entityplayer, boolean forceClearScores) {
        // TODO: implement a timer or something; for now scores stay until they relog, better than immediately disappearing
        getPlayerData(entityplayer).updateSiegeScoreboard(entityplayer, forceClearScores);

        SiegeTeam team = getPlayerTeam(entityplayer);
        team.leavePlayer(entityplayer);

        if(!hasSpectator(entityplayer))
            team.setCurrentTeamLives(team.getCurrentTeamLives() + 1);

        restoreAndClearBackupSpawnPoint(entityplayer);
        Kit.clearPlayerInvAndKit(entityplayer);

        UUID playerID = entityplayer.getUniqueID();

        if (dispelOnEnd) {
            dispel(entityplayer);
        }
    }

    public static void messagePlayer(EntityPlayer entityplayer, String text) {
        messagePlayer(entityplayer, text, EnumChatFormatting.RED);
    }

    public static void messagePlayer(EntityPlayer entityplayer, String text, EnumChatFormatting color) {
        IChatComponent message = new ChatComponentText(text);
        message.getChatStyle().setColor(color);
        entityplayer.addChatMessage(message);
    }

    public void messageAllSiegePlayers(String text) {
        messageAllPlayers(text, EnumChatFormatting.RED, true);
    }

    private void messageAllPlayers(String text, EnumChatFormatting color, boolean onlyInSiege) {
        List playerList = MinecraftServer.getServer().getConfigurationManager().playerEntityList;
        for (Object player : playerList) {
            EntityPlayer entityplayer = (EntityPlayer) player;
            if (!onlyInSiege || (hasPlayer(entityplayer) || hasSpectator(entityplayer))) {
                messagePlayer(entityplayer, text, color);
            }
        }
    }

    private void announceActiveSiege() {
        String name = getSiegeName();
        String joinMsg = "To join the active siege " + name + ", put your items somewhere safe, then do /siege_play join " + name + " [use TAB key to choose team and kit]";
        messageAllPlayers(joinMsg, EnumChatFormatting.GOLD, false);
    }

    private void updatePlayer(EntityPlayerMP entityplayer, boolean inSiege) {
        World world = entityplayer.worldObj;
        SiegePlayerData playerData = getPlayerData(entityplayer);
        SiegeTeam team = getPlayerTeam(entityplayer);

        if (!entityplayer.capabilities.isCreativeMode) {
            boolean inSiegeRange = isLocationInSiege(entityplayer.posX, entityplayer.posY, entityplayer.posZ);
            double dx = entityplayer.posX - (xPos + 0.5D);
            double dz = entityplayer.posZ - (zPos + 0.5D);
            float angle = (float) Math.atan2(dz, dx);

            if (inSiege) {
                playerData.incrementOfflineTicks();

                if (!inSiegeRange) {
                    double putRange = radius - EDGE_PUT_RANGE;
                    int newX = xPos + MathHelper.floor_double(putRange * MathHelper.cos(angle));
                    int newZ = zPos + MathHelper.floor_double(putRange * MathHelper.sin(angle));
                    int newY = world.getTopSolidOrLiquidBlock(newX, newZ);
                    entityplayer.setPositionAndUpdate(newX + 0.5D, newY + 0.5D, newZ + 0.5D);

                    messagePlayer(entityplayer, "Stay inside the siege area!");
                }

                FMLInterModComms.sendRuntimeMessage(SiegeMode.instance, "lotr", "SIEGE_ACTIVE", entityplayer.getCommandSenderName());
            } else {
                if (inSiegeRange) {
                    double putRange = radius + EDGE_PUT_RANGE;
                    int newX = xPos + MathHelper.floor_double(putRange * MathHelper.cos(angle));
                    int newZ = zPos + MathHelper.floor_double(putRange * MathHelper.sin(angle));
                    int newY = world.getTopSolidOrLiquidBlock(newX, newZ);
                    entityplayer.setPositionAndUpdate(newX + 0.5D, newY + 0.5D, newZ + 0.5D);

                    messagePlayer(entityplayer, "A siege is occurring here - stay out of the area!");
                }
            }
        }

        playerData.updateSiegeScoreboard(entityplayer, false);
    }

    public void onPlayerDeath(EntityPlayer entityplayer, DamageSource source) {
        if (hasPlayer(entityplayer)) {
            UUID playerID = entityplayer.getUniqueID();
            SiegePlayerData playerData = getPlayerData(playerID);
            SiegeTeam team = getPlayerTeam(entityplayer);

            if (!entityplayer.capabilities.isCreativeMode) {
                EntityPlayer killingPlayer = null;
                Entity killer = source.getEntity();
                if (killer instanceof EntityPlayer) {
                    killingPlayer = (EntityPlayer) killer;
                } else {
                    EntityLivingBase lastAttacker = entityplayer.func_94060_bK();
                    if (lastAttacker instanceof EntityPlayer) {
                        killingPlayer = (EntityPlayer) lastAttacker;
                    }
                }

                if (killingPlayer != null) {
                    if (hasPlayer(killingPlayer) && !killingPlayer.capabilities.isCreativeMode) {
                        SiegePlayerData killingPlayerData = getPlayerData(killingPlayer);
                        killingPlayerData.onKill();
                        SiegeTeam killingTeam = getPlayerTeam(killingPlayer);
                        killingTeam.addTeamKill();

                        int killstreak = killingPlayerData.getKillstreak();
                        if (killstreak >= KILLSTREAK_ANNOUNCE) {
                            messageAllSiegePlayers(killingPlayer.getCommandSenderName() + " (" + killingTeam.getTeamName() + ") has a killstreak of " + killstreak + "!");
                        }
                    }
                }
            }
            boolean flag1 = playerData.onDeath();
            boolean flag2 = team.addTeamDeath();

            if(!flag2 && team.getPlayerList().size() <= 0)
                messageAllSiegePlayers("Team " + team.getTeamName() + " has been eliminated! Still waiting for the siege to end.");
            if ((siegeType == SiegeType.PlayerAttempts && !flag1) || (siegeType == SiegeType.TeamAttempts && !flag2)) {
                addSpectator(team, entityplayer);

                if ((siegeType == SiegeType.TeamAttempts && getTeamsLeft() <= 1 && team.getPlayerList().size() <= 0) || (maxEnterTime < initialDuration - ticksRemaining && siegeType == SiegeType.PlayerAttempts && getTeamsLeftPlayers() <= 1))
                    endSiege();
            }

            String nextTeamName = playerData.getNextTeam();
            if (nextTeamName != null) {
                SiegeTeam nextTeam = getTeam(nextTeamName);
                if (nextTeam != null && nextTeam != team) {
                    team.leavePlayer(entityplayer);
                    nextTeam.joinPlayer(entityplayer);
                    team = getPlayerTeam(entityplayer);

                    playerData.onTeamChange();

                    messageAllSiegePlayers(entityplayer.getCommandSenderName() + " is now playing on team " + team.getTeamName());
                }

                playerData.setNextTeam(null);
            }

            // to not drop siege kit
            Kit.clearPlayerInvAndKit(entityplayer);

            int dim = entityplayer.dimension;
            ChunkCoordinates coords = entityplayer.getBedLocation(dim);
            boolean forced = entityplayer.isSpawnForced(dim);

            BackupSpawnPoint bsp = new BackupSpawnPoint(dim, coords, forced);
            playerData.setBackupSpawnPoint(bsp);
            markDirty();

            ChunkCoordinates teamSpawn = team.getRespawnPoint();
            entityplayer.setSpawnChunk(teamSpawn, true, dim);
        }
    }

    public void onPlayerRespawn(EntityPlayer entityplayer) {
        if (hasPlayer(entityplayer)) {
            restoreAndClearBackupSpawnPoint(entityplayer);
            applyPlayerKit(entityplayer);
        }
    }

    private void restoreAndClearBackupSpawnPoint(EntityPlayer entityplayer) {
        UUID playerID = entityplayer.getUniqueID();
        SiegePlayerData playerData = getPlayerData(playerID);

        BackupSpawnPoint bsp = playerData.getBackupSpawnPoint();
        if (bsp != null) {
            entityplayer.setSpawnChunk(bsp.spawnCoords, bsp.spawnForced, bsp.dimension);
        }
        playerData.setBackupSpawnPoint(null);
    }

    public void applyPlayerKit(EntityPlayer entityplayer) {
        SiegeTeam team = getPlayerTeam(entityplayer);
        UUID playerID = entityplayer.getUniqueID();
        SiegePlayerData playerData = getPlayerData(playerID);

        Kit kit = KitDatabase.getKit(playerData.getChosenKit());
        if (kit == null || !team.containsKit(kit)) {
            kit = team.getRandomKit(entityplayer.getRNG());
            messagePlayer(entityplayer, "No kit chosen! Using a random kit: " + kit.getKitName());
        }

        kit.applyTo(entityplayer);
        playerData.setCurrentKit(kit);
        setHasSiegeGivenKit(entityplayer, true);

        if (respawnImmunity > 0) {
            entityplayer.addPotionEffect(new PotionEffect(Potion.field_76444_x.id, respawnImmunity * 20, 64));
        }
    }

    public static boolean hasSiegeGivenKit(EntityPlayer entityplayer) {
        return entityplayer.getEntityData().getBoolean("HasSiegeKit");
    }

    public static void setHasSiegeGivenKit(EntityPlayer entityplayer, boolean flag) {
        entityplayer.getEntityData().setBoolean("HasSiegeKit", flag);
    }

    public void dispel(EntityPlayer entityplayer) {
        BackupSpawnPoint spawnPoint = previousSpawnLocations.get(entityplayer.getUniqueID());
        ChunkCoordinates spawnCoords = spawnPoint.spawnCoords;
        int dimension = spawnPoint.dimension;

        if (spawnCoords != null) {
            if (entityplayer.dimension != dimension) {
                entityplayer.travelToDimension(dimension);
            }
            entityplayer.setPositionAndUpdate(spawnCoords.posX + 0.5D, spawnCoords.posY + 0.5D, spawnCoords.posZ + 0.5D);
        }
    }

    public void onPlayerLogin(EntityPlayerMP entityplayer) {
        SiegePlayerData playerData = getPlayerData(entityplayer);
        if (toClear.contains(playerData))
            leavePlayer(entityplayer, false);
        if (playerData != null) {
            playerData.onLogin(entityplayer);
        }
    }

    public void onPlayerLogout(EntityPlayerMP entityplayer) {
        SiegePlayerData playerData = getPlayerData(entityplayer);
        if (playerData != null) {
            playerData.onLogout(entityplayer);
        }
    }

    public void markDirty() {
        needsSave = true;
    }

    public void markSaved() {
        needsSave = false;
    }

    public boolean needsSave() {
        return needsSave;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void deleteSiege() {
        if (isActive()) {
            endSiege();
        }

        deleted = true;
        markDirty();
    }

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setString("SiegeID", siegeID.toString());
        nbt.setString("Name", siegeName);
        nbt.setBoolean("Deleted", deleted);

        nbt.setBoolean("LocationSet", isLocationSet);
        nbt.setInteger("Dim", dimension);
        nbt.setInteger("XPos", xPos);
        nbt.setInteger("ZPos", zPos);
        nbt.setInteger("Radius", radius);

        // -------------------------
        nbt.setString("SiegeType", siegeType.name());
        nbt.setInteger("MaxPlayerLives", maxPlayerLives);
        nbt.setInteger("MaxEnterTime", maxEnterTime);
        nbt.setInteger("InitialDuration", initialDuration);
        nbt.setInteger("MaxTimeOffline", maxTimeOffline);

        NBTTagList previousLocationTags = new NBTTagList();
        for (Entry<UUID, BackupSpawnPoint> e : previousSpawnLocations.entrySet()) {
            UUID playerID = e.getKey();
            BackupSpawnPoint backupSpawnPoint = e.getValue();

            NBTTagCompound previousSpawnLocation = new NBTTagCompound();
            previousSpawnLocation.setString("PlayerID", playerID.toString());
            backupSpawnPoint.writeToNBT(previousSpawnLocation);
            previousLocationTags.appendTag(previousSpawnLocation);
        }
        nbt.setTag("PreviousSpawnLocations", previousLocationTags);

        NBTTagList dataTags = new NBTTagList();
        for (SiegePlayerData siegePlayerData : toClear) {
            NBTTagCompound data = new NBTTagCompound();
            siegePlayerData.writeToNBT(data);
            dataTags.appendTag(data);
        }
        nbt.setTag("ToClear", dataTags);
        // -------------------------

        nbt.setInteger("TicksRemaining", ticksRemaining);

        NBTTagList teamTags = new NBTTagList();
        for (SiegeTeam team : siegeTeams) {
            NBTTagCompound teamData = new NBTTagCompound();
            team.writeToNBT(teamData);
            teamTags.appendTag(teamData);
        }
        nbt.setTag("Teams", teamTags);

        nbt.setInteger("MaxTeamDiff", maxTeamDifference);
        nbt.setBoolean("FriendlyFire", friendlyFire);
        nbt.setBoolean("MobSpawning", mobSpawning);
        nbt.setBoolean("TerrainProtect", terrainProtect);
        nbt.setBoolean("TerrainProtectInactive", terrainProtectInactive);
        nbt.setInteger("RespawnImmunity", respawnImmunity);
        nbt.setBoolean("Dispel", dispelOnEnd);

        NBTTagList playerTags = new NBTTagList();
        for (Entry<UUID, SiegePlayerData> e : playerDataMap.entrySet()) {
            UUID playerID = e.getKey();
            SiegePlayerData player = e.getValue();

            NBTTagCompound playerData = new NBTTagCompound();
            playerData.setString("PlayerID", playerID.toString());
            player.writeToNBT(playerData);
            playerTags.appendTag(playerData);
        }
        nbt.setTag("PlayerData", playerTags);
    }

    public void readFromNBT(NBTTagCompound nbt) {
        siegeID = UUID.fromString(nbt.getString("SiegeID"));
        siegeName = nbt.getString("Name");
        deleted = nbt.getBoolean("Deleted");

        isLocationSet = nbt.getBoolean("LocationSet");
        dimension = nbt.getInteger("Dim");
        xPos = nbt.getInteger("XPos");
        zPos = nbt.getInteger("ZPos");
        radius = nbt.getInteger("Radius");

        // -------------------------
        siegeType = SiegeType.valueOf(nbt.getString("SiegeType"));
        maxPlayerLives = nbt.getInteger("MaxPlayerLives");
        maxEnterTime = nbt.getInteger("MaxEnterTime");
        initialDuration = nbt.getInteger("InitialDuration");
        maxTimeOffline = nbt.getInteger("MaxTimeOffline");

        previousSpawnLocations.clear();
        if (nbt.hasKey("PreviousSpawnLocations")) {
            NBTTagList spawnLocations = nbt.getTagList("PreviousSpawnLocations", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < spawnLocations.tagCount(); i++) {
                NBTTagCompound locationData = spawnLocations.getCompoundTagAt(i);
                UUID id = UUID.fromString(locationData.getString("PlayerID"));

                BackupSpawnPoint spawnPoint = BackupSpawnPoint.fromNBT(locationData);
                previousSpawnLocations.put(id, spawnPoint);
            }
        }

        toClear.clear();
        if (nbt.hasKey("ToClear")) {
            NBTTagList toClearTags = nbt.getTagList("ToClear", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < toClearTags.tagCount(); i++) {
                NBTTagCompound toClearData = toClearTags.getCompoundTagAt(i);
                SiegePlayerData data = new SiegePlayerData(this);
                data.readFromNBT(toClearData);
                toClear.add(data);
            }
        }
        // -------------------------

        ticksRemaining = nbt.getInteger("TicksRemaining");

        siegeTeams.clear();
        if (nbt.hasKey("Teams")) {
            NBTTagList teamTags = nbt.getTagList("Teams", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < teamTags.tagCount(); i++) {
                NBTTagCompound teamData = teamTags.getCompoundTagAt(i);
                SiegeTeam team = new SiegeTeam(this);
                team.readFromNBT(teamData);
                siegeTeams.add(team);
            }
        }

        maxTeamDifference = nbt.getInteger("MaxTeamDiff");
        friendlyFire = nbt.getBoolean("FriendlyFire");
        mobSpawning = nbt.getBoolean("MobSpawning");
        terrainProtect = nbt.getBoolean("TerrainProtect");
        terrainProtectInactive = nbt.getBoolean("TerrainProtectInactive");
        if (nbt.hasKey("RespawnImmunity")) {
            respawnImmunity = nbt.getInteger("RespawnImmunity");
        }
        if (nbt.hasKey("Dispel")) {
            dispelOnEnd = nbt.getBoolean("Dispel");
        }

        playerDataMap.clear();
        if (nbt.hasKey("PlayerData")) {
            NBTTagList playerTags = nbt.getTagList("PlayerData", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < playerTags.tagCount(); i++) {
                NBTTagCompound playerData = playerTags.getCompoundTagAt(i);
                UUID playerID = UUID.fromString(playerData.getString("PlayerID"));
                if (playerID != null) {
                    SiegePlayerData player = new SiegePlayerData(this);
                    player.readFromNBT(playerData);
                    playerDataMap.put(playerID, player);
                }
            }
        }
    }

    // -------------------------
    public void setSiegeType(SiegeType sT) {
        this.siegeType = sT;
    }

    public SiegeType getSiegeType() {
        return siegeType;
    }

    public void setMaxPlayerLives(int i) {
        this.maxPlayerLives = i;
    }

    public int getMaxPlayerLives() {
        return maxPlayerLives;
    }

    public void setMaxEnterTime(int i) {
        this.maxEnterTime = i;
    }

    public int getMaxEnterTime() {
        return maxEnterTime;
    }

    public int getInitialDuration() {
        return initialDuration;
    }

    public void setMaxTimeOffline(int i) {
        this.maxTimeOffline = i;
    }

    public int getMaxTimeOffline() {
        return maxTimeOffline;
    }

    private void addSpectator(SiegeTeam team, EntityPlayer player) {
        team.addSpectator(player.getUniqueID());
        leavePlayer((EntityPlayerMP) player, false);
    }

    private SiegeTeam getSpectatorTeam(EntityPlayer entityplayer) {
        for (SiegeTeam team : siegeTeams)
            if (team.containsSpectator(entityplayer.getUniqueID()))
                return team;
        return null;
    }

    public boolean hasSpectator(EntityPlayer entityplayer) {
        return getSpectatorTeam(entityplayer) != null;
    }

    private int getTeamsLeft() {
        int teamsAlive = 0;
        for (SiegeTeam team : siegeTeams) {
            if (team.getCurrentTeamLives() > 0 || team.getPlayerList().size() > 0)
                teamsAlive++;
        }
        return teamsAlive;
    }

    private int getTeamsLeftPlayers() {
        int teamsAlive = 0;
        for (SiegeTeam team : siegeTeams) {
            if (team.getPlayerList().size() > 0)
                teamsAlive++;
        }
        return teamsAlive;
    }
    // -------------------------
}
