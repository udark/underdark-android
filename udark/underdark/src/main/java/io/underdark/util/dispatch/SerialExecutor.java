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

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executor;

// http://www.javaspecialists.eu/archive/Issue206.html

public class SerialExecutor implements Executor {
	private final Queue<Runnable> tasks = new LinkedList<>();
	private final Executor executor;
	private Runnable active;

	public SerialExecutor(Executor executor)
	{
		this.executor = executor;
	}

	public synchronized void execute(final Runnable r)
	{
		tasks.add(new Runnable() {
			public void run() {
				try {
					r.run();
				} finally {
					scheduleNext();
				}
			}
		});
		if (active == null) {
			scheduleNext();
		}
	}

	protected synchronized void scheduleNext()
	{
		if ((active = tasks.poll()) != null) {
			executor.execute(active);
		}
	}
}
