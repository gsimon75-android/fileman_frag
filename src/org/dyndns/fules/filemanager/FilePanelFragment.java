package org.dyndns.fules.filemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View.OnCreateContextMenuListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dyndns.fules.PosixFile;

public class FilePanelFragment extends Fragment implements AdapterView.OnItemClickListener, View.OnCreateContextMenuListener {
	private static final String	TAG = "fileman";

	Activity activity;

	View file_panel;            // the whole file panel
	TextView working_dir;       // the display of the current folder
	ListView content_list;      // the list of the folder content
	View progress_block;        // the progress block (progress_bar + progress_name), so it can be hidden/shown as one
	ProgressBar progress_bar;   // progress of the operations
	TextView progress_name;     // current item that's being operated on

	private Handler handler;    // for refreshing the progress from worker threads

	// filters what to display in the content list
	Pattern filter = null;
	boolean showHidden = true;
	boolean showFiles = true;
	boolean showOthers = true;
	boolean showUnreadable = true;

	String workingDir = "/";    // current folder
	FilePanelFragment destinationPanel = null;  // the destination of the copy/move operations

	FileInfoAdapter adapter;    // data source for the folder content list

	static FilePanelFragment hack_context_menu = null; // http://stackoverflow.com/questions/5297842/how-to-handle-oncontextitemselected-in-a-multi-fragment-activity
	// in short: all fragments get notification for context menu selections, but only the current one needs to handle it

	/*******************************************************************************
	 * class FileInfoItem
	 *
	 * An entry of the file panel list, it contains a File, a displayable name (needed because of '..')
	 * Responsible for highlighting when selected and when not accessible.
	 */

	class FileInfoItem implements Checkable, View.OnTouchListener {
		// item types, needed for filtering
		static final int TYPE_FILE      = 0;
		static final int TYPE_DIR       = 1;
		static final int TYPE_OTHER     = 2;
		static final int TYPE_BROKEN    = 3;
		static final int TYPE_MAX       = 4;

		PosixFile   file;       // the file itself
		String      dispName;   // the name to display ('..' does have a path, but this is what must be shown)
		int         type;
		boolean     checked;
		boolean     iconClicked;    // clicking on a folder icon means selection, clicking on the name means entering the folder

		public FileInfoItem(File f, String s) {
			file = new PosixFile(f.getPath());
			/*if (Symlink.isLink(file.getPath())) {
			  	if (s == null)
			  		s = file.getName();
			  	file = Symlink.resolveLink(file);
			  }*/
			if (s == null)
				dispName = file.getName();
			else
				dispName = s;

			if (!file.canRead())
				type = TYPE_BROKEN;
			else if (file.isFile())
				type = TYPE_FILE;
			else if (file.isDirectory())
				type = TYPE_DIR;
			else
				type = TYPE_OTHER;

			checked = false;
			iconClicked = false;
		}

		public int getType() {
			return type;
		}

		public PosixFile getFile() {
			return file;
		}

		public String getName() {
			return dispName;
		}

		public boolean isChecked() { // from Checkable
			return checked;
		}

		public void toggle() { // from Checkable
			setChecked(!checked);
		}

		public void setChecked(boolean c) { // from Checkable
			// NOTE: the view if NOT redrawn, it would slow down mass-(un)checking
			if ((c != checked) && !dispName.equals("/") && !dispName.equals(".."))
				checked = c;
			iconClicked = false;
		}

		public boolean isIconClicked() {
			return iconClicked;
		}

		public boolean onTouch(View v, MotionEvent event) { // from View.OnTouchListener
			if ((event.getAction() == MotionEvent.ACTION_DOWN) && (type == TYPE_DIR) && !dispName.equals("..") && !dispName.equals("/"))
				iconClicked = true;
			return false;
		}

	}

	/*******************************************************************************
	 * class FileInfoAdapter
	 *
	 * Stores the content of a base folder (FileInfoItems) and provides them for a ListView
	 * Responsible for the sorting and filtering.
	 */

	class FileInfoAdapter extends ArrayAdapter<FileInfoItem> {
		static final int layoutId = R.layout.file_panel_item;

		// compare criterion: primarily folders < files, secondarily by name
		Comparator<FileInfoItem> DIRS_FIRST = new Comparator<FileInfoItem>() {
			public int compare(FileInfoItem fi1, FileInfoItem fi2) {
				PosixFile f1 = fi1.getFile();
				PosixFile f2 = fi2.getFile();
				boolean isDir1 = f1.isDirectory();
				boolean isDir2 = f2.isDirectory();

				if (isDir1 && !isDir2)
					return -1;
				if (!isDir1 && isDir2)
					return 1;

				return fi1.getName().compareTo(fi2.getName());
			}
		};

		// findViewById() is costly, so keep the results cached and attached to the parent View as a tag
		class ViewHolder {
			FileInfoItem item; // needed to register OnTouchListener to it

			ImageView viewIcon;
			TextView viewName;
			TextView viewSize;
			TextView viewPerms;
			TextView viewOwner;
			TextView viewGroup;
			TextView viewMTime;
		}

		PosixFile base = null;  // base folder

		public FileInfoAdapter(Context context) {
			super(context, layoutId); // layoutId must be added
		}

		public void setBase(PosixFile f) {
			clear();
			base = f; // perhaps Symlink.resolveLink(f);

			if (base.isDirectory()) {
				File[] files = base.listFiles();
				if (files != null)
					for (File file : files) {
						//file = Symlink.resolveLink(file);
						add(new FileInfoItem(file, null));
					}
			}
			else {
				// NOTE: it doesn't make too much sense, but anyway...
				//base = Symlink.resolveLink(base);
				add(new FileInfoItem(base, null));
			}
			sort(DIRS_FIRST);

			File bp = base.getParentFile();
			if (bp != null) {
				insert(new FileInfoItem(new File("/"), "/"), 0);
				insert(new FileInfoItem(bp, ".."), 1);
			}
			notifyDataSetChanged();
		}

		// update an icon according to the type and the selection status
		public void updateIcon(FileInfoItem item, View itemView) {
			ViewHolder holder = (ViewHolder)itemView.getTag();

			if (holder == null) 
				return;

			if (item.isChecked()) {
				holder.viewName.setTextColor(0xffffff00);
				switch (item.getType()) {
					case FileInfoItem.TYPE_FILE:
						holder.viewIcon.setImageResource(R.drawable.icon_list_active_file);
						break;
					case FileInfoItem.TYPE_DIR:
						holder.viewIcon.setImageResource(R.drawable.icon_list_active_folder);
						break;
					case FileInfoItem.TYPE_BROKEN: 
						holder.viewIcon.setImageResource(R.drawable.icon_list_active_broken);
						break;
				}
			}
			else {
				holder.viewName.setTextColor(0xffffffff);
				switch (item.getType()) {
					case FileInfoItem.TYPE_FILE:
						holder.viewIcon.setImageResource(R.drawable.icon_list_passive_file);
						break;
					case FileInfoItem.TYPE_DIR:
						holder.viewIcon.setImageResource(R.drawable.icon_list_passive_folder);
						break;
					case FileInfoItem.TYPE_BROKEN: 
						holder.viewIcon.setImageResource(R.drawable.icon_list_passive_broken);
						break;
				}
			}
		}

		// create a new item view, re-using convertView if possible
		@Override public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			FileInfoItem item = getItem(position);

			if (convertView == null) {
				convertView = getActivity().getLayoutInflater().inflate(layoutId, parent, false);
				holder = new ViewHolder();
				holder.viewIcon = (ImageView)convertView.findViewById(R.id.file_panel_icon);
				holder.viewName = (TextView)convertView.findViewById(R.id.file_panel_name);
				holder.viewSize = (TextView)convertView.findViewById(R.id.file_panel_size);
				holder.viewPerms = (TextView)convertView.findViewById(R.id.file_panel_perms);
				holder.viewOwner = (TextView)convertView.findViewById(R.id.file_panel_owner);
				holder.viewGroup = (TextView)convertView.findViewById(R.id.file_panel_group);
				holder.viewMTime = (TextView)convertView.findViewById(R.id.file_panel_mtime);
				convertView.setTag(holder);
				// this was the costly part, compare it to the getTag() below :D
			}
			else {
				holder = (ViewHolder)convertView.getTag();
			}

			if ((item != null) && (holder != null)) {
				if (holder.item != item) {
					// this view already existed, but belonged to another item (eg. one of the previously displayed folder)
					holder.item = item;
					holder.viewIcon.setOnTouchListener(item);
				}
				PosixFile f = item.getFile();
				holder.viewName.setText(item.getName());
				holder.viewSize.setText(String.valueOf(f.length()));
				holder.viewPerms.setText(f.getPermissionString());
				holder.viewOwner.setText(String.valueOf(f.getOwner()));
				holder.viewGroup.setText(String.valueOf(f.getGroup()));
				holder.viewMTime.setText(DateFormat.format("yyyy-MM-dd kk:mm:ss", f.lastModified()));
				updateIcon(item, convertView);
			}
			return convertView;
		}

