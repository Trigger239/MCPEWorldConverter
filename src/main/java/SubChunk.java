import nbt.tags.ByteArrayTag;
import nbt.tags.ByteTag;
import nbt.tags.CompoundTag;
import nbt.tags.Tag;

import java.util.ArrayList;

public class SubChunk {
    public static final int chunkW = 16, chunkL = 16, chunkH = 16;

    public static final int area = chunkW * chunkL;
    public static final int vol = area * chunkH;

    public static final int POS_VERSION = 0;
    public static final int POS_BLOCK_IDS = POS_VERSION + 1;
    public static final int POS_META_DATA = POS_BLOCK_IDS + vol;
    public static final int POS_SKY_LIGHT = POS_META_DATA + (vol >> 1);
    public static final int POS_BLOCK_LIGHT = POS_SKY_LIGHT + (vol >> 1);
    public static final int TERRAIN_LENGTH = POS_BLOCK_LIGHT + (vol >> 1);

    public static final int POS_HEIGHTMAP = 0;
    // it looks like each biome takes 2 bytes, and the first 1 byte of every 2 bytes is always 0!?
    public static final int POS_BIOME_DATA = POS_HEIGHTMAP + area + area;
    public static final int DATA2D_LENGTH = POS_BIOME_DATA + area;

    private int chunkX;
    private int chunkZ;
    private byte chunkID;

    private int dimension;

    private byte version;
    private byte[] blockID; //order: XZY
    private byte[] blockData; //order: XZY
    private byte[] skyLight; //order: XZY
    private byte[] blockLight; //order: XZY

    private byte[] heightMap;
    private byte[] biomeID;

    private boolean lightPopulated = false;

    public SubChunk(ChunkAddress chunkAddress, byte chunkID) {
        this(chunkAddress.getChunkX(), chunkAddress.getChunkZ(), chunkID, chunkAddress.getDimension());
    }

    public SubChunk(int chunkX, int chunkZ, byte chunkID, int dimension) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.chunkID = chunkID;
        this.dimension = dimension;
    }

    public SubChunk(ChunkAddress chunkAddress, byte chunkID, byte[] terrainData) {
        this(chunkAddress.getChunkX(), chunkAddress.getChunkZ(), chunkID, chunkAddress.getDimension());
        loadTerrain(terrainData);
    }

    public SubChunk(int chunkX, int chunkZ, byte chunkID, int dimension, byte[] terrainData) {
        this(chunkX, chunkZ, chunkID, dimension);
        loadTerrain(terrainData);
    }

    public void loadTerrain(byte[] data) {
        int i;
        version = data[POS_VERSION];

        blockID = new byte[vol];
        for (i = POS_BLOCK_IDS; i < POS_META_DATA; i++)
            blockID[i - POS_BLOCK_IDS] = data[i];

        blockData = new byte[vol >> 1];
        for (i = POS_META_DATA; i < POS_SKY_LIGHT; i++)
            blockData[i - POS_META_DATA] = data[i];

        if (data.length != POS_SKY_LIGHT) {
            lightPopulated = true;

            skyLight = new byte[vol >> 1];
            for (i = POS_SKY_LIGHT; i < POS_BLOCK_LIGHT; i++)
                skyLight[i - POS_SKY_LIGHT] = data[i];

            blockLight = new byte[vol >> 1];
            for (i = POS_BLOCK_LIGHT; i < TERRAIN_LENGTH; i++)
                blockLight[i - POS_BLOCK_LIGHT] = data[i];
        } else { //strange terrain data: length is 1b+4kb+2kb
            lightPopulated = false;

            skyLight = new byte[vol >> 1];
            for (i = POS_SKY_LIGHT; i < POS_BLOCK_LIGHT; i++)
                skyLight[i - POS_SKY_LIGHT] = (byte) 0xFF;

            blockLight = new byte[vol >> 1];
            for (i = POS_BLOCK_LIGHT; i < TERRAIN_LENGTH; i++)
                blockLight[i - POS_BLOCK_LIGHT] = (byte) 0xFF;
        }
    }

    public void loadData2D(byte[] data) {
        int i;
        heightMap = new byte[vol];
        for (i = POS_HEIGHTMAP; i < POS_BIOME_DATA; i++)
            heightMap[i - POS_HEIGHTMAP] = data[i++]; //even byte - data, odd - always 0

        biomeID = new byte[vol >> 1];
        for (i = POS_BIOME_DATA; i < DATA2D_LENGTH; i++)
            biomeID[i - POS_BIOME_DATA] = data[i];
    }

    private byte[] XZYtoYZX(byte[] data){
        byte[] newData = new byte[data.length];
        for(byte x = 0; x < chunkW; x++)
            for(byte z = 0; z < chunkL; z++)
                for(byte y = 0; y < chunkH; y++)
                    newData[chunkW * (chunkL * y + z) + x] = data[chunkH * (chunkL * x + z) + y];
        return newData;
    }

    private byte[] XZYtoYZXasNibble(byte[] data){
        byte[] newData = new byte[data.length];
        for(byte x = 0; x < chunkW; x++)
            for(byte z = 0; z < chunkL; z++)
                for(byte y = 0; y < chunkH; y++) {
                    int oldIndex = chunkH * (chunkL * x + z) + y;
                    int newIndex = chunkW * (chunkL * y + z) + x;
                    byte nibble = (oldIndex % 2 == 0) ? (byte) (data[oldIndex / 2] & 0x0F) :
                                                        (byte) ((data[oldIndex / 2] >> 4) & 0x0F);
                    if (newIndex % 2 == 0) newData[newIndex / 2] = (byte) ((newData[newIndex / 2] & 0xF0) | nibble);
                    else newData[newIndex / 2] = (byte) ((newData[newIndex / 2] & 0x0F) | (nibble << 4));
                }
        return newData;
    }

    public boolean isLightPopulated(){
        return lightPopulated;
    }

    public int getHighestBlockYAt(byte x, byte z){
        byte highestBlockY;
        for(highestBlockY = chunkH - 1; (highestBlockY >= 0) && (getBlockID(x, highestBlockY, z) == 0); highestBlockY--);
        return highestBlockY;
    }

    public byte getBlockID(byte x, byte y, byte z){
        return blockID[chunkH * (chunkL * x + z) + y];
    }

    public CompoundTag toTag(){
        ArrayList<Tag> tag = new ArrayList<>();
        tag.add(new ByteTag("Y", chunkID));
        tag.add(new ByteArrayTag("Blocks", XZYtoYZX(blockID)));
        tag.add(new ByteArrayTag("Data", XZYtoYZXasNibble(blockData)));
        tag.add(new ByteArrayTag("BlockLight", XZYtoYZXasNibble(blockLight)));
        tag.add(new ByteArrayTag("SkyLight", XZYtoYZXasNibble(skyLight)));
        return new CompoundTag("", tag);
    }
}
