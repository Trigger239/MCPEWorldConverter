package anvilWorld;

import nbt.convert.DataConverter;
import worldElements.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static utils.Zlib.deflate;

public class Region {
    public static final int regionW = 32, regionL = 32;
    public static final int area = regionW * regionL;

    public static final int POS_LOCATION = 0;
    public static final int POS_TIMESTAMP = POS_LOCATION + area * 4;
    public static final int CHUNK_LOCATION = POS_TIMESTAMP + area * 4;

    public static final int HEADER_SIZE = CHUNK_LOCATION;

    public static final int CHUNK_HEADER_SIZE = 5; //4 bytes - chunk data length, 1 byte - compression type

    public static final int SECTOR_LENGTH = 4096;

    public static final byte COMPRESSION_GZIP = (byte) 1;
    public static final byte COMPRESSION_ZLIB = (byte) 2;

    private RandomAccessFile region;
    private int regionX;
    private int regionZ;

    public Region(File regionDirectory, int regionX, int regionZ) throws IOException {
        File regionFile = getRegionFile(regionDirectory, regionX, regionZ);
        if (!regionFile.exists())
            regionDirectory.createNewFile();
        region = new RandomAccessFile(regionFile, "rw");
        if (region.length() < HEADER_SIZE) {
            region.seek(POS_LOCATION);
            region.write(new byte[HEADER_SIZE]);
        }
        this.regionX = regionX;
        this.regionZ = regionZ;
    }

    public void writeChunk(Chunk chunk) throws Exception {
        int chunkX = (chunk.getChunkX() % regionW + regionW) % regionW;
        int chunkZ = (chunk.getChunkZ() % regionL + regionL) % regionL;
        int offset = getSectorCount();

        writeChunkOffset(chunkX, chunkZ, offset);
        writeChunkTimestamp(chunkX, chunkZ, (int) (System.currentTimeMillis() / 1000));

        byte[] compressedData = deflate(DataConverter.write(chunk.toTag(), false));
        int chunkDataLength = compressedData.length + 1;
        byte[] chunkData = new byte[(chunkDataLength + CHUNK_HEADER_SIZE - 1 + SECTOR_LENGTH - 1) / SECTOR_LENGTH * SECTOR_LENGTH];
        chunkData[0] = (byte) ((chunkDataLength >> 24) & 0xFF);
        chunkData[1] = (byte) ((chunkDataLength >> 16) & 0xFF);
        chunkData[2] = (byte) ((chunkDataLength >> 8) & 0xFF);
        chunkData[3] = (byte) (chunkDataLength & 0xFF);
        chunkData[4] = COMPRESSION_ZLIB;
        System.arraycopy(compressedData, 0, chunkData, CHUNK_HEADER_SIZE, chunkDataLength - 1);

        region.seek(offset * SECTOR_LENGTH);
        region.write(chunkData);

        writeChunkLength(chunkX, chunkZ, (byte) (chunkData.length / SECTOR_LENGTH));
    }

    private int readChunkOffset(int chunkX, int chunkZ) throws IOException {
        int locationOffset = POS_LOCATION + (regionW * chunkZ + chunkX) * 4;
        region.seek(locationOffset);
        byte[] rawData = new byte[3];
        region.read(rawData);
        return (rawData[2] << 16) |
                (rawData[1] << 8) |
                rawData[0];
    }

    private void writeChunkOffset(int chunkX, int chunkZ, int offset) throws IOException {
        int locationOffset = POS_LOCATION + (regionW * chunkZ + chunkX) * 4;
        region.seek(locationOffset);
        region.writeByte((byte) ((offset >> 16) & 0xFF));
        region.writeByte((byte) ((offset >> 8) & 0xFF));
        region.writeByte((byte) (offset & 0xFF));
    }

    private void writeChunkLength(int chunkX, int chunkZ, byte length) throws IOException {
        int locationOffset = POS_LOCATION + (regionW * chunkZ + chunkX) * 4;
        region.seek(locationOffset + 3);
        region.writeByte(length);
    }

    private void writeChunkTimestamp(int chunkX, int chunkZ, int timestamp) throws IOException {
        int timestampOffset = POS_TIMESTAMP + (regionW * chunkZ + chunkX) * 4;
        region.seek(timestampOffset);
        region.writeByte((byte) ((timestamp >> 24) & 0xFF));
        region.writeByte((byte) ((timestamp >> 16) & 0xFF));
        region.writeByte((byte) ((timestamp >> 8) & 0xFF));
        region.writeByte((byte) (timestamp & 0xFF));
    }

    private int getSectorCount() throws IOException {
        return (int) region.length() / SECTOR_LENGTH;
    }

    public boolean chunkExists(int chunkX, int chunkZ) throws IOException {
        return readChunkOffset(chunkX, chunkZ) != 0;
    }

    public int getRegionX() {
        return regionX;
    }

    public int getRegionZ() {
        return regionZ;
    }

    public static File getRegionFile(File regionDirectory, int regionX, int regionZ) {
        return new File(regionDirectory.getPath() + "/" + "r." + regionX + "." + regionZ + ".mca");
    }
}
