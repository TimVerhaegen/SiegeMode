package siege.common.siege;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;

public class BackupSpawnPoint
{
	public int dimension;
	public ChunkCoordinates spawnCoords;
	public boolean spawnForced;
	
	public BackupSpawnPoint(int dim, ChunkCoordinates coords, boolean forced)
	{
		dimension = dim;
		spawnCoords = coords;
		spawnForced = forced;
	}

	public static BackupSpawnPoint fromNBT(NBTTagCompound previousSpawnLocation) {
		int dimension = previousSpawnLocation.getInteger("Dimension");
		boolean spawnForced = previousSpawnLocation.getBoolean("SpawnForced");

		int x = previousSpawnLocation.getInteger("X");
		int y = previousSpawnLocation.getInteger("Y");
		int z = previousSpawnLocation.getInteger("Z");

		ChunkCoordinates spawnCoords = new ChunkCoordinates(x,y,z);

		return new BackupSpawnPoint(dimension, spawnCoords, spawnForced);
	}

	public void readFromNBT(NBTTagCompound previousSpawnLocation) {
		dimension = previousSpawnLocation.getInteger("Dimension");
		spawnForced = previousSpawnLocation.getBoolean("SpawnForced");

		int x = previousSpawnLocation.getInteger("X");
		int y = previousSpawnLocation.getInteger("Y");
		int z = previousSpawnLocation.getInteger("Z");

		spawnCoords = new ChunkCoordinates(x,y,z);
	}

    public void writeToNBT(NBTTagCompound previousSpawnLocation) {
		previousSpawnLocation.setInteger("Dimension", dimension);
		previousSpawnLocation.setBoolean("SpawnForced", spawnForced);

		previousSpawnLocation.setInteger("X", spawnCoords.posX);
		previousSpawnLocation.setInteger("Y", spawnCoords.posY);
		previousSpawnLocation.setInteger("Z", spawnCoords.posZ);
    }
}
