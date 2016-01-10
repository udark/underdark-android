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

package impl.underdark.licensing;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.util.Random;

import io.underdark.util.dispatch.DispatchQueue;

public class Licenser
{
	private static final String trialText = "Underdark trial";
	private static final String siteUrl = "http://underdark.io";

	private Context context;
	private String license = null;
	private boolean notifyShown = false;

	public Licenser(Context context)
	{
		this.context = context.getApplicationContext();
	}

	public void setLicense(String license)
	{
		this.license = license;
	}

	private boolean isLicenseValid()
	{
		if(license == null)
			return false;

		if(!license.equalsIgnoreCase("F4DF0CDA-7A3B-4882-A8FD-C09DB76DF63B"))
			return false;

		return true;
	}

	public boolean checkLicense()
	{
		if(isLicenseValid())
			return true;

		DispatchQueue.main.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				Toast.makeText(context, trialText + ": " + siteUrl, Toast.LENGTH_LONG)
						.show();

				if(!notifyShown)
				{
					notifyShown = true;

					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl));
					//intent.setData(Uri.parse(siteUrl));
					PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, 0);

					Notification notification =
							new Notification.Builder(context)
									.setSmallIcon(android.R.drawable.sym_def_app_icon)
									.setContentTitle(trialText)
									.setContentText(siteUrl)
									.setTicker(trialText)
									//.setAutoCancel(true)
									//.setDeleteIntent(contentIntent)
									.setContentIntent(contentIntent)
									.build();

					NotificationManager manager = (NotificationManager)
							context.getSystemService(Context.NOTIFICATION_SERVICE);
					manager.notify(new Random().nextInt(), notification);
				} // if
			}
		});

		return false;
	}
} // Licenser
