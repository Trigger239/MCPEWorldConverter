import nbt.convert.*;
import nbt.tags.*;

import org.iq80.leveldb.*;

import static java.lang.Math.floor;
import static org.iq80.leveldb.impl.Iq80DBFactory.*;
import static utils.Zlib.*;

import java.io.*;

import java.io.File;
import java.util.*;

public class Main {
    public static final int NBTVersion = 19133;

    public static final byte DATA2D_TAG = (byte) 45;
    public static final byte SUB_CHUNK_TAG = (byte) 47;
    public static final byte OLD_FULL_CHUNK_TAG = (byte) 48;
    public static final byte TILE_ENTITY_TAG = (byte) 49;
    public static final byte ENTITY_TAG = (byte) 50;
    public static final byte VERSION_TAG = (byte) 118;

    public static final int regionW = 32, regionL = 32;
    public static final int area = regionW * regionL;

    public static final int POS_LOCATION = 0;
    public static final int POS_TIMESTAMP = POS_LOCATION + area * 4;
    public static final int CHUNK_LOCATION = POS_TIMESTAMP + area * 4;

    public static final int HEADER_SIZE = CHUNK_LOCATION;

    public static final int SECTOR_LENGTH = 4096;

    public static final byte COMPRESSION_GZIP = (byte) 1;
    public static final byte COMPRESSION_ZLIB = (byte) 2;

