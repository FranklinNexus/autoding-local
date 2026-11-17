package android.os;
import java.util.PriorityQueue;
public final class Handler {
    public static long now, sequence;
    private static final class Job implements Comparable<Job> {
        Handler owner; Runnable work; long at, sequence;
        public int compareTo(Job other) { int order = Long.compare(at, other.at); return order == 0 ? Long.compare(sequence, other.sequence) : order; }
    }
    private static final PriorityQueue<Job> jobs = new PriorityQueue<>();
    public Handler(Looper looper) {}
    public boolean post(Runnable work) { return postDelayed(work, 0); }
    public boolean postDelayed(Runnable work, long delay) {
        Job job = new Job(); job.owner = this; job.work = work; job.at = now + delay; job.sequence = sequence++;
        jobs.add(job); return true;
    }
    public void removeCallbacksAndMessages(Object ignored) { jobs.removeIf(job -> job.owner == this); }
    public static boolean step() { Job job = jobs.poll(); if (job == null) return false; now = job.at; job.work.run(); return true; }
    public static void drain() { int count = 0; while (step()) if (++count > 1000) throw new AssertionError("scheduler runaway"); }
    public static void reset() { jobs.clear(); now = 0; sequence = 0; }
}
