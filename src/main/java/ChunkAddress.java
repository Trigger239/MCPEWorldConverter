public class ChunkAddress {
    private int chunkX;
    private int chunkZ;
    private int dimension;

    public ChunkAddress(int chunkX, int chunkZ, int dimension) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimension = dimension;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public int getDimension() {
        return dimension;
    }
}
