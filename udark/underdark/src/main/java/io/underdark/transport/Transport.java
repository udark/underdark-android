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

package io.underdark.transport;

import java.util.concurrent.Executor;

/**
 * Abstract transport interface, which can aggregate multiple
 * network communication protocols.
 * All methods of this class must be called via same handler
 * that was supplied when creating its object.
 */
public interface Transport
{
	/**
	 * Starts underlying network advertising and discovery.
	 * For each call of this method there must be corresponding
	 * stop() call.
	 */
	void start();

	/**
	 * Stops network advertising and discovery
	 * and disconnects all links.
	 * For each call of this method there must be corresponding
	 * start() call in the past.
	 */
	void stop();

	/**
	 * Must be called whenever main activity onResume() is called.
	 */
	void onMainActivityResumed();

	/**
	 * Must be called whenever main activity onPause() is called.
	 */
	void onMainActivityPaused();
} // Transport
