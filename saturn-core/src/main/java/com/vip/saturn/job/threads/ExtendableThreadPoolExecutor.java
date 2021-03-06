/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.vip.saturn.job.threads;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Same as a java.util.concurrent.ThreadPoolExecutor but implements a much more efficient {@link #getSubmittedCount()}
 * method, to be used to properly handle the work queue. If a RejectedExecutionHandler is not specified a default one
 * will be configured and that one will always throw a RejectedExecutionException
 *
 */
public class ExtendableThreadPoolExecutor extends java.util.concurrent.ThreadPoolExecutor {

	/**
	 * The number of tasks submitted but not yet finished. This includes tasks in the queue and tasks that have been
	 * handed to a worker thread but the latter did not start executing the task yet. This number is always greater or
	 * equal to {@link #getActiveCount()}.
	 */
	private final AtomicInteger submittedCount = new AtomicInteger(0);

	public ExtendableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
			TaskQueue workQueue, ThreadFactory threadFactory) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, new RejectHandler());
		workQueue.setParent(this);
	}

	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		submittedCount.decrementAndGet();
	}

	public int getSubmittedCount() {
		return submittedCount.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute(Runnable command) {
		execute(command, 0, TimeUnit.MILLISECONDS);
	}

	/**
	 * Executes the given command at some time in the future. The command may execute in a new thread, in a pooled
	 * thread, or in the calling thread, at the discretion of the <tt>Executor</tt> implementation. If no threads are
	 * available, it will be added to the work queue. If the work queue is full, the system will wait for the specified
	 * time and it throw a RejectedExecutionException if the queue is still full after that.
	 *
	 * @param command the runnable task
	 * @param timeout A timeout for the completion of the task
	 * @param unit The timeout time unit
	 * @throws RejectedExecutionException if this task cannot be accepted for execution - the queue is full
	 * @throws NullPointerException if command or unit is null
	 */
	public void execute(Runnable command, long timeout, TimeUnit unit) {
		submittedCount.incrementAndGet();
		try {
			super.execute(command);
		} catch (RejectedExecutionException rx) {
			if (super.getQueue() instanceof TaskQueue) {
				final TaskQueue queue = (TaskQueue) super.getQueue();
				try {
					if (!queue.force(command, timeout, unit)) {
						submittedCount.decrementAndGet();
						throw new RejectedExecutionException("Queue capacity is full.");
					}
				} catch (Exception ignore) {
					submittedCount.decrementAndGet();
					throw new RejectedExecutionException(ignore);
				}
			} else {
				submittedCount.decrementAndGet();
				throw rx;
			}

		}
	}

	private static class RejectHandler implements RejectedExecutionHandler {
		@Override
		public void rejectedExecution(Runnable r, java.util.concurrent.ThreadPoolExecutor executor) {
			throw new RejectedExecutionException();
		}

	}

}
