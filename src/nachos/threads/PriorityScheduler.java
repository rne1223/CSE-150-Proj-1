package nachos.threads;
import nachos.machine.*;
import java.util.LinkedList;
import java.util.Iterator;   
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Comparator;
import nachos.threads.PrioritySchedulerTest;

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

public class PriorityScheduler extends Scheduler 
{

	// Variable for debugging
	private static final char dbgQueue = 'q';

	public ThreadQueue newThreadQueue(boolean transferPriority) 
	{
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) 
	{
		Lib.assertTrue(Machine.interrupt().disabled());
		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) 
	{
		Lib.assertTrue(Machine.interrupt().disabled());
		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) 
	{
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);
		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() 
	{
		boolean intStatus = Machine.interrupt().disable();
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority+1);
		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() 
	{
		boolean intStatus = Machine.interrupt().disable();
		KThread thread = KThread.currentThread();
		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority-1);
		Machine.interrupt().restore(intStatus);
		return true;
	}

	public static void selfTest()
	{
		System.out.println("Running Priority Donation Tests");		
		// PrioritySchedulerTest.run();
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

	protected ThreadState getThreadState(KThread thread) 
	{
		Lib.assertTrue(Machine.interrupt().disabled());
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}//end threadstate

	protected class PriorityQueue extends ThreadQueue 
	{
		PriorityQueue(boolean transferPriority) 
		{
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) 
		{
			Lib.assertTrue(Machine.interrupt().disabled());
			// Lib.debug(dbgQueue, printQueue());
			getThreadState(thread).waitForAccess(this);
			// Lib.debug(dbgQueue,"PriorityQueue waitForAccess: " + printQueue());
		}

		public void acquire(KThread thread) 
		{
			Lib.assertTrue(Machine.interrupt().disabled());
			// Lib.debug(dbgQueue, printQueue());
			getThreadState(thread).acquire(this);
			// Lib.debug(dbgQueue, "PriorityQueue acquire "+ printQueue());
		}

		public KThread nextThread() 
		{
			Lib.assertTrue(Machine.interrupt().disabled());
			if (threadStates.isEmpty())
				return null;

			//high priority off treeset
			ThreadState tState = threadStates.pollLast();
			tState.placement = 0;
			KThread thread = tState.thread;

			if (thread != null)
			{
				if (this.owner != null)
				{
					//Remove from queue
					this.owner.ownedQueues.remove(this);
					this.owner.effectivePriority = 0;

					//Update priority
					Iterator<PriorityQueue> it = this.owner.ownedQueues.iterator();
					while(it.hasNext())
					{
						PriorityQueue temp = it.next();

						if (temp.pickNextThread() == null)
							continue;
						if(temp.pickNextThread().getWinningPriority() > this.owner.getEffectivePriority())
							this.owner.effectivePriority = temp.pickNextThread().getWinningPriority();
					}
				}
				
				((ThreadState) thread.schedulingState).acquire(this);
				((ThreadState) thread.schedulingState).waitingQueue = null;
			}
			return thread;
		}

		protected ThreadState pickNextThread() 
		{
			Lib.assertTrue(Machine.interrupt().disabled());
			if (threadStates.isEmpty())
				return null;
			return threadStates.last();
		}

		public String printQueue() {
			Lib.assertTrue(Machine.interrupt().disabled());
			Iterator<ThreadState> it = threadStates.descendingIterator();

			int i = 0;
			String temp= "--ThSt : " + threadStates.size() + " items [ ";
			while (it.hasNext())
			{
				ThreadState curr = it.next();
				temp += curr.thread.getName() + " EP: " + curr.getEffectivePriority() + 
												// " Own: " +  curr.ownedQueues. + ", "
												" Ow: " +  curr.waitingQueue.owner.thread.getName() + " " +
												" OwS: " +  curr.waitingQueue.owner.ownedQueues.size() + ", ";
				i++;
			}
			temp += " ]";

			if (pickNextThread() != null)
				System.out.println("Thread to be popped:" + pickNextThread().thread);
			return temp;
		}

		@Override
		public void print() {
			// TODO Auto-generated method stub

		}


		public boolean transferPriority;

		// holds threadstates
		public TreeSet<ThreadState> threadStates = new TreeSet<ThreadState>(new ThreadComparator());
		public ThreadState owner = null;

	}//end class priorityqueue


	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue
	 * it's waiting for, if any.
	 *
	 * @see	nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState 
	{
		public ThreadState(KThread thread) 
		{
			this.thread = thread;
			this.time = 0;
			this.placement = 0;
			if(Lib.test(dbgQueue))
			{
				this.priority = ((int)(Math.random()*7)+1);
				setPriority(this.priority);
			}
			else
			{
				this.priority = priorityDefault;
				setPriority(priorityDefault);
			}
			effectivePriority = priorityMinimum;
		}

		public int getPriority() 
		{
			return priority;
		}

		public int getEffectivePriority() 
		{
			Lib.assertTrue(Machine.interrupt().disabled());
			return getWinningPriority();
		}

		public void setPriority(int priority) 
		{
			Lib.assertTrue(Machine.interrupt().disabled());
			if (this.priority == priority)
				return;

			this.priority = priority;
			recalculateThreadScheduling();
			update();
		}

		public int getWinningPriority()
		{
			return priority > effectivePriority ? priority : effectivePriority;
		}

		public void waitForAccess(PriorityQueue waitQueue) 
		{
			Lib.assertTrue(Machine.interrupt().disabled());
			// Lib.assertTrue(waitingQueue == null);

			time = Machine.timer().getTime();
			waitQueue.threadStates.add(this);
			waitingQueue = waitQueue;
			// Lib.debug(dbgQueue, printOwnedQueues(waitQueue));

			if(placement == 0)
				placement = placementInc++;
			update();
		}

		public void acquire(PriorityQueue waitQueue) 
		{
			Lib.assertTrue(Machine.interrupt().disabled());
			if (waitQueue.owner != null)
				waitQueue.owner.ownedQueues.remove(waitQueue);

			waitQueue.owner = this;
			// Lib.debug(dbgQueue, printOwnedQueues(waitQueue));
			ownedQueues.add(waitQueue);
			// Lib.debug(dbgQueue, printOwnedQueues(waitQueue));

			if (waitQueue.pickNextThread() == null)
				return;

			if (waitQueue.pickNextThread().getEffectivePriority() > this.getEffectivePriority() && 
				waitQueue.transferPriority)
			{
				this.effectivePriority = waitQueue.pickNextThread().getEffectivePriority();
				recalculateThreadScheduling();
				update();
			}
		}

		public void update() 
		{
			Lib.debug(dbgQueue, "Before Update " + printOwnedQueues(waitingQueue));
			if (waitingQueue == null)
				return;
			else if (waitingQueue.owner == null)
				return;
			else if (waitingQueue.pickNextThread() == null)
				return;

			if (waitingQueue.transferPriority && 
				waitingQueue.pickNextThread().getWinningPriority() > waitingQueue.owner.getWinningPriority())
			{
				waitingQueue.owner.effectivePriority = waitingQueue.pickNextThread().getWinningPriority();
				waitingQueue.owner.recalculateThreadScheduling();
				waitingQueue.owner.update();
			}
			Lib.debug(dbgQueue, "After Update " + printOwnedQueues(waitingQueue));
		}

		@Override
		public boolean equals(Object o)
		{
			ThreadState curr = (ThreadState)o;

			return (curr.placement == this.placement);
		}

		//Updates the order
		public void recalculateThreadScheduling()
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			if (waitingQueue != null)
			{
				waitingQueue.threadStates.remove(this);
				waitingQueue.threadStates.add(this);
			}
		}

		public String printOwnedQueues(PriorityQueue waitQueue)
		{
			if(waitQueue == null)
				return "waitQueue is null";

			// Iterator<PriorityQueue> it = waitQueue.owner.ownedQueues.iterator();
			Iterator<ThreadState> it = waitQueue.threadStates.iterator();
			int i = 0;
			String temp = "";

			if(waitQueue != null)
				temp= "--Owner: " + waitQueue.owner.thread.getName() + " Pr: " + waitQueue.owner.getWinningPriority() +" [ ";
			// temp = "[ ";
			while(it.hasNext())
			{
				ThreadState ts = it.next();

				if(ts == null)
					continue;

				temp += ts.thread.getName() + " Pr: " + ts.getWinningPriority() + ", ";
			}
			temp += " ]";

			// if (waitQueue.pickNextThread() != null)
			// 	System.out.println("Thread to be popped:" + waitQueue.pickNextThread().thread);
			// return temp;
			return temp;
		}

		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;

		public long time = 0;
		public int effectivePriority;
		public int placement;
		public HashSet<PriorityQueue> ownedQueues = new HashSet<PriorityQueue>();
		public PriorityQueue waitingQueue = null;
	}//end class Threadstate

	public static int placementInc = 1;
	
	class ThreadComparator implements Comparator<ThreadState>
	{
		public int compare(ThreadState a, ThreadState b)
		{
			if (a.getWinningPriority() == b.getWinningPriority() && a.time != b.time)
			{
				//Time is in reverse order since we want the minimum time to be ordered above the maximum time
				return (int)((b.time-a.time));
			}//end if
			else if (a.getWinningPriority() != b.getWinningPriority())
			{
				return a.getWinningPriority() - b.getWinningPriority();
			}//end else if
			else
			{
				return a.placement-b.placement;
			}//end else
		}//end compare
	}//end threadcomparator
}//end class priorityscheduler