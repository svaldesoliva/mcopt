package mcopt.metal.chunkio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.UTFDataFormatException;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

public final class SaveChecks {
	public static void main(String[] args) throws Exception {
		Random random = new Random(17);
		for (int trial = 0; trial < 500; trial++) {
			FastDataOutput fast = new FastDataOutput(trial % 8);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream reference = new DataOutputStream(bytes);
			for (int op = 0; op < 30; op++) {
				int x = random.nextInt();
				long y = random.nextLong();
				StringBuilder string = new StringBuilder();
				for (int i = 0; i < 20; i++) string.append((char) random.nextInt(65536));
				for (java.io.DataOutput out : new java.io.DataOutput[] {fast, reference}) {
					out.writeInt(x);
					out.writeLong(y);
					out.writeFloat(Float.intBitsToFloat(x));
					out.writeDouble(Double.longBitsToDouble(y));
					out.writeUTF(string.toString());
				}
			}
			check(Arrays.equals(Arrays.copyOf(fast.array(), fast.length()), bytes.toByteArray()), "Serializer differs from DataOutputStream");
		}
		FastDataOutput fast = new FastDataOutput(1);
		fast.writeUTF("a".repeat(65535));
		int before = fast.length();
		try {
			fast.writeUTF("a".repeat(65536));
			throw new AssertionError("Accepted oversized UTF");
		} catch (UTFDataFormatException expected) {
			check(fast.length() == before, "Oversized UTF changed the output");
		}
		CompoundTag tag = new CompoundTag();
		tag.putLong("LastUpdate", 100);
		tag.putString("text", "\u0000\u0800\uD83D\uDE00");
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		NbtIo.write(tag, new DataOutputStream(bytes));
		RegionSkip.Buffers serialized = RegionSkip.serialize(tag);
		check(Arrays.equals(bytes.toByteArray(), Arrays.copyOf(serialized.fresh.array(), serialized.fresh.length())), "NBT serialization differs");

		byte[] fresh = sample(100, true), stored = sample(200, true);
		RegionSkip.Buffers buffers = new RegionSkip.Buffers();
		buffers.fresh.write(fresh);
		for (int size = 1; size <= fresh.length; size++) {
			final int limit = size;
			var in = new ByteArrayInputStream(stored) {
				@Override public synchronized int read(byte[] b, int off, int len) {
					return super.read(b, off, Math.min(limit, len));
				}
			};
			check(RegionSkip.compareStream(buffers, in) == RegionSkip.Result.LAST_UPDATE_ONLY, "Read boundary changes comparison");
		}
		int stamp = RegionSkip.lastUpdateOffset(fresh, fresh.length);
		check(stamp >= 0, "Root timestamp missing");
		for (int i = 0; i < fresh.length; i++) {
			byte[] altered = fresh.clone();
			altered[i] ^= 1;
			var expected = i >= stamp && i < stamp + 8 ? RegionSkip.Result.LAST_UPDATE_ONLY : RegionSkip.Result.DIFFERENT;
			check(RegionSkip.compareStream(buffers, new ByteArrayInputStream(altered)) == expected, "Ignored payload change at byte " + i);
			buffers.stored = altered;
			buffers.storedLength = altered.length;
			check(RegionSkip.compare(buffers) == expected, "Probe disagrees at byte " + i);
		}
		check(RegionSkip.compareStream(buffers, new ByteArrayInputStream(fresh)) == RegionSkip.Result.IDENTICAL, "Identical save differs");
		check(RegionSkip.compareStream(buffers, new ByteArrayInputStream(Arrays.copyOf(fresh, fresh.length - 1))) == RegionSkip.Result.DIFFERENT, "Truncated save accepted");
		check(RegionSkip.compareStream(buffers, new ByteArrayInputStream(Arrays.copyOf(fresh, fresh.length + 1))) == RegionSkip.Result.DIFFERENT, "Longer save accepted");
		byte[] nestedOnly = sample(100, false);
		check(RegionSkip.lastUpdateOffset(nestedOnly, nestedOnly.length) == -1, "Nested timestamp treated as root");
		for (int length = 0; length <= stamp; length++) {
			check(RegionSkip.lastUpdateOffset(fresh, length) == -1, "Incomplete root timestamp accepted");
		}
		System.out.println("Save serializer, nested timestamps, payload mutations and read boundaries passed");
	}

	private static byte[] sample(long stamp, boolean rootStamp) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeByte(10); out.writeUTF("");
		out.writeByte(10); out.writeUTF("nested");
		out.writeByte(4); out.writeUTF("LastUpdate"); out.writeLong(88); out.writeByte(0);
		out.writeByte(9); out.writeUTF("list"); out.writeByte(3); out.writeInt(2); out.writeInt(1); out.writeInt(2);
		out.writeByte(12); out.writeUTF("array"); out.writeInt(2); out.writeLong(1); out.writeLong(2);
		if (rootStamp) { out.writeByte(4); out.writeUTF("LastUpdate"); out.writeLong(stamp); }
		out.writeByte(1); out.writeUTF("payload"); out.writeByte(7); out.writeByte(0);
		return bytes.toByteArray();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
