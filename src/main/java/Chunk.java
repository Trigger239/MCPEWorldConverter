import nbt.tags.*;
import utils.Zlib;

import java.util.ArrayList;

public class Chunk {
    public static final int NBTVersion = 19133;

    public static final int chunkW = 16, chunkL = 16, chunkH = 128;
    public static final int maxSubChunksCount = 16;

    public static final int subChunksCount = 8;

    public static final int area = chunkW * chunkL;
    public static final int vol = area * chunkH;

    public static final int POS_BLOCK_IDS = 0;
    public static final int POS_META_DATA = POS_BLOCK_IDS + vol;
    public static final int POS_SKY_LIGHT = POS_META_DATA + (vol >> 1);
    public static final int POS_BLOCK_LIGHT = POS_SKY_LIGHT + (vol >> 1);

    public static final int POS_HEIGHTMAP = POS_BLOCK_LIGHT + (vol >> 1);

    public static final int POS_BIOME_DATA = POS_HEIGHTMAP + area;

    public static final int LENGTH = POS_BIOME_DATA + (area * 4);

    public static final int DATA2D_POS_HEIGHTMAP = 0;
    public static final int DATA2D_POS_BIOME_DATA = DATA2D_POS_HEIGHTMAP + area + area;
    public static final int DATA2D_LENGTH = DATA2D_POS_BIOME_DATA + area;

    private  byte version;

    private int chunkX, chunkZ;
    private int dimension;
    private boolean lightPopulated;
    private boolean terrainPopulated;

    private int[] heightMap; //order ZX
    private byte[] biomeID; //order ZX

    private ArrayList<SubChunk> subChunks;
    private ArrayList<Tag> entities;
    private ArrayList<Tag> tileEntities;

    public Chunk(){}

    public Chunk(int chunkX, int chunkZ, int dimension) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimension = dimension;

        version = 2;

        lightPopulated = true;
        terrainPopulated = true;

        heightMap = intArray(-1, area);
        biomeID = new byte[area];

        subChunks = new ArrayList<>();
        entities = new ArrayList<>();
        tileEntities = new ArrayList<>();
    }

    public Chunk fromSubChunks(ChunkAddress chunkAddress, byte version, byte[] data2D, ArrayList<Tag> entities, ArrayList<Tag> tileEntities) {
        return fromSubChunks(chunkAddress.getChunkX(), chunkAddress.getChunkZ(), chunkAddress.getDimension(), version, data2D, entities, tileEntities);
    }

    public Chunk fromSubChunks(int chunkX, int chunkZ, int dimension, byte version, byte[] data2D, ArrayList<Tag> entities, ArrayList<Tag> tileEntities){
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimension = dimension;
        this.version = version;

        lightPopulated = false;
        terrainPopulated = false;

        heightMap = intArray(-1, area);
        biomeID = new byte[area];

        if(data2D != null) loadData2D(data2D);

        this.entities = entities;
        this.tileEntities = tileEntities;

        subChunks = new ArrayList<>();

        return this;
    }

    public Chunk fromOldFullChunk(ChunkAddress chunkAddress, byte[] version, byte[] terrainData, ArrayList<Tag> entities, ArrayList<Tag> tileEntities) {
        return fromOldFullChunk(chunkAddress.getChunkX(), chunkAddress.getChunkZ(), chunkAddress.getDimension(), version, terrainData, entities, tileEntities);
    }

    public Chunk fromOldFullChunk(int chunkX, int chunkZ, int dimension, byte[] version, byte[] terrainData, ArrayList<Tag> entities, ArrayList<Tag> tileEntities){
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimension = dimension;
        this.version = (version != null) ? version[0] : 2;

        lightPopulated = false;
        terrainPopulated = true;

        heightMap = intArray(-1, area);
        biomeID = new byte[area];

        subChunks = new ArrayList<>();

        loadTerrainData(terrainData);

        this.entities = entities;
        this.tileEntities = tileEntities;

        return this;
    }

    private void loadTerrainData(byte[] data){
        for(byte subChunkID = 0; subChunkID < subChunksCount; subChunkID++){
            byte[] terrainData = new byte[SubChunk.TERRAIN_LENGTH];
            boolean emptySubChunkFlag = true;
            for(byte x = 0; x < chunkW; x++)
                for(byte z = 0; z < chunkL; z++) {
                    for (byte y = 0; y < SubChunk.chunkH; y++) {
                        int oldIndex = chunkH * (chunkL * x + z) + (SubChunk.chunkH * subChunkID + y);
                        int newIndex = SubChunk.chunkH * (SubChunk.chunkL * x + z) + y;
                        terrainData[SubChunk.POS_BLOCK_IDS + newIndex/*motovskikh.ru*/] = data[POS_BLOCK_IDS + oldIndex];
                        if(terrainData[SubChunk.POS_BLOCK_IDS + newIndex] != 0) emptySubChunkFlag = false;
                        writeNibble(terrainData, readNibble(data, oldIndex, POS_META_DATA), newIndex, SubChunk.POS_META_DATA);
                        writeNibble(terrainData, readNibble(data, oldIndex, POS_SKY_LIGHT), newIndex, SubChunk.POS_SKY_LIGHT);
                        writeNibble(terrainData, readNibble(data, oldIndex, POS_BLOCK_LIGHT), newIndex, SubChunk.POS_BLOCK_LIGHT);
                    }
                }
            if(!emptySubChunkFlag) addSubChunk(new SubChunk(chunkX, chunkZ, subChunkID, dimension, terrainData));
        }
        for(byte z = 0; z < chunkL; z++)
            for(byte x = 0; x < chunkW; x++) {
                heightMap[z * chunkW + x] = data[POS_HEIGHTMAP + x * chunkL + z]; //maybe, z * chunkW + x?
                biomeID[z * chunkW + x] = data[POS_BIOME_DATA + (x * chunkL + z) * 4];
            }
    }

    private void loadData2D(byte[] data) {
        int i;
        for (i = DATA2D_POS_HEIGHTMAP; i < DATA2D_POS_BIOME_DATA; i++)
            heightMap[i / 2 - DATA2D_POS_HEIGHTMAP] = data[i++]; //even byte - data, odd - always 0

        for (i = DATA2D_POS_BIOME_DATA; i < DATA2D_LENGTH; i++)
            biomeID[i - DATA2D_POS_BIOME_DATA] = data[i];
    }

    public void addSubChunk(SubChunk subChunk){
        subChunks.add(subChunk);
        if(!subChunk.isLightPopulated()) lightPopulated = false;
        terrainPopulated = true;
    }

    public void addEntity(CompoundTag entity){
        entities.add(entity);
    }

    public void addTileEntity(CompoundTag tileEntity){
        tileEntities.add(tileEntity);
    }

    public byte readNibble(byte[] data, int index, int startPosition){
        return (byte) ((index % 2 == 0) ? data[index / 2 + startPosition] & 0x0F : (data[index / 2 + startPosition] >> 4) & 0x0F);
    }

    public byte readNibble(byte[] data, int index){
        return (byte) ((index % 2 == 0) ? data[index / 2] & 0x0F : (data[index / 2] >> 4) & 0x0F);
    }

    public void writeNibble(byte[] data, byte nibble, int index, int startPosition){
        nibble &= 0x0F;
        if (index % 2 == 0) data[index / 2 + startPosition] = (byte) ((data[index / 2 + startPosition] & 0xF0) | nibble);
        else data[index / 2 + startPosition] = (byte) ((data[index / 2 + startPosition] & 0x0F) | (nibble << 4));
    }

    public void writeNibble(byte[] data, byte nibble, int index){
        nibble &= 0x0F;
        if (index % 2 == 0) data[index / 2] = (byte) ((data[index / 2] & 0xF0) | nibble);
        else data[index / 2] = (byte) ((data[index / 2] & 0x0F) | (nibble << 4));
    }

    public CompoundTag toTag() {
        ArrayList<Tag> tag = new ArrayList<>();

        tag.add(new IntTag("DataVersion", NBTVersion));

        ArrayList<Tag> level = new ArrayList<>();
        level.add(new IntTag("xPos", chunkX));
        level.add(new IntTag("zPos", chunkZ));
        level.add(new LongTag("LastUpdate", System.currentTimeMillis())); //maybe 0?
        level.add(new ByteTag("LightPopulated", lightPopulated ? (byte) 1 : (byte) 0));
        level.add(new ByteTag("TerrainPopulated", terrainPopulated ? (byte) 1 : (byte) 0));
        level.add(new ByteTag("V", (byte) 1));
        level.add(new LongTag("InhabitedTime", 0));
        level.add(new ByteArrayTag("Biomes", biomeID));
        level.add(new IntArrayTag("HeightMap", heightMap));
        level.add(new ByteTag("TerrainGenerated", (byte) 1));

        ArrayList<Tag> sections = new ArrayList<>();
        subChunks.forEach((SubChunk subChunk) -> sections.add(subChunk.toTag()));

        level.add(new ListTag("Sections", sections));
        level.add(new ListTag("Entities", entities));
        level.add(new ListTag("TileEntities", tileEntities));

        tag.add(new CompoundTag("Level", level));

        return new CompoundTag("", tag);
    }

    public int getChunkX(){
        return this.chunkX;
    }

    public int getChunkZ(){
        return this.chunkZ;
    }

    private int[] intArray(int data, int length){
        int[] arr = new int[length];
        for(int i = 0; i< length; i++)
            arr[i] = data;
        return arr;
    }

    private byte[] byteArray(byte data, int length){
        byte[] arr = new byte[length];
        for(int i = 0; i< length; i++)
            arr[i] = data;
        return arr;
    }
}
