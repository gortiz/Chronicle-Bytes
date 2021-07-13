    /*
     * Copyright 2016-2020 chronicle.software
     *
     * https://chronicle.software
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */

    package net.openhft.chronicle.bytes;

    import net.openhft.chronicle.bytes.internal.BytesInternal;
    import net.openhft.chronicle.bytes.internal.NativeBytesStore;
    import net.openhft.chronicle.core.*;
    import net.openhft.chronicle.core.annotation.Java9;
    import net.openhft.chronicle.core.io.IORuntimeException;
    import net.openhft.chronicle.core.util.StringUtils;
    import org.jetbrains.annotations.NotNull;
    import org.jetbrains.annotations.Nullable;

    import java.nio.BufferOverflowException;
    import java.nio.BufferUnderflowException;
    import java.nio.ByteBuffer;

    import static net.openhft.chronicle.bytes.NoBytesStore.noBytesStore;

    /**
     * Simple Bytes implementation which is not Elastic.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public class VanillaBytes<Underlying>
            extends AbstractBytes<Underlying>
            implements Byteable<Bytes<Underlying>, Underlying>, Comparable<CharSequence> {

        /* make protected in x.22 */
        public VanillaBytes(@NotNull BytesStore bytesStore)
                throws IllegalStateException, IllegalArgumentException {
            this(bytesStore, bytesStore.writePosition(), bytesStore.writeLimit());
        }

        /* make protected in x.22 */
        public VanillaBytes(@NotNull BytesStore bytesStore, long writePosition, long writeLimit)
                throws IllegalStateException, IllegalArgumentException {
            super(bytesStore, writePosition, writeLimit);
        }

        /**
         * Return a VanillaBytes which can have it's bytesStore() replaced as needed.
         *
         * @return a non elastic bytes with noByteStore()
         */
        @NotNull
        public static VanillaBytes<Void> vanillaBytes() {
            try {
                return new NativeBytes<>(noBytesStore());

            } catch (IllegalStateException | IllegalArgumentException e) {
                throw new AssertionError(e);
            }
        }

        @Java9
        private static boolean isEqual0(@NotNull byte[] bytes, byte coder, @NotNull NativeBytesStore bs, long address) {

            @Nullable Memory memory = bs.memory;

            if (coder == 0) {
                int i = 0;
                for (; i < bytes.length; i++) {
                    byte b = memory.readByte(address + i);
                    char c = (char) (bytes[i] & 0xFF);
                    if (b != c) {
                        return false;
                    }
                }
            } else {
                int i = 0;
                for (; i < bytes.length; i++) {
                    byte b = memory.readByte(address + i);
                    char c = (char) (((bytes[i + 1] & 0xFF) << 8) | (bytes[i] & 0xFF));

                    if (b != c) {
                        return false;
                    }
                }
            }

            return true;
        }

        private static boolean isEqual0(@NotNull char[] chars, @NotNull NativeBytesStore bs, long address) {

            @Nullable Memory memory = bs.memory;
            int i = 0;
            for (; i < chars.length - 3; i += 4) {
                int b = memory.readInt(address + i);
                int b0 = b & 0xFF;
                int b1 = (b >> 8) & 0xFF;
                int b2 = (b >> 16) & 0xFF;
                int b3 = (b >> 24) & 0xFF;
                if (b0 != chars[i] || b1 != chars[i + 1] || b2 != chars[i + 2] || b3 != chars[i + 3])
                    return false;
            }
            for (; i < chars.length; i++) {
                int b = memory.readByte(address + i) & 0xFF;
                if (b != chars[i])
                    return false;
            }

            return true;
        }

        private static boolean isEqual1(@NotNull char[] chars, @NotNull BytesStore bytesStore, long readPosition)
                throws BufferUnderflowException, IllegalStateException {
            for (int i = 0; i < chars.length; i++) {
                int b = bytesStore.readByte(readPosition + i) & 0xFF;
                if (b != chars[i])
                    return false;
            }
            return true;
        }

        @Java9
        private static boolean isEqual1(@NotNull byte[] bytes, byte coder, @NotNull BytesStore bytesStore, long readPosition)
                throws BufferUnderflowException, IllegalStateException {
            for (int i = 0; i < bytes.length; i++) {
                int b = bytesStore.readByte(readPosition + i) & 0xFF;
                char c;

                if (coder == 0) {
                    c = (char) (bytes[i] & 0xFF);
                } else {
                    c = (char) (((bytes[i + 1] & 0xFF) << 8) | (bytes[i] & 0xFF));
                    i++;
                }

                if (b != c)
                    return false;
            }
            return true;
        }

        @Override
        public long readVolatileLong(long offset)
                throws BufferUnderflowException, IllegalStateException {
            readCheckOffset(offset, 8, true);
            return bytesStore.readVolatileLong(offset);
        }

        @Override
        public void bytesStore(@NotNull BytesStore<Bytes<Underlying>, Underlying> byteStore, long offset, long length)
                throws IllegalStateException, IllegalArgumentException, BufferUnderflowException {
            setBytesStore(byteStore);
            // assume its read-only
            readLimit(offset + length);
            readPosition(offset);
            try {
                writeLimit(offset + length);
            } catch (BufferOverflowException e) {
                throw new AssertionError(e);
            }
        }

        private void setBytesStore(@NotNull BytesStore<Bytes<Underlying>, Underlying> bytesStore)
                throws IllegalStateException, IllegalArgumentException {
            if (this.bytesStore != bytesStore) {
                @Nullable BytesStore oldBS = this.bytesStore;
                this.bytesStore(bytesStore);
                bytesStore.reserve(this);
                oldBS.release(this);
            }
            clear();
        }

        @Override
        public long offset() {
            return readPosition();
        }

        @Override
        public long maxSize() {
            return readRemaining();
        }

        @Override
        public boolean isElastic() {
            return false;
        }

        @NotNull
        @Override
        public Bytes<Underlying> bytesForRead()
                throws IllegalStateException {
            try {
                return isClear()
                        ? new VanillaBytes<>(bytesStore, writePosition(), bytesStore.writeLimit())
                        : new SubBytes<>(bytesStore, readPosition(), readLimit());
            } catch (IllegalArgumentException | BufferUnderflowException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public boolean isEqual(@Nullable String s)
                throws IllegalStateException {
            if (s == null || s.length() != readRemaining()) return false;
            ReportUnoptimised.reportOnce();

            long realLength = realReadRemaining();
            try {
                if (Jvm.isJava9Plus()) {
                    byte[] bytes = StringUtils.extractBytes(s);
                    byte coder = StringUtils.getStringCoder(s);
                    if (bytesStore instanceof NativeBytesStore && realLength == readRemaining()) {
                        @NotNull NativeBytesStore bs = (NativeBytesStore) this.bytesStore;
                        long address = bs.addressForRead(readPosition);
                        return isEqual0(bytes, coder, bs, address);

                    } else {
                        return isEqual1(bytes, coder, bytesStore, readPosition);
                    }
                } else {
                    char[] chars = StringUtils.extractChars(s);
                    if (bytesStore instanceof NativeBytesStore && realLength == readRemaining()) {
                        @NotNull NativeBytesStore bs = (NativeBytesStore) this.bytesStore;
                        long address = bs.addressForRead(readPosition);
                        return isEqual0(chars, bs, address);

                    } else {
                        return isEqual1(chars, bytesStore, readPosition);
                    }
                }
            } catch (BufferUnderflowException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public long realCapacity() {
            return bytesStore.realCapacity();
        }

        @NotNull
        @Override
        public BytesStore<Bytes<Underlying>, Underlying> copy()
                throws IllegalStateException {
            ReportUnoptimised.reportOnce();

            if (bytesStore.underlyingObject() instanceof ByteBuffer) {
                try {
                    ByteBuffer bb = ByteBuffer.allocateDirect(Maths.toInt32(readRemaining()));
                    @NotNull ByteBuffer bbu = (ByteBuffer) bytesStore.underlyingObject();
                    ByteBuffer slice = bbu.slice();
                    slice.position((int) readPosition());
                    slice.limit((int) readLimit());
                    bb.put(slice);
                    bb.clear();
                    return (BytesStore) BytesStore.wrap(bb);
                } catch (ArithmeticException e) {
                    throw new AssertionError(e);
                }
            } else {
                return (BytesStore) NativeBytes.copyOf(this);
            }
        }

        @NotNull
        @Override
        public Bytes<Underlying> write(@NotNull RandomDataInput bytes, long offset, long length)
                throws BufferOverflowException, BufferUnderflowException, IllegalStateException, IllegalArgumentException {
            ensureCapacity(writePosition() + length);
            optimisedWrite(bytes, offset, length);
            return this;
        }

        protected void optimisedWrite(@NotNull RandomDataInput bytes, long offset, long length)
                throws BufferOverflowException, BufferUnderflowException, IllegalStateException, IllegalArgumentException {
            if (length <= safeCopySize() && isDirectMemory() && bytes.isDirectMemory()) {
                long len = Math.min(writeRemaining(), Math.min(bytes.capacity() - offset, length));
                if (len > 0) {
                    writeCheckOffset(writePosition(), len);
                    long address = bytes.addressForRead(offset);
                    long address2 = addressForWritePosition();
                    assert address != 0;
                    assert address2 != 0;
                    OS.memory().copyMemory(address, address2, len);
                    writeSkip(len);
                }
            } else {
                super.write(bytes, offset, length);
            }
        }

        public void write(long position, @NotNull CharSequence str, int offset, int length)
                throws BufferOverflowException, IllegalArgumentException, ArithmeticException, IllegalStateException, BufferUnderflowException {

            ensureCapacity(writePosition() + length);
            if (offset + length > str.length())
                throw new IllegalArgumentException("offset=" + offset + " + length=" + length + " > str.length =" + str.length());

            for (int i = 0; i < length; i++) {
                int index = offset + i;
                bytesStore.writeByte(position + i, charAt(str, index));
            }
        }

        private char charAt(@NotNull CharSequence str, int index) {
            return str.charAt(index);
        }

        @Override
        @NotNull
        public VanillaBytes append(@NotNull CharSequence str, int start, int end)
                throws IndexOutOfBoundsException {
            assert end > start : "end=" + end + ",start=" + start;
            try {
                if (isDirectMemory()) {
                    if (str instanceof BytesStore) {

                        write((BytesStore) str, (long) start, end - start);
                        return this;
                    }
                    if (str instanceof String) {
                        if (Jvm.isJava9Plus()) {
                            byte coder = StringUtils.getStringCoder((String) str);
                            appendUtf8(StringUtils.extractBytes((String) str), start, end - start, coder);
                        } else {
                            appendUtf8(StringUtils.extractChars((String) str), start, end - start);
                        }

                        return this;
                    }
                }
                ReportUnoptimised.reportOnce();
                super.append(str, start, end);
                return this;

            } catch (Exception e) {
                throw new IndexOutOfBoundsException(e.toString());
            }
        }

        @NotNull
        @Override
        public VanillaBytes appendUtf8(@NotNull CharSequence str)
                throws BufferOverflowException {
            ReportUnoptimised.reportOnce();
            try {
                if (isDirectMemory()) {
                    if (str instanceof BytesStore) {
                        write((BytesStore) str, 0L, str.length());
                        return this;
                    }
                    if (str instanceof String) {
                        if (Jvm.isJava9Plus()) {
                            String str1 = (String) str;
                            byte coder = StringUtils.getStringCoder(str1);
                            appendUtf8(StringUtils.extractBytes(str1), 0, str.length(), coder);
                        } else {
                            appendUtf8(StringUtils.extractChars((String) str), 0, str.length());
                        }
                        return this;
                    }
                }
                super.append(str, 0, str.length());
                return this;

            } catch (Exception e) {
                @NotNull BufferOverflowException e2 = new BufferOverflowException();
                e2.initCause(e);
                throw e2;
            }
        }

        @Override
        @NotNull
        public Bytes<Underlying> append8bit(@NotNull CharSequence cs)
                throws BufferOverflowException, BufferUnderflowException, IndexOutOfBoundsException, IllegalStateException {
            if (cs instanceof RandomDataInput)
                return write((RandomDataInput) cs);

            if (isDirectMemory() && cs instanceof String)
                return append8bitNBS_S((String) cs);
            return append8bit0(cs);
        }

        @Override
        @NotNull
        public Bytes<Underlying> append8bit(@NotNull BytesStore bs)
                throws BufferOverflowException, BufferUnderflowException, IllegalStateException {
            long remaining = bs.readLimit() - bs.readPosition();
            try {
                return write(bs, 0L, remaining);
            } catch (IllegalArgumentException e) {
                throw new AssertionError(e);
            }
        }

        @NotNull
        @Override
        public Bytes<Underlying> write(@NotNull BytesStore bytes, long offset, long length)
                throws BufferOverflowException, BufferUnderflowException, IllegalStateException, IllegalArgumentException {
            ensureCapacity(writePosition() + length);
            if (length == (int) length) {
                if (bytes.canReadDirect(length) && canWriteDirect(length)) {
                    long wAddr = addressForWritePosition();
                    writeSkip(length);
                    long rAddr = bytes.addressForRead(offset);
                    UnsafeMemory.copyMemory(rAddr, wAddr, (int) length);
                    return this;

                } else {
                    bytesStore.write(writePosition(), bytes.bytesStore(), offset, length);
                    uncheckedWritePosition(writePosition() + length);
                    return this;
                }
            }
            ReportUnoptimised.reportOnce();
            BytesInternal.writeFully(bytes, offset, length, this);
            return this;
        }

        @Override
        @NotNull
        public Bytes<Underlying> append8bit(@NotNull String cs)
                throws BufferOverflowException, IllegalStateException {
            if (isDirectMemory())
                return append8bitNBS_S(cs);
            return append8bit0(cs);
        }

        @NotNull
        private Bytes<Underlying> append8bitNBS_S(@NotNull String s)
                throws BufferOverflowException, IllegalStateException {
            BytesStore<Bytes<Underlying>, Underlying> bs = this.bytesStore;
            if (bs instanceof NativeBytesStore)
                append8bitNBS_S(s, (NativeBytesStore) bs);
            else
                append8bitNBS_S(s, (net.openhft.chronicle.bytes.NativeBytesStore) bs);
            return this;
        }

        @NotNull
        private Bytes<Underlying> append8bitNBS_S(@NotNull String s, NativeBytesStore bytesStore)
                throws BufferOverflowException, IllegalStateException {
            int length = s.length();
            long offset = writeOffsetPositionMoved(length); // can re-assign the byteStore if not large enough.
            final long address = bytesStore.address + bytesStore.translate(offset);
            @Nullable final Memory memory = bytesStore.memory;

            if (memory == null)
                bytesStore.throwExceptionIfReleased();

            if (Jvm.isJava9Plus()) {
                final byte[] chars = StringUtils.extractBytes(s);

                int i;
                for (i = 0; i < length; i++) {
                    memory.writeByte(address + i, chars[i]);
                }
            } else {
                final char[] chars = StringUtils.extractChars(s);
                int i;
                for (i = 0; i < length - 3; i += 4) {
                    int c0 = chars[i] & 0xFF;
                    int c1 = chars[i + 1] & 0xFF;
                    int c2 = chars[i + 2] & 0xFF;
                    int c3 = chars[i + 3] & 0xFF;
                    memory.writeInt(address + i, c0 | (c1 << 8) | (c2 << 16) | (c3 << 24));
                }
                for (; i < length; i++) {
                    int c0 = chars[i];
                    memory.writeByte(address + i, (byte) c0);
                }
            }
            return this;
        }

        @NotNull
        private Bytes<Underlying> append8bitNBS_S(@NotNull String s, net.openhft.chronicle.bytes.NativeBytesStore bytesStore)
                throws BufferOverflowException, IllegalStateException {
            int length = s.length();
            long offset = writeOffsetPositionMoved(length); // can re-assign the byteStore if not large enough.
            final long address = bytesStore.address + bytesStore.translate(offset);
            @Nullable final Memory memory = bytesStore.memory;

            if (memory == null)
                bytesStore.throwExceptionIfReleased();

            if (Jvm.isJava9Plus()) {
                final byte[] chars = StringUtils.extractBytes(s);

                int i;
                for (i = 0; i < length; i++) {
                    memory.writeByte(address + i, chars[i]);
                }
            } else {
                final char[] chars = StringUtils.extractChars(s);
                int i;
                for (i = 0; i < length - 3; i += 4) {
                    int c0 = chars[i] & 0xFF;
                    int c1 = chars[i + 1] & 0xFF;
                    int c2 = chars[i + 2] & 0xFF;
                    int c3 = chars[i + 3] & 0xFF;
                    memory.writeInt(address + i, c0 | (c1 << 8) | (c2 << 16) | (c3 << 24));
                }
                for (; i < length; i++) {
                    int c0 = chars[i];
                    memory.writeByte(address + i, (byte) c0);
                }
            }
            return this;
        }

        @NotNull
        public String toString() {
            try {
                return bytesStore instanceof NativeBytesStore
                        ? toString2((NativeBytesStore) bytesStore)
                        : toString0();
            } catch (IllegalStateException e) {
                throw Jvm.rethrow(e);
            }
        }

        private String toString2(@NotNull NativeBytesStore bytesStore) {
            int length = (int)
                    Math.min(Bytes.MAX_HEAP_CAPACITY, realReadRemaining());
            @NotNull char[] chars = new char[length];
            @Nullable final Memory memory = bytesStore.memory;
            final long address = bytesStore.address + bytesStore.translate(readPosition());
            for (int i = 0; i < length && i < realCapacity(); i++)
                chars[i] = (char) (memory.readByte(address + i) & 0xFF);

            return StringUtils.newString(chars);
        }

        @NotNull
        protected String toString0()
                throws IllegalStateException {
            int length = (int) Math.min(Bytes.MAX_HEAP_CAPACITY, readRemaining());
            @NotNull char[] chars = new char[length];
            try {
                for (int i = 0; i < length; i++) {
                    chars[i] = (char) (bytesStore.readByte(readPosition() + i) & 0xFF);
                }
            } catch (BufferUnderflowException e) {
                // ignored
            }
            return StringUtils.newString(chars);
        }

        @NotNull
        protected Bytes<Underlying> append8bit0(@NotNull CharSequence cs)
                throws BufferOverflowException, IllegalStateException {
            int length = cs.length();
            long offset = writeOffsetPositionMoved(length);
            for (int i = 0; i < length; i++) {
                char c = charAt(cs, i);
                if (c > 255) c = '?';
                bytesStore.writeByte(offset + i, (byte) c);
            }
            return this;
        }

        @Override
        public boolean equalBytes(@NotNull BytesStore bytesStore, long length)
                throws BufferUnderflowException, IllegalStateException {
            ReportUnoptimised.reportOnce();

            if (isDirectMemory() &&
                    bytesStore instanceof VanillaBytes &&
                    bytesStore.isDirectMemory()) {
                @NotNull VanillaBytes b2 = (VanillaBytes) bytesStore;
                @NotNull NativeBytesStore nbs0 = (NativeBytesStore) this.bytesStore;
                @Nullable NativeBytesStore nbs2 = (NativeBytesStore) b2.bytesStore();
                long i = 0;
                for (; i < length - 7; i += 8) {
                    long addr0 = nbs0.address + readPosition() - nbs0.start() + i;
                    long addr2 = nbs2.address + b2.readPosition() - nbs2.start() + i;
                    long l0 = nbs0.memory.readLong(addr0);
                    long l2 = nbs2.memory.readLong(addr2);
                    if (l0 != l2)
                        return false;
                }
                for (; i < length; i++) {
                    long offset2 = readPosition() + i - nbs0.start();
                    long offset21 = b2.readPosition() + i - nbs2.start();
                    byte b0 = nbs0.memory.readByte(nbs0.address + offset2);
                    byte b1 = nbs2.memory.readByte(nbs2.address + offset21);
                    if (b0 != b1)
                        return false;
                }
                return true;
            } else {
                return super.equalBytes(bytesStore, length);
            }
        }

        public void read8Bit(char[] chars, int length)
                throws BufferUnderflowException, IllegalStateException {
            ReportUnoptimised.reportOnce();

            if (isDirectMemory()) {
                long position = readPosition();
                @NotNull NativeBytesStore nbs = (NativeBytesStore) bytesStore();
                nbs.read8bit(position, chars, length);
            } else {
                long pos = this.readPosition();
                for (int i = 0; i < length; i++)
                    chars[i] = (char) this.readUnsignedByte(pos + i);
            }
        }

        // TODO: protected?
        @Override
        public int byteCheckSum(int start, int end)
                throws IORuntimeException, BufferUnderflowException {
            byte b = 0;
            // the below cast is safe as should only be called from net.openhft.chronicle.bytes.AbstractBytes.byteCheckSum(long, long)
            @Nullable NativeBytesStore bytesStore = (NativeBytesStore) bytesStore();
            @Nullable Memory memory = bytesStore.memory;
            assert memory != null;
            long addr = bytesStore.addressForRead(start);
            int i = 0, len = end - start;
            for (; i < len - 3; i += 4) {
                b += memory.readByte(addr + i)
                        + memory.readByte(addr + i + 1)
                        + memory.readByte(addr + i + 2)
                        + memory.readByte(addr + i + 3);
            }
            for (; i < len; i++) {
                b += memory.readByte(addr + i);
            }
            return b & 0xFF;
        }

        @NotNull
        @Override
        public Bytes<Underlying> appendUtf8(char[] chars, int offset, int length)
                throws BufferOverflowException, IllegalStateException, BufferUnderflowException, IllegalArgumentException {
            long actualUTF8Length = AppendableUtil.findUtf8Length(chars, offset, length);
            ensureCapacity(writePosition() + actualUTF8Length);
            if (bytesStore instanceof NativeBytesStore) {
                @Nullable NativeBytesStore nbs = (NativeBytesStore) this.bytesStore;
                long position = nbs.appendUtf8(writePosition(), chars, offset, length);
                writePosition(position);
            } else {
                ReportUnoptimised.reportOnce();
                super.appendUtf8(chars, offset, length);
            }
            return this;
        }

        @Override
        public ByteBuffer toTemporaryDirectByteBuffer()
                throws IllegalArgumentException, ArithmeticException, IllegalStateException {
            if (isClear())
                return bytesStore.toTemporaryDirectByteBuffer();
            return super.toTemporaryDirectByteBuffer();
        }

        @Override
        public int read(@NotNull byte[] bytes)
                throws BufferUnderflowException, IllegalStateException {
            ReportUnoptimised.reportOnce();

            int len = (int) Math.min(bytes.length, readRemaining());
            if (bytesStore instanceof NativeBytesStore) {
                @Nullable NativeBytesStore nbs = (NativeBytesStore) this.bytesStore;
                long len2 = nbs.read(readPosition(), bytes, 0, len);
                try {
                    readSkip(len2);
                } catch (BufferUnderflowException e) {
                    throw new AssertionError(e);
                }
                return (int) (len2);
            }
            return super.read(bytes);
        }

        @Override
        public int compareTo(@NotNull CharSequence cs) {
            long len1 = readRemaining();
            int len2 = cs.length();
            long lim = Math.min(len1, len2);

            int k = 0;
            while (k < lim) {
                char c1 = charAt(bytesStore, k);
                char c2 = charAt(cs, k);
                if (c1 != c2) {
                    return c1 - c2;
                }
                k++;
            }
            return (int) (len1 - len2);
        }

    }
