package com.assistant.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.assistant.R;

import java.io.File;
import java.util.ArrayList;

import com.assistant.ui.FileChooserAdapter.FileInfo;

public class FileChooserActivity extends BaseAppCompatActivity {
    public static final String EXTRA_FILE_CHOOSER = "file_chooser";

    private GridView mGridView;
    private View mBackView;
    private View mBtExit;
    private TextView mTvPath;

    private String mSdcardRootPath;  //sdcard 根路�?
    private String mLastFilePath;    //当前显示的路�?
    private ArrayList<FileChooserAdapter.FileInfo> mFileLists;
    private FileChooserAdapter mAdatper;
    private OnClickListener mClickListener = new OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.imgBackFolder:
                    backProcess();
                    break;
                case R.id.btExit:
                    setResult(RESULT_CANCELED);
                    finish();
                    break;
                default:
                    break;
            }
        }
    };
    private OnItemClickListener mItemClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> adapterView, View view, int position,
                                long id) {
            FileInfo fileInfo = (FileInfo)(((FileChooserAdapter) adapterView.getAdapter()).getItem(position));
            if (fileInfo.isDirectory())
                updateFileItems(fileInfo.getFilePath());
            else if (true/*fileInfo.isPPTFile()*/) {
                Intent intent = new Intent();
                intent.putExtra(EXTRA_FILE_CHOOSER, fileInfo.getFilePath());
                setResult(RESULT_OK, intent);
                finish();
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

        setTitle(mSdcardRootPath);

        mBackView = findViewById(R.id.imgBackFolder);
        mBackView.setOnClickListener(mClickListener);
        mBtExit = findViewById(R.id.btExit);
        mBtExit.setOnClickListener(mClickListener);

        mTvPath = (TextView) findViewById(R.id.tvPath);

        mGridView = (GridView) findViewById(R.id.gvFileChooser);
        mGridView.setEmptyView(findViewById(R.id.tvEmptyHint));
        mGridView.setOnItemClickListener(mItemClickListener);
        setGridViewAdapter(mSdcardRootPath);
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
        mTvPath.setText(mLastFilePath);

        setTitle(mLastFilePath);

        if (mFileLists == null)
            mFileLists = new ArrayList<FileChooserAdapter.FileInfo>();
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