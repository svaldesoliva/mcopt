package mcopt.metal;

import java.lang.foreign.Arena;
import java.lang.reflect.InvocationTargetException;

public final class BufferChecks {
	public static void main(String[] args) throws Exception {
		// A zeroed Ctx has no Metal device: allocation returns nil without exhausting real GPU memory.
		try (Arena arena = Arena.ofConfined()) {
			long ctx = arena.allocate(80, 8).address();
			failed(() -> Native.bufferNew(ctx, 128));
			failed(() -> Native.bufferPrivate(ctx, 0, 128));
			try {
				Class<?> own = Class.forName("mcopt.metal.own.OwnNative");
				var buffer = own.getDeclaredMethod("buffer", long.class, long.class, int.class);
				buffer.setAccessible(true);
				for (int kind = 0; kind < 3; kind++) {
					try {
						buffer.invoke(null, ctx, 128L, kind);
						throw new AssertionError("Own terrain accepted a failed allocation");
					} catch (InvocationTargetException e) {
						if (!(e.getCause() instanceof IllegalStateException)) throw e;
					}
				}
			} catch (ClassNotFoundException absentOn26_2) {
			}
		}
		System.out.println("Buffer allocation failures rejected");
	}

	private static void failed(Runnable allocate) {
		try {
			allocate.run();
			throw new AssertionError("Accepted a failed allocation");
		} catch (IllegalStateException expected) {
			if (!expected.getMessage().contains("128")) throw new AssertionError("Missing allocation size", expected);
		}
	}
}