		@Override public boolean isEnabled(int position) {
			PosixFile file = ((FileInfoItem)getItem(position)).getFile();

			if ((!file.canRead() && !showUnreadable) || 
			    (file.isFile() && !showFiles) ||
			    (file.isHidden() && !showHidden) ||
			    (!file.isFile() && !file.isDirectory() && !showOthers))
				return false;

			if (file.isDirectory()) // always show all folders, regardless even of a filter
				return true;

			return (filter == null) || filter.matcher(file.getName()).find();
		}

	}

	/*******************************************************************************
	 * class Recurse
	 *
	 * An accessory class for recursing file hierarchies, see the callbacks
	 * for details.
	 */

	public abstract class Recurse {
		int depth = 0;

		// Called before entering a directory
		// Return value: true=enter it, false=skip it
		abstract boolean beforeDir(PosixFile d);

		// Called when processing a file in a directory
		// Return value: true=ok, false=abort
		abstract boolean onFile(PosixFile f);

		// Called after processing a directory
		// Return value: true=ok, false=abort
		abstract boolean afterDir(PosixFile d);

		// Initiate the traversal
		public boolean process(PosixFile px) {
			if (px.isDirectory()) {
				if (beforeDir(px)) {
					depth++;
					for (File c: px.listFiles())
						if (!process(new PosixFile(c.getPath()))) {
							depth--;
							return false;
						}
					depth--;
					if (!afterDir(px))
						return false;
				}
			}
			else {
				if (!onFile(px))
					return false;
			}
			return true;
		}

	}

	public class CollectFolderStatistics extends Recurse {
		long totalItems = 0;
		long totalLength = 0;

		public void reset() {
			totalItems = totalLength = 0;
		}

		public long getTotalItems() { return totalItems; }
		public long getTotalLength() { return totalLength; }

		@Override boolean beforeDir(PosixFile d) {
			return true;
		}

		@Override boolean onFile(PosixFile f) {
			totalItems++;
			totalLength += f.length();
			return true;
		}
		
		@Override boolean afterDir(PosixFile d) {
			totalItems++;
			return true;
		}
	}



	/*******************************************************************************
	 * class FilePanelFragment
	 *
	 * The fragment that contains the current-folder display and the file list
	 * Responsible for all the lifecycle management, the context menu and the file
	 * operations.
	 */

	@Override public void onAttach(Activity a) {
		// NOTE: from API23 this is deprecated, usage of onAttach(Context) is recommended instead
		super.onAttach(activity);
		Log.d(TAG, "onAttach");
		activity = a;
		handler = new Handler();
		adapter = new FileInfoAdapter(activity);
	}

	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.d(TAG, "onCreateView");
		file_panel = inflater.inflate(R.layout.file_panel_fragment, container, false);

		working_dir = (TextView)file_panel.findViewById(R.id.working_dir);

		content_list = (ListView)file_panel.findViewById(R.id.content_list);
		content_list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		content_list.setOnItemClickListener(this);
		registerForContextMenu(content_list);

		adapter.setBase(new PosixFile(workingDir));
		content_list.setAdapter(adapter);

		progress_block = file_panel.findViewById(R.id.progress_block);
		progress_bar = (ProgressBar)progress_block.findViewById(R.id.progress_bar);
		progress_name = (TextView)progress_block.findViewById(R.id.progress_name);

		return file_panel;
	}

	@Override public void onDestroyView() {
		Log.d(TAG, "onDestroyView");
		super.onDestroyView();
		// we want nice NullPointerExceptions if anyone tried to use the views, a stracktrace helps a lot to find the culprit
		file_panel = null;
		working_dir = null;
		content_list = null;
		progress_block = null;
		progress_bar = null;
		progress_name = null;
	}

	@Override public void onDetach() {
		Log.d(TAG, "onDetach");
		super.onDetach();
		activity = null;
	}

	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) { // from View.OnCreateContextMenuListener
		super.onCreateContextMenu(menu, v, menuInfo);
		Log.d(TAG, "onCreateContextMenu");
		getActivity().getMenuInflater().inflate(R.menu.file_panel_context_menu, menu);
		hack_context_menu = this;
	}

	public void onItemClick(AdapterView parent, View view, int position, long id) { // from AdapterView.OnItemClickListener
		// an item of the content list has been clicked: enter folder or select item
		FileInfoItem item = (FileInfoItem)content_list.getAdapter().getItem(position);
		Log.d(TAG, "onItemClick, position=" + position + ", id=" + id + ", type=" + item.getType() + ", isIconClicked=" + item.isIconClicked());

		if ((item.getType() == FileInfoItem.TYPE_DIR) && !item.isIconClicked()) {
			setWorkingDir(item.getFile().getPath());
		}
		else {
			item.toggle();
			adapter.updateIcon(item, view);
		}
	}

	// rescan after file operations
	public void rescan() {
		adapter.setBase(new PosixFile(workingDir));
		adapter.notifyDataSetChanged();
	}

	public String getWorkingDir() {
		return workingDir;
	}

	public void setWorkingDir(String wd) {
		if (!wd.endsWith("/"))
			wd = wd + "/";
		Log.d(TAG, "setWorkingDir('" + wd + "')");
		workingDir = wd;
		working_dir.setText(workingDir);
		working_dir.invalidate();
		rescan();
	}

	public void setDestinationPanel(FilePanelFragment other) {
		destinationPanel = other;
	}

	void setRegex(String s) {
		filter = (s != null) ? Pattern.compile(s) : null;
		content_list.invalidate(); // postInvalidate();
	}

	void setShowHidden(boolean v) {
		if (showHidden != v) {
			showHidden = v;
			adapter.notifyDataSetChanged();
		}
	}

	void setShowFiles(boolean v) {
		if (showFiles != v) {
			showFiles = v;
			adapter.notifyDataSetChanged();
		}
	}

	void setShowOthers(boolean v) {
		if (showOthers != v) {
			showOthers = v;
			adapter.notifyDataSetChanged();
		}
	}

	void setShowUnreadable(boolean v) {
		if (showUnreadable != v) {
			showUnreadable = v;
			adapter.notifyDataSetChanged();
		}
	}

	/*******************************************************************************
	 * Context menu of FilePanelFragment
	 *
	 * This is the entry point of the chooseable context menu actions ('commands'),
	 * then the commands themselves with the required helpers.
	 */

	@Override public boolean onContextItemSelected(MenuItem item) {
		if (hack_context_menu != this)
			return super.onContextItemSelected(item);
		hack_context_menu = null;

		Log.d(TAG, "onContextItemSelected, item=" + item);
		switch (item.getItemId()) {

			case R.id.file_panel_select:
				hack_context_menu = this; // re-enable it for the sub-menu selection
				break;

			case R.id.file_panel_select_none:
				{
					int nItems = adapter.getCount();
					for (int i = 0; i < nItems; i++)
						((FileInfoItem)adapter.getItem(i)).setChecked(false);
					adapter.notifyDataSetChanged();
				}
				break;

			case R.id.file_panel_select_all:
				{
					int nItems = adapter.getCount();
					for (int i = 0; i < nItems; i++)
						((FileInfoItem)adapter.getItem(i)).setChecked(true);
					adapter.notifyDataSetChanged();
				}
				break;

			case R.id.file_panel_select_toggle:
				{
					int nItems = adapter.getCount();
					for (int i = 0; i < nItems; i++)
						((FileInfoItem)adapter.getItem(i)).toggle();
					adapter.notifyDataSetChanged();
				}
				break;

			case R.id.file_panel_new_folder:
				{
					LayoutInflater inflater = getActivity().getLayoutInflater();
					AlertDialog d = new AlertDialog.Builder(getActivity()).setView(inflater.inflate(R.layout.file_panel_mkdir, null))
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int id) { cmdMkdir((AlertDialog)dialog); } })
						.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int id) { } })
						.create();
					d.show();
				}
				break;

			case R.id.file_panel_rename:
				{
					LayoutInflater inflater = getActivity().getLayoutInflater();
					AlertDialog d = new AlertDialog.Builder(getActivity()).setView(inflater.inflate(R.layout.file_panel_rename, null))
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int id) { cmdRename((AlertDialog)dialog); } })
						.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int id) { } })
						.create();
					d.show();
				}
				break;

			case R.id.file_panel_copy:
				new Thread(new Runnable() { public void run() { cmdCopy(); } } ).start();
				break;

			case R.id.file_panel_move:
				new Thread(new Runnable() { public void run() { cmdMove(); } } ).start();
				break;

			case R.id.file_panel_change_perms:
				{
					LayoutInflater inflater = getActivity().getLayoutInflater();
					AlertDialog d = new AlertDialog.Builder(getActivity()).setView(inflater.inflate(R.layout.file_panel_chmod, null))
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int id) { cmdChmod((AlertDialog)dialog); } })
						.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int id) { } })
						.create();
					d.show();
				}
				break;

			case R.id.file_panel_change_own:
				{
					LayoutInflater inflater = getActivity().getLayoutInflater();
					AlertDialog d = new AlertDialog.Builder(getActivity()).setView(inflater.inflate(R.layout.file_panel_chown, null))
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int id) { cmdChown((AlertDialog)dialog); } })
						.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int id) { } })
						.create();
					d.show();
				}
				break;

			case R.id.file_panel_delete:
				new Thread(new Runnable() { public void run() { cmdDelete(); } } ).start();
				break;

			default:
				return false;
		}
		return true;
	}

	// helper: sanity check for the destination panel
	private boolean destinationValid() {
		if (destinationPanel == null) {
			Log.e(TAG, "No destination panel");
			return false;
		}
		String destinationDir = destinationPanel.getWorkingDir();
		if (destinationDir.equals(workingDir)) { // TODO: handle symlinks that point to the same folder...
			Log.e(TAG, "Destination folder is the same as the source");
			return false;
		}
		if (!new PosixFile(destinationDir).canWrite()) {
			Log.e(TAG, "Destination folder is not writable");
			return false;
		}
		return true;
	}

	// helper: prepare and show progress blocks, collect ToDo statistics (items, bytes)
	private CollectFolderStatistics prepareFileOperations() {
		handler.post(new Runnable() {
			public void run() {
				content_list.setEnabled(false);
				progress_block.setVisibility(View.VISIBLE);
				progress_bar.setProgress(0);
				progress_name.setText("");
			}
		});

		int nItems = adapter.getCount();
		// count the items to process, needed for progress indicator
		CollectFolderStatistics cfs = new CollectFolderStatistics();
		for (int i = 0; i < nItems; i++) {
			FileInfoItem it = (FileInfoItem)adapter.getItem(i);
			if (it.isChecked())
				if (!cfs.process(it.getFile()))
					break;
		}
		return cfs;
	}

	// helper: update the progress indicator
	private void sendProgress(final long value, final String name) {
		handler.post(new Runnable() {
			public void run() {
				progress_bar.setProgress((int)value);
				progress_name.setText(name);
			}
		});
	}

	// helper: hide progress block and rescan folders
	private void finishFileOperations() {
		handler.post(new Runnable() {
			public void run() {
				progress_block.setVisibility(View.GONE);
				content_list.setEnabled(true);
				rescan();
				if (destinationPanel != null)
					destinationPanel.rescan();
			}
		});
	}

	// helper: convert a source glob to regex pattern, eg. "a*tree.*" -> "^\Qa\E(.*)\Qtree.\E(.*)$"
	static String srcGlobToRegex(String glob) {
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;

		sb.append("^");
		for (String s: glob.split("\\*", -1)) {
			if (isFirst)
				isFirst = false;
			else
				sb.append("(.*)");

			if (!s.isEmpty()) {
				sb.append("\\Q");
				sb.append(s);
				sb.append("\\E");
			}
		}
		sb.append("$");
		return sb.toString();
	}

	// helper: convert a destination glob to regex replacement, eg. "a*tree.*" -> "a$1tree.$2"
	static String dstGlobToReplacement(String glob) {
		StringBuilder sb = new StringBuilder();
		int idx = 0;

		for (String s: glob.split("\\*", -1)) {
			if (idx > 0) {
				sb.append("$");
				sb.append(String.valueOf(idx));
			}
			idx++;

			if (!s.isEmpty())
				sb.append(s);
		}
		return sb.toString();
	}

	// helper: copy a file
	// dear google, if FileUtils.java is open-source, it might be accessible as well...
	public static boolean copyFile(File srcFile, File destFile) {
		boolean result = false;
		Log.d(TAG, "cp " + srcFile.getPath() + " " + destFile.getPath());

		try {
			Thread.sleep(srcFile.length() / 1024L);
		}
		catch (InterruptedException e) {
		}

		try {
			InputStream in = new FileInputStream(srcFile);
			try {
				if (destFile.exists())
					destFile.delete();
				FileOutputStream out = new FileOutputStream(destFile);
				try {
					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = in.read(buffer)) >= 0)
						out.write(buffer, 0, bytesRead);
				}
				finally {
					out.flush();
					out.close();
				}
				result = true;
			}
			finally  {
				in.close();
			}
		}
		catch (IOException e) { }
		return result;
	}

	// "Create new folder"

	public void cmdMkdir(AlertDialog d) {
		EditText edit_name = (EditText)d.findViewById(R.id.file_panel_mkdir_name);
		String path = edit_name.getText().toString();
		File newDir = new File(workingDir + path);

		try {
			if (!newDir.getCanonicalPath().startsWith(new PosixFile(workingDir).getCanonicalPath())) {
				Log.e(TAG, "Did not create illegal folder " + path);
				return;
			}
		}
		catch (IOException e) {
			Log.e(TAG, "Could not create " + path + ": " + e);
			return;
		}
		if (newDir.mkdir()) {
			Log.d(TAG, "Created " + path);
			rescan();
		}
		else {
			Log.e(TAG, "Could not create '" + path + "'");
		}
	}

	// "Rename"

	public void cmdRename(AlertDialog d) {
		String src_mask = srcGlobToRegex(((EditText)d.findViewById(R.id.file_panel_src_mask)).getText().toString());
		if (src_mask.contains("/")) {
			Log.e(TAG, "Did not rename from illegal pattern " + src_mask);
			return;
		}

		String dst_mask = dstGlobToReplacement(((EditText)d.findViewById(R.id.file_panel_dst_mask)).getText().toString());
		if (dst_mask.contains("/")) {
			Log.e(TAG, "Did not rename to illegal pattern " + dst_mask);
			return;
		}

		// NOTE: rename is cheap, is not recursive, so no progress bar
		Pattern p = Pattern.compile(src_mask);
		int nItems = adapter.getCount();
		for (int i = 0; i < nItems; i++) {
			FileInfoItem it = (FileInfoItem)adapter.getItem(i);
			if (it.isChecked()) {
				Matcher m = p.matcher(it.getName());
				if (m.matches()) {
					String newName = m.replaceAll(dst_mask);
					if (it.getFile().renameTo(new PosixFile(workingDir, newName))) {
						Log.d(TAG, "Renamed " + it.getName() + " to " + newName);
						it.setChecked(false);
					}
					else {
						Log.d(TAG, "Cannot rename " + it.getName() + " to " + newName);
					}
				}
			}
		}
		rescan();
	}

	// "Delete"

	void cmdDelete() {
		Log.i(TAG, "Deleting selection from " + workingDir);
		CollectFolderStatistics cfs = prepareFileOperations();
		final int nSelected = (int)cfs.getTotalItems();
		handler.post(new Runnable() { public void run() { progress_bar.setMax(nSelected); } });

		// do the actual task
		Recurse r = new Recurse() {
			long processedItems = 0;
			@Override boolean beforeDir(PosixFile d) {
				return true;
			}
			@Override boolean onFile(PosixFile f) {
				String s = f.getPath();
				sendProgress(++processedItems, s);
				if (!f.delete())
					Log.e(TAG, "Could not delete " + s);
				else
					Log.d(TAG, "Deleted " + s);
				return true;
			}
			@Override boolean afterDir(PosixFile d) {
				String s = d.getPath();
				sendProgress(++processedItems, s);
				if (!d.delete())
					Log.e(TAG, "Could not delete " + s);
				else
					Log.d(TAG, "Deleted " + s);
				return true;
			}
		};

		int nItems = adapter.getCount();
		for (int i = 0; i < nItems; i++) {
			FileInfoItem it = (FileInfoItem)adapter.getItem(i);
			if (it.isChecked()) {
				if (!r.process(it.getFile()))
					break;
				it.setChecked(false);
			}
		}

		finishFileOperations();
	}

	// "Copy"

	void cmdCopy() {
		if (!destinationValid())
			return;

		final String destinationDir = destinationPanel.getWorkingDir();
		Log.i(TAG, "Copying selection from " + workingDir + " to " + destinationDir);
		CollectFolderStatistics cfs = prepareFileOperations();
		final int nKBytes = (int)(cfs.getTotalLength() / 1024L);
		final int wdLen = workingDir.length(); // for generating the destination paths
		handler.post(new Runnable() { public void run() { progress_bar.setMax(nKBytes); } });

		// create the copier that will be invoked for the selection
		Recurse r = new Recurse() {
			long processedBytes = 0;

			@Override boolean beforeDir(PosixFile d) {
				String dst = destinationDir + d.getPath().substring(wdLen);
				File df = new File(dst);
				if (!df.mkdir()) {
					Log.e(TAG, "Could not create " + dst);
					return false;
				}
				Log.d(TAG, "Created " + dst);
				return true;
			}
			
			@Override boolean onFile(PosixFile f) {
				String src = f.getPath();
				String dst = destinationDir + src.substring(wdLen);
				File df = new File(dst);
				if (copyFile(f, df)) {
					Log.d(TAG, "Copied " + src + " to " + dst);
				}
				else {
					Log.e(TAG, "Could not copy " + src + " to " + dst);
					processedBytes += f.length();
					sendProgress(processedBytes / 1024L, dst);
				}
				return true;
			}
			
			@Override boolean afterDir(PosixFile d) {
				return true;
			}
		};

		// apply the copier for the selected items
		int nItems = adapter.getCount();
		for (int i = 0; i < nItems; i++) {
			FileInfoItem it = (FileInfoItem)adapter.getItem(i);
			if (it.isChecked()) {
				if (!r.process(it.getFile()))
					break;
				it.setChecked(false);
			}
		}
		finishFileOperations();
	}

	// "Move"

	void cmdMove() {
		if (!destinationValid())
			return;

		final String destinationDir = destinationPanel.getWorkingDir();
		Log.i(TAG, "Moving selection from " + workingDir + " to " + destinationDir);
		CollectFolderStatistics cfs = prepareFileOperations();
		final int nKBytes = (int)(cfs.getTotalLength() / 1024L);
		final long destDeviceId = new PosixFile(destinationDir).getDeviceId();
		final int wdLen = workingDir.length(); // for generating the destination paths
		handler.post(new Runnable() { public void run() { progress_bar.setMax(nKBytes); } });

		Recurse r = new Recurse() {
			long processedBytes = 0;

			@Override boolean beforeDir(PosixFile d) {
				String src = d.getPath();
				String dst = destinationDir + src.substring(wdLen);
				File df = new File(dst);

				if (destDeviceId == d.getDeviceId()) { // moving to the same device, try renaming first
					if (d.renameTo(df)) // moved successfully, no need to traverse
						return false; 
					Log.w(TAG, "Cannot move " + src + " to " + dst + " (same device), trying copy+del instead");
				}
				if (df.mkdir()) // created dest folder, traverse and move the content
					return true;

				Log.e(TAG, "Folder " + dst + " cannot be created");
				return false;
			}

			@Override boolean onFile(PosixFile f) {
				String src = f.getPath();
				String dst = destinationDir + src.substring(wdLen);
				File df = new File(dst);
				boolean success = false;

				if (destDeviceId == f.getDeviceId()) { // moving to the same device, try renaming first
					if (f.renameTo(df))
						success = true;
					else
						Log.w(TAG, "Cannot move " + src + " to " + dst + " (same device), trying copy+del instead");
				}

				if (!success) {
					if (copyFile(f, df)) {
						success = true;
						if (!f.delete())
							Log.w(TAG, "Could not delete " + src + " after copying");
					}
					else {
						Log.e(TAG, "Could not copy " + src + " to " + dst);
					}
				}

				if (success) {
					Log.d(TAG, "Moved " + src + " to " + dst);
					processedBytes += f.length();
					sendProgress(processedBytes / 1024L, dst);
				}
				return success;
			}
			@Override boolean afterDir(PosixFile d) {
				if (!d.delete())
					Log.w(TAG, "Could not delete folder " + d.getPath() + " after copying");
				return true;
			}
		};

		int nItems = adapter.getCount();
		for (int i = 0; i < nItems; i++) {
			FileInfoItem it = (FileInfoItem)adapter.getItem(i);
			if (it.isChecked()) {
				if (!r.process(it.getFile()))
					break;
				it.setChecked(false);
			}
		}
		finishFileOperations();
	}

	// "Chmod"

	public void cmdChmod(AlertDialog d) {

		Log.i(TAG, "Changing permissions of selection from " + workingDir);
		final int filePerms = 
			(((CheckBox)d.findViewById(R.id.file_panel_us_file)).isChecked() ? 04000 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_ur_file)).isChecked() ? 00400 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_uw_file)).isChecked() ? 00200 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_ux_file)).isChecked() ? 00100 : 0) +
			//
			(((CheckBox)d.findViewById(R.id.file_panel_gs_file)).isChecked() ? 02000 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_gr_file)).isChecked() ? 00040 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_gw_file)).isChecked() ? 00020 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_gx_file)).isChecked() ? 00010 : 0) +
			//
			(((CheckBox)d.findViewById(R.id.file_panel_os_file)).isChecked() ? 01000 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_or_file)).isChecked() ? 00004 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_ow_file)).isChecked() ? 00002 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_ox_file)).isChecked() ? 00001 : 0);

		final int dirPerms = 
			(((CheckBox)d.findViewById(R.id.file_panel_us_dir)).isChecked() ? 04000 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_ur_dir)).isChecked() ? 00400 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_uw_dir)).isChecked() ? 00200 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_ux_dir)).isChecked() ? 00100 : 0) +
			//
			(((CheckBox)d.findViewById(R.id.file_panel_gs_dir)).isChecked() ? 02000 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_gr_dir)).isChecked() ? 00040 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_gw_dir)).isChecked() ? 00020 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_gx_dir)).isChecked() ? 00010 : 0) +
			//
			(((CheckBox)d.findViewById(R.id.file_panel_os_dir)).isChecked() ? 01000 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_or_dir)).isChecked() ? 00004 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_ow_dir)).isChecked() ? 00002 : 0) +
			(((CheckBox)d.findViewById(R.id.file_panel_ox_dir)).isChecked() ? 00001 : 0);

		CollectFolderStatistics cfs = prepareFileOperations();
		final int nSelected = (int)cfs.getTotalItems();
		handler.post(new Runnable() { public void run() { progress_bar.setMax(nSelected); } });

		// do the actual task
		Recurse r = new Recurse() {
			long processedItems = 0;
			@Override boolean beforeDir(PosixFile d) {
				d.setPermissions(0777); // try to make sure we can enter - will be set to dirPerms afterwards anyway
				return true;
			}
			@Override boolean onFile(PosixFile f) {
				String s = f.getPath();
				sendProgress(++processedItems, s);
				if (!f.setPermissions(filePerms))
					Log.e(TAG, "Could not chmod " + s);
				else
					Log.d(TAG, "Chmodded " + s);
				return true;
			}
			@Override boolean afterDir(PosixFile d) {
				String s = d.getPath();
				sendProgress(++processedItems, s);
				if (!d.setPermissions(dirPerms))
					Log.e(TAG, "Could not chmod " + s);
				else
					Log.d(TAG, "Chmodded " + s);
				return true;
			}
		};

		int nItems = adapter.getCount();
		for (int i = 0; i < nItems; i++) {
			FileInfoItem it = (FileInfoItem)adapter.getItem(i);
			if (it.isChecked()) {
				if (!r.process(it.getFile()))
					break;
				it.setChecked(false);
			}
		}

		finishFileOperations();
	}

	// "Chown"

	public void cmdChown(AlertDialog d) {
		Log.i(TAG, "Changing ownership of selection from " + workingDir);

		String uidString = ((EditText)d.findViewById(R.id.file_panel_owner)).getText().toString();
		String gidString = ((EditText)d.findViewById(R.id.file_panel_group)).getText().toString();
		final long uid = uidString.isEmpty() ? -1 : Long.parseLong(uidString);
		final long gid = gidString.isEmpty() ? -1 : Long.parseLong(gidString);

		CollectFolderStatistics cfs = prepareFileOperations();
		final int nSelected = (int)cfs.getTotalItems();
		handler.post(new Runnable() { public void run() { progress_bar.setMax(nSelected); } });

		// do the actual task
		Recurse r = new Recurse() {
			long processedItems = 0;
			@Override boolean beforeDir(PosixFile d) {
				return true;
			}
			@Override boolean onFile(PosixFile f) {
				String s = f.getPath();
				sendProgress(++processedItems, s);
				if (!f.setOwnerAndGroup(uid, gid))
					Log.e(TAG, "Could not chown " + s);
				else
					Log.d(TAG, "Chowned " + s);
				return true;
			}
			@Override boolean afterDir(PosixFile d) {
				String s = d.getPath();
				sendProgress(++processedItems, s);
				if (!d.setOwnerAndGroup(uid, gid))
					Log.e(TAG, "Could not chown " + s);
				else
					Log.d(TAG, "Chowned " + s);
				return true;
			}
		};

		int nItems = adapter.getCount();
		for (int i = 0; i < nItems; i++) {
			FileInfoItem it = (FileInfoItem)adapter.getItem(i);
			if (it.isChecked()) {
				if (!r.process(it.getFile()))
					break;
				it.setChecked(false);
			}
		}

		finishFileOperations();
	}

}
// vim: set ts=4 noet nowrap:
