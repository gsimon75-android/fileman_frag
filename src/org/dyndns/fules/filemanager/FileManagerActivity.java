package org.dyndns.fules.filemanager;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.Window;
import android.os.Bundle;
import android.util.Log;

public class FileManagerActivity extends Activity {
	public static final String TAG = "fileman";
	FragmentManager fragmgr;
	FilePanelFragment panel1, panel2;

	@Override public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate start");
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.filemanager);
		Log.d(TAG, "onCreate content view is set");
		
		fragmgr = getFragmentManager();
		panel1 = (FilePanelFragment)fragmgr.findFragmentById(R.id.file_panel_1);
		panel2 = (FilePanelFragment)fragmgr.findFragmentById(R.id.file_panel_2);

		panel1.setDestinationPanel(panel2);
		panel2.setDestinationPanel(panel1);

        Intent intent = getIntent();
		if (intent != null) {
			String mountPath = "/";
			Uri uri = intent.getData();
			if (uri != null) {
				mountPath = uri.getPath();
				if (!mountPath.endsWith("/"))
					mountPath = mountPath + "/";
			}
			else {
				Log.d(TAG, "No starting URI");
			}

			Log.d(TAG, "Mount path is " + mountPath);
			panel1.setWorkingDir("/");
			panel2.setWorkingDir(mountPath);
		}
		else {
			Log.d(TAG, "No starting intent found");
		}
		Log.d(TAG, "onCreate done");
	}

}
// vim: set ts=4 noet nowrap:
