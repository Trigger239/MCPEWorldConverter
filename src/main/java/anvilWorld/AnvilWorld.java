package anvilWorld;

import nbt.convert.LevelDataConverter;
import nbt.tags.CompoundTag;
import worldElements.Chunk;

import java.io.File;
import java.io.IOException;

import static java.lang.Math.floor;

public class AnvilWorld {
    private File regionDirectory;
    private File levelDataFile;

    public AnvilWorld(File worldDirectory) throws IOException {
        regionDirectory = new File(worldDirectory.getPath() + "/" + "region");
        regionDirectory.mkdir();

        levelDataFile = new File(worldDirectory.getPath() + "/" + "level.dat");
        if (!levelDataFile.exists()) levelDataFile.createNewFile();
    }

    public void writeChunk(Chunk chunk) throws Exception {
        Region region = new Region(regionDirectory, (int) floor(chunk.getChunkX() / (double) Region.regionW),
                (int) floor(chunk.getChunkZ() / (double) Region.regionL));
        region.writeChunk(chunk);
    }

    public void writeLevelData(CompoundTag tag) throws IOException {
        LevelDataConverter.write(tag, levelDataFile, true, false);
    }

    public long getSizeOnDisk() {
        return regionDirectory.getTotalSpace();
    }
}
