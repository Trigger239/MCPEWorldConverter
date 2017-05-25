package utils;

public class NibbleOperation {
    public static byte readNibble(byte[] data, int index, int startPosition) {
        return (byte) ((index % 2 == 0) ? data[index / 2 + startPosition] & 0x0F : (data[index / 2 + startPosition] >> 4) & 0x0F);
    }

    public static byte readNibble(byte[] data, int index) {
        return (byte) ((index % 2 == 0) ? data[index / 2] & 0x0F : (data[index / 2] >> 4) & 0x0F);
    }

    public static void writeNibble(byte[] data, byte nibble, int index, int startPosition) {
        nibble &= 0x0F;
        if (index % 2 == 0)
            data[index / 2 + startPosition] = (byte) ((data[index / 2 + startPosition] & 0xF0) | nibble);
        else data[index / 2 + startPosition] = (byte) ((data[index / 2 + startPosition] & 0x0F) | (nibble << 4));
    }

    public static void writeNibble(byte[] data, byte nibble, int index) {
        nibble &= 0x0F;
        if (index % 2 == 0) data[index / 2] = (byte) ((data[index / 2] & 0xF0) | nibble);
        else data[index / 2] = (byte) ((data[index / 2] & 0x0F) | (nibble << 4));
    }
}
