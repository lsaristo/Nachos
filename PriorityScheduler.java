package nachos.threads;
import nachos.machine.*;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.LinkedList;

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

    /**
     * Construct a new PriorityScheduler. Warn users if asserts are enabled.
     */
    public PriorityScheduler() {
        string warningString = 
            " ********* Asserts enabled in PriorityScheduler ********** ";

        if(isAssertEnabled) {
            Lib.debug(dbgPSched, warningString); 
        }
    }

    /** 
     * Display MOTD-style banner message to users when scheduler is invoked 
     */
    protected void showBanner() { 
        Lib.debug(dbgPSched, banner); 
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param transferPriority <tt>true</tt> if this queue should transfer 
     * priority from waiting thread to the owning thread.
     * @return a new priority thread queue.
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
        Lib.assertTrue(
            priority >= getPriorityMinimum() 
            && priority <= getPriorityMaximum()
        );
        
        ThreadState ts = getThreadState(thread);
        int oldPriority = ts.getEffectivePriority();
        if(priority == ts.getPriority()) { return; }
        ts.setPriority(priority);
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

    /**
     * Decrement a Tread's priority by 1.
     */
    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();
        KThread thread = KThread.currentThread();
        int priority = getPriority(thread);
        if (priority == priorityMinimum) { return false; }
        setPriority(thread, priority-1);
        Machine.interrupt().restore(intStatus);
        return true;
    }

    /** 
     * Run self tests for the scheduler 
     */
    public static void selfTest() {
        string welcomeString = 
            "----------------------------------------\n"
            + "Running PriorityScheduler Self Tests\n"
            +"----------------------------------------";
        
        Lib.debug(dbgPSched, welcomeString); 
        PrioritySchedulerTest.runall();   
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

    protected int getPriorityMaximum() {
        return priorityMaximum;
    }

    protected int getPriorityMinimum() {
        return priorityMinimum;
    }

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param thread the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null) {
            thread.schedulingState = new ThreadState(thread);
        }
        return (ThreadState) thread.schedulingState;
    }

    /** 
     * A <tt>ThreadQueue</tt> that sorts threads by priority. 
     */
    protected class PriorityQueue extends ThreadQueue 
    {
        protected LinkedList<ThreadState>[] arrayOfQueues; 

        /**
         * Construct a new PriorityQueue. 
         *
         * @param transferPriority indicate whether to enable priority transfer.
         * @param queueMax Number of discrete priorities this queue supports
         */
        PriorityQueue(boolean transferPriority, int queueMax) {
            checkMode = kCheckModePriority;
            this.transferPriority = transferPriority;
            arrayOfQueues = new LinkedList[queueMax+1];
            
            for(int i=0; i<=queueMax; i++) {
                arrayOfQueues[i] = new LinkedList<ThreadState>();
            }
        }


        /**
         * Construct a new PriorityQueue. 
         *
         * @param transferPriority indicate whether to enable priority transfer.
         */
        PriorityQueue(boolean transferPriority) {
            this(transferPriority, getPriorityMaximum());    
        }

        
        /** 
         * Return a queue from the arrayOfQueues.
         *
         * @param index of queue in arrayOfQueues
         * @return LinkedList of ThreadStates
         */
        protected LinkedList<ThreadState> getQueue(int index) { 
            return arrayOfQueues[index]; 
        }    


        /** Return the number of queues in arrayOfQueues */
        protected int getQueueSize() { 
            return arrayOfQueues.length; 
        }


        /**
         * Enqueue a thread.
         *
         * @param thread that is waiting for the resource served by this queue
         */
        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).waitForAccess(this);
        }



        /** 
         * Dump contents of this queue. Used for debugging.  
         */
        @Override
        public void print() {
            String[] arrayOfStrings = new String[getQueueSize()];

            for(int i = 0; i<getQueueSize(); i++) {
                if(getQueue(i).size() > 0) {
                    arrayOfStrings[i] = new String();
                    for(ThreadState ts : getQueue(i)) {
                        string outMessage = 
                            "-->"
                            + ts.thread
                            + "("
                            + ts.getPriority()
                            + "/"
                            + ts.getEffectivePriority()
                            + ")"; 
                        arrayOfStrings[i] += outMessage; 
                    }
                } else {
                    arrayOfStrings[i] = null;
                }
            }
            String dumpString = "\nQueue: " + queueID + ":";
            
            if(queueID == KThread.getReadyQueueID()) { 
                dumpString += "(READY QUEUE)"; 
            }
            dumpString += "transferPriority? "+transferPriority+" "; 
            
            if(resourceHolder != null) {          
                dumpString += "resourceHolder: "+resourceHolder.thread;
            } else {
                dumpString += "resourceHolder: NULL"; 
            }
            dumpString += " nextThread: ";
            
            if(pickNextThread() != null) {
                dumpString += 
                    ""+pickNextThread().thread
                    + "("
                    + pickNextThread().getPriority()
                    + "/"
                    + pickNextThread().getEffectivePriority()
                    + ")"; 
            } else {
                dumpString += " the NULL thread";
            }

            for(int i =0; i<getQueueSize(); i++) {
                if(arrayOfStrings[i] != null) {
                    dumpString += "\nP" + i + arrayOfStrings[i];
                }
            }
            Lib.debug(dbgPSched, dumpString);
        }

        /** 
         * Acquire the resource served by this queue 
         *
         * @param thread that is aquiring the resource
         */
        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            resourceHolder = getThreadState(thread); 
            getThreadState(thread).acquire(this);
        }

        /** 
         * Revoke donations to this thread from all other threads on this queue 
         * that have made donations. 
         *
         * @param goodbyeThread ThreadState being chosen to be removed from 
         * this queue. 
         * @return the number of donors that were told to revoke their 
         * donations to goodbyeThread 
         */
        protected int revokeAllDonationsMadeFromQueue(ThreadState goodbyeThread {
            if(goodbyeThread == null) { return kStatusError; }
            int numRevoked = 0;
            HashSet<ThreadState.DonationTracker> dummyTracker = 
                (HashSet<ThreadState.DonationTracker>)goodbyeThread
                .donationManagementDB
                .clone(); 

            for(ThreadState.DonationTracker donor : dummyTracker) {
                boolean okayToRevoke = true;

                for(PriorityQueue queue : donor.donor.queuesThisThreadIsOn) {
                    okayToRevoke = 
                        okayToRevoke 
                        && (queue.resourceHolder != goodbyeThread || queue == this);
                }
                if(okayToRevoke) {
                    goodbyeThread.revokeDonation(donor);
                    numRevoked++;
                } else {
                    string errorString = 
                        "Tried to revoke a donation sent to  "
                        + goodbyeThread.thread
                        + " by"
                        + donor.donor.thread
                        + " but it's still waiting for it somewhere");
                    Lib.debug(dbgPSched, errorString);
                }
            }
            return numRevoked;
        }

        /** 
         * Return the next thread from the queue by priority. 
         */
        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            int i = getNextNonemptyQueue();
            ThreadState sanityThread = verifyQueueData(checkMode);          
            String dumpString = "";
            HashSet<ThreadState.DonationTracker> dummyTracker = 
                new HashSet<ThreadState.DonationTracker>();

            if(sanityThread == null) {
                if(queueID == KThread.getReadyQueueID() && emptyReadyWarning) {
                    string debugString = 
                        "**** Warning ***** Ready queue is empty. Old resource "
                        + "holder is "
                        + resourceHolder.thread;
                    Lib.debug(dbgPSched, debugString);
                    emptyReadyWarning = false;
                }
                resourceHolder = null;
                return null;
            }
            if(resourceHolder != null) { 
                dummyTracker = 
                    (HashSet<ThreadState.DonationTracker>)resourceHolder
                    .donationManagementDB
                    .clone(); 
            }
            Lib.assertTrue(i != kInvalidQueueIndex);
            revokeAllDonationsMadeFromQueue(resourceHolder);                
            Lib.assertTrue(getQueue(i).remove(pickNextThread()));
            sanityThread.deleteQueueFromThreadDB(this);
            ThreadState oldResourceHolder = resourceHolder;
            resourceHolder = sanityThread;

            // Formatting the debugging output.
            dumpString += 
                "[ nextThread ]: Returning " 
                + sanityThread.thread
                + " ("
                + sanityThread.getPriority()
                + "/"
                + sanityThread.getEffectivePriority()
                + ")" 
                + " on queue " 
                + queueID;
                + (queueID == KThread.getReadyQueueID()) ? " (READY QUEUE) " : "";
                + ". Old resource holder: ";
                + (oldResourceHolder == null) ? "" : oldResourceHolder.thread;
            Lib.debug(dbgPSched, dumpString);
            print();

            for(ThreadState.DonationTracker oldDonor : dummyTracker) {
                if(oldDonor.donor.queuesThisThreadIsOn.contains(this)) {
                    String assertString = 
                        "ERROR: "
                        + oldDonor.donor.thread
                        + " thinks its on "
                        + queueID
                        + " but the queue thinks differently";
                    boolean assertCondition = 
                        getQueue(oldDonor.donor.getEffectivePriority())
                        .contains(oldDonor.donor);
                    Lib.assertTrue(assertCondition, assertString);
                    oldDonor.donor.checkIfDonationRequired(this);
                }
            }
            return sanityThread.thread;
        }

        /** 
         * Return the highest-index non-empty queue.
         *
         * @return index of highest priority non-empty queue or kInvalidQueue 
         * if no such queue exists.  
         */
        protected int getNextNonemptyQueue() {
            int i = getQueueSize()-1;
            for( ; i>0 && getQueue(i).size()==0; i--)
                ;
            if(getQueue(i).size() > 0) { return i; }
            return kInvalidQueueIndex;
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would return.
         */
        protected ThreadState pickNextThread() {
            int i = getPriorityMaximum();
            while(getQueue(i).size() == 0 && i != getPriorityMinimum()) {
                 i--; 
            }
            return getQueue(i).peek(); 
        }
        
        /* PriorityQueue members */ 
        public boolean transferPriority;
        protected boolean emptyReadyWarning = true;
        protected ThreadState resourceHolder;
        protected int checkMode;
        protected final int queueID = hashCode()%10000;


        /** 
         * Consistency checks for when a queue is empty
         *
         * @param sanityThread input thread that is being checked by 
         * verifyQueueData()
         * @return true if the queue was in-fact empty. 
         */ 
        protected boolean emptyQueueCheck(ThreadState sanityThread) {
            boolean areAllQueuesEmpty = true;
            boolean cond1 = sanityThread == null;
            boolean cond2 = getNextNonemptyQueue() == kInvalidQueueIndex;
            boolean cond3 = areAllQueuesEmpty;

            for(int i=getQueueSize()-1; i != kInvalidQueueIndex; i--) {
                areAllQueuesEmpty = areAllQueuesEmpty && getQueue(i).size() == 0;
            }
            if(cond1 || cond2 || cond3) {
                Lib.assertTrue(getNextNonemptyQueue() == kInvalidQueueIndex);
                Lib.assertTrue(sanityThread == null);
                Lib.assertTrue(areAllQueuesEmpty);
 
                if(resourceHolder != null) {
                    for(ThreadState.DonationTracker dt : resourceHolder.donationManagementDB) {
                        Lib.assertTrue(dt.queueDonorCameFrom != this);
                    }
                }
                return true;
            }
            return false;
         }

        /**
         * Consistency check for currentBest* caches. Note that these checks 
         * are incompatible with lottery scheduler. 
         * 
         * @param sanityThread input thread that is being checked by 
         * verifyQueueData().
         * @see LotteryScheduler.java.
         */
        protected void currentBestCacheChecks(ThreadState sanityThread) {
            String failString = 
                "ERROR: nextThread "
                + sanityThread.thread
                + " has inconsistent databases";
            boolean firstCond = 
                sanityThread.donationManagementDB.size() == 0 
                && sanityThread.currentBestDonor == null;
            boolean secondCond = 
                sanityThread.currentBestOffer == kInvalidEP 
                && sanityThread.currentBestDonor==null;
            boolean thirdCond = 
                sanityThread.currentBestOffer == kInvalidEP 
                && sanityThread.donationManagementDB.size() == 0;

            if(sanityThread.currentBestOffer == kInvalidEP) {
                Lib.assertTrue(firstCond, failString);
            }
            if(sanityThread.donationManagementDB.size() == 0) {
                Lib.assertTrue(secondCond, failString);
            }
            if(sanityThread.currentBestDonor == null) {
                Lib.assertTrue(thirdCond, failString);
            }
        }  

        /**
         * Consistency checks specific to PriorityScheduler's priority 
         * implementation. Ensures sanityThread came from a queue of the right 
         * effective priority. Note that these checks are incompatible with 
         * LotteryScheduler. 
         *
         * @param sanityThread thread being checked by verifyQueueData().
         * @see LotteryScheduler.java.
         */
        protected void prioritySchedulerChecks(ThreadState sanityThread) {
            int i = getNextNonemptyQueue();
            String failString = 
                "ERROR: "
                + sanityThread
                + " has an EP of "
                + sanityThread.getEffectivePriority()
                + " but nextThread grabbed it from queue of priority "
                + i
                + "for queueID: "
                + queueID;
            boolean failCond = sanityThread.getEffectivePriority() == i;
            Lib.assertTrue(failCond, failString);
        }

        /** 
         * Any misc consistency checks that are universal to both lottery and 
         * priority scheduler and don't fit in anywhere else go here.
         *
         * @param sanityThread thread being checked by verifyQueueData()
         */
        protected void miscQueueChecks(ThreadState sanityThread) {
            String firstFail = 
                "ERROR: "
                + sanityThread.thread
                + " received a donation"
                + " (or thinks it did) on a queue ("
                + queueID
                + ") in which tranosferPriority is "
                + transferPriority;
            String secondFail = 
                "ERROR sanity check for "
                + sanityThread
                + " FAILED"
                + ". Donor: "
                + dt.donor.thread
                + " doesn't know it's a donor";
            String thirdFail = 
                "ERROR sanity check for "
                + sanityThread
                + " FAILED"
                + ". Received donation from queue "
                + dt.queueDonorCameFrom.queueID
                + " but transferPriority is false";
            
            // Make sure no donation happens when donations are disabled. 
            if(!transferPriority) { 
                for(ThreadState.DonationTracker dt : sanityThread.donationManagementDB) {
                    Lib.assertTrue(dt.queueDonorCameFrom != this, firstFail);
                }
            }

            // Ensure every thread this thread has a donation from knows about 
            // it and check transferPriority consistency for them
            for(ThreadState.DonationTracker dt : sanityThread.donationManagementDB) {
                Lib.assertTrue(dt.donor.threadsDonatedTo.contains(sanityThread), secondFail);
                Lib.assertTrue(dt.queueDonorCameFrom.transferPriority, thirdFail);
            }

            // Ensure all donations match getEffectivePriority() for this thread
            for(ThreadState ts : sanityThread.threadsDonatedTo) {
                for(ThreadState.DonationTracker dt : ts.donationManagementDB) {
                    if(dt.donor == sanityThread) {
                        Lib.assertTrue(dt.donation == sanityThread.getEffectivePriority());
                    }
                }
            }
        }

        /** 
         * Data consistency checks for threads on their way out of the queue.
         *
         * This method is a dispatch method for various queue state conditions.  
         *
         * @param mode coorsponding to what scheduler we're running.
         * This way, we can run only those tests that are pertinent to the 
         * particular scheduler without having to override verifyQueueData in 
         * the LotteryQueue. 
         * @return ThreadState that this checker used in its verifications. 
         * @see checkMode
         */
        protected ThreadState verifyQueueData(int mode) {
            ThreadState sanityThread = pickNextThread();
            if(emptyQueueCheck(sanityThread)) { return sanityThread; }
            String sanityFail = 
                "ERROR: " 
                + sanityThread.thread 
                + " failed verifyThreadStateData() in nextThread";
            Lib.assertTrue(sanityThread.verifyThreadStateData(), sanityFail); 
            miscQueueChecks(sanityThread);

            // Run tests that are specific to priority queues if mode indicates 
            // we're checking a priority queue.
            if(mode == kCheckModePriority) { 
                currentBestCacheChecks(sanityThread);           
                prioritySchedulerChecks(sanityThread);
            }               
            return sanityThread;        
        }
    } // End of PriorityQueue class


    /**
     * The scheduling state of a thread. 
     *
     * This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see        nachos.threads.KThread#schedulingState
     */
    protected class ThreadState 
    { 
        /**
         * DonationTracker inner class. 
         *
         * Maintains an association of threads that have donated
         * priority and which queues the donee owned when the donation was made
         * 
         * Equality Symantics: object1.equals(object2) iff object1.donor == 
         * object2.donor
         * hashCode Symantics (used in HashSet for membership testing): 
         * object1.hashCode() == object2.hashCode() iff object1.donor.hashCode() 
         * == object2.donor.hashCode(). 
         */
        protected class DonationTracker
        {
            ThreadState donor;
            PriorityQueue queueDonorCameFrom;
            int donation;

            /**
             * Constructor for DonationTracker.
             *
             * @param inOffer offer from the donor thread.
             * @param inDonor ThreadState offering donation.
             * @param inQueue the queue the donor is waiting on.
             */
            DonationTracker(int inOffer, ThreadState inDonor, PriorityQueue inQueue) {
                donor = inDonor;
                queueDonorCameFrom = inQueue;
                donation = inOffer;
            }

            @Override
            public int hashCode() { 
                return donor.hashCode(); 
            }

            @Override
            public boolean equals(Object inObject) { 
                return donor == ((DonationTracker)inObject).donor; 
            }
        } // End of DonationTracker class


        /** 
         * Perform database consistency checks for this ThreadState and it's 
         * associated ThreadStates. 
         *
         * @return false if any of the checks fail, true otherwise. 
         */
        protected boolean verifyThreadStateData() {
            boolean outValue = true;
            boolean subValue = false; 
            String firstFail = 
                "ERROR: currentBestDonor is not in donationManagementDB for"
                + thread;
            String secondFail = 
                "ERROR: currentBestOffer doesn't match currenBestDonor's EP";
            String thirdFail = 
                "ERROR: "
                + thread
                + " has an outstanding donation to a thread it is not waiting"
                + " for. Use -p to see threadDump()"

            // Make sure currentBestDonor is actually in the 
            // donationManagementDB datbase (i.e., the cache is still valid).
            if(currentBestDonor != null) {
                outValue = 
                    outValue 
                    && donationManagementDB
                    .contains(new DonationTracker(0,currentBestDonor, null));
            }
            if(!outValue) {
                System.out.println(firstFail);
            }
    
            // Ensure currentBestOffer matches currentBestDonor's
            // getEffectivePriority()
            if(currentBestDonor != null) {
                outValue = 
                    outValue 
                    && (currentBestOffer == currentBestDonor.getEffectivePriority());
            }
            if(!outValue) {
                System.out.println(secondFail);
            }
               
            // Ensure all outstanding priority donations are to threads we're 
            // still waiting for resources from and that the donation matches 
            // getEffectivePriority() for this thread.
            for(ThreadState ts : threadsDonatedTo) {
                for(PriorityQueue queue : queuesThisThreadIsOn) {
                    subValue = subValue || (queue.resourceHolder == ts);
               
                    // Find the donation we made and make sure it matches the 
                    // current effective priority of this thread.
                    if(queue.resourceHolder == ts) {
                        for(DonationTracker dt : ts.donationManagementDB) {
                            if(dt.donor == this) {
                                subValue = subValue && dt.donation == getEffectivePriority();
                            }
                        }
                    }
                }
            }
            if(threadsDonatedTo.size() > 0) {
                outValue = outValue && subValue;
            }
            if(!outValue && threadsDonatedTo.size() > 0) {
                System.out.println(thirdFail);
                threadDump();
            }
            return outValue;
        }

        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;
            donationManagementDB = new HashSet<DonationTracker>();
            threadsDonatedTo = new HashSet<ThreadState>();
            queuesThisThreadIsOn = new HashSet<PriorityQueue>();
            currentBestOffer = kInvalidEP;
            currentBestDonor = null;
            setPriority(priorityDefault);
        }

        /** 
         * Dump useful information about this ThreadState to the debug console 
         * (visible with -p debug flag) 
         */ 
        public void threadDump() {
            String outString = "";
            String outString2 = "";
            String donatedString = "";
            int i = 0; 
            for(DonationTracker td : donationManagementDB) {
                i++;
                outString += 
                    "\n\t"
                    + i 
                    + ") Donor: " 
                    + td.donor.thread 
                    + "(EP: "
                    + td.donor.getEffectivePriority()
                    + ")"
                    + ", Donation: " 
                    + td.donation 
                    + ", QueueID: " 
                    + td.queueDonorCameFrom.queueID;
            }
            if(i == 0) { 
                outString += "NONE"; 
            }
            i = 0; 
            for(PriorityQueue queue : queuesThisThreadIsOn) {
                i++;
                outString2 += "" + i + ") QueueID: " + queue.queueID; 
                if(queue.queueID == KThread.getReadyQueueID()) { 
                    outString2 += " (READY QUEUE) ";
                }
                if(queue != null)  {
                    outString2 += " resourceHolder: ";
                    if(queue.resourceHolder != null) {
                        outString2 += queue.resourceHolder.thread;
                    } else {
                        outString2 += "NONE";
                    }
                }
                if(i % 4 == 0) {
                    outString2 += "\n";
                }
            }
            if(i == 0) { 
                outString2 += "NONE"; 
            }
            i=0;
            for(ThreadState ts : threadsDonatedTo) {
                i++;
                donatedString += i + ") " + ts.thread + ". ";
            }
            if(i==0) { 
                donatedString += "NONE"; 
            }

            String debugString = 
                "[ ThreadDump ]:\nThread: "
                + thread 
                + ", P/E{ "
                + getPriority() 
                + "/"
                + getEffectivePriority()
                + "\ndonationManagementDB: "
                + outString
                + "\nqueuesThisThreadIsOn: "
                + outString2
                + "\nthreadsDonatedTo: "
                + donatedString;
            Lib.debug(dbgPSched, debugString); 
            debugString = "Best offer: " + currentBestOffer + ". Best donor: ";
            if(currentBestDonor != null) { 
                dumpString += 
                    currentBestDonor.thread
                    + "("
                    + currentBestDonor.getPriority()
                    + "/"
                    + currentBestDonor.getEffectivePriority()
                    + ")";
            } else {
                debugString += "NULL";
            }
            Lib.debug(dbgPSched, debugString);
        }



        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }


        /** 
         * Revoke a donation from donor to this thread 
         *
         * @param thread who is revoking their donation to this thread.
         */
        public void revokeDonation(DonationTracker donor) {
            String event = "revoke";
            Lib.assertTrue(donationManagementDB.remove(donor));
            Lib.assertTrue(donor.donor.threadsDonatedTo.remove(this));

            if(priorityUpdateRequired(donor, event))
                calculatePriorityDonation();
        }



        /** 
         * Check if effective priority may have changed.
         *
         * @param donor thread that may have influenced this thread's effective 
         * priority. 
         * @param event that donor has performed against this thread.  
         * @return true if effective priority may have changed and needs to be 
         * recalculated.
         */
        protected boolean priorityUpdateRequired(DonationTracker donor, String event) {
            if(event == "revoke") { 
                return donor.donor == currentBestDonor; 
            }
            if(event == "receive") {
                return (
                    donor.donation > currentBestOffer 
                    || (donor.donor.equals(currentBestDonor) 
                    && donor.donation < currentBestOffer)
                );
            }
            if(event == "set") {
                return (donor.donation != getEffectivePriority());
            }
            return false;
        } 



        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() { 
            return Math.max(priority, currentBestOffer); 
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * This method must also ensure that if the new priority value alters 
         * this thread's effective priority that that change is propagated to 
         * all threads that have valid donations from this thread via the 
         * propagatePriorityDonation() method. 
         *
         * @param priority the new priority.
         */
        public void setPriority(int inPriority) {
            Lib.assertTrue(Machine.interrupt().disabled());
            boolean cond1 = inPriority >= getPriorityMinimum(); 
            boolean cond2 = inPriority <= getPriorityMaximum();
            String fail = "ERROR in setPriority, "+inPriority + " out of range";
            Lib.assertTrue(cond1 && cond2, fail);
            if (this.priority == inPriority) { return; }
            int currentPriority = getPriority();
            int currentEP = getEffectivePriority();
            String event = "set";
            DonationTracker dt = new DonationTracker(currentEP, this, null);
            priority = inPriority;
           
            if(priorityUpdateRequired(dt, event)) {
                for(PriorityQueue queue : queuesThisThreadIsOn) {
                    moveThreadOnQueue(queue, currentEP, getEffectivePriority());
                }
                propagatePriorityDonation();
            }
        }



        /**
         * Move a thread from one queue to another.
         *
         * @param queue that is involed in the move. 
         * @param from original queue this thread is currently on.
         * @param to queue this thread is being moved to.
         */
        protected void moveThreadOnQueue(PriorityQueue queue, int from, int to) {
            Lib.assertTrue(queue.getQueue(from).remove(this));
            Lib.assertTrue(!queue.getQueue(to).remove(this));
            queue.getQueue(to).add(this);
        }




        /**
         * receiveOffer() is responsible for processing all priority donations 
         * received by this thread. 
         * <p>
         * If the donated priority doesn't change this threads effective 
         * priority, just add the donation to the database of donations 
         * received. If it does, then update effective priority via 
         * calculatePriorityDonation()
         *
         * @param offer of priority being made to this thread.
         * @param donor thread offering the donation.
         * @param waitQueue that donor is waiting for resources on and that 
         * this thread is the resourceHolder of.
         */
        protected void receiveOffer(int offer, ThreadState donor, PriorityQueue waitQueue) {
            Lib.assertTrue(Machine.interrupt().disabled());
            Lib.assertTrue(waitQueue.transferPriority); 
            String event = "receive";
            DonationTracker donationObject = new DonationTracker(offer, donor, waitQueue);
            
            if(donationManagementDB.contains(donationObject)) {
                donationManagementDB.remove(donationObject);
            }
            donationManagementDB.add(donationObject);
            if(priorityUpdateRequired(donationObject, event)) {
                calculatePriorityDonation();
            }
        }
 
 
        /**
         * Compute effective priority of this thread.
         *
         * This method will take the maximum value of this.priority and all 
         * priority donations received from other threads but asking all of 
         * those threads what their effective priorities are. This value is  
         * stored as currentBestOffer for the highest donor currentBestDonor. 
         * This caching is aimed at preventing needless (and time-consuming) 
         * recomputation. If, as a result of this computation, this thread's 
         * effective priority changes, this method calls 
         * propagatePriorityDonation() to notify all threads this thread has 
         * donated priority to in the past that its effective priority has 
         * changed. 
         */
        protected void calculatePriorityDonation() {
            Lib.debug(dbgPSched, "Calculating priority donation for " + thread);
            Lib.assertTrue(Machine.interrupt().disabled());
           
            // First invalidate currentBestOffer 
            int oldEffectivePriority = getEffectivePriority(); 
            int bestOfferSeenSoFar = kInvalidEP;
            ThreadState bestDonorSeenSoFar = null;            
        
            // Find the best donor out of all donors in donationManagementDB 
            for(ThreadState.DonationTracker ts : donationManagementDB) {   

                // Data consistency check. Donor thread better know it's still 
                // a donor thread
                if(!ts.donor.threadsDonatedTo.contains(this)) {
                    System.out.println(
                        "ERROR: Donor/donnee data inconsistent: Donor: "
                        + ". Enable debug output (-p) to see threadDump()s" 
                    );
                    ts.donor.threadDump();
                    threadDump();
                    Lib.assertTrue(false);
                }                
                if(bestOfferSeenSoFar < ts.donation) {
                    bestOfferSeenSoFar = ts.donation;
                    bestDonorSeenSoFar = ts.donor; 
                }
            }
            currentBestOffer = bestOfferSeenSoFar;
            currentBestDonor = bestDonorSeenSoFar;

            // If the thread's effective priority has changed, move the thread 
            // on all queues it's sitting on.
            if(oldEffectivePriority != getEffectivePriority()) {
                for(PriorityQueue queue : queuesThisThreadIsOn) {
                    Lib.assertTrue(queue.getQueue(oldEffectivePriority).contains(this), "ERROR");
                    queue.getQueue(oldEffectivePriority).remove(this);
                    queue.getQueue(getEffectivePriority()).add(this);
                }
                
                // Finally, tell everyone else about the change. 
                propagatePriorityDonation(); 
            }
        }
       
       
        
        /**
         * Remove waitQueue from this thread's queuesThisThreadIsOn database. 
         * This method should be called whenever this thread is pulled from the 
         * waitQueue (e.g., as a result of a call to nextThread()
         *
         * @param waitQueue that we're removing from this thread's 
         * queuesThisThreadIsOn database
         */
        protected void deleteQueueFromThreadDB(ThreadQueue waitQueue) {
            if(!queuesThisThreadIsOn.remove(waitQueue)) {
                String failString = 
                    "ERROR: Tried to remove " 
                    + ((PriorityQueue)waitQueue).queueID
                    + " from thread " 
                    + thread 
                    + " but queuesThisThreadIsOn has no entry of waitQueue.";
                Lib.assertTrue(false, failString);
                threadDump();
            }   
        }



        /**
         * Send an updated priority donation offer to all threads this thread 
         * has sent an offer to previously.
         * <p>
         * This method should be called whenever this thread's effective 
         * priority changes (e.g., in the event this thread receives a better 
         * donation offer or it's setPriority() method is called with
         * a new value that changes its effective priority. 
         */
        protected void propagatePriorityDonation() {
            Lib.assertTrue(Machine.interrupt().disabled());
            for(PriorityQueue queue : queuesThisThreadIsOn) {
                for(ThreadState ts : threadsDonatedTo) {
                    Lib.assertTrue(ts != null, "ERROR");
                    if(queue.transferPriority && queue.resourceHolder == ts) {
                        ts.receiveOffer(getEffectivePriority(), this, queue);
                    }
                }
            }
        }


        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * <p>
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param waitQueue the queue that the associated thread is
         * @see nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {
            Lib.assertTrue(Machine.interrupt().disabled());
            String dumpString = 
                "[ waitForAccess ]: " 
                + thread 
                + "("
                + getPriority()
                +"/"
                + getEffectivePriority()
                + ") waiting on " 
                + waitQueue.queueID
                + ". Resource holder: ";
                + (waitQueue.resourceHolder == null 
                    ? " null " 
                    : waitQueue.resourceHolder.thread);

            if(waitQueue.queueID == KThread.getReadyQueueID()) { 
                dumpString += " (READY QUEUE) "; 
            }
            Lib.debug(dbgPSched, dumpString);
            
            // waitQueue should not already contain this thread, so assert
            Lib.assertTrue(
                !queuesThisThreadIsOn.contains(waitQueue), 
                "ERROR: tried to waitForAccess on a queue"
                +"("
                + waitQueue.queueID
                + ") that this thread ("
                + thread
                + ") is already on"
            );
            queuesThisThreadIsOn.add(waitQueue);
            checkIfDonationRequired(waitQueue);        

            // Add this thread to the waitQueue at (effective) priorit 
            if(!waitQueue.getQueue(getEffectivePriority()).add(this)) {
                Lib.debug(dbgPSched, "ERROR: Thread already on queue");
                waitQueue.print();
                threadDump();
                Lib.assertTrue(false, "ERROR: Failed consistency check");
            }
        }



        /**
         * Figure out whether or not to make a priority donation to the current 
         * resdource holder of the wait queue and if so, make it.
         *
         * @param waitQueue priority queue thread is initiating a donation from. 
         */
        private void checkIfDonationRequired(PriorityQueue waitQueue){
            Lib.assertTrue(Machine.interrupt().disabled());
            boolean cond1 = waitQueue.resourceHolder != null;
            boolean cond2 = waitQueue.resourceHolder != this;
            boolean cond3 = waitQueue.transferPriority;
            String debugString = 
                "[ ThreadState.waitForAccess ]: " 
                + thread 
                + " sending donation to " 
                + waitQueue.resourceHolder.thread 
                + " of " 
                + getEffectivePriority() 
                + " on queue " 
                + waitQueue.queueID;

            if(cond1 && cond2 && cond3) {
                Lib.debug(dbgPSched, debugString);
                threadsDonatedTo.add(waitQueue.resourceHolder);
                waitQueue.resourceHolder.receiveOffer(
                    getEffectivePriority(), this , waitQueue
                );
            }
        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see        nachos.threads.ThreadQueue#acquire
         * @see        nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            Lib.assertTrue(Machine.interrupt().disabled());
            for(int i=0; i< waitQueue.getQueueSize(); i++) {
                Lib.assertTrue(waitQueue.getQueue(i).size() == 0);
            }
        }        

        /* ThreadState members */
        protected KThread thread;
        protected int priority;
        protected HashSet<DonationTracker> donationManagementDB;
        protected HashSet<PriorityQueue> queuesThisThreadIsOn;
        protected HashSet<ThreadState> threadsDonatedTo;
        protected int currentBestOffer;
        protected ThreadState currentBestDonor;
    } // End of ThreadState class

    /* PriorityScheduler members */
    private static boolean isAssertEnabled = false; 
    private static final int kInvalidEP = -1;
    protected static final int kInvalidQueueIndex = -1;
    protected static final int kStatusError = -2;
    protected static final int kCheckModePriority = 1;
    protected static final int kCheckModeLottery = 2;
    protected static final char dbgPSched = 'q'; 
    protected String banner;
   



    /**
     * A wrapper class for nachos.machine.Lib. This wrapper allows easy 
     * disablement of assert functionality via the global boolean 
     * PriorityScheduler.isAssertEnabled. If isAssertEnabled is set to true, 
     * this class simply forwards any calls to Lib.x to nachos.machine.Lib.x 
     * for Lib method x. If it is false, calls to Lib.assertTrue() and 
     * Lib.assertNotReached() are dropped here.
     */
    protected static class Lib {
        protected static void assertTrue(boolean statement, String message) { 
            if(isAssertEnabled) { 
                nachos.machine.Lib.assertTrue(statement, message); 
            } 
        }
           
        protected static void assertTrue(boolean statement) { 
            if(isAssertEnabled) { 
                nachos.machine.Lib.assertTrue(statement);
            }
        }

        protected static void assertNotReached(String message) { 
            if(isAssertEnabled) { 
                nachos.machine.Lib.assertNotReached(message); 
            }
        }

        protected static void debug(char flag, String message) { 
            nachos.machine.Lib.debug(flag, message); 
        }
    } // End Lib class
} // End PriorityScheduler class
