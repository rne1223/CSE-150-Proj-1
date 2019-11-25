package nachos.threads;

import nachos.machine.*;
/*
 * PrioritySchedulerTest
 */
public class PrioritySchedulerTest {
    static boolean high_run = false;

    public static void run() {
        Lock l1 = new Lock();
        Lock l2 = new Lock();
        Lock l3 = new Lock();
        PriorityScheduler sched = (PriorityScheduler) ThreadedKernel.scheduler;

        System.out.println("Testing complex priority inversion:");

        KThread t1 = new KThread(new Thread(l1, 1));
        KThread t2 = new KThread(new Thread(l2, l1, 2));
        KThread t3 = new KThread(new Thread(l3, l2, 3));
        KThread t4 = new KThread(new Thread(l3, 4));

        t1.fork();
        t2.fork();
        t3.fork();
        t4.fork();

        KThread.yield();

        boolean intStatus = Machine.interrupt().disable();
        sched.setPriority(t4, 3);
        if (sched.getEffectivePriority(t1) != 3)
            System.out.println("Priority not correctly donated.");
        else
            System.out.println("Priority correctly donated.");
        Machine.interrupt().restore(intStatus);

        KThread.yield();

        intStatus = Machine.interrupt().disable();
        if (sched.getEffectivePriority(t1) != 1)
            System.out.println("Priority donation not revoked.");
        else
            System.out.println("Priority donation correctly revoked.");
        Machine.interrupt().restore(intStatus);


        /* Make sure its all finished before quitting */
        t1.join();
        t2.join();
        t3.join();
        t4.join();
    }

    private static class Thread implements Runnable {
        private Lock lock;
        private Lock altLock;
        private int num;

        public Thread(Lock l, int n) {
            lock = l;
            num = n;
            altLock = null;
        }

        public Thread(Lock l, Lock a, int n) {
            lock = l;
            num = n;
            altLock = a;
        }

        public void run() {
            System.out.println("Thread: " + num + " sleeping");
            lock.acquire();
            if (altLock != null)
                altLock.acquire();

            KThread.yield();

            System.out.println("Thread: " + num + " woken");
            if (altLock != null)
                altLock.release();
            lock.release();
        }
    }
}    