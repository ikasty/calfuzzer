package javato.activetesting;

import javato.activetesting.activechecker.ActiveChecker;
import javato.activetesting.analysis.AnalysisImpl;
import javato.activetesting.common.Parameters;

import javato.activetesting.lockset.LockSet;
import javato.activetesting.lockset.LockSetTracker;
import javato.activetesting.reentrant.IgnoreRentrantLock;
import javato.activetesting.HybridRaceTracker;

import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * Copyright (c) 2007-2008,
 * Koushik Sen    <ksen@cs.berkeley.edu>
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * <p/>
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * <p/>
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * <p/>
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
public class HybridAnalysis extends AnalysisImpl {
//    need to declare data structures
//    In my implementation I had the following datastructure
    private VectorClockTracker vcTracker;
    private LockSetTracker lsTracker;
    private IgnoreRentrantLock ignoreRentrantLock;
    private HybridRaceTracker eb;

    public void initialize() {
        synchronized (ActiveChecker.lock) {
//    Your code goes here.
//    In my implementation I had the following code:
            vcTracker = new VectorClockTracker();
            lsTracker = new LockSetTracker();
            ignoreRentrantLock = new IgnoreRentrantLock();
            eb = new HybridRaceTracker();
        }
    }

    public void lockBefore(Integer iid, Integer thread, Integer lock) {
        synchronized (ActiveChecker.lock) {
//    Your code goes here.
//    In my implementation I had the following code:
            if (ignoreRentrantLock.lockBefore(thread, lock)) {
                boolean isDeadlock = lsTracker.lockBefore(iid, thread, lock);
            }
        }
    }

    public void unlockAfter(Integer iid, Integer thread, Integer lock) {
        synchronized (ActiveChecker.lock) {
//    Your code goes here.
//    In my implementation I had the following code:
            if (ignoreRentrantLock.unlockAfter(thread, lock)) {
                lsTracker.unlockAfter(thread);
            }
        }
    }

    public void newExprAfter(Integer iid, Integer object, Integer objOnWhichMethodIsInvoked) {
//  ignore this
    }

    public void methodEnterBefore(Integer iid) {
//  ignore this
    }

    public void methodExitAfter(Integer iid) {
//  ignore this
    }

    public void startBefore(Integer iid, Integer parent, Integer child) {
        synchronized (ActiveChecker.lock) {
//    Your code goes here.
//    In my implementation I had the following code:
            vcTracker.startBefore(parent, child);
        }
    }

    public void waitAfter(Integer iid, Integer thread, Integer lock) {
        synchronized (ActiveChecker.lock) {
//    Your code goes here.
//    In my implementation I had the following code:
            vcTracker.waitAfter(thread, lock);
        }
    }

    public void notifyBefore(Integer iid, Integer thread, Integer lock) {
        synchronized (ActiveChecker.lock) {
//    Your code goes here.
//    In my implementation I had the following code:
            vcTracker.notifyBefore(thread, lock);
        }
    }

    public void notifyAllBefore(Integer iid, Integer thread, Integer lock) {
        synchronized (ActiveChecker.lock) {
//    Your code goes here.
//    In my implementation I had the following code:
            vcTracker.notifyBefore(thread, lock);
        }
    }

    public void joinAfter(Integer iid, Integer parent, Integer child) {
        synchronized (ActiveChecker.lock) {
//    Your code goes here.
//    In my implementation I had the following code:
            vcTracker.joinAfter(parent, child);
        }
    }

    public void readBefore(Integer iid, Integer thread, Long memory) {
        synchronized (ActiveChecker.lock) {
//    Your code goes here.
//    In my implementation I had the following code:
            LockSet ls = lsTracker.getLockSet(thread);
            eb.checkRace(iid, thread, memory, false, vcTracker.getVectorClock(thread), ls);
            eb.addEvent(iid, thread, memory, false, vcTracker.getVectorClock(thread), ls);
        }
    }

    public void writeBefore(Integer iid, Integer thread, Long memory) {
        synchronized (ActiveChecker.lock) {
//    Your code goes here.
//    In my implementation I had the following code:
            LockSet ls = lsTracker.getLockSet(thread);
            eb.checkRace(iid, thread, memory, true, vcTracker.getVectorClock(thread), ls);
            eb.addEvent(iid, thread, memory, true, vcTracker.getVectorClock(thread), ls);
        }
    }

    public void finish() {
        synchronized (ActiveChecker.lock) {
            int nRaces=0; // nRaces must be equal to the number races detected by the hybrid race detector
//    Your code goes here.
//    In my implementation I had the following code:
            nRaces = eb.dumpRaces();
            System.out.println("*--> HybridAnalysis reveals " + nRaces
                + " potential races");
//    The following method call creates a file "error.list" containing the list of numbers "1,2,3,...,nRaces"
//    This file is used by run.xml to initialize Parameters.errorId with a number from from the list.
//    Parameters.errorId tells RaceFuzzer the id of the race that RaceFuzzer should try to create
            Parameters.writeIntegerList(Parameters.ERROR_LIST_FILE, nRaces);
        }
    }
}

class VectorClock {
  public static final int MAX_THREADS = 155;
  public Integer[] vc;
  VectorClock() {
    vc = new Integer[MAX_THREADS];
    for (int i = 0; i < MAX_THREADS; ++i) {
      vc[i] = 0;
    }
  }

  public void maximumUpdate(VectorClock rhs) {
    for (int i = 0; i < MAX_THREADS; ++i) {
      if (rhs.vc[i] > this.vc[i]) {
        this.vc[i] = rhs.vc[i];
      }
    }
  }

  public boolean lessThanEqual(VectorClock rhs) {
    for (int i = 0; i < MAX_THREADS; ++i) {
      if (this.vc[i] > rhs.vc[i]) {
        return false;
      }
    }
    return true;
  }

  public void increment(Integer thread) {
    this.vc[thread]++;
  }

  public String toString() {
    String s = "[";
    for (int i = 0; i < 20; ++i) {
      s += vc[i] + ",";
    }
    return s + "]";
  }
}

class VectorClockTracker {
  private VectorClock[] vectorClocks = new VectorClock[VectorClock.MAX_THREADS];
  private Map<Integer, VectorClock> lockClocks = new HashMap<Integer, VectorClock>();

  VectorClockTracker() {
    for (int i = 0; i < VectorClock.MAX_THREADS; ++i) {
      vectorClocks[i] = new VectorClock();
      vectorClocks[i].increment(i);
    }
  }

  public void startBefore(Integer parent, Integer child) {
    vectorClocks[child].maximumUpdate(vectorClocks[parent]);
    vectorClocks[child].increment(child);
    vectorClocks[parent].increment(parent);
  }
  public void joinAfter(Integer parent, Integer child) {
    vectorClocks[parent].maximumUpdate(vectorClocks[child]);
    vectorClocks[parent].increment(parent);
    vectorClocks[child].increment(child);
  }

  public void notifyBefore(Integer thread, Integer lock) {
    VectorClock lc = lockClocks.get(lock);
    lockClocks.remove(lock);
    if (lc != null) {
      vectorClocks[thread].maximumUpdate(lc);
      vectorClocks[thread].increment(thread);
    }
  }
  public void waitAfter(Integer thread, Integer lock) {
    VectorClock lc = lockClocks.get(lock);
    if (lc != null) {
      lc.maximumUpdate(vectorClocks[thread]);
      vectorClocks[thread].increment(thread);
    } else {
      lc = vectorClocks[thread];
    }
    lockClocks.put(lock, lc);
  }

  public VectorClock getVectorClock(Integer thread) {
    return vectorClocks[thread];
  }

}


