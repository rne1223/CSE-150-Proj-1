package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;

////////////////////////////////////////////////////////////////////////////////
//
// PRIORITY SCHEDULER
//
////////////////////////////////////////////////////////////////////////////////

public class PriorityScheduler extends Scheduler {

    public static final int priorityDefault = 1;
    public static final int priorityMinimum = 0;
    public static final int priorityMaximum = 7;

    // -------------------------------------------------------------------------
    public PriorityScheduler() { }

    // -------------------------------------------------------------------------
    // public static void selfTest() {
    //     PrioritySchedulerTest.runTest();
    // }

    // -------------------------------------------------------------------------
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
    }

    // -------------------------------------------------------------------------
    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return ThreadState.read(thread).priority;
    }

    // -------------------------------------------------------------------------
    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        // Get the default priority of the thread.
        final ThreadState state = ThreadState.read(thread);
        int priority = state.priority;

        // Loop over each queue owned by the thread.  If it is a queue that
        // transfers priority, loop over each thread waiting on it and search
        // for priorities that are less than the one calculated above.
        for (final PriorityQueue queue : state.owned)
            if (queue.transferPriority)
                for (final KThread pendingOwner : queue.pendingOwners)
                    priority = Math.min(priority,
                        this.getEffectivePriority(pendingOwner));

        // Return the effective priority calculated above.
        return priority;
    }

    // -------------------------------------------------------------------------
    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum &&
                       priority <= priorityMaximum);

        ThreadState.read(thread).priority = priority;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // PRIORITY QUEUE
    //
    ////////////////////////////////////////////////////////////////////////////

    private final class PriorityQueue extends ThreadQueue {

        private final boolean transferPriority;

        /** A list of threads that will eventually own the resource. */
        private final ArrayList<KThread> pendingOwners =
            new ArrayList<KThread>();

        /** The current owner of the resource, or null if there is no owner. */
        private KThread owner = null;
        
        // ---------------------------------------------------------------------
        private PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
        }

        // ---------------------------------------------------------------------
        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());

            //
            // ??? Semaphore.P() will call ThreadQueue.waitForAccess(), not
            //     ThreadQueue.acquire(), when there are no pending owners of
            //     the queue.  This seems to contradict the documentation:
            //
            //     * Notify this thread queue that the specified thread is
            //     * waiting for access. This method should only be called if
            //     * the thread cannot immediately obtain access (e.g. if the
            //     * thread wants to acquire a lock but another thread already
            //     * holds the lock).
            //
            //     Lib.assertTrue(this.owner != null);
            //
            //     This implementation will add the thread to the queue, and
            //     that means it is possible this object will have entries in
            //     the queue when there is no owner.
            //

            //
            // ??? KThread.ready() calls ThreadQueue.waitForAccess() multiple
            //     times.
            //
            //     Lib.assertTrue(!this.pendingOwners.contains(thread));
            //
            //     This implementation will add the thread only once.
            //

            if (!this.pendingOwners.contains(thread))
                this.pendingOwners.add(thread);
        }

        // ---------------------------------------------------------------------
        public void print() {
            System.out.print("owner = " + this.owner);
            for (final KThread thread : this.pendingOwners)
                System.out.print("  [*] " + thread);
        }

        // ---------------------------------------------------------------------
        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            Lib.assertTrue(this.owner == null);
            Lib.assertTrue(this.pendingOwners.size() == 0);

            // Record in the thread state that the thread owns this instance.
            ThreadState.read(thread).owned.add(this);

            // Record in this instance that the thread is the owner.
            this.owner = thread;
        }

        // ---------------------------------------------------------------------
        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());

            //
            // ??? Semaphore.V() calls this method when there is no owner.
            //
            //     Lib.assertTrue(this.owner != null);
            //

            // Return null if there are no threads waiting on this instance.
            KThread thread = null;
            if (this.pendingOwners.size() > 0) {

                // Mark the oldest thread as the next owner.
                int index = 0;
                thread = this.pendingOwners.get(index);
                int priority = getEffectivePriority(thread);

                // Search for threads that have higher priorities.
                for (int i = 1; i < this.pendingOwners.size(); i++) {
                    final KThread t = this.pendingOwners.get(i);
                    final int p = getEffectivePriority(t);
                    if (p < priority) {
                        index = i;
                        priority = p;
                        thread = t;
                    }
                }

                // Remove the best match from the queue.
                this.pendingOwners.remove(index);
            }

            // Inform the old owner it no longer owns this instance.
            if (this.owner != null)
                ThreadState.read(this.owner).owned.remove(this);

            // Record in this instance that the new thread is the owner.
            this.owner = thread;

            // Record in the thread state that the new thead owns this instance.
            if (this.owner != null)
                ThreadState.read(this.owner).owned.add(this);

            // Return the new owner of this instance, or null.
            return this.owner;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // THREAD STATE
    //
    ////////////////////////////////////////////////////////////////////////////

    private final static class ThreadState {

        private int priority = priorityDefault;

        /** An array of resources owned by the thread instance. */
        private final ArrayList<PriorityQueue> owned =
            new ArrayList<PriorityQueue>();

        // ---------------------------------------------------------------------
        private ThreadState() { }

        // ---------------------------------------------------------------------
        private static ThreadState read(KThread thread) {
            if (thread.schedulingState == null)
                thread.schedulingState = new ThreadState();

            return (ThreadState) thread.schedulingState;
        }
    }
}