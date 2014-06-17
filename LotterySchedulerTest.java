/** 
 * Test the functionality of the PriorirtyScheduler class Note that this module
 * must be imported into files wishing to be tested.
 *
 * Calling symantics: - Run a single test contained herein:
 * PrioritySchedulerTest.testx() for test number x.  - Run all tests contained
 * hereing: PrioritySchedulerTest.runall().
 *
 * Note that methods declared here must be static to function properly.  'make'
 * changes to this file from the respective projx directory. 
 *
 * To see DEBUGGING output on the console, run nachos with the -d x switch
 * (along with any other debugging flags, of course. 
 */
package nachos.threads;
import nachos.threads.*;
import nachos.machine.*;
 
public class LotterySchedulerTest 
{
    // Shared data goes here to be accessed by all threads
    private static Lock lock1 = new Lock();
    private static Lock lock2 = new Lock();
    private static Lock conditionLock = new Lock();
    private static Lock sharedIntLock = new Lock();
    private static Semaphore mutex = new Semaphore(0);
    private static Semaphore mutex2 = new Semaphore(0);
    private static Semaphore mutex3 = new Semaphore(0);
    private static Condition2 cond = new Condition2(conditionLock);
    private static Semaphore cond2 = new Semaphore(0);
    private static int whoFinishedFirst = 0;
    private static int sharedData = 0;
    private static int sharedInteger = 0;
    private static double nonLinearSharedData = 5;
    private static Semaphore ordering = new Semaphore(0);
    private static Semaphore ordering2 = new Semaphore(0);
    private static boolean wrongOrder = true;
    
    // This test program grabs a lock then does a long computation 
    // spanning multiple yield()s. The purpose of this test is to 
    // verify that the functionality of the PriorityQueue behaves as 
    // expected with the Lock class. 
    static class Program_1 implements Runnable
    {
        public void run()
        {
            Lib.debug(dbgTesting,"[ Program_1 ]: Entered new Program_1");
            Lib.debug(dbgTesting,"[ Program_1 ]: Thread running me is: " + KThread.getCurrentThreadStats());

            Lib.debug(dbgTesting,"[ Program_1 ]: Aquiring Lock lock1");
            lock1.acquire(); 

            // Wake up the main thread
            Lib.debug(dbgTesting,"[ Program_1 ]: Calling mutex.V() to wake up main thread");
            mutex.V();
            Lib.debug(dbgTesting,"[ Program_1 ]: Now that we've got the lock, lets do something time-consuming");
            int x = 0;
            for(int i = 1; i<1000000000; i++) {
                if(i % 10000000 == 0)
                    KThread.yield();
                x = x + i - (x/i);
            }
            Lib.debug(dbgTesting,"[ Program_1 ]: Done with the computation, releasing the lock");
            sharedData = 1;
            lock1.release();
            whoFinishedFirst = 1;
        }
    }

    static class Program_2 implements Runnable
    {
        public void run()
        {
            Lib.debug(dbgTesting,"[ Program_2 ]: Entered Program_2");
            Lib.debug(dbgTesting,"[ Program_2 ]: Thread running me is: " + KThread.getCurrentThreadStats());
            Lib.debug(dbgTesting,"[ Program_2 ]: First, lets just yield() so Program_1 can start and grab the lock");
            KThread.yield();
            Lib.debug(dbgTesting,"[ Program_2 ]: Lets try to acquire the lock Program_1 is working with...");
            lock1.acquire();
            Lib.assertTrue(sharedData == 1, "Expected sharedData = 1 got " + sharedData);
            Lib.debug(dbgTesting,"[ Program_2 ]: We have the lock now");
            Lib.debug(dbgTesting,"[ Program_2 ]: Leaving Program_2 now");
            sharedData = 2;
            lock1.release();
            whoFinishedFirst = 2;
        }
    }

    static class Program_3 implements Runnable
    {
        public void run()
        {
            Lib.debug(dbgTesting,"[ Program_3 ]: Entered Program_3");
            Lib.debug(dbgTesting,"[ Program_3 ]: Acquiring lock...");
            lock1.acquire();
            Lib.debug(dbgTesting,"[ Program_3 ]: We got the lock, releasing it and ending program 3");
            lock1.release();
        }
    }


    // Simple program that really just says hello and yeilds(). Uses
    // sharedInteger to echo a count. Useful for shared testing
    static class Program_4 implements Runnable
    {
        private static final String ID = "Program_4";
        private int loopCount;

        Program_4(int loop)
        {
            loopCount = loop;
        }


