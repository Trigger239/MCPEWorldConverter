package converter;

import anvilWorld.AnvilWorld;
import levelDBWorld.LevelDBWorld;
import nbt.tags.*;
import utils.ClearDirectory;
import worldElements.Chunk;
import worldElements.ChunkAddress;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

public class Converter {
    public static final int NBT_VERSION = 19133;

    public static void convertWorld(File levelDBWorldDirectory, File anvilWorldDirectory) throws Exception {
        LevelDBWorld levelDBWorld = new LevelDBWorld(levelDBWorldDirectory);
        ClearDirectory.clearDir(anvilWorldDirectory);
        AnvilWorld anvilWorld = new AnvilWorld(anvilWorldDirectory);

        Set<ChunkAddress> chunkAddresses = levelDBWorld.getChunkAddresses();
        for (ChunkAddress chunkAddress : chunkAddresses) {
            Chunk chunk = levelDBWorld.readChunk(chunkAddress);
            anvilWorld.writeChunk(chunk);
        }

        CompoundTag levelDBWorldLevelData = levelDBWorld.getLevelData();
        anvilWorld.writeLevelData(convertLevelData(levelDBWorldLevelData, anvilWorld.getSizeOnDisk()));
    }

    public static CompoundTag convertLevelData(CompoundTag levelDBLevelData, long sizeOnDisk) {
        CompoundTag data = new CompoundTag("Data", new ArrayList<>());

        CompoundTag dimensionData = new CompoundTag("DimensionData", new ArrayList<>());
        dimensionData.put(new CompoundTag("1", new ArrayList<>()));

        data.put(dimensionData);
        data.put(new IntTag("version", NBT_VERSION));
        data.put(new ByteTag("initialized", (byte) 0)); //or 0?
        data.put(levelDBLevelData.getChildTagByKey("LevelName"));
        data.put(new StringTag("generatorName", "default"));
        data.put(new IntTag("generatorVersion", 0)); //?
        long randomSeed = (long) levelDBLevelData.getChildTagByKey("RandomSeed").getValue();
        data.put(new LongTag("RandomSeed", (int) randomSeed));
        data.put(new ByteTag("MapFeatures", (byte) 1));
        Tag lastPlayed = levelDBLevelData.getChildTagByKey("LastPlayed");
        data.put((lastPlayed != null) ? lastPlayed : new LongTag("LastPlayed", System.currentTimeMillis()));
        data.put(new LongTag("SizeOnDisc", sizeOnDisk));
        Tag allowCommands = levelDBLevelData.getChildTagByKey("commandsEnabled");
        data.put(new ByteTag("allowCommands", (byte) ((allowCommands != null) ? allowCommands.getValue() : 0)));
        data.put(new ByteTag("hardcore", (byte) 0));
        Tag gameType = levelDBLevelData.getChildTagByKey("GameType");
        data.put((gameType != null) ? gameType : new IntTag("GameType", (byte) 0));
        Tag difficulty = levelDBLevelData.getChildTagByKey("Difficulty");
        data.put((difficulty != null) ? difficulty : new ByteTag("Difficulty", (byte) 2));
        data.put(new ByteTag("DifficultyLocked", (byte) 0));
        Tag time = levelDBLevelData.getChildTagByKey("currentTick");
        data.put(new LongTag("Time", (time != null) ? (long) time.getValue() : 0));
        data.put(new LongTag("DayTime", (long) levelDBLevelData.getChildTagByKey("Time").getValue()));
        data.put(levelDBLevelData.getChildTagByKey("SpawnX"));
        data.put(levelDBLevelData.getChildTagByKey("SpawnY"));
        data.put(levelDBLevelData.getChildTagByKey("SpawnZ"));
        data.put(new DoubleTag("BorderCenterX", 0.0));
        data.put(new DoubleTag("BorderCenterZ", 0.0));
        data.put(new DoubleTag("BorderSize", 60000000.0));
        data.put(new DoubleTag("BorderSafeZone", 5.0));
        data.put(new DoubleTag("BorderWarningBlocks", 5.0));
        data.put(new DoubleTag("BorderWarningTime", 15.0));
        data.put(new DoubleTag("BorderSizeLerpTarget", 60000000.0));
        data.put(new LongTag("BorderSizeLerpTime", 0));
        data.put(new DoubleTag("BorderDamagePerBlock", 0.2));
        Tag rainLevel = levelDBLevelData.getChildTagByKey("rainLevel");
        data.put(new ByteTag("raining", ((rainLevel != null) && ((float) rainLevel.getValue() > 0)) ? (byte) 1 : (byte) 0));
        Tag rainTime = levelDBLevelData.getChildTagByKey("rainTime");
        data.put((rainTime != null) ? rainTime : new IntTag("rainTime", (byte) 0));
        Tag lightningLevel = levelDBLevelData.getChildTagByKey("lightningLevel");
        data.put(new ByteTag("thundering", ((lightningLevel != null) && ((float) lightningLevel.getValue() > 0)) ? (byte) 1 : (byte) 0));
        Tag lightningTime = levelDBLevelData.getChildTagByKey("lightningTime");
        data.put(new IntTag("thunderTime", (lightningTime != null) ? (int) lightningTime.getValue() : 0));
        data.put(new IntTag("clearWeatherTime", 0));
        data.put(new CompoundTag("GameRules", new ArrayList<>()));

        CompoundTag anvilLevelData = new CompoundTag("", new ArrayList<>());
        anvilLevelData.put(data);

        return anvilLevelData;
    }

    public static void convertEntity(Tag entity) {
        if (entity instanceof CompoundTag) {
            Tag pos = ((CompoundTag) entity).getChildTagByKey("Pos");
            if ((pos instanceof ListTag) && (((ListTag) pos).getValue().size() != 0)) {
                if (((ListTag) pos).getValue().get(0) instanceof FloatTag) {
                    ListTag posDouble = new ListTag("Pos");
                    posDouble.put(((FloatTag) ((ListTag) pos).getValue().get(0)).toDoubleTag());
                    posDouble.put(((FloatTag) ((ListTag) pos).getValue().get(1)).toDoubleTag());
                    posDouble.put(((FloatTag) ((ListTag) pos).getValue().get(2)).toDoubleTag());
                    ((CompoundTag) entity).put(posDouble);
                }
            }
            Tag motion = ((CompoundTag) entity).getChildTagByKey("Motion");
            if (motion == null) {
                ListTag motionDouble = new ListTag("Motion");
                motionDouble.put(new DoubleTag("", 0.0));
                motionDouble.put(new DoubleTag("", 0.0));
                motionDouble.put(new DoubleTag("", 0.0));
                ((CompoundTag) entity).put(motionDouble);
            } else if ((motion instanceof ListTag) && (((ListTag) motion).getValue().size() != 0)) {
                if (((ListTag) motion).getValue().get(0) instanceof FloatTag) {
                    ListTag motionDouble = new ListTag("Motion");
                    motionDouble.put(((FloatTag) ((ListTag) motion).getValue().get(0)).toDoubleTag());
                    motionDouble.put(((FloatTag) ((ListTag) motion).getValue().get(1)).toDoubleTag());
                    motionDouble.put(((FloatTag) ((ListTag) motion).getValue().get(2)).toDoubleTag());
                    ((CompoundTag) entity).put(motionDouble);
                }
            }

        }
    }
}
