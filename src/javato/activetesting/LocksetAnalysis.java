package javato.activetesting;

import java.util.* ;

import javato.activetesting.activechecker.ActiveChecker;
import javato.activetesting.analysis.AnalysisImpl;
import javato.activetesting.common.Parameters;

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


public class LocksetAnalysis extends AnalysisImpl {

	public enum MemoryState {Virgin, Exclusive, Shared, SharedModified} ;

	public class Tuple<X, Y, Z> {
		public final X x;
		public final Y y;
		public final Z z;
		public Tuple(X x, Y y, Z z) { this.x = x; this.y = y; this.z = z; }
	}
	public class BeforeThreadInfo extends Tuple<Integer /*iid*/, Integer /*thread*/, Boolean /*isWrite*/> {
		public BeforeThreadInfo(Integer x, Integer y, Boolean z) { super(x, y, z); }
	}

	/* Data per thread -- */
	public HashMap<Integer /*thread*/, LinkedList<Integer> /*seq of iids*/>  stacks = new HashMap<Integer, LinkedList<Integer>>() ;
	public HashMap<Integer /*thread*/, LinkedList<Integer> /*seq of locks*/> heldLocks = new HashMap<Integer, LinkedList<Integer>>() ;
	/* -- Data per thread */

	/* Data per memory -- */
	public HashMap<Long /*memory*/, HashSet<Integer/*set of locks*/>> candidates = new HashMap<Long, HashSet<Integer>>() ;
	public HashMap<Long /*memory*/, MemoryState /*state*/> state = new HashMap<Long, MemoryState>() ;
	public HashMap<Long /*memory*/, Integer /*thread*/> firstThread = new HashMap<Long, Integer>() ;
	public HashMap<Long /*memory*/, BeforeThreadInfo> lastAccessLoc = new HashMap<Long, BeforeThreadInfo>() ;
	/* -- Data per memory */

	public void initialize() {

	}

	public void methodEnterBefore(Integer iid, Integer thread, String method) {
		synchronized(stacks) {
			LinkedList<Integer> st = stacks.get(thread) ;
			if (st == null) {
				st = new LinkedList<Integer>() ;
				stacks.put(thread, st) ;
			}
			st.addFirst(iid) ; //push iid which represents the code location of a method call site into the stack for this thread.
		}
	}

	public void methodExitAfter(Integer iid, Integer thread, String method) {
		synchronized(stacks) {
			LinkedList<Integer> st = stacks.get(thread) ;
			st.removeFirst() ; // pop the top-most iid from the stack.
		}
	}

	private void reportDatarace(Integer iid, Integer thread, boolean isWrite, BeforeThreadInfo beforeInfo) {
		System.out.println("LocksetAnalysis.java WARN: Detect data race at " +
							javato.activetesting.analysis.Observer.getIidToLine(iid) +
							" with " + (isWrite ? "write" : "read") + " operation");

		printStackTrace(thread, iid);

		Integer lastiid = beforeInfo.x;
		Integer lastThread = beforeInfo.y;
		Boolean isBeforeWrite = beforeInfo.z;

		System.out.print("\t\tLast access from ");
		System.out.print(javato.activetesting.analysis.Observer.getIidToLine(lastiid));
		System.out.print(" with " + (isBeforeWrite ? "write" : "read") + " operation");
		System.out.print(" by thread#" + lastThread + "\n");
	}

	private void printStackTrace(Integer thread, Integer iid) {
		LinkedList<Integer> st ;

		System.out.println("Stack trace of thread-" + thread) ;
		System.out.println("\t" + javato.activetesting.analysis.Observer.getIidToLine(iid)) ;

		synchronized (stacks) {
			st = stacks.get(thread) ;
			if (st != null) {
				for (Iterator<Integer> itr = st.iterator() ; itr.hasNext() ; ) {
					System.out.println("\t" + javato.activetesting.analysis.Observer.getIidToLine(itr.next())) ;
				}
			}
		}
	}

	public void lockBefore(Integer iid, Integer thread, Integer lock, Object actualLock) {
		synchronized(heldLocks) {
			LinkedList<Integer> currentLock = heldLocks.get(thread);
			if (currentLock == null) currentLock = new LinkedList<Integer>();

			currentLock.addLast(lock);
			heldLocks.put(thread, currentLock);
		}
	}

	public void lockAfter(Integer iid, Integer thread, Integer lock, Object actualLock) {
	}

	public void unlockAfter(Integer iid, Integer thread, Integer lock) {
		synchronized(heldLocks) {
			LinkedList<Integer> currentLock = heldLocks.get(thread);
			if (currentLock == null) currentLock = new LinkedList<Integer>();

			Integer beforeLock = currentLock.peekLast();
			if (beforeLock == lock) {
				currentLock.pollLast();
			} else {
				System.out.println("unlockAfter ERROR: lock and unlock doesn't match in " + iid);
			}
			heldLocks.put(thread, currentLock);
		}
	}

	public void startBefore(Integer iid, Integer parent, Integer child) {
	}

	public void waitAfter(Integer iid, Integer thread, Integer lock) {
	}

	public void notifyBefore(Integer iid, Integer thread, Integer lock) {
	}

	public void notifyAllBefore(Integer iid, Integer thread, Integer lock) {
	}

	public void joinAfter(Integer iid, Integer parent, Integer child) {
	}

	public void readBefore(Integer iid, Integer thread, Long memory, boolean isVolatile) {
		if (isVolatile) return ;

		LinkedList<Integer> currentLock;
		MemoryState currentState;
		Integer firstThreadNo;
		HashSet<Integer> lockCandidate;
		BeforeThreadInfo beforeInfo = null;

		synchronized (heldLocks) {
			currentLock = heldLocks.get(thread);
			if (currentLock == null) currentLock = new LinkedList<Integer>();
			heldLocks.put(thread, currentLock);
		}

		synchronized (firstThread) {
			firstThreadNo = firstThread.get(memory);
			if (firstThreadNo == null) firstThreadNo = thread;
		}

		synchronized (state) {
			currentState = state.get(memory);
			if (currentState == null) currentState = MemoryState.Virgin;

			switch (currentState) {
			case Virgin:
			case SharedModified:
			case Shared:
			default:
				break;
			case Exclusive:
				if (firstThreadNo == thread) break;
				currentState = MemoryState.Shared;
			}
			state.put(memory, currentState);
		}

		synchronized (candidates) {
			lockCandidate = candidates.get(memory);
			if (lockCandidate == null) {
				lockCandidate = new HashSet<Integer>();
				lockCandidate.addAll(currentLock);
			}

			lockCandidate.retainAll(currentLock);
			candidates.put(memory, lockCandidate);
		}

		synchronized (lastAccessLoc) {
			beforeInfo = lastAccessLoc.get(memory);
			lastAccessLoc.put(memory, new BeforeThreadInfo(iid, thread, false));
		}

		if (lockCandidate.size() == 0 && currentState == MemoryState.SharedModified) {
			reportDatarace(iid, thread, false, beforeInfo);
		}
	}

	public void readAfter(Integer iid, Integer thread, Long memory, boolean isVolatile) {
	}

	public void writeBefore(Integer iid, Integer thread, Long memory, boolean isVolatile) {
		if (isVolatile) return ;

		LinkedList<Integer> currentLock;
		MemoryState currentState;
		Integer firstThreadNo;
		HashSet<Integer> lockCandidate;
		BeforeThreadInfo beforeInfo = null;

		synchronized (heldLocks) {
			currentLock = heldLocks.get(thread);
			if (currentLock == null) currentLock = new LinkedList<Integer>();
			heldLocks.put(thread, currentLock);
		}

		synchronized (firstThread) {
			firstThreadNo = firstThread.get(memory);
			if (firstThreadNo == null) {
				firstThreadNo = thread;
				firstThread.put(memory, firstThreadNo);
			}
		}

		synchronized (state) {
			currentState = state.get(memory);
			if (currentState == null) currentState = MemoryState.Virgin;

			switch (currentState) {
			case Virgin:
				currentState = MemoryState.Exclusive;
				break;
			case Exclusive:
				if (firstThreadNo == thread) break;
			case Shared:
			case SharedModified:
			default:
				currentState = MemoryState.SharedModified;
				break;
			}
			state.put(memory, currentState);
		}

		synchronized (candidates) {
			lockCandidate = candidates.get(memory);
			if (lockCandidate == null) {
				lockCandidate = new HashSet<Integer>();
				lockCandidate.addAll(currentLock);
			}

			lockCandidate.retainAll(currentLock);
			candidates.put(memory, lockCandidate);
		}

		synchronized (lastAccessLoc) {
			beforeInfo = lastAccessLoc.get(memory);
			lastAccessLoc.put(memory, new BeforeThreadInfo(iid, thread, true));
		}

		if (lockCandidate.size() == 0 && currentState == MemoryState.SharedModified) {
			reportDatarace(iid, thread, true, beforeInfo);
		}
	}

	public void writeAfter(Integer iid, Integer thread, Long memory, boolean isVolatile) {
	}

	public void finish() {
	}
}
