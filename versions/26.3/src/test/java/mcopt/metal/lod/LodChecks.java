package mcopt.metal.lod;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import mcopt.metal.MetalBridge;
import org.lwjgl.system.MemoryUtil;

public final class LodChecks {
	public static void main(String[] args) throws Exception {
		Path root = Path.of("cache");
		Path first = LodField.cachePath(root, "World", 1, "minecraft_overworld");
		check(!first.equals(LodField.cachePath(root, "World", 2, "minecraft_overworld")), "Different seeds share a cache");
		check(!first.equals(LodField.cachePath(root, "World", 1, "minecraft_the_nether")), "Dimensions share a cache");
		check(first.equals(LodField.cachePath(root, "World", 1, "minecraft_overworld")), "Cache path changes between sessions");

		long allocation = MemoryUtil.nmemAlloc(8);
		check(allocations().contains(allocation), "Debug allocator is required for this check");
		MemoryUtil.nmemFree(allocation);
		long ctx = MetalBridge.createHeadlessContext();
		LodClip clip = new LodClip(ctx, 256, 1);
		check(clip.buffers().length == 5 && Arrays.stream(clip.buffers()).anyMatch(b -> b == clip.texBuf), "Clip cleanup omits texture data");
		LodMesh mesh = new LodMesh(ctx, clip);
		Thread[] workers = (Thread[]) field(mesh, "workers");
		long deadline = System.nanoTime() + 5_000_000_000L;
		for (Thread worker : workers) {
			while (worker.getState() != Thread.State.WAITING && System.nanoTime() < deadline) Thread.yield();
			check(worker.getState() == Thread.State.WAITING, "Mesh worker did not reach its idle wait");
		}
		workers[0].interrupt();
		workers[0].join();
		CountDownLatch started = new CountDownLatch(1), finishRead = new CountDownLatch(1);
		AtomicBoolean readDone = new AtomicBoolean();
		workers[0] = new Thread(() -> {
			started.countDown();
			boolean finished = false;
			while (!finished) {
				try {
					finishRead.await();
					finished = true;
				} catch (InterruptedException ignored) {
				}
			}
			MemoryUtil.memGetInt(clip.geom);
			readDone.set(true);
		}, "mesh shutdown check");
		workers[0].start();
		started.await();
		Set<Long> owned = allocations();
		long ready = MemoryUtil.nmemAlloc(16), held = MemoryUtil.nmemAlloc(16);
		queue(mesh, "results").add(new long[] {0, 0, ready});
		queue(mesh, "publishLater").add(new long[] {0, 0, held});
		Thread.ofPlatform().start(() -> {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				finishRead.countDown();
			}
		});
		Thread.currentThread().interrupt();
		mesh.close();
		check(readDone.get(), "Shutdown returned before an active buffer read finished");
		check(Thread.interrupted(), "Shutdown lost the caller's interrupt status");
		check(Arrays.stream(workers).noneMatch(Thread::isAlive), "Shutdown returned with workers alive");
		check(queue(mesh, "results").isEmpty() && queue(mesh, "publishLater").isEmpty(), "Shutdown kept mesh results");
		Set<Long> remaining = allocations();
		check(!remaining.contains(ready) && !remaining.contains(held), "Queued native mesh blocks leaked");
		check(!remaining.contains((long) field(mesh, "tableHost")) && !remaining.contains(mesh.frame), "Host buffers leaked");
		Set<Long> scratch = new HashSet<>();
		MemoryUtil.memReport((address, size, thread, name, stack) -> {
			if (owned.contains(address) && Arrays.stream(stack).anyMatch(s -> s.getClassName().equals(LodMesh.class.getName()))) scratch.add(address);
		});
		check(scratch.isEmpty(), "Mesh worker scratch leaked: " + scratch);
		mesh.close();
		for (long b : mesh.buffers()) LodNative.release(b);
		clip.free();
		System.out.println("Far-terrain cache identity, worker shutdown and native cleanup passed");
	}

	private static Set<Long> allocations() {
		Set<Long> addresses = new HashSet<>();
		MemoryUtil.memReport((address, size, thread, name, stack) -> addresses.add(address));
		return addresses;
	}

	private static Object field(LodMesh mesh, String name) throws Exception {
		var field = LodMesh.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(mesh);
	}

	@SuppressWarnings("unchecked")
	private static Queue<long[]> queue(LodMesh mesh, String name) throws Exception {
		return (Queue<long[]>) field(mesh, name);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
