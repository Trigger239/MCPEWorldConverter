package nbt.tags;

import nbt.convert.NBTConstants;

public class FloatTag extends Tag<Float> {

    private static final long serialVersionUID = -1566271877968979568L;

    public FloatTag(String name, float value) {
        super(name, value);
    }

    @Override
    public NBTConstants.NBTType getType() {
        return NBTConstants.NBTType.FLOAT;
    }

    @Override
    public FloatTag getDeepCopy() {
        return new FloatTag(name, value);
    }

    public DoubleTag toDoubleTag(){
        return new DoubleTag(this.name, (double) this.getValue());
    }
}