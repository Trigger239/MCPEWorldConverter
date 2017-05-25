package levelDBWorld;

import converter.Converter;
import nbt.convert.*;
import nbt.tags.*;
import org.iq80.leveldb.*;
import worldElements.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class LevelDBWorld {
    private static int DIMENSION_OVERWORLD = 0;

    public static final byte DATA2D_TAG = (byte) 45;
    public static final byte SUB_CHUNK_TAG = (byte) 47;
    public static final byte OLD_FULL_CHUNK_TAG = (byte) 48;
    public static final byte TILE_ENTITY_TAG = (byte) 49;
    public static final byte ENTITY_TAG = (byte) 50;
    public static final byte VERSION_TAG = (byte) 118;

    private DB db;
    private File levelDataFile;

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

    public LevelDBWorld(File worldDirectory) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        options.compressionType(CompressionType.ZLIB);
        db = factory.open(new File(worldDirectory.getPath() + "/" + "db"), options);

        levelDataFile = new File(worldDirectory.getPath() + "/" + "level.dat");
    }

    public Set<ChunkAddress> getChunkAddresses() {
        Set<ChunkAddress> chunkAddresses = new HashSet<>();

        DBIterator dbIterator = db.iterator();
        for (dbIterator.seekToFirst(); dbIterator.hasNext(); dbIterator.next()) {
            byte[] key = dbIterator.peekNext().getKey();
            // Only Overworld chunks are read
            if (((getTag(key) == SUB_CHUNK_TAG) || (getTag(key) == OLD_FULL_CHUNK_TAG)) && (getChunkDimension(key) == DIMENSION_OVERWORLD))
                chunkAddresses.add(new ChunkAddress(getChunkX(key), getChunkZ(key), getChunkDimension(key)));
        }
        return chunkAddresses;
    }

    public Chunk readChunk(ChunkAddress chunkAddress) throws IOException {
        Chunk chunk = new Chunk();
        byte[] terrainData = db.get(getKey(chunkAddress, OLD_FULL_CHUNK_TAG));

        byte[] entitiesRaw = db.get(getKey(chunkAddress, ENTITY_TAG));
        byte[] tileEntitiesRaw = db.get(getKey(chunkAddress, TILE_ENTITY_TAG));

        ArrayList<Tag> entities;
        ArrayList<Tag> tileEntities;

        if (entitiesRaw != null) entities = DataConverter.read(entitiesRaw);
        else entities = new ArrayList<>();
        if (tileEntitiesRaw != null) tileEntities = DataConverter.read(tileEntitiesRaw);
        else tileEntities = new ArrayList<>();

        for (Tag entity : entities) {
            Converter.convertEntity(entity);
        }

        if (terrainData != null) { //Old format
            chunk.fromOldFullChunk(chunkAddress, db.get(getKey(chunkAddress, VERSION_TAG)), terrainData,
                    entities, tileEntities);
        } else { //New format
            chunk.fromSubChunks(chunkAddress, db.get(getKey(chunkAddress, VERSION_TAG))[0],
                    db.get(getKey(chunkAddress, DATA2D_TAG)),
                    entities, tileEntities);
            for (byte subChunkID = 0; subChunkID < Chunk.maxSubChunksCount; subChunkID++) {
                byte[] subChunkTerrain = db.get(getKey(chunkAddress, SUB_CHUNK_TAG, subChunkID));
                if (subChunkTerrain != null)
                    chunk.addSubChunk(new SubChunk(chunkAddress, subChunkID, db.get(getKey(chunkAddress, SUB_CHUNK_TAG, subChunkID))));
            }
        }

        return chunk;
    }

    public CompoundTag getLevelData() throws IOException {
        return LevelDataConverter.read(levelDataFile);
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

    private static int getChunkDimension(byte[] key) {
        if ((key.length == 13) || (key.length == 14))
            return (key[11] << 24) |
                    (key[10] << 16) |
                    (key[9] << 8) |
                    key[8];
        return DIMENSION_OVERWORLD;
    }

    private static byte getTag(byte[] key) {
        if ((key.length == 9) || (key.length == 10)) return key[8];
        if ((key.length == 13) || (key.length == 14)) return key[12];
        return 0;
    }

    private static byte getChunkID(byte[] key) {
        if (key.length == 10) return key[9];
        if (key.length == 14) return key[13];
        return 0;
    }

    private static byte[] getKey(int chunkX, int chunkZ, byte tag) {
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

    private static byte[] getKey(int chunkX, int chunkZ, byte tag, byte subChunkID) {
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

    private static byte[] getKey(ChunkAddress chunkAddress, byte tag) {
        return getKey(chunkAddress.getChunkX(), chunkAddress.getChunkZ(), chunkAddress.getDimension(), tag);
    }

    private static byte[] getKey(int chunkX, int chunkZ, int dimension, byte tag) {
        if (dimension == DIMENSION_OVERWORLD) return getKey(chunkX, chunkZ, tag);

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

    private static byte[] getKey(ChunkAddress chunkAddress, byte tag, byte subChunkID) {
        return getKey(chunkAddress.getChunkX(), chunkAddress.getChunkZ(), chunkAddress.getDimension(), tag, subChunkID);
    }

    private static byte[] getKey(int chunkX, int chunkZ, int dimension, byte tag, byte subChunkID) {
        if (dimension == DIMENSION_OVERWORLD) return getKey(chunkX, chunkZ, tag, subChunkID);

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
}
