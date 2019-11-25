package nachos.threads;

import nachos.machine.*;

// import java.util.TreeSet;
// import java.util.HashSet;
// import java.util.Iterator;
// import java.util.PriorityQueue;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}
	
	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param	transferPriority	<tt>true</tt> if this queue should
	 *					transfer priority from waiting threads
	 *					to the owning thread.
	 * @return	a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
				   
		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
				   
		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());
				   
		Lib.assertTrue(priority >= priorityMinimum &&
			   priority <= priorityMaximum);
		
		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
				   
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority+1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
				   
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority-1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public static void selfTest() {
		System.out.println("Priority Scheduler Self Test:\n");
		Test1.run();
		System.out.println("");
		// Test2.run();
		// System.out.println("");
		// Test3.run();
		// System.out.println("");
		Test4.run();
		// System.out.println("");
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;    

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param	thread	the thread whose scheduling state to return.
	 * @return	the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}


	static protected void changeEffectivePriority (PriorityQueue pq, ThreadState ts, int val) {
			//no hay colas de espera, cambiar y regresar
			if (pq == null) {
				ts.setEffectivePriority(val);
				return;
			}

			//remover, actualizar y colocar
			pq.threadQueue.remove(ts);
			ts.setEffectivePriority(val);
			System.out.println("agregado en changeEffectivePriority");
			pq.threadQueue.add(ts);

			//si la ultima donacion no corresponde con el thread de turno
			if (pq.lastDonation != pq.threadQueue.peek().getEffectivePriority()) {
				//quitar donacion y hacer la nueva
				if (pq.transferPriority) {
					pq.holder.revoke(pq.lastDonation);
					pq.holder.donate(val);
				}
				pq.lastDonation = val;
			}
		}




	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		//quitar donacion al holder actual
		//cambiar holder y hacer donacion
		private ThreadState updateHolder (ThreadState ts) {
			if ((holder != null) && transferPriority)
				holder.revoke(lastDonation);

			holder = ts;

			if ((holder != null) && transferPriority)
				holder.donate(lastDonation);

			return ts;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());

			ThreadState ts = getThreadState(thread);

			getThreadState(thread).waitForAccess(this);

			//agregar si no esta en cola de espera
			if (!threadQueue.contains(ts))
				System.out.println("agregado en waitForAccess");
				threadQueue.add(getThreadState(thread));

			if (ts.getEffectivePriority() > lastDonation) {
				//actualizar prioridad
				if ((holder != null) && transferPriority) {
					holder.revoke(lastDonation);
					holder.donate(ts.getEffectivePriority());
				}

				lastDonation = ts.getEffectivePriority();
			}
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
			updateHolder(getThreadState(thread));
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			
			//el actual se retira
			updateHolder(null);

			ThreadState ts = threadQueue.poll();

			if (threadQueue.size() > 0)
				lastDonation = threadQueue.peek().getEffectivePriority();
			else
				lastDonation = 0;

			//nuevo holder
			updateHolder(ts);

			//limpiar cola asociada, el thread ahora esta en ejecucion, no esperando
			if (ts != null) {
				ts.waitingOnQueue = null;
				return ts.thread;
			} else {
				return null;
			}
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected ThreadState pickNextThread() {
			return threadQueue.peek();
		}
		
		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;

		//cola de threads
		private java.util.PriorityQueue<ThreadState> threadQueue = new java.util.PriorityQueue<ThreadState>();
		private ThreadState holder = null;
		private int lastDonation = 0;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue
	 * it's waiting for, if any.
	 *
	 * @see	nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState 
					implements Comparable<ThreadState> {
		//implementa Comparable para poder usar PriorityQueue para ordenar

		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param	thread	the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			
			setPriority(priorityDefault);

			//llevar control de donaciones para actualizar mas rapidamente
			for (int i=0; i<donations.length; i++)
				donations[i] = 0;
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return	the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return	the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			return effectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
			return;
			
			this.priority = priority;
			//cambiar la prioridad y actualizar lista
			if (priority > effectivePriority)
				changeEffectivePriority(waitingOnQueue, this, priority);
		}

		protected void setEffectivePriority(int priority) {
			effectivePriority = priority;
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param	waitQueue	the queue that the associated thread is
		 *				now waiting on.
		 *
		 * @see	nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			//tiempo que se puso en cola para ordenar
			time = Machine.timer().getTime();
			//cola de espera del thread
			waitingOnQueue = waitQueue;
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see	nachos.threads.ThreadQueue#acquire
		 * @see	nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire (PriorityQueue waitQueue) {
			//actualizar holder
			//se realiza en nextThread()
		}

		public void donate (int val) {
			donations[val]++;

			//actualiza prioridad y cola
			if (val > effectivePriority) {
				effectivePriority = val;
				changeEffectivePriority(waitingOnQueue, this, effectivePriority);
			}
		}

		public void revoke (int val) {
			donations[val]--;

			int newPrio = priority;

			//revisar si queda alguna donacion
			//revisar el arreglo es mas rapido que ir a ver las colas buscando que donacion es necesaria
			for (int i=priority; i<donations.length; i++) {
				if (donations[i] > 0)
					newPrio = i;
			}

			if (newPrio != effectivePriority) {
				effectivePriority = newPrio;
				changeEffectivePriority(waitingOnQueue, this, effectivePriority);
			}
		}

/*
		public void changeEffectivePriority (PriorityQueue pq, ThreadState ts, int val) {
			//no hay colas de espera, cambiar y regresar
			if (pq == null) {
				ts.effectivePriority = val;
				return;
			}

			//remover, actualizar y colocar
			pq.threadQueue.remove(ts);
			ts.effectivePriority = val;
			System.out.println("agregado en changeEffectivePriority");
			pq.threadQueue.add(ts);

			//si la ultima donacion no corresponde con el thread de turno
			if (pq.lastDonation != pq.threadQueue.peek().getEffectivePriority()) {
				//quitar donacion y hacer la nueva
				if (pq.transferPriority) {
					pq.holder.revoke(pq.lastDonation);
					pq.holder.donate(val);
				}
				pq.lastDonation = val;
			}
		}
*/

		//comparar para poder usar PriorityQueue de Java
		public int compareTo (ThreadState o) {
			int myPrio = effectivePriority;
			int otherPrio = o.getEffectivePriority();

			if (myPrio < otherPrio)
				return 1;
			if (myPrio == otherPrio)
				if (time > o.time)
					return 1;

			return -1;
		}

		/** The thread with which this object is associated. */	   
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		protected int effectivePriority;

		protected long time = Long.MAX_VALUE;

		//cola de espera
		protected PriorityQueue waitingOnQueue = null;

		//donaciones realizadas
		protected int donations[] = new int[PriorityScheduler.priorityMaximum];
	}
}



// TESTS

/**
 * Tests the scheduler with out any priority donation, or different
 * priorities. Yes I know this is kind of pointless considering it's
 * tested by pretty much all the other tests simply running, but meh,
 * its easy.
 * Note, if its run at just the wrong time, then one of the threads
 * can get preempted. This makes the test appear to fail. There is no
 * way to get around this as fork automatically reenables interrupts.
 */
class Test1 {
    public static void run() {
        System.out.println("Testing basic scheduling:");
        KThread threads[] = new KThread[4];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new KThread(new Thread(i));
            threads[i].fork();
        }

        for (int i = 0; i < threads.length; i++)
            threads[i].join();
    }

    private static class Thread implements Runnable {
        private int num;
        public Thread(int n) {
            num = n;
        }
        public void run() {
            for (int i = 1; i < 3; i++) {
                System.out.println("Thread: " + num + " looping");
                KThread.yield();
            }
        }
    }
}

/**
 * Tests basic scheduling with priorities involved.
 */
class Test2 {
    public static void run() {
        PriorityScheduler sched = (PriorityScheduler) ThreadedKernel.scheduler;

        System.out.println("Testing priority scheduling:");
        KThread threads[] = new KThread[4];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new KThread(new Thread(3 - i));
            boolean intStatus = Machine.interrupt().disable();
            sched.setPriority(threads[i], 7 - i);
            Machine.interrupt().restore(intStatus);
            threads[i].fork();
        }

        for (int i = 0; i < threads.length; i++)
            threads[i].join();
    }

    private static class Thread implements Runnable {
        private int num;
        public Thread(int n) {
            num = n;
        }
        public void run() {
            for (int i = 1; i < 3; i++) {
                System.out.println("Priority: " + num + " looping");
                KThread.yield();
            }
        }
    }
}

/**
 * Tests priority donation by running 4 threads, 2 with equal priority
 * and the other with higher and lower priority. The high priority thread
 * then waits on the low priority one and we see how long it takes to get
 * scheduled.
 */
class Test3 {
    static boolean high_run = false;

    public static void run() {
        Lock l = new Lock();
        PriorityScheduler sched = (PriorityScheduler) ThreadedKernel.scheduler;

        System.out.println("Testing basic priority inversion:");

        KThread low = new KThread(new Low(l));
        KThread med1 = new KThread(new Med(1));
        KThread med2 = new KThread(new Med(2));
        KThread high = new KThread(new High(l));

        boolean intStatus = Machine.interrupt().disable();
        sched.setPriority(high, 4);
        sched.setPriority(med1, 3);
        sched.setPriority(med2, 3);
        sched.setPriority(low, 1);
        Machine.interrupt().restore(intStatus);

        low.fork();
        KThread.yield();
        med1.fork();
        high.fork();
        med2.fork();
        KThread.yield();

        /* Make sure its all finished before quitting */
        low.join();
        med2.join();
        med1.join();
        high.join();
    }

    private static class High implements Runnable {
        private Lock lock;

        public High(Lock l) {
            lock = l;
        }

        public void run() {
            System.out.println("High priority thread sleeping");
            lock.acquire();
            Test3.high_run = true;
            System.out.println("High priority thread woken");
            lock.release();
        }
    }

    private static class Med implements Runnable {
        int num;
        public Med(int n) {
            num = n;
        }
        public void run() {
            for (int i = 1; i < 3; i++)
                KThread.yield();

            if (Test3.high_run)
                System.out.println("High thread finished before thread " + num + ".");
            else
                System.out.println("Error, meduim priority thread finished"
                                   + " before high priority one!");
        }
    }

    private static class Low implements Runnable {
        private Lock lock;

        public Low(Lock l) {
            lock = l;
        }

        public void run() {
            System.out.println("Low priority thread running");
            lock.acquire();
            KThread.yield();
            System.out.println("Low priority thread finishing");
            lock.release();
        }
    }
}

/**
 * A more advanced priority inversion test.
 */
class Test4 {
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