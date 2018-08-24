package siege.common.siege;

/**
 * The kind of games within the siege mod. Just for convenience.
 */
public enum SiegeType {
    Regular, //The regular game as implemented by Mevans (TheChildWalrus). Deathmatch with unlimited attempts.
    TeamAttempts, //Same as regular but with limited attempts to join the siege after dying per team.
    PlayerAttempts //Same as regular but with limited attempts to join the siege after dying per player.

}
