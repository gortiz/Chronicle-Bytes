package net.openhft.chronicle.bytes.readme;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.HexDumpBytes;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class PrimitiveTest {
    @Test
    public void testBinaryPrimitive() {
        HexDumpBytes bytes = new HexDumpBytes();
        bytes.comment("true").writeBoolean(true);
        bytes.comment("s8").writeByte((byte) 1);
        bytes.comment("u8").writeUnsignedByte(2);
        bytes.comment("s16").writeShort((short) 3);
        bytes.comment("u16").writeUnsignedShort(4);
        bytes.comment("char").writeStopBit('5'); // char
        bytes.comment("s32").writeInt(6);
        bytes.comment("u32").writeUnsignedInt(7);
        bytes.comment("s64").writeLong(8);
        bytes.comment("f32").writeFloat(9);
        bytes.comment("f64").writeDouble(10);

        System.out.println(bytes.toHexString());

        boolean flag = bytes.readBoolean();
        byte s8 = bytes.readByte();
        int u8 = bytes.readUnsignedByte();
        short s16 = bytes.readShort();
        int u16 = bytes.readUnsignedShort();
        char ch = bytes.readStopBitChar();
        int s32 = bytes.readInt();
        long u32 = bytes.readUnsignedInt();
        long s64 = bytes.readLong();
        float f32 = bytes.readFloat();
        double f64 = bytes.readDouble();

        assertEquals(true, flag);
        assertEquals(1, s8);
        assertEquals(2, u8);
        assertEquals(3, s16);
        assertEquals(4, u16);
        assertEquals('5', ch);
        assertEquals(6, s32);
        assertEquals(7, u32);
        assertEquals(8, s64);
        assertEquals(9, f32, 0.0);
        assertEquals(10, f64, 0.0);
    }

    @Test
    public void testBinaryPrimitiveOffset() {
        Bytes<ByteBuffer> bytes = Bytes.elasticHeapByteBuffer(64);
        bytes.writeBoolean(0, true);
        bytes.writeByte(1, (byte) 1);
        bytes.writeUnsignedByte(2, 2);
        bytes.writeShort(3, (short) 3);
        bytes.writeUnsignedShort(5, 4);
        bytes.writeInt(7, 6);
        bytes.writeUnsignedInt(11, 7);
        bytes.writeLong(15, 8);
        bytes.writeFloat(23, 9);
        bytes.writeDouble(27, 10);
        bytes.writePosition(35);

        System.out.println(bytes.toHexString());

        boolean flag = bytes.readBoolean(0);
        byte s8 = bytes.readByte(1);
        int u8 = bytes.readUnsignedByte(2);
        short s16 = bytes.readShort(3);
        int u16 = bytes.readUnsignedShort(5);
        int s32 = bytes.readInt(7);
        long u32 = bytes.readUnsignedInt(11);
        long s64 = bytes.readLong(15);
        float f32 = bytes.readFloat(23);
        double f64 = bytes.readDouble(27);

        assertEquals(true, flag);
        assertEquals(1, s8);
        assertEquals(2, u8);
        assertEquals(3, s16);
        assertEquals(4, u16);
        assertEquals(6, s32);
        assertEquals(7, u32);
        assertEquals(8, s64);
        assertEquals(9, f32, 0.0);
        assertEquals(10, f64, 0.0);
    }
}