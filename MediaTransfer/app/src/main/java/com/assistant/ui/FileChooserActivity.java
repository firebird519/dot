package com.assistant.ui;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.assistant.R;

import java.io.File;
import java.util.ArrayList;

import com.assistant.ui.FileChooserAdapter.FileInfo;

import com.assistant.utils.Log;

public class FileChooserActivity extends BaseAppCompatActivity {
    public static final String EXTRA_FILE_PATH_NAME = "file_path_name";
    public static final String EXTRA_CHOOSER_TYPE = "file_chooser_type"; // file or folder

    public static final int CHOOSE_TYPE_FILE = 0;
    public static final int CHOOSE_TYPE_FOLDER = 1;

    private GridView mGridView;
    private View mBtnExit;
    private View mBtnSelect;
    private TextView mCurPath;

    private int mChooseType = CHOOSE_TYPE_FILE;

    private String mSdcardRootPath;  //sdcard 根路
    private String mLastFilePath;    //当前显示的路
    private ArrayList<FileChooserAdapter.FileInfo> mFileLists;
    private FileChooserAdapter mAdatper;
    private OnClickListener mClickListener = new OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.exit:
                    setResult(RESULT_CANCELED);
                    finish();
                    break;
                case R.id.select:
                    onSelected(mLastFilePath);
                default:
                    break;
            }
        }
    };
    private OnItemClickListener mItemClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> adapterView, View view, int position,
                                long id) {
            FileInfo fileInfo = (((FileChooserAdapter) adapterView.getAdapter()).getItem(position));
            if (fileInfo.isDirectory())
                updateFileItems(fileInfo.getFilePath());
            else if (true/*fileInfo.isPPTFile()*/) {
                onSelected(fileInfo.getFilePath());
            } else {
                toast(getText(R.string.open_file_error_format));
            }
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.filechooser_show);

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mSdcardRootPath = Environment.getExternalStorageDirectory().getAbsolutePath();

        getSupportActionBar().setSubtitle(mSdcardRootPath);

        mBtnExit = findViewById(R.id.exit);
        mBtnExit.setOnClickListener(mClickListener);

        mBtnSelect = findViewById(R.id.select);
        mBtnSelect.setOnClickListener(mClickListener);

        mCurPath = (TextView) findViewById(R.id.cur_path);

        mGridView = (GridView) findViewById(R.id.file_grid_view);
        mGridView.setEmptyView(findViewById(R.id.empty_hint));
        mGridView.setOnItemClickListener(mItemClickListener);
        setGridViewAdapter(mSdcardRootPath);

        initAccordingToChooseType();
    }

    public static void showChooseFileActivity(Fragment fragment, Context context, int type,
                                              int requestCode) {
        Log.d("FileChooserActivity", "showChooseFileActivity, type:" + type
                + ", context:" + fragment + ", requestCode:" + requestCode);
        if (fragment == null) {
            return;
        }

        Intent intent = new Intent();
        intent.setClass(context, FileChooserActivity.class);

        intent.putExtra(EXTRA_CHOOSER_TYPE, type);

        fragment.startActivityForResult(intent, requestCode);
    }

    private void initAccordingToChooseType() {
        Intent intent = getIntent();

        if (intent != null && intent.hasExtra(EXTRA_CHOOSER_TYPE)) {
            mChooseType = intent.getIntExtra(EXTRA_CHOOSER_TYPE, CHOOSE_TYPE_FILE);
        }

        if (mChooseType == CHOOSE_TYPE_FOLDER) {
            getSupportActionBar().setTitle(R.string.select_folder_title);
            mBtnSelect.setVisibility(View.VISIBLE);
        } else {
            getSupportActionBar().setTitle(R.string.select_file_title);
            mBtnSelect.setVisibility(View.GONE);
        }
    }

    private void onSelected(String pathName) {
        Log.d(this, "onSelected, pathName:" + pathName);

        Intent intent = new Intent();
        intent.putExtra(EXTRA_FILE_PATH_NAME, pathName);
        setResult(RESULT_OK, intent);

        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            //onBackPressed();
            backProcess();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
        backProcess();
    }

    private void setGridViewAdapter(String filePath) {
        updateFileItems(filePath);
        mAdatper = new FileChooserAdapter(this, mFileLists);
        mGridView.setAdapter(mAdatper);
    }

    private void updateFileItems(String filePath) {
        mLastFilePath = filePath;
        mCurPath.setText(mLastFilePath);

        getSupportActionBar().setSubtitle(mLastFilePath);

        if (mFileLists == null)
            mFileLists = new ArrayList<>();
        if (!mFileLists.isEmpty())
            mFileLists.clear();

        File[] files = folderScan(filePath);
        if (files == null)
            return;

        for (int i = 0; i < files.length; i++) {
            if (files[i].isHidden())  // 不显示隐藏文�?
                continue;

            String fileAbsolutePath = files[i].getAbsolutePath();
            String fileName = files[i].getName();
            boolean isDirectory = false;
            if (files[i].isDirectory()) {
                isDirectory = true;
            }
            FileInfo fileInfo = new FileInfo(fileAbsolutePath, fileName, isDirectory);
            mFileLists.add(fileInfo);
        }
        //When first enter , the object of mAdatper don't initialized
        if (mAdatper != null)
            mAdatper.notifyDataSetChanged();  //重新刷新
    }

    private File[] folderScan(String path) {
        File file = new File(path);
        File[] files = file.listFiles();
        return files;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode()
                == KeyEvent.KEYCODE_BACK) {
            backProcess();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    //返回上一层目录的操作
    public void backProcess() {
        //判断当前路径是不是sdcard路径 �?如果不是，则返回到上�?���?
        if (!mLastFilePath.equals(mSdcardRootPath)) {
            File thisFile = new File(mLastFilePath);
            String parentFilePath = thisFile.getParent();
            updateFileItems(parentFilePath);
        } else {   //是sdcard路径 ，直接结�?
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void toast(CharSequence hint) {
        Toast.makeText(this, hint, Toast.LENGTH_SHORT).show();
    }
}