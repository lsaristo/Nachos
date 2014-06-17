/** 
 * Test the functionality of the PriorirtyScheduler class. Note that this
 * module must be imported into files wishing to be tested.
 *
 * Calling symantics: - Run a single test contained herein:
 * PrioritySchedulerTest.testx() for test number x.  - Run all tests contained
 * hereing: PrioritySchedulerTest.runall().
 *
 * Note that methods declared here must be static to function properly. 'make'
 * changes to this file from the respective projx directory. 
 *
 * To see DEBUGGING output on the console, run nachos with the -d x switch
 * (along with any other debugging flags, of course. 
 */
package nachos.threads;
import nachos.threads.*;
import nachos.machine.*;
 
public class PrioritySchedulerTest 
{
    // Shared data goes here to be accessed by all threads
    private static Lock lock1 = new Lock();
    private static Lock conditionLock = new Lock();
    private static Lock sharedIntLock = new Lock();
    private static Semaphore mutex = new Semaphore(0);
    private static Condition2 cond = new Condition2(conditionLock);
    private static int whoFinishedFirst = 0;
    private static int sharedData = 0;
    private static int sharedInteger = 0;
    private static double nonLinearSharedData = 5;

    // This test program grabs a lock then does a long computation 
    // spanning multiple yield()s. The purpose of this test is to 
    // verify that the functionality of the PriorityQueue behaves as 
    // expected with the Lock class.
    static class Program_1 implements Runnable
    {
        public void run()
        {
            Lib.debug(dbgTesting,"[ Program_1 ]: Entered new Program_1");
            Lib.debug(
                dbgTesting
                , "[ Program_1 ]: Thread running me is: " 
                + KThread.getCurrentThreadStats()
            );

            Lib.debug(dbgTesting,"[ Program_1 ]: Aquiring Lock lock1");
            lock1.acquire(); 

            // Wake up the main thread
            Lib.debug(
                dbgTesting
                , "[ Program_1 ]: Calling mutex.V() to wake up main thread"
            );
            mutex.V();
            Lib.debug(
                dbgTesting
                , "[ Program_1 ]: Now that we've got the lock, lets do " 
                + "something time-consuming"
            );
            int x = 0;
            for(int i = 1; i<1000000000; i++) {
                if(i % 10000000 == 0)
                    KThread.yield();
                x = x + i - (x/i);
            }
            Lib.debug(
                dbgTesting
                , "[ Program_1 ]: Done with the computation, releasing the lock"
            );
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
            Lib.debug(
                dbgTesting
                , "[ Program_2 ]: Thread running me is: " 
                + KThread.getCurrentThreadStats()
            );
            Lib.debug(
                dbgTesting
                , "[ Program_2 ]: First, lets just yield() so Program_1 can" 
                + " start and grab the lock"
            );
            KThread.yield();
            Lib.debug(
                dbgTesting
                , "[ Program_2 ]: Lets try to acquire the lock Program_1 is "
                + "working with..."
            );
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

        Program_12(KThread inThread, KThread transThread) { transitiveDonationThread = transThread; currentBestThread = inThread; }

        public void run()
        {
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 Entered Program_12.i Grabbing conditionLock");
            PriorityScheduler.ThreadState currentThreadState = ((PriorityScheduler)ThreadedKernel.scheduler).getThreadState(KThread.getCurrentThread());
            conditionLock.acquire();
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 got conditionLock, calling mutex.p() to sleep"); 
            mutex.P(); 
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 Woke back up from mutex");
            
            // We should have received priority donations of 5 and 7 from 2 
            // threads waiting on lock1 so EP=7 
            boolean st = Machine.interrupt().disable();
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 7);
            Lib.assertTrue(currentThreadState.donationManagementDB.size() == 3);
            Lib.assertTrue(currentThreadState.currentBestDonor.thread == currentBestThread);
            Machine.interrupt().restore(st);
            
            // Release the lock, this should make the other two threads revoke 
            // their donations 
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 releasing conditionLock. T2 and T3 should be waiting for it");
            conditionLock.release();
            st = Machine.interrupt().disable();
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 3);
            Lib.assertTrue(currentThreadState.donationManagementDB.size() == 1);
        
            // Next, force the priority of this thread 
            ThreadedKernel.scheduler.setPriority(KThread.getCurrentThread(), 0);
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 1);
            ThreadedKernel.scheduler.setPriority(KThread.getCurrentThread(), 2);
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 2);
            Machine.interrupt().restore(st);

            // 5 wants the lock1 resource and 7 wants 5's resource...I smell 
            // transitive donation 
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 acquiring lock1");
            lock1.acquire();
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 got lock1, yield()ing now");
            KThread.yield();
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 back from yield() still holding lock1");
            st = Machine.interrupt().disable();

            // Here we should have received a transitive donation from 7 --> 5 
            // ---> me 
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 7);
            Lib.assertTrue(currentThreadState.currentBestDonor.thread == transitiveDonationThread);

            // We call setPriority on 7 to lower it to 6. Now we have 6 --> 5 
            // --> me so make sure 6 updates 5 and 5 updates me 
            ThreadedKernel.scheduler.setPriority(currentBestThread, 6);
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 6);
            Lib.assertTrue(currentThreadState.currentBestDonor.thread == transitiveDonationThread);

            // Next, set from priority 6 to priority 4. This should make 5 the 
            // effective priority since it has priority higher than 4 and is the 
            // thread actually donating 
            ThreadedKernel.scheduler.setPriority(currentBestThread, 4);
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 5);
            Lib.assertTrue(currentThreadState.currentBestDonor.thread == transitiveDonationThread);

            // Lastly, set priority of 7 back to 7 since it expects to be 7 from 
            // here on. Also make sure it updates this thread's donation 
            // accordinyl back to what it was 
            ThreadedKernel.scheduler.setPriority(currentBestThread, 7);
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 7);
            Lib.assertTrue(currentThreadState.currentBestDonor.thread == transitiveDonationThread);
            Lib.debug(dbgTesting, "[ Program_12 ]: T1 releasing lock1 and exiting");
            lock1.release();

            // Once lock1 is released, 5 gets lock1 and eventually 7 gets 
            // conditionLock from 5 so all donations to here get revoked now 
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 2); 
            Machine.interrupt().restore(st);
        }
    }

    // Part of a 3 program set in test8 with Program_14 and Program_12 that runs 
    // a series of chained dependence priority donation tests.
    static class Program_13 implements Runnable
    {
        static KThread currentBestThread;
    
        Program_13(KThread inThread) { currentBestThread = inThread; }

        public void run()
        {
            Lib.debug(dbgTesting, "[ Program_13 ]: T2 Entered Program_13");

            boolean st = Machine.interrupt().disable();
            PriorityScheduler.ThreadState currentThreadState = ((PriorityScheduler)ThreadedKernel.scheduler).getThreadState(KThread.getCurrentThread());
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 7);
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 7);
            Machine.interrupt().restore(st);
            
            Lib.debug(dbgTesting, "[ Program_13 ]: T2 Acquiring conditionLock. We're expecting donor to be "+currentBestThread); 
            conditionLock.acquire();
            Lib.debug(dbgTesting, "[ Program_13 ]: T2 Has conditionLock now. Now sleeping on cond.");
            st = Machine.interrupt().disable();
            Lib.assertTrue(currentThreadState.currentBestDonor.thread == currentBestThread);
            Lib.assertTrue(currentThreadState.currentBestOffer == 5);
            Machine.interrupt().restore(st);

            cond.sleep(); // give Program_14 a chance to get conditionLock          
            Lib.debug(dbgTesting, "[ Program_13 ]: T2 woke from cond. Releasing conditionLock and exiting");
            conditionLock.release();
        }
    }

    // Together with Program_13 and 12, runs a series of transitivity donation
    // tests by creating a triangular-style resource dependency
    static class Program_14 implements Runnable
    {
        public void run()
        {
            Lib.debug(dbgTesting, "[ Program_14 ]: Entered Program_14, trying to acquire conditionLock"); 
            conditionLock.acquire();
            Lib.debug(dbgTesting, "[ Program_14 ]: Has the conditionLock, now we will wake Program_13");
                    
            cond.wakeAll(); // Wake Program_13 now that we have the lock. 

            // This thread now holds a lock that was just held by program_12 but
            // is still being waited for by Program 15 The program running this
            // thread has native priority of 5 but should have a donation of 7
            boolean st = Machine.interrupt().disable();
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 5);
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 5);
            Machine.interrupt().restore(st);
      
            Lib.debug(dbgTesting, "[ Program_14 ]: Trying to acquire lock1, should be held by Program_12 (T1)");
            
            // Try to acquire lock1 held by Program_12 of priority 2
            lock1.acquire();
            Lib.debug(dbgTesting, "[ Program_14 ]: Got lock1. Relasing it and the conditionLock and exiting");
            st = Machine.interrupt().disable();

            // Priority 7 thread wants conditionLock so it donates priority. We
            // then donate to Program_12 to get lock1
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 7);
            lock1.release();
            conditionLock.release();
            
            // Priority 7 thread was waiting for conditionLock so it should now
            // revoke its donation
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 5);
            Machine.interrupt().restore(st);
 
            // Now that we released the lock, the last thread (prio 5) should
            // hae grabbed it and revoked our EP
            st = Machine.interrupt().disable();
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 5);
            Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(KThread.getCurrentThread()) == 5);
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
        ThreadedKernel.scheduler.setPriority(thread3, 0); 
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
        ThreadedKernel.scheduler.setPriority(prog1_thread, 0);
        ThreadedKernel.scheduler.setPriority(prog3_thread, 5);
        Lib.assertTrue(ThreadedKernel.scheduler.getEffectivePriority(prog1_thread) == 0,
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
        ThreadedKernel.scheduler.setPriority(t2, 0); 
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
        ThreadedKernel.scheduler.setPriority(t8, 0); 
        ThreadedKernel.scheduler.setPriority(t9, 1); 
        ThreadedKernel.scheduler.setPriority(t10, 1); 
        ThreadedKernel.scheduler.setPriority(t11, 1); 
        ThreadedKernel.scheduler.setPriority(t12, 4); 
        ThreadedKernel.scheduler.setPriority(t13, 4); 
        ThreadedKernel.scheduler.setPriority(t14, 3); 
        ThreadedKernel.scheduler.setPriority(t15, 1); 
        ThreadedKernel.scheduler.setPriority(t16, 6); 
        ThreadedKernel.scheduler.setPriority(t17, 1); 
        ThreadedKernel.scheduler.setPriority(t18, 0); 
        ThreadedKernel.scheduler.setPriority(t19, 7); 
        ThreadedKernel.scheduler.setPriority(t20, 0); 
        ThreadedKernel.scheduler.setPriority(t21, 5); 
        ThreadedKernel.scheduler.setPriority(t22, 7); 
        ThreadedKernel.scheduler.setPriority(t23, 0); 
        ThreadedKernel.scheduler.setPriority(t24, 1); 
        ThreadedKernel.scheduler.setPriority(t25, 2); 
        ThreadedKernel.scheduler.setPriority(t26, 2); 
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

    // Execute two threads operating on shared data that must execute in the
    // proper order.
    public static boolean test6()
    {
        double nonLinearSharedData_OLD = nonLinearSharedData;
        KThread sq = new KThread(new Program_10());
        KThread sqrt = new KThread(new Program_9());
        boolean st = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(sq, 0);
        ThreadedKernel.scheduler.setPriority(sqrt, 7);
        sq.setName("square");
        sqrt.setName("sqroot");
        Machine.interrupt().restore(st);

        // Fork threads in reverse priority order
        sq.fork();
        sqrt.fork();

        // Now yield() to give them control
        KThread.yield();

        // Compute the expected return value if the threads execute in the
        // proper order.
        double correctOutput = (new Program_10().returnValue((new Program_9()).returnValue(5)));

        // Wait for both threads to finish
        sq.join();
        sqrt.join();
        double outValue = nonLinearSharedData;
        nonLinearSharedData = nonLinearSharedData_OLD;

        // Finally, check that the results are as expected
        if(outValue == correctOutput)
            return EXIT_SUCCESS;

        System.out.println("[ TEST6 ]: ERROR: Failed test6. Expected " + correctOutput + " got " + nonLinearSharedData);
        return EXIT_FAILURE;
    }

    // Run a similar test to test6 but with priority donation factored in
    public static boolean test7()
    {
        double nonLinearSharedData_OLD = nonLinearSharedData;
        Lib.debug(dbgTesting, "[ TEST7 ]: Entered TEST7");
        KThread sq = new KThread(new Program_10());
        KThread sqrt = new KThread(new Program_9());
        KThread joinThread = new KThread(new Program_11());
        boolean st = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(sq, 5);
        ThreadedKernel.scheduler.setPriority(sqrt, 7);
        sq.setName("square");
        sqrt.setName("sqroot");
        joinThread.setName("JoinThread");
        Machine.interrupt().restore(st);

        // Fork threads according to priority
        Lib.debug(dbgTesting, "[ TEST7 ]: Forking joinThread");
        joinThread.fork();
        KThread.yield();
        sqrt.fork();
        sq.fork();

        // Now yield() to give them control
        KThread.yield();

        // Compute the expected return value if the threads execute in the
        // proper order. NOTE the initial value now is 4 not 5.  This is a
        // consequence of Program_11 being donated to by Program_9 and thus
        // executing before Program_10 as expected.  Without priority donation,
        // Program_10 would precede Program_11 (with priority 1) and thus
        // nonLinearSharedData would be the wrong value after the threads all
        // run to completion. 
        double correctOutput = (new Program_10().returnValue((new Program_9()).returnValue(4)));

        // Wait for threads to finish
        joinThread.join();
        sq.join();
        sqrt.join();

        // Restore state data
        double outValue = nonLinearSharedData;
        nonLinearSharedData = nonLinearSharedData_OLD;
        
        // Finally, check that the results are as expected
        if(outValue == correctOutput)
            return EXIT_SUCCESS;

        System.out.println("[ TEST7 ]: ERROR: Failed test7. Expected " + correctOutput + " got " + nonLinearSharedData);
        return EXIT_FAILURE;
    }

    // Run a series of 3 programs with resources used in a systematic way to
    // compel donation transitivity, concurrent donation, and donation
    // revocation
    public static boolean test8()
    {
        Lib.debug(dbgTesting, "[ TEST8 ]: Entered TEST8");
        KThread t3 = new KThread(new Program_14()); // PRIO 5
        KThread t2 = new KThread(new Program_13(t3)); // PRIO 7
        KThread t1 = new KThread(new Program_12(t2, t3)); // PRIO 3
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
        Lib.debug(dbgTesting, "Main yield()ing so t1 can run");
        KThread.yield();

        // Now that t1 has the resources it needs, fork the remaining threads
        t2.fork();
        t3.fork();
         
        Lib.debug(dbgTesting, "Main is yield()ing so t2 and t3 can try to grab resources");
        KThread.yield(); //Let t2 and t3 try to acquire() lock1
        Lib.debug(dbgTesting, "Main is waking up T1 sleeping on mutex here");
        mutex.V();  // wake up t1, which is the holder of lock1 at the moment. 

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
        Lib.assertTrue(test1());    
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
        System.out.println("[ TEST6 ]: PASSED");
        Lib.assertTrue(test7());
        System.out.println("[ TEST7 ]: PASSED");
        Lib.assertTrue(test8());
        System.out.println("[ TEST8 ]: PASSED");

        System.out.println("\n******* All PriorityScheduler Tests Passed ************\n");
    }

    // Global configuration and debug parameters
    private static final char dbgTesting = 'x';
    private static final boolean EXIT_SUCCESS = true;
    private static final boolean EXIT_FAILURE = false;
}
