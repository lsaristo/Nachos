package nachos.threads;
import nachos.machine.*;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler 
{

    @Override
    protected int getPriorityMaximum() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected int getPriorityMinimum() {
        return 1;
    }

    /**
     * Get a ThreadState object associated with a given KThread.
     *
     * This method is overridden to create ThreadState2's if the object does 
     * not already exist versus the origin method in PriorityScheduler which 
     * creates ThreadState objects.
     *
     * @see ThreadState2 for details on specific differences in behavior versus 
     * ThreadState
     * @param thread that we're looking up the ThreadState2 for.
     * @return ThreadState returned for this thread. 
     */
    @Override
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null) {
            thread.schedulingState = new ThreadState2(thread);
        }
        return (ThreadState) thread.schedulingState;
    }

    /**
     * Allocate a new lottery thread queue.
     *
     * @param transferPriority <tt>true</tt> if this queue should
     * transfer tickets from waiting threads
     * to the owning thread.
     * @return a new lottery thread queue.
     */
    @Override
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new LotteryQueue(transferPriority); 
    }

    public static void selfTest() {
        LotterySchedulerTest.runall();
    }


    /** 
     * LotteryQueue inner class. Extends PriorityQueue 
     *
     * @see PriorityQueue.java
     */
    protected class LotteryQueue extends PriorityQueue
    {
        LotteryQueue(boolean transferPriority) {
            super(transferPriority, kQueueIndiciesNeeded);
            checkMode = kCheckModeLottery; 
        }

        /** 
         * Invalidate the cached copy of the next thread to be returned. 
         * Will force recalculation on the next call to pickNextThread() or 
         * nextThread() 
         */
        protected void invalidateCachedThread() { 
            nextThreadOut = null; 
        }



        /**
         * getQueue returns the backing queue for this LotteryQueue.
         * <p>
         * In the lottery sheduler, the index parameter is ignored since we're 
         * only using a single versus the PriorityQueue where we use 
         * priorityMaximum of these queues. For this reason calls to 
         * getQueue(index) simply forwards the call to getQueue() which returns 
         * the first queue (i.e., index=0) from the backing store 
         * arrayOfQueues[]. 
         */
        @Override 
        protected LinkedList<ThreadState> getQueue(int index) { 
            return getQueue(); 
        }


        /** 
         * To be used in the LotteryScheduler only. 
         * Implied array index 0 
         */
        private final LinkedList<ThreadState> getQueue() { 
            return super.getQueue(0); 
        }


        /** 
         * Return the next thread from this queue. Override of 
         * PriorityQueue.nextThread() 
         */
        @Override
        public KThread nextThread()  {
            if(nextThreadOut != null) {
                Lib.assertTrue(super.nextThread() == nextThreadOut.thread);
                ThreadState outThread = nextThreadOut;
                invalidateCachedThread();
                return outThread.thread; 
            } else if(pickNextThread() != null)  {
                return nextThread();
            } else {
                if(queueID == KThread.getReadyQueueID() && emptyReadyWarning) {
                    Lib.debug(dbgPSched, "WARNING: Ready queue is empty."); 
                    print();
                    emptyReadyWarning = false;
                }
                return null;
            }
        }

        /** 
         * Determine the next thread to remove from this queue.
         *
         * Winning ticket range = [0.0, 1.0)
         * Because the choice of next thread is stochastic, if this method is 
         * called, we must store the value returned by it for later use with 
         * nextThread().
         *
         * @return the next ThreadState that will be chosen from the queue. 
         */ 
        @Override
        public ThreadState pickNextThread() 
            if(nextThreadOut != null) { return nextThreadOut; }
            nextThreadOut = null;
            double sumOfThreadPriorities = 0.0;
            double indexOffset = 0.0;
            double winningTicket = randMaker.nextDouble(); 
            int i = 0;
            int numThreadsOnThisQueue = getQueue().size();
            double[] selectionArray = new double[numThreadsOnThisQueue];

            for(ThreadState thread : getQueue()) {
                sumOfThreadPriorities += thread.getEffectivePriority();
            }
        
            // These two statements (should be) logically equivalent so verify 
            // that this is true before returning 
            if(sumOfThreadPriorities == 0 || numThreadsOnThisQueue == 0) {
                Lib.assertTrue(sumOfThreadPriorities == 0 && numThreadsOnThisQueue == 0);
                return null;
            }

            for(ThreadState thread : getQueue()) {
                selectionArray[i] = 
                    ((double)thread.getEffectivePriority() 
                    / sumOfThreadPriorities) 
                    + indexOffset;
                indexOffset = selectionArray[i];

                // Lower bound inclusive, upper bound exclusive*
                if(winningTicket < selectionArray[i]) {
                    nextThreadOut = getQueue().get(i);
                    Lib.assertTrue(nextThreadOut != null);
                    return nextThreadOut;   
                }
                i++;
            }
            Lib.assertNotReached("Did not find a match in pickNextThread."); 
            return null; 
        }

        /* LotteryQueue class properties */
        private Random randMaker = new Random();
        private ThreadState nextThreadOut = null;
        private static final int kQueueIndiciesNeeded = 0; 

    } // End of LotteryQueue class 

    /** 
     * ThreadState2 inner class. 
     * Extends ThreadState 
     *
     * @see PriorityScheduler.ThreadState
     */
    protected class ThreadState2 extends ThreadState
    {
        ThreadState2(KThread inThread) {
            super(inThread);
        }


        /**
         * Move a thread from one queue to another.
         *
         * In PriorityQueue this is required since we have different queues for 
         * different priority levels. Since LotteryQueue only uses one backing 
         * queue, this method is not needed. To maintain compatibility with 
         * existing methods in PriorityQueue, this method is overridden here to 
         * do nothing. 
         */
        @Override
        protected void moveThreadOnQueue(PriorityQueue queue, int from, int to) {
        } 


        /** 
         * Gets this thread's effective priority. 
         * 
         * Override of ThreadState.getEffectivePriority(). Here, effective 
         * priority is the sum of this thread's intrinsic priority as well as 
         * the sum of all valid donations to this thread. 
         *
         * @return this thread's effective priority. 
         */
        @Override
        public int getEffectivePriority() {
            int outSum = getPriority();
            for(DonationTracker dt : donationManagementDB) {{
                outSum += dt.donation;
                Lib.assertTrue(dt.donor.threadsDonatedTo.contains(this)); 
            }
            return outSum;
        }



        /**
         * Check if effective priority may have changed and needs to be 
         * recalculated.
         *
         * In a priority queue, a thread's effective priority does not 
         * necessarily change after receiving a priority donation from another 
         * thread. As such, this method is used to avoid needlessly 
         * recalculating effective priority which can be expensive. 
         * In a lottery queue, however, any donation or revocation of priority 
         * changes a thread's effective priority and so this method is 
         * overridden for the lottery queue to always return true, forcing 
         * recalculation after every priority donation and revocation. 
         *
         * @param   donor   Donor that may have influenced this thread priority.
         * @param   event   What happend to trigger this possible donation. 
         * @return true if this thread's priority should be recalculated.
         */
        @Override
        protected boolean priorityUpdateRequired(DonationTracker donor, String event) {
            return true;
        }



        /**
         * Recalculate a thread's effective priority to determine if it has 
         * changed and if so, propagate that change to all other threads it has 
         * donated to. 
         *
         * In a lottery queue, calculation of effective priority is done in 
         * real time via getEffectivePriority(). Moreover, we already know that 
         * any change in donation changes this thread's effective priority. 
         * Consequently, this method simply forwards a call to 
         * propagatePriorityDonation() to notify all other threads this thread 
         * has donated to of the change in its EP to maintain transitivity of 
         * donation and invalidates the cache of any queue this thread is 
         * sitting on, forcing recalculation in pickNextThread or nextThread. 
         */
        @Override
        protected void calculatePriorityDonation() {
            propagatePriorityDonation();
            for( PriorityQueue queue : queuesThisThreadIsOn) {
                ((LotteryQueue)queue).invalidateCachedThread();
            }
        }
    } // End of ThreadState2 class
} // End of LotteryScheduler class
