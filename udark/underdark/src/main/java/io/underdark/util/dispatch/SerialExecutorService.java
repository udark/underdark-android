/*
 * Copyright (c) 2016 Vladimir L. Shabanov <virlof@gmail.com>
 *
 * Licensed under the Underdark License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://underdark.io/LICENSE.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.underdark.util.dispatch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link SerialExecutorService} implementation which executes one task at a time
 * using an underlying executor as the actual processor of the task.
 * <p>
 * 
 * This implementation differs from an {@link Executor} created by
 * {@link Executors#newSingleThreadExecutor()} in that there is not actual
 * thread handling done by {@link SerialExecutorService} but instead the processing of
 * the tasks is delegated to an underlying executor that can be shared among
 * many {@link Executor}. <p>
 * 
 * Tasks submitted on a {@link SerialExecutorService} will be processed sequentially
 * and in the exact same order in which they were submitted, regardless of the
 * number of threads available in the underlying executor.
 * 
 * 
 * @author muralx (Diego Belfer)
 * https://github.com/muralx/java-fun
 * 
 */
public class SerialExecutorService extends AbstractExecutorService {
    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int TERMINATED = 2;

    final Lock lock = new ReentrantLock();
    final Condition termination = lock.newCondition();

    final Executor underlyingExecutor;
    final ArrayDeque<Runnable> commands;

    volatile int state = RUNNING;
    Runnable currentCommand;

    /*
     * The runnable we submit into the underlyingExecutor, we avoid creating
     * unnecessary runnables since only one will be submitted at a time
     */
    private final Runnable innerRunnable = new Runnable() {

        public void run() {
            /*
             * If state is TERMINATED, skip execution
             */
            if (state == TERMINATED) {
                return;
            }

            try {
                currentCommand.run();
            } finally {
                lock.lock();
                try {
                    currentCommand = commands.pollFirst();
                    if (currentCommand != null && state < TERMINATED) {
                        try {
                            underlyingExecutor.execute(this);
                        } catch (Exception e) {
                            //The underlying executor may have been shutdown.
                            //We would need a kind of handler for this.
                            //Terminate this executor and clean pending command for now
                            currentCommand = null;
                            commands.clear();
                            transitionToTerminated();
                        }
                    } else {
                        if (state == SHUTDOWN) {
                            transitionToTerminated();
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    };

    /**
     * Creates a new {@link SerialExecutorService}. <p>
     * 
     * @param underlyingExecutor
     *            The underlying executor to use for executing the tasks
     *            submitted into this executor.
     */
    public SerialExecutorService(Executor underlyingExecutor) {
        this.underlyingExecutor = underlyingExecutor;
        this.commands = new ArrayDeque<Runnable>();
    }

    public void execute(Runnable command) {
        lock.lock();
        try {
            if (state != RUNNING) {
                //throw new IllegalStateException("Executor has been shutdown");
                return;
            }
            if (currentCommand == null && commands.isEmpty()) {
                currentCommand = command;
                underlyingExecutor.execute(innerRunnable);
            } else {
                commands.add(command);
            }
        } finally {
            lock.unlock();
        }
    }

    public void shutdown() {
        lock.lock();
        try {
            if (state == RUNNING) {
                if (currentCommand == null && commands.isEmpty()) {
                    transitionToTerminated();
                } else {
                    state = SHUTDOWN;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public List<Runnable> shutdownNow() {
        lock.lock();
        try {
            if (state < TERMINATED) {
                transitionToTerminated();
                ArrayList<Runnable> result = new ArrayList<Runnable>(commands);
                commands.clear();
                return result;
            }
            return Collections.<Runnable>emptyList();
        } finally {
            lock.unlock();
        }
    }

    public boolean isShutdown() {
        return state > RUNNING;
    }

    public boolean isTerminated() {
        return state == TERMINATED;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lock();
        try {
            while (!isTerminated() && nanos > 0) {
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
        }
        return isTerminated();
    }
    
    /*
     * Lock must me held when calling this method
     */
    private void transitionToTerminated() {
        state = TERMINATED;
        termination.signalAll();
    }
}