    public static void main(String[] args) throws Exception{
        String PATH = System.getProperty("user.dir");
        //String levelDBWorldPath = PATH + "/" + args[0];
        Scanner s = new Scanner(System.in);
        System.out.println("Enter LevelDB world folder name:");
        String levelDBWorldPath = PATH + "/" + s.nextLine();
        Options options = new Options();
        options.createIfMissing(true);
        options.compressionType(CompressionType.ZLIB);
        final DB db;
        try {
            db = factory.open(new File(levelDBWorldPath + "/db"), options);
        } catch(IOException e){
            System.out.println("Couldn't open LevelDB database");
            e.printStackTrace();
            return;
        }

        //tags used: [0, 97, 100, 45, 110, 47, 48, 49, 50, 114, 51, 115, 118, 54]
        //we will use: 45, 47, 48, 49, 50, 118

        //OldFullChunk (tag=48) - always has no SubChunkID, data length is 83200
        //Corresponding Version can not exists.

        //SubChunk (tag=47) - always has SubChunkID, data length can be 6145 (?) or 10241
        //SubChunk version is always 0
        //If there is SubChunk with chunkX, chunkZ, there is no OldFullChunk with chunkX, chunkZ,
        //but there always are corresponding data2D and Version (2, 3, or 4).
        //Entity and TileEntity can not exist.

        //data2D (tag=45) - always has no SubChunkID (not Nether). Data length is 768 - 256*2 bytes of HeightMap and 256 BiomeIDs.
        //Entity (tag=50) - always has no SubChunkID. Can consist of more then one CompoundTag.
        //TileEntity (tag=49) - always has no SubChunkID Can consist of more then one CompoundTag..

        //Version (tag=118) - always has no SubChunkID, data length is 1 (single byte), value is 2, 3 or 4 (?)

        // Dimension field of tag presents only for not-OWERWORLD tags.

        //So, for each pair (chunkX, chunkZ) there are 2 cases:
        //1) New format
        //Tags are:
        //  SubChunk (multiply)
        //  data2D (only for OverWorld)
        //  Version
        //  Entity, TileEntity (not always)
        //
        //2) Old format
        //Tags are:
        //  OldFullChunk
        //  data2D (not always) If not presents, HeightMap and BiomeID are correct.
        //                      If presents, then it's data is similar to OldFullChunk's data.
        //                      So, we can always use data from OldFullChunk.
        //  Entity, TileEntity (not always)
        //  Version (not always)

        Set<ChunkAddress> chunkAddresses= new HashSet<>();

        DBIterator dbIterator = db.iterator();
        for(dbIterator.seekToFirst(); dbIterator.hasNext(); dbIterator.next()) {
            byte[] key = dbIterator.peekNext().getKey();
            if(((getTag(key) == SUB_CHUNK_TAG) || (getTag(key) == OLD_FULL_CHUNK_TAG)) && (getChunkDimension(key) == DIMENSION_OVERWORLD))
                chunkAddresses.add(new ChunkAddress(getChunkX(key), getChunkZ(key), getChunkDimension(key)));
        }

        ArrayList<Tag> chunks = new ArrayList<>();

        File anvilWorldFolder = new File(levelDBWorldPath + "_Anvil");
        anvilWorldFolder.mkdir();
        clearDir(anvilWorldFolder);
        File regionFolder = new File(anvilWorldFolder.getPath() + "/region");
        regionFolder.mkdir();

        Iterator<ChunkAddress> chunkIterator= chunkAddresses.iterator();
        while (chunkIterator.hasNext()){
            ChunkAddress chunkAddress = chunkIterator.next();
            Chunk chunk = new Chunk();
            byte[] terrainData = db.get(getKey(chunkAddress, OLD_FULL_CHUNK_TAG));

            byte[] entitiesRaw = db.get(getKey(chunkAddress, ENTITY_TAG));
            byte[] tileEntitiesRaw = db.get(getKey(chunkAddress, TILE_ENTITY_TAG));

            ArrayList<Tag> entities = new ArrayList<>();
            ArrayList<Tag> tileEntities = new ArrayList<>();

            if(entitiesRaw != null) entities = DataConverter.read(entitiesRaw);
            if(tileEntitiesRaw != null) tileEntities = DataConverter.read(tileEntitiesRaw);

            if(entities == null) entities = new ArrayList<>();
            if(tileEntities == null) tileEntities = new ArrayList<>();

            for(Tag entity: entities){ //Changing FloatTags to DoubleTags
                if(entity instanceof CompoundTag) {
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
                    }
                    else if ((motion instanceof ListTag) && (((ListTag) motion).getValue().size() != 0)) {
                        if (((ListTag) motion).getValue().get(0) instanceof FloatTag) {
                            ListTag motionDouble = new ListTag("Motion");
                            motionDouble.put(((FloatTag) ((ListTag) motion).getValue().get(0)).toDoubleTag());
                            motionDouble.put(((FloatTag) ((ListTag) motion).getValue().get(1)).toDoubleTag());
                            motionDouble.put(((FloatTag) ((ListTag) motion).getValue().get(2)).toDoubleTag());
                            ((CompoundTag) entity).put(motionDouble);
                        }
                    }
                }
                //if(((CompoundTag) entity).getChildTagByKey("Motion") == null)
            }

            if(terrainData != null){ //Old format
                chunk.fromOldFullChunk(chunkAddress, db.get(getKey(chunkAddress, VERSION_TAG)), terrainData,
                        entities, tileEntities);
            }
            else{
                chunk.fromSubChunks(chunkAddress, db.get(getKey(chunkAddress, VERSION_TAG))[0],
                        db.get(getKey(chunkAddress, DATA2D_TAG)),
                        entities, tileEntities);
                for(byte subChunkID = 0; subChunkID < Chunk.maxSubChunksCount; subChunkID++){
                    byte[] subChunkTerrain = db.get(getKey(chunkAddress, SUB_CHUNK_TAG, subChunkID));
                    if(subChunkTerrain != null)
                        chunk.addSubChunk(new SubChunk(chunkAddress, subChunkID, db.get(getKey(chunkAddress, SUB_CHUNK_TAG, subChunkID))));
                }
            }

            int regionX = (int)floor(chunk.getChunkX() / (double)regionW);
            int regionZ = (int)floor(chunk.getChunkZ() / (double)regionL);

            File regionFile = new File(regionFolder.getPath() + "/" + "r." + regionX + "." + regionZ + ".mca");
            if(!regionFile.exists()) regionFile.createNewFile();

            RandomAccessFile region = new RandomAccessFile(regionFile, "rw");
            writeChunk(region, chunk);
            region.close();
        }

        File levelDBWorldDataFile = new File(levelDBWorldPath + "/level.dat");
        levelDBWorldDataFile.createNewFile();
        CompoundTag levelDBLevelData;

        try {
            levelDBLevelData = LevelDataConverter.read(levelDBWorldDataFile);
        } catch (IOException e) {
            System.out.println("Couldn't read level.dat");
            try {
                db.close();
            } catch (IOException e1) {
                System.out.println("Couldn't close LevelDB database");
            }
            return;
        }

        //Anvil level.dat - GZip'ed NBT, BigEndian!!!
        //Region - BigEndian!!!

        File anvilLevelDataFile = new File(anvilWorldFolder + "/level.dat");
        LevelDataConverter.write(levelDataConvert(levelDBLevelData, regionFolder.getTotalSpace()),anvilLevelDataFile,true,false);

        try {
            db.close();
        } catch(IOException e){
            System.out.println("Couldn't close LevelDB database");
            return;
        }
    }

    private static void writeChunk(RandomAccessFile region, Chunk chunk) throws Exception{
        region.seek(0);
        if(region.length() < HEADER_SIZE){
            region.write(new byte[HEADER_SIZE]);
        }
        int chunkX = (chunk.getChunkX() % regionW + regionW) % regionW;
        int chunkZ = (chunk.getChunkZ() % regionL + regionL) % regionL;

        int headerOffset = (regionW * chunkZ + chunkX) * 4;
        int locationOffset = POS_LOCATION + headerOffset;
        int timestampOffset = POS_TIMESTAMP + headerOffset;

        long offset = region.length();
        int sectorsOffset = (int) (offset / SECTOR_LENGTH);
        
        region.seek(locationOffset);
        region.writeByte((byte)((sectorsOffset >> 16) & 0xFF));
        region.writeByte((byte)((sectorsOffset >> 8) & 0xFF));
        region.writeByte((byte)(sectorsOffset & 0xFF));

        region.seek(timestampOffset);
        int time = 100000;
        region.writeByte((byte)((time >> 24) & 0xFF));
        region.writeByte((byte)((time >> 16) & 0xFF));
        region.writeByte((byte)((time >> 8) & 0xFF));
        region.writeByte((byte)(time & 0xFF));
        
        byte[] compressedData = deflate(DataConverter.write((Tag) chunk.toTag(), false)); //BigEndian!!!
        int chunkDataLength = compressedData.length + 1;
        byte[] chunkData = new byte[(chunkDataLength + 4 + SECTOR_LENGTH - 1) / SECTOR_LENGTH * SECTOR_LENGTH];
        chunkData[0] = (byte) ((chunkDataLength >> 24) & 0xFF);
        chunkData[1] = (byte) ((chunkDataLength >> 16) & 0xFF);
        chunkData[2] = (byte) ((chunkDataLength >> 8) & 0xFF);
        chunkData[3] = (byte) (chunkDataLength & 0xFF);
        chunkData[4] = COMPRESSION_ZLIB;
        System.arraycopy(compressedData, 0, chunkData, 5, chunkDataLength - 1);
        
        region.seek(offset);
        region.write(chunkData);

        region.seek(locationOffset + 3);
        region.writeByte(chunkData.length / SECTOR_LENGTH);
    }

    private static CompoundTag levelDataConvert(CompoundTag levelDBLevelData, long sizeOnDisk){
        ArrayList<Tag> data = new ArrayList<>();

        ArrayList<Tag> dimensionData = new ArrayList<>();
        dimensionData.add(new CompoundTag("1", new ArrayList<>()));

        data.add(new CompoundTag("DimensionData", dimensionData));
        data.add(new IntTag("version", NBTVersion));
        data.add(new ByteTag("initialized", (byte) 0)); //or 0?
        data.add(levelDBLevelData.getChildTagByKey("LevelName"));
        data.add(new StringTag("generatorName", "default"));
        data.add(new IntTag("generatorVersion", 0)); //?
        long randomSeed = (long) levelDBLevelData.getChildTagByKey("RandomSeed").getValue();
        data.add(new LongTag("RandomSeed", (int) randomSeed));
        data.add(new ByteTag("MapFeatures", (byte) 1));
        Tag lastPlayed = levelDBLevelData.getChildTagByKey("LastPlayed");
        data.add((lastPlayed != null) ? lastPlayed : new LongTag("LastPlayed", System.currentTimeMillis()));
        data.add(new LongTag("SizeOnDisc", sizeOnDisk));
        Tag allowCommands = levelDBLevelData.getChildTagByKey("commandsEnabled");
        data.add(new ByteTag("allowCommands", (byte) ((allowCommands != null) ? allowCommands.getValue() : 0)));
        data.add(new ByteTag("hardcore", (byte) 0));
        Tag gameType = levelDBLevelData.getChildTagByKey("GameType");
        data.add((gameType != null) ? gameType : new IntTag("GameType", (byte) 0));
        Tag difficulty = levelDBLevelData.getChildTagByKey("Difficulty");
        data.add((difficulty != null) ? difficulty: new ByteTag("Difficulty", (byte) 2));
        data.add(new ByteTag("DifficultyLocked", (byte) 0));
        Tag time = levelDBLevelData.getChildTagByKey("currentTick");
        data.add(new LongTag("Time", (time != null) ? (long) time.getValue() : 0));
        data.add(new LongTag("DayTime", (long) levelDBLevelData.getChildTagByKey("Time").getValue()));
        data.add(levelDBLevelData.getChildTagByKey("SpawnX"));
        data.add(levelDBLevelData.getChildTagByKey("SpawnY"));
        data.add(levelDBLevelData.getChildTagByKey("SpawnZ"));
        data.add(new DoubleTag("BorderCenterX", 0.0));
        data.add(new DoubleTag("BorderCenterZ", 0.0));
        data.add(new DoubleTag("BorderSize", 60000000.0));
        data.add(new DoubleTag("BorderSafeZone", 5.0));
        data.add(new DoubleTag("BorderWarningBlocks", 5.0));
        data.add(new DoubleTag("BorderWarningTime", 15.0));
        data.add(new DoubleTag("BorderSizeLerpTarget", 60000000.0));
        data.add(new LongTag("BorderSizeLerpTime", 0));
        data.add(new DoubleTag("BorderDamagePerBlock", 0.2));
        Tag rainLevel = levelDBLevelData.getChildTagByKey("rainLevel");
        data.add(new ByteTag("raining", ((rainLevel != null) && ((float) rainLevel.getValue() > 0)) ? (byte) 1 : (byte) 0));
        Tag rainTime = levelDBLevelData.getChildTagByKey("rainTime");
        data.add((rainTime != null) ? rainTime : new IntTag("rainTime", (byte) 0));
        Tag lightningLevel = levelDBLevelData.getChildTagByKey("lightningLevel");
        data.add(new ByteTag("thundering", ((lightningLevel != null) && ((float) lightningLevel.getValue() > 0)) ? (byte) 1 : (byte) 0));
        Tag lightningTime = levelDBLevelData.getChildTagByKey("lightningTime");
        data.add(new IntTag("thunderTime", (lightningTime != null) ? (int) lightningTime.getValue() : 0));
        data.add(new IntTag("clearWeatherTime", 0));
        data.add(new CompoundTag("GameRules", new ArrayList<>()));

        //ArrayList<Tag> version = new ArrayList<>();
        //version.add(new IntTag("Id", ))

        CompoundTag dataTag = new CompoundTag("Data", data);

        ArrayList<Tag> anvilLevelData = new ArrayList<>();
        anvilLevelData.add(dataTag);

        CompoundTag anvilLevelDataTag = new CompoundTag("", anvilLevelData);
        return anvilLevelDataTag;
    }

    private static int getChunkX(byte[] key) {
        return (key[3] << 24) |
                (key[2] << 16) |
                (key[1] << 8) |
                key[0];
    }

    private static int getChunkZ(byte[] key) {
        return (key[7] << 24) |
                (key[6] << 16) |
                (key[5] << 8) |
                key[4];
    }

    private static int DIMENSION_OVERWORLD = 0;

    private static int getChunkDimension(byte[] key) {
        if((key.length == 13)||(key.length == 14))
            return (key[11] << 24) |
                 (key[10] << 16) |
                 (key[9] << 8) |
                 key[8];
        return DIMENSION_OVERWORLD;
    }

    private static byte getTag(byte[] key){
        if((key.length == 9)||(key.length == 10)) return key[8];
        if((key.length == 13)||(key.length == 14))return key[12];
        return 0;
    }

    private static byte getChunkID(byte[] key){
        if(key.length == 10) return key[9];
        if(key.length == 14) return key[13];
        return 0;
    }

    private static byte[] getKey(int chunkX, int chunkZ, byte tag){
        byte[] key = new byte[9];
        key[0] = (byte) (chunkX & 0xFF);
        key[1] = (byte) ((chunkX >> 8) & 0xFF);
        key[2] = (byte) ((chunkX >> 16) & 0xFF);
        key[3] = (byte) ((chunkX >> 24) & 0xFF);

        key[4] = (byte) (chunkZ & 0xFF);
        key[5] = (byte) ((chunkZ >> 8) & 0xFF);
        key[6] = (byte) ((chunkZ >> 16) & 0xFF);
        key[7] = (byte) ((chunkZ >> 24) & 0xFF);

        key[8] = tag;

        return key;
    }

    private static byte[] getKey(int chunkX, int chunkZ, byte tag, byte subChunkID){
        byte[] key = new byte[10];
        key[0] = (byte) (chunkX & 0xFF);
        key[1] = (byte) ((chunkX >> 8) & 0xFF);
        key[2] = (byte) ((chunkX >> 16) & 0xFF);
        key[3] = (byte) ((chunkX >> 24) & 0xFF);

        key[4] = (byte) (chunkZ & 0xFF);
        key[5] = (byte) ((chunkZ >> 8) & 0xFF);
        key[6] = (byte) ((chunkZ >> 16) & 0xFF);
        key[7] = (byte) ((chunkZ >> 24) & 0xFF);

        key[8] = tag;
        key[9] = subChunkID;

        return key;
    }

    private static byte[] getKey(ChunkAddress chunkAddress, byte tag){
        return getKey(chunkAddress.getChunkX(), chunkAddress.getChunkZ(), chunkAddress.getDimension(), tag);
    }

    private static byte[] getKey(int chunkX, int chunkZ, int dimension, byte tag){
        if(dimension == DIMENSION_OVERWORLD) return getKey(chunkX, chunkZ, tag);

        byte[] key = new byte[13];
        key[0] = (byte) (chunkX & 0xFF);
        key[1] = (byte) ((chunkX >> 8) & 0xFF);
        key[2] = (byte) ((chunkX >> 16) & 0xFF);
        key[3] = (byte) ((chunkX >> 24) & 0xFF);

        key[4] = (byte) (chunkZ & 0xFF);
        key[5] = (byte) ((chunkZ >> 8) & 0xFF);
        key[6] = (byte) ((chunkZ >> 16) & 0xFF);
        key[7] = (byte) ((chunkZ >> 24) & 0xFF);

        key[8] = (byte) (dimension & 0xFF);
        key[9] = (byte) ((dimension >> 8) & 0xFF);
        key[10] = (byte) ((dimension >> 16) & 0xFF);
        key[11] = (byte) ((dimension >> 24) & 0xFF);

        key[12] = tag;

        return key;
    }

    private static byte[] getKey(ChunkAddress chunkAddress, byte tag, byte subChunkID){
        return getKey(chunkAddress.getChunkX(), chunkAddress.getChunkZ(), chunkAddress.getDimension(), tag, subChunkID);
    }

    private static byte[] getKey(int chunkX, int chunkZ, int dimension, byte tag, byte subChunkID){
        if(dimension == DIMENSION_OVERWORLD) return getKey(chunkX, chunkZ, tag, subChunkID);

        byte[] key = new byte[14];
        key[0] = (byte) (chunkX & 0xFF);
        key[1] = (byte) ((chunkX >> 8) & 0xFF);
        key[2] = (byte) ((chunkX >> 16) & 0xFF);
        key[3] = (byte) ((chunkX >> 24) & 0xFF);

        key[4] = (byte) (chunkZ & 0xFF);
        key[5] = (byte) ((chunkZ >> 8) & 0xFF);
        key[6] = (byte) ((chunkZ >> 16) & 0xFF);
        key[7] = (byte) ((chunkZ >> 24) & 0xFF);

        key[8] = (byte) (dimension & 0xFF);
        key[9] = (byte) ((dimension >> 8) & 0xFF);
        key[10] = (byte) ((dimension >> 16) & 0xFF);
        key[11] = (byte) ((dimension >> 24) & 0xFF);

        key[12] = tag;
        key[13] = subChunkID;

        return key;
    }

    private static boolean clearDir(File dir){
        if(!dir.isDirectory()) return false;
        boolean cleared = true;
        for(File file: dir.listFiles())
            if(file.isDirectory()){
                if(!clearDir(file)) cleared = false;
            }
            else
                if(!file.delete()) cleared = false;
        return cleared;
    }
}