        public void run()
        {
            Lib.debug(dbgTesting,ID+" started execution. Owning thread is " + KThread.getCurrentThreadStats());
            Lib.debug(dbgTesting,ID+ " acquiring the shared integer lock");
            sharedIntLock.acquire(); 
            Lib.debug(dbgTesting,ID+ " got the shared integer lock");
            sharedInteger = loopCount;
            Lib.debug(dbgTesting,ID+ " releasing shared integer lock"); 
            sharedIntLock.release();
            KThread auxThread = new KThread(new Program_5(90));
            auxThread.fork();
            
            while(sharedInteger > 0)
            {
                Lib.debug(dbgTesting,ID+ " value of shared integer is "+ sharedInteger+" yielding now");
                sharedIntLock.acquire();
                sharedInteger--;
                sharedIntLock.release();
                KThread.yield();
            }
            auxThread.join();
            Lib.debug(dbgTesting,ID+" leaving now");
        }
    }    

    // An even simpler program than program 4, just prints thread stats and
    // yields. Input arg is loop count
    static class Program_5 implements Runnable
    {
        private int loopCount;

        Program_5(int loop) { loopCount = loop; }
    
        public void run()
        {
            while (loopCount > 0)
            {
                sharedIntLock.acquire();
                Lib.debug(dbgTesting,KThread.getCurrentThreadStats());
                loopCount--;
                Lib.debug(dbgTesting,"Yielding in Program_5");
                sharedIntLock.release();
                KThread.yield();
                Lib.debug(dbgTesting,"Back from yield()");
            }
        }
    }

    // This program ping/pongs with Program_7 using condition variables and
    // semaphores
    static class Program_6 implements Runnable
    {
        public void run()
        {
            KThread wakeThread = new KThread(new Program_7());
            wakeThread.setName("WAKE THREAD FOR PROG 6");
            Lib.debug(dbgTesting,"Entering Program_6...acquiring condition Lock");
            conditionLock.acquire(); 
            wakeThread.fork();
            Lib.debug(dbgTesting,"Got the lock...calling cond.sleep()");
            cond.sleep();
            Lib.debug(dbgTesting,"Got back from cond.sleep() for thread with statis:"+ KThread.getCurrentThreadStats());
            Lib.debug(dbgTesting,"Since we've still got the lock, lets change whoFinishedFirst to 9");
            whoFinishedFirst = 9;
            Lib.debug(dbgTesting,"releasing the lock");
            conditionLock.release();
            sharedIntLock.acquire();
            Lib.debug(dbgTesting,"");
            sharedIntLock.release();
            wakeThread.join();
        }
    }

    // This program forks its own threads. This should be used as part of test5
    // to add complexity
    static class Program_8 implements Runnable
    {
        public void run()
        {
            KThread childThread = new KThread(new Program_6());
            KThread childThread2 = new KThread(new Program_5(89));
            KThread childThread3 = new KThread(new Program_7()); 
            childThread.setName("C1");
            childThread2.setName("C2");
            childThread3.setName("C3");
            boolean status = Machine.interrupt().disable();
            ThreadedKernel.scheduler.setPriority(childThread, 6);
            ThreadedKernel.scheduler.setPriority(childThread2, 1);
            ThreadedKernel.scheduler.setPriority(childThread3, 4);
            Machine.interrupt().restore(status);
            childThread.fork();
            childThread2.fork();
            childThread3.fork();

            childThread.join();
            childThread2.join();
            childThread3.join();
        }
    }



    // Ping poings with program_6 using condition variables. This one wakes up 6
    static class Program_7 implements Runnable
    {
        public void run()
        {
            sharedIntLock.acquire();
            Lib.debug(dbgTesting,"Entered program_7...acquireing conditionLock");
            sharedIntLock.release();
            conditionLock.acquire();
            Lib.debug(dbgTesting,"got the lock in prog 7, setting whoFinishedFirst to 12 and waking up prog6");
            whoFinishedFirst = 12;
            cond.wakeAll();
            Lib.debug(dbgTesting,"Done calling cond.wakeAll(). releasing conditionLock and leaving the pgroam");
            conditionLock.release();
        }
    }

    // This program performs a nonlinear operation on a shared data item. The
    // intent is to be used with another program that performs some other
    // non-linear operations. This can be used to detect if two threads execute
    // in the proper order by examining the shared data value after both have
    // executed. 
    static class Program_10 implements Runnable
    { 
        // Return the value produed by the running of this program as a KThread
        public double returnValue(double inValue)
        {
            double outValue = inValue;
            for(int i=3; i>0; i--) {
                outValue = outValue * outValue;
            }
            return outValue;
        }

        public void run()
        {
            for(int i=3; i>0; i--) {
                Lib.debug(dbgTesting, "[ Program_10 ]: Looping again, i is "+i+" outValue is "+nonLinearSharedData);
                sharedIntLock.acquire();
                Lib.debug(dbgTesting, "[ Program_10]: Got the lock, changing nonLinearSharedData");
                nonLinearSharedData = nonLinearSharedData * nonLinearSharedData;
                sharedIntLock.release();
                KThread.yield();
            }
        }
    }

    // Grab a lock and wait for priority donation. This thread should receive
    // priority of 7
    static class Program_11 implements Runnable
    {
        public void run()
        {
            // Grab the lock sqrt thread is going to be wanting and change
            // nonLinearSharedData
            sharedIntLock.acquire();
            nonLinearSharedData--;

            // First ensure that priority is default at this point since it
            // shouldn't have changed yet
            boolean st = Machine.interrupt().disable();
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 1,
                "ERROR: "+KThread.getCurrentThread()+" should have priority 1");
            Machine.interrupt().restore(st);

            Lib.debug(dbgTesting, "[Program_11]: Got the lock, yielding()");
            KThread.yield();

            // sqrt should have donated priority of 7 to this thread by now            
            st = Machine.interrupt().disable();
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 7,
                "ERROR: "+KThread.getCurrentThread()+" should have priority 7");
            Machine.interrupt().restore(st);

            Lib.debug(dbgTesting, "[Program_11]: Releasing the lock");
            sharedIntLock.release();

            // Now that the lock has been released, priority should have
            // returned to 1 by now via release()
            st = Machine.interrupt().disable();
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 1,
                "ERROR: "+KThread.getCurrentThread()+" should have priority 7");
            Machine.interrupt().restore(st);
        }
    }

    // The second of two non-linear transformation programs on a shared data
    // variable
    static class Program_9 implements Runnable
    {
        // Return the value produed by the running of this program as a KThread
        public double returnValue(double inValue)
        {
            double outValue = inValue;
            for(int i=3; i>0; i--) {
                outValue = Math.sqrt(outValue * outValue + outValue);
            }
            return outValue;
        }

        public void run()
        {
            for(int i=3; i>0; i--) {
                Lib.debug(dbgTesting, "[ Program_9 ]: Looping again, i is "+i+" outValue is "+nonLinearSharedData);
                sharedIntLock.acquire();
                Lib.debug(dbgTesting, "[ Program_9 ]: Got the lock in sqrt, changing data");
                nonLinearSharedData = Math.sqrt(nonLinearSharedData*nonLinearSharedData + nonLinearSharedData);
                sharedIntLock.release();
                KThread.yield();
            }
        }
    }

    // This program grabs a series of resources in a systematic way to compel
    // priority donation. Works in conjunction with Programs 13 and 14 and
    // test8()
    static class Program_12 implements Runnable
    {
        static KThread currentBestThread;
        static KThread transitiveDonationThread;
        int checkValue = 0;

        Program_12(KThread inThread, KThread transThread) 
        {
            transitiveDonationThread = transThread;
            currentBestThread = inThread; 
        }

        public void run()
        {
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 Entered Program_12.i Grabbing conditionLock");
            PriorityScheduler.ThreadState currentThreadState 
                = ((PriorityScheduler)ThreadedKernel.scheduler).getThreadState(KThread.getCurrentThread());
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 got conditionLock, calling mutex.p() to sleep and waking up main"); 

            // Grab the conditionLock and go to sleep
            conditionLock.acquire();

            // Wake up main and then go to sleep. main will then fork off T2 and T3
            ordering.V();
            mutex.P();

            // Verify donation from Program_13 and Program_14 which are waiting
            // for conditionLock
            boolean st = Machine.interrupt().disable();
            checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread());
            Lib.assertTrue(checkValue == 15, "** DONATION ERROR: Expected 15, got "+checkValue);
            checkValue = currentThreadState.donationManagementDB.size();
            Lib.assertTrue(checkValue == 2, "** Donation DB ERROR: expected 2, got "+checkValue);
            
            // Verify that priority donation changes when setPriority is called
            ThreadedKernel.scheduler.setPriority(KThread.getCurrentThread(), 1200);
            checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread());
            Lib.assertTrue(checkValue == 1212, "** DONATION ERROR: expected 1212, got "+ checkValue);
            Machine.interrupt().restore(st);
            
            // Now set priority of a donor thread and verify that it propagates
            // here
            st = Machine.interrupt().disable();
            ThreadedKernel.scheduler.setPriority(transitiveDonationThread, 50);
            checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread());
            Lib.assertTrue(checkValue == 1257, "** DONATION ERROR: expected 1257, got "+ checkValue);
            Machine.interrupt().restore(st);
           
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 releasing conditionLock. T2 and T3 should be waiting for it");
            conditionLock.release();
            st = Machine.interrupt().disable();
            checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread());
            Lib.assertTrue(checkValue == 1200, "** DONATION ERROR: Expecte 1200, got "+checkValue);
            checkValue = currentThreadState.donationManagementDB.size();
            Lib.assertTrue(checkValue == 0, "** Donation DB ERROR: expected 0, got "+checkValue);
            Machine.interrupt().restore(st);

            // Grab lock1 and sleep. Program_13 and Program_14 should run now
            lock1.acquire();
            ordering2.P();
        
            // Woken up by Program_13 which is now bloked on lock1. Program_14
            // is blocked on lock2 held by Program_13.  We should have a
            // donation strategy as follows: T3 ---> L2, T2 ----> L1, T1
            st = Machine.interrupt().disable();
            checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()); 
            Lib.assertTrue(checkValue == 1257, "** DONATION ERROR: Expected 1257, got "+checkValue);
            checkValue = currentThreadState.donationManagementDB.size();

            // Note that now we only receive 1 donation but it's actually the
            // sum of 2 donors via transitivity
            Lib.assertTrue(checkValue == 1, "** Donation DB ERROR: expected 1, got "+checkValue);
            Machine.interrupt().restore(st);

            // Finally, set priority of T3 and verify that it propagates through
            // the network as shown above
            st = Machine.interrupt().disable();
            ThreadedKernel.scheduler.setPriority(transitiveDonationThread, 47);
            checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread());
            Lib.assertTrue(checkValue == 1254, "** DONATION ERROR: expected 1254, got "+ checkValue);
            checkValue = currentThreadState.donationManagementDB.size();
            Lib.assertTrue(checkValue == 1, "** Donation DB ERROR: expected 1, got "+checkValue);
            ThreadedKernel.scheduler.setPriority(transitiveDonationThread, 100);
            checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread());
            Lib.assertTrue(checkValue == 1307, "** DONATION ERROR: expected 1307, got "+ checkValue);
            Machine.interrupt().restore(st);

            // Release the lock and verify priority changes accordingly
            lock1.release();
            st = Machine.interrupt().disable();
            checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread());
            Lib.assertTrue(checkValue == 1200, "** DONATION ERROR: expected 1200, got "+ checkValue);
            checkValue = currentThreadState.donationManagementDB.size();
            Lib.assertTrue(checkValue == 0, "** Donation DB ERROR: expected 0, got "+checkValue);
            Machine.interrupt().restore(st);

            // Nothing left to do in here. Program_13 should now have control


        }
    }

    // Part of a 3 program set in test8 with Program_14 and Program_12 that runs
    // a series of chained dependence priority donation tests
    static class Program_13 implements Runnable
    {
        static KThread currentBestThread;
    
        Program_13(KThread inThread) { currentBestThread = inThread; }

        public void run()
        {
            // Everyone is asleep except (maybe) Program_14
            Lib.debug(dbgTesting, "[ Program_13 ]: T2 Entered Program_13");

            boolean st = Machine.interrupt().disable();
            PriorityScheduler.ThreadState currentThreadState = 
                ((PriorityScheduler)ThreadedKernel.scheduler).getThreadState(KThread.getCurrentThread());
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 7);
            Machine.interrupt().restore(st);
          
            // Block on conditionLock now since T1 has it 
            Lib.debug(dbgTesting, "[ Program_13 ]: T2 Acquiring conditionLock");

            mutex2.V();
            mutex2 = new Semaphore(1);
            conditionLock.acquire();
            wrongOrder = false;

            // Lock 2 is going to be blocked on Program_14. Now Program_12 has
            // lock1 so this thread will block
            lock2.acquire(); 
            mutex3.V();
            mutex3 = new Semaphore(1); 
            conditionLock.release();

            lock1.acquire(); 

            // We're back so Program_12 release lock1 and is now dead. 1st
            // verify donation from Program_14
            st = Machine.interrupt().disable();
            int checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread());
            Lib.assertTrue(checkValue == 107, "** DONATION ERROR: expected 107, got "+ checkValue);
            checkValue = currentThreadState.donationManagementDB.size();
            Lib.assertTrue(checkValue == 1, "** Donation DB ERROR: expected 1, got "+checkValue);
            Machine.interrupt().restore(st);

            // Release the lock2 and conditionLock
            lock2.release();
        }
    }

    // Together with Program_13 and 12, runs a series of transitivity donation
    // tests by creating a triangular-style resource dependency
    static class Program_14 implements Runnable
    {
        int checkValue = 0;
        public void run()
        {
            PriorityScheduler.ThreadState currentThreadState = 
                ((PriorityScheduler)ThreadedKernel.scheduler).getThreadState(KThread.getCurrentThread());

            boolean st = Machine.interrupt().disable();
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 5);
            Machine.interrupt().restore(st);

            // Main is sleeping on ordering. T1 is sleeping on mutex. We want to
            // force Program_13 to run before Program_14. If Program_13 ran
            // first, then mutex2 has a Semaphore value of 1 so a call to P()
            // shouldn't do anything here. Otherwise, if we're running
            // Program_14 before Program_13, then this call will be on a
            // Semaphore with value of 0 and so will put Program_14 to sleep so
            // Program_13 can run. 
            mutex2.P();

            // Should now block and donate to Program_12
            mutex.V();
            conditionLock.acquire();

            if( wrongOrder == true )
                conditionLock.release();
            mutex3.P();

            ordering2.V(); // Wake up Program_12 again before blocking. 

            // Program_13 is holding this. It goes T3 ---> L2, T2 -----> L1, T1
            lock2.acquire();
            lock2.release();

            // Now that we're back, Program_12 and Program_13 must have already
            // exited. Verify priority and wake main
            st = Machine.interrupt().disable();
            int checkValue = currentThreadState.donationManagementDB.size();
            Lib.assertTrue(checkValue == 0, "** Donation DB ERROR: expected 0, got "+checkValue);
            checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread());
            Lib.assertTrue(checkValue == 100, "** DONATION ERROR: expected 100, got "+ checkValue);
            Machine.interrupt().restore(st);

            // Now wake up main and go to sleep. Then main will try to join()
            // compelling donation 
            ordering.V(); // Wake up main.
            ordering.P(); // Sleep this thread. 

            // Now that we're back, main must be waiting for us to finish.
            // Verify priority donation and exit
            st = Machine.interrupt().disable();
            checkValue = ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread());
            Lib.assertTrue(checkValue == 101, "** DONATION ERROR: expected 101, got "+ checkValue);
            checkValue = currentThreadState.donationManagementDB.size();
            Lib.assertTrue(checkValue == 1, "** Donation DB ERROR: expected 1, got "+checkValue);
            Machine.interrupt().restore(st);
        }
    }

    // Test basic fork/join operations and thread priority. This is a prelim and
    // should be enhanced
    public static boolean test1() 
    {
        Lib.debug(dbgTesting, "[ TEST DRIVER ]: Entered PriorirtyScheduler test1 routine...");
        KThread thread1 = new KThread(new Program_4(1));
        KThread thread2 = new KThread(new Program_4(1));
        KThread thread3 = new KThread(new Program_4(1));
        thread2.setName("join thread...p=0");
        thread2.setName("Low priority");
        thread1.setName("High priority");

        //Initialize 3 threads to priorities 7,2,0
        boolean state = Machine.interrupt().disable();
        Lib.debug(dbgTesting, "[ TEST DRIVER ]: Setting priorities for 3 threads here");
        ThreadedKernel.scheduler.setPriority(thread1, 7); 
        ThreadedKernel.scheduler.setPriority(thread2, 2); 
        ThreadedKernel.scheduler.setPriority(thread3, 1); 
        Machine.interrupt().restore(state);

        thread1.fork();
        thread2.fork();
        thread3.fork();
        Lib.debug(dbgTesting, "[ TEST DRIVER ]: *** Calling join on thread3 (priority 0) from currentThread");
        thread3.join();
        Lib.debug(dbgTesting, "[ TEST DRIVER ]: Just after thread3 join here");
        thread1.join();
        thread2.join();
        return EXIT_SUCCESS;   
    }

    // A basic thread fork/join test with priority donation
    public static boolean test2()
    {
        String ID = "test2";
        
        Lib.debug(dbgTesting,"[ test2 ]: Creating threads, prog1_thread and prog2_thread");
        KThread prog1_thread = new KThread(new Program_1());
        KThread prog2_thread = new KThread(new Program_2());
        KThread prog3_thread = new KThread(new Program_3());
        prog2_thread.setName("PROG2");
        prog1_thread.setName("PROG1");
        prog3_thread.setName("PROG3");

        // Set priority of the 3 created threads and assert correct expected
        // priorities*/
        boolean oldInt = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(prog2_thread, 5);
        ThreadedKernel.scheduler.setPriority(prog1_thread, 1);
        ThreadedKernel.scheduler.setPriority(prog3_thread, 5);
        Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(prog1_thread) == 1,
            "Expected 0, got " + ThreadedKernel.scheduler.getEffectivePriority(prog1_thread) );
        Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(prog2_thread) == 5,
            "Expected 5, got " + ThreadedKernel.scheduler.getEffectivePriority(prog2_thread));
        Machine.interrupt().restore(oldInt);

        // Begin execution of thread 1 (Program 1)
        prog1_thread.fork();

        // Call mutex. This will put the current thread (should be main) to
        // sleep
        Lib.debug(dbgTesting,"calling mutex.P() in test2()");
        mutex.P();

        // Run program 2 and 3        
        prog2_thread.fork();
        prog3_thread.fork();

        Lib.debug(dbgTesting,ID + " calling join on PROG1");
        prog1_thread.join();
        Lib.debug(dbgTesting,ID + " calling join on PROG2");
        prog2_thread.join();
        Lib.debug(dbgTesting,ID + " calling join on PROG3");
        prog3_thread.join();

        return EXIT_SUCCESS;
    }

    // Run 3 threads of Program_4. All threads will have the same priority which
    // will asses whether Round-Robin type functionality still exists. Also, we
    // use a shared data variable to index the counts which will identify any
    // issues with order of execution. 
    public static boolean test4()
    {
        String ID = "test4";
        Lib.debug(dbgTesting,ID+ " Entering test...");
    
        // All should be initialized with default priority of 1
        KThread t1 = new KThread(new Program_4(1));
        KThread t2 = new KThread(new Program_4(1));
        KThread t3 = new KThread(new Program_4(1));
        t1.setName("T1");
        t2.setName("T2");
        t3.setName("T3");

        t1.fork();
        t2.fork();
        t3.fork();

        // Now join() this thread to wait for the other three to finish
        t1.join();
        t2.join();
        t3.join();
        
        return EXIT_SUCCESS;
    }
        
    // This test ensures that no priority donation takes place when
    // transferPriority is false
    public static boolean test3()
    {
        Lib.debug(dbgTesting,"Beginning test 3");
        KThread t1 = new KThread(new Program_5(3));
        KThread t2 = new KThread(new Program_5(3));
        t1.setName("T1");
        t2.setName("T2");

        boolean state = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(t1, 7); 
        ThreadedKernel.scheduler.setPriority(t2, 1); 
        Machine.interrupt().restore(state);
    
        t1.fork();
        t2.fork();

        Lib.debug(dbgTesting,"Joining t1");
        t1.join();
        Lib.debug(dbgTesting,"Joining t2");
        t2.join();
      
        return EXIT_SUCCESS;
    }

    // Burn this sucker down. Run as many threads in as many different ways
    // possible to try to break the scheduler
    public static boolean test5()
    {
       Lib.debug(dbgTesting,"Beginning test 4");
        KThread t1 = new KThread(new Program_5(35));
        KThread t2 = new KThread(new Program_5(43));
        KThread t3 = new KThread(new Program_5(53));
        KThread t4 = new KThread(new Program_5(23));
        KThread t5 = new KThread(new Program_5(13));
        KThread t6 = new KThread(new Program_5(33));
        KThread t7 = new KThread(new Program_5(63));
        KThread t8 = new KThread(new Program_5(73));
        KThread t9 = new KThread(new Program_5(83));
        KThread t10 = new KThread(new Program_5(53));
        KThread t11 = new KThread(new Program_5(43));
        KThread t12 = new KThread(new Program_5(63));
        KThread t13 = new KThread(new Program_5(83));
        KThread t14 = new KThread(new Program_4(83));
        KThread t15 = new KThread(new Program_4(83));
        KThread t16 = new KThread(new Program_4(83));
        KThread t17 = new KThread(new Program_4(43));
        KThread t18 = new KThread(new Program_4(33));
        KThread t19 = new KThread(new Program_4(63));
        KThread t20 = new KThread(new Program_6());
        KThread t21 = new KThread(new Program_5(83));
        KThread t22 = new KThread(new Program_5(23));
        KThread t23 = new KThread(new Program_6());
        KThread t24 = new KThread(new Program_8());
        KThread t25 = new KThread(new Program_8());
        KThread t26 = new KThread(new Program_8());
        KThread t27 = new KThread(new Program_8());
        KThread t28 = new KThread(new Program_8());
        KThread t29 = new KThread(new Program_7());
        KThread t30 = new KThread(new Program_7());
        t1.setName("T1");
        t2.setName("T2");
        t3.setName("T3");
        t4.setName("T4");
        t5.setName("T5");
        t6.setName("T6");
        t7.setName("T7");
        t8.setName("T8");
        t9.setName("T9");
        t10.setName("T10");
        t11.setName("T11");
        t12.setName("T12");
        t13.setName("T13");
        t14.setName("T14");
        t15.setName("T15");
        t16.setName("T16");
        t17.setName("T17");
        t18.setName("T18");
        t19.setName("T19");
        t20.setName("T20");
        t21.setName("T21");
        t22.setName("T22");
        t23.setName("T23");
        t24.setName("T24");
        t25.setName("T25");
        t26.setName("T26");
        t27.setName("T27");
        t28.setName("T28");
        t29.setName("T29");
        t30.setName("T30");

        boolean state = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(t1, 7); 
        ThreadedKernel.scheduler.setPriority(t2, 6); 
        ThreadedKernel.scheduler.setPriority(t3, 5); 
        ThreadedKernel.scheduler.setPriority(t4, 4); 
        ThreadedKernel.scheduler.setPriority(t5, 3); 
        ThreadedKernel.scheduler.setPriority(t6, 2); 
        ThreadedKernel.scheduler.setPriority(t7, 1); 
        ThreadedKernel.scheduler.setPriority(t8, 1); 
        ThreadedKernel.scheduler.setPriority(t9, 1); 
        ThreadedKernel.scheduler.setPriority(t10, 1); 
        ThreadedKernel.scheduler.setPriority(t11, 1); 
        ThreadedKernel.scheduler.setPriority(t12, 4); 
        ThreadedKernel.scheduler.setPriority(t13, 4); 
        ThreadedKernel.scheduler.setPriority(t14, 3); 
        ThreadedKernel.scheduler.setPriority(t15, 1); 
        ThreadedKernel.scheduler.setPriority(t16, 6); 
        ThreadedKernel.scheduler.setPriority(t17, 1); 
        ThreadedKernel.scheduler.setPriority(t18, 1); 
        ThreadedKernel.scheduler.setPriority(t19, 7); 
        ThreadedKernel.scheduler.setPriority(t20, 1); 
        ThreadedKernel.scheduler.setPriority(t21, 5); 
        ThreadedKernel.scheduler.setPriority(t22, 7); 
        ThreadedKernel.scheduler.setPriority(t23, 1); 
        ThreadedKernel.scheduler.setPriority(t24, 1); 
        ThreadedKernel.scheduler.setPriority(t25, 2); 
        ThreadedKernel.scheduler.setPriority(t26, 25252); 
        ThreadedKernel.scheduler.setPriority(t27, 3); 
        ThreadedKernel.scheduler.setPriority(t28, 6); 
        ThreadedKernel.scheduler.setPriority(t29, 2); 
        ThreadedKernel.scheduler.setPriority(t30, 7); 
        Machine.interrupt().restore(state);
    
        t1.fork();
        t2.fork();
        t3.fork();
        t4.fork();
        t5.fork();
        t6.fork();
        t7.fork();
        t8.fork();
        t9.fork();
        t10.fork();
        t11.fork();
        t12.fork();
        t13.fork();
        t14.fork();
        t15.fork();
        t16.fork();
        t17.fork();
        t18.fork();
        t19.fork();
        t20.fork();
        t21.fork();
        t22.fork();
        t23.fork();
        t24.fork();
        t25.fork();
        t26.fork();
        t27.fork();
        t28.fork();
        t29.fork();
        t30.fork();

        t1.join();
        t2.join();
        t3.join();
        t4.join();
        t5.join();
        t6.join();
        t7.join();
        t8.join();
        t9.join();
        t10.join();
        t11.join();
        t12.join();
        t13.join();
        t14.join();
        t15.join();
        t16.join();
        t17.join();
        t18.join();
        t19.join();
        t20.join();
        t21.join();
        t22.join();
        t23.join();
        t24.join();
        t25.join();
        t26.join();
        t27.join();
        t28.join();
        t29.join();
        t30.join();


        return EXIT_SUCCESS; 
    }

    // Run 3 threads of very different priorities and dump their execution order
    public static boolean test6()
    {
        System.out.println("Entered test6()");
        KThread T1 = new KThread(new KThread.PingTest(1));
        KThread T2 = new KThread(new KThread.PingTest(2000));
        KThread T3 = new KThread(new KThread.PingTest(2001));
        KThread T4 = new KThread(new KThread.PingTest(65536));
        KThread T5 = new KThread(new KThread.PingTest(1200000));
        KThread T6 = new KThread(new KThread.PingTest(72000));

        T1.setName("T1");
        T2.setName("T2");
        T3.setName("T3");
        T4.setName("T4");
        T5.setName("T5");
        T6.setName("T6");

        boolean st = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(T1, 1);
        ThreadedKernel.scheduler.setPriority(T2, 2000);
        ThreadedKernel.scheduler.setPriority(T3, 2001);
        ThreadedKernel.scheduler.setPriority(T4, 65536);
        ThreadedKernel.scheduler.setPriority(T6, 72000);
        ThreadedKernel.scheduler.setPriority(T5, 1200000);
        Machine.interrupt().restore(st);

        System.out.println("Forking 3 threads");
        T1.fork();
        T2.fork();
        T3.fork();
        T4.fork();
        T5.fork();
        T6.fork();
        KThread.yield();

        T1.join();
        T2.join();
        T3.join();
        T4.join();
        T5.join();
        T6.join();

        return true;
    }

    // Run a series of 3 programs with resources used in a systematic way to
    // compel donation transitivity, concurrent donation, and donation
    // revocation
    public static boolean test8()
    {
        Lib.debug(dbgTesting, "[ TEST8 ]: Entered TEST8");
        KThread t3 = new KThread(new Program_14());         // PRIO 5
        KThread t2 = new KThread(new Program_13(t3));       // PRIO 7
        KThread t1 = new KThread(new Program_12(t2, t3));   // PRIO 3
        boolean st = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(t1, 3);
        ThreadedKernel.scheduler.setPriority(t2, 7);
        ThreadedKernel.scheduler.setPriority(t3, 5);
        t1.setName("T1");
        t2.setName("T2");
        t3.setName("T3");
        Machine.interrupt().restore(st);
    
        // Fork t1 then yield so that t1 can acquire resources
        t1.fork();
        Lib.debug(dbgTesting, "Main is sleeping on the ordering lock now so T1 can run.");
        ordering.P();
        Lib.debug(dbgTesting, "Main is back from ordering lock, forking T2 and T3");

        // Now that t1 has the resources it needs, fork the remaining threads
        t2.fork();
        t3.fork();
         
        Lib.debug(dbgTesting, "Main is sleeping on the ordering lock again so T2 and T3 can run with T1");
        ordering.P();
        Lib.debug(dbgTesting, "Main is back from ordering sleep and is waking up T1 sleeping on mutex here");

        // We were just woken up by Program_14 which is sleeping on ordering
        // now. Wake it back up and join() against it 
        ordering.V();

        Lib.debug(dbgTesting, "main joining on t1");
        t1.join();
        Lib.debug(dbgTesting, "main joining on t2");
        t2.join();
        Lib.debug(dbgTesting, "main joining on t3");
        t3.join();

        // If nothing 'sploded, it must have executed properly so return success
        return EXIT_SUCCESS;
    }

    // Run all tests defined here 
    public static void runall() 
    {
         Lib.assertTrue(test1());    //Check for return value per test. They
         should return true if they succeed. 
         System.out.println("[ TEST1 ]: PASSED");
         Lib.assertTrue(test2());   
         System.out.println("[ TEST2 ]: PASSED");
         Lib.assertTrue(test3());
         System.out.println("[ TEST3 ]: PASSED");
         Lib.assertTrue(test4());
         System.out.println("[ TEST4 ]: PASSED");
         Lib.assertTrue(test5());
         System.out.println("[ TEST5 ]: PASSED");
         Lib.assertTrue(test6());
         System.out.println("Does test 6 look reasonable to you??");
         Lib.assertTrue(test8());
         System.out.println("[ TEST8 ]: PASSED");
    }

    // Global configuration and debug parameters 
    private static final char dbgTesting = 'Q';
    private static final boolean EXIT_SUCCESS = true;
    private static final boolean EXIT_FAILURE = false;
}
