package io.underdark.app;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.Random;

import io.underdark.app.model.Node;

public class MainActivity extends AppCompatActivity
{
	private TextView peersTextView;
	private TextView framesTextView;

	Node node;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		peersTextView = (TextView) findViewById(R.id.peersTextView);
		framesTextView = (TextView) findViewById(R.id.framesTextView);

		node = new Node(this);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		node.start();
	}

	@Override
	protected void onStop()
	{
		super.onStop();

		if(node != null)
			node.stop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings)
		{
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private static boolean started = false;

	public void sendFrames(View view)
	{
		/*if(!started)
		{
			started = true;
			node = new Node(this);
			node.start();
			return;
		}*/

		node.broadcastFrame(new byte[1]);

		for(int i = 0; i < 2000; ++i)
		{
			byte[] frameData = new byte[1024];
			new Random().nextBytes(frameData);

			node.broadcastFrame(frameData);
		}

		/*for(int i = 0; i < 100; ++i)
		{
			byte[] frameData = new byte[100 * 1024];
			new Random().nextBytes(frameData);

			node.broadcastFrame(frameData);
		}*/
	}

	public void refreshPeers()
	{
		peersTextView.setText(node.getLinks().size() + " connected");
	}

	public void refreshFrames()
	{
		framesTextView.setText(node.getFramesCount() + " frames");
	}
} // MainActivity
