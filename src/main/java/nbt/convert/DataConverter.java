package nbt.convert;

import nbt.tags.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class DataConverter {

    public static ArrayList<Tag> read(byte[] input) throws IOException {
        return read(input, true);
    }

    public static ArrayList<Tag> read(byte[] input, boolean littleEndian) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(input);
        NBTInputStream in = new NBTInputStream(bais, false, littleEndian);
        ArrayList<Tag> tags = in.readTopLevelTags();
        in.close();
        return tags;
    }

    public static byte[] write(List<Tag> tags) throws IOException {
        return write(tags, true);
    }

    public static byte[] write(List<Tag> tags, boolean littleEndian) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NBTOutputStream out = new NBTOutputStream(bos, false, littleEndian);
        for (Tag tag : tags) {
            out.writeTag(tag);
        }
        out.close();
        return bos.toByteArray();
    }

    public static byte[] write(Tag tag) throws IOException {
        return write(tag, true);
    }

    public static byte[] write(Tag tag, boolean littleEndian) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NBTOutputStream out = new NBTOutputStream(bos, false, littleEndian);
        out.writeTag(tag);
        out.close();
        return bos.toByteArray();
    }
}
