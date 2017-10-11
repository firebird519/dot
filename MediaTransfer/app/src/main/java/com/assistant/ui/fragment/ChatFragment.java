package com.assistant.ui.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.assistant.R;
import com.assistant.connection.ConnectionManager;
import com.assistant.events.ChatMessageEvent;
import com.assistant.events.ClientInfo;
import com.assistant.events.Event;
import com.assistant.events.FileEvent;
import com.assistant.mediatransfer.ClientManager;
import com.assistant.ui.FileChooserActivity;
import com.assistant.utils.FileOpenIntentUtils;
import com.assistant.utils.Log;
import com.assistant.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class ChatFragment extends Fragment {
    private static final String TAG = "ChatFragment";

    private final static String INTENT_EXTRA_CONNECTION_INDEX = "extra_connection_index";

    private static final int MENU_DELETE_ID = 0;
    private static final int MENU_OPEN_ID = 1;
    private static final int MENU_SEND_ID = 2;
    private static final int MENU_SAVE_AS_ID = 3;

    private final static int FILE_CHOOSER_REQUEST_CODE = 1;
    private final static int FOLDER_CHOOSER_REQUEST_CODE = 2;

    private ListView mChattingListView;
    private ChattingAdapter mChattingAdapter;

    private Button mSendBtn;
    private EditText mMsgEditText;
    private ImageButton mFileChooseBtn;

    private LayoutInflater mLayoutInflater = null;

    private Context mContext;
    private Activity mParentActivity;

    private int mConnId = -1;
    private ClientManager mMediaTransManager;
    private ConnectionManager mConnectionManager;

    private ClientInfo mConnClientInfo;
    private List<Event> mChatEventList;

    private String mSelectedFilePathName;
    private FileEvent mSaveAsFileEvent;

    //2 mins, and 10s for test mode
    private static final long TIME_DISPLAY_TIMESTAMP = Utils.DEBUG_CONNECT_SELF ? 10*1000 : 2*60*1000;

    private static final int EVENT_LIST_UPDATE = 0;
    private static final int EVENT_SCREEN_UPDATE = 1;
    private static final int EVENT_CONNECTION_DISCONNECTED = 2;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_LIST_UPDATE:
                    mChattingAdapter.notifyDataSetChanged();
                    break;
                case EVENT_SCREEN_UPDATE:
                    updateChatScreen();
                    break;
                case EVENT_CONNECTION_DISCONNECTED:
                    handleConnectionDisconnected();
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.chatting_layout, container, false);
    }

    private void initListItemLongClickContextMenu(ContextMenu menu, Event event) {
        menu.setHeaderTitle(R.string.chat_option_header);
        menu.add(0, MENU_DELETE_ID, 0, R.string.delete);

        if (event instanceof FileEvent) {
            menu.add(0, MENU_OPEN_ID, 0, R.string.open);
            menu.add(0, MENU_SAVE_AS_ID, 0, R.string.save_as);
        }

        if (false) {
            menu.add(0, MENU_SEND_ID, 0, R.string.send);
        }
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 向 ViewTreeObserver 注册方法，以获取控件尺寸
        /*ViewTreeObserver vto = view.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                int h = view.getHeight();
                Log.d(TAG, "Height=" + h); // 得到正确结果

                // 成功调用一次后，移除 Hook 方法，防止被反复调用
                // removeGlobalOnLayoutListener() 方法在 API 16 后不再使用
                // 使用新方法 removeOnGlobalLayoutListener() 代替
                view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        });*/

        Log.d(this, "onDialogViewCreated");

        mChattingListView = (ListView)view.findViewById(R.id.chatting_list_view);
        mChattingListView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu,
                                            View v,
                                            ContextMenu.ContextMenuInfo menuInfo) {
                int position = ((AdapterView.AdapterContextMenuInfo)menuInfo).position;
                Log.d(this, "onCreateContextMenu, position:" + position);

                Event event = mChattingAdapter.getItem(position);
                initListItemLongClickContextMenu(menu, event);
            }
        });

        mMsgEditText = (EditText)view.findViewById(R.id.msg_input_view);
        mFileChooseBtn = (ImageButton)view.findViewById(R.id.msg_file_choose_btn);
        mFileChooseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FileChooserActivity.showChooseFileActivity(ChatFragment.this,
                        mContext,
                        FileChooserActivity.CHOOSE_TYPE_FILE,
                        FILE_CHOOSER_REQUEST_CODE);
            }
        });
        mSendBtn = (Button)view.findViewById(R.id.msg_send_btn);
        mSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = mMsgEditText.getText().toString();

                if (msg.length() > 0) {
                    mMsgEditText.setText("");

                    Log.d(this, "msg:" + msg);
                    if (!TextUtils.isEmpty(mSelectedFilePathName)) {
                        mMediaTransManager.sendFile(mConnId, mSelectedFilePathName, null);
                        mSelectedFilePathName = "";
                    } else {
                        ChatMessageEvent event =
                                new ChatMessageEvent(msg,
                                        System.currentTimeMillis(),
                                        mConnId,
                                        mConnClientInfo.clientUniqueId,
                                        false);

                        mMediaTransManager.sendEvent(mConnId, event, null);
                        mHandler.sendEmptyMessage(EVENT_LIST_UPDATE);
                    }
                }
            }
        });

        mChattingAdapter = new ChattingAdapter();
        mChattingListView.setAdapter(mChattingAdapter);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = ((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).position;

        switch (item.getItemId()) {
            case MENU_DELETE_ID:
                mChattingAdapter.delete(position);
                break;
            case MENU_OPEN_ID:
                mChattingAdapter.openFile(position);
                break;
            case MENU_SAVE_AS_ID:
                handleFileSaveAsAction(position);
                break;
            case MENU_SEND_ID:
                break;
            default:
                return super.onContextItemSelected(item);
        }

        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(this, "onActivityResult, requestCode:" + requestCode
                + ", resultCode:" + resultCode);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                String filePathName = data.getStringExtra(FileChooserActivity.EXTRA_FILE_PATH_NAME);
                mSelectedFilePathName = filePathName;

                Log.d(this, "onActivityResult, tempFilePathName:" + filePathName);

                if (!TextUtils.isEmpty(filePathName)) {
                    mMsgEditText.setText(mContext.getString(R.string.file) + mSelectedFilePathName);
                }
            } else {
                mSelectedFilePathName = "";
            }
        } if(requestCode == FOLDER_CHOOSER_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                String filePathName =
                        data.getStringExtra(FileChooserActivity.EXTRA_FILE_PATH_NAME);

                fileSaveAs(mSaveAsFileEvent, filePathName);
            }

            mSaveAsFileEvent = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private String getTimeString(long time) {
        SimpleDateFormat dateformat = new SimpleDateFormat("MM-dd HH:mm:ss");
        return dateformat.format(time);
    }

    private void init() {
        if (mMediaTransManager == null) {
            mConnectionManager = ConnectionManager.getInstance(mContext.getApplicationContext());
            mConnClientInfo = mConnectionManager.getClientInfo(mConnId);

            if (mConnClientInfo != null) {
                getActivity().setTitle(mConnClientInfo.name);
            }

            mMediaTransManager = ClientManager.getInstance(mContext.getApplicationContext());

            mChatEventList = mMediaTransManager.getMessageList(mConnId);

            if (mChatEventList != null) {
                mChattingListView.setSelection(mChatEventList.size() - 1);
            }

            mHandler.sendEmptyMessage(EVENT_SCREEN_UPDATE);

            mMediaTransManager.addListener(new ClientManager.ClientManagerListener() {
                @Override
                public void onClientAvailable(int id, ClientInfo info) {
                    if (id == mConnId) {
                        mConnClientInfo = info;
                        mHandler.sendEmptyMessage(EVENT_SCREEN_UPDATE);
                    }
                }

                @Override
                public void onClientDisconnected(int id, int reason) {
                    mHandler.sendEmptyMessage(EVENT_CONNECTION_DISCONNECTED);
                }

                @Override
                public void onMessageReceived(int clientId, Event event) {
                    Log.d(this, "onEventReceived:" + event);
                    mHandler.sendEmptyMessage(EVENT_LIST_UPDATE);
                }

                @Override
                public void onMessageSendResult(int clientId, int msgId, boolean isSuccess) {

                }
            });
        }
    }

    public void handleFileSaveAsAction(int position) {
        Event event = mChattingAdapter.getItem(position);

        if (event instanceof FileEvent) {
            mSaveAsFileEvent = (FileEvent) event;
            FileChooserActivity.showChooseFileActivity(ChatFragment.this,
                    mContext,
                    FileChooserActivity.CHOOSE_TYPE_FOLDER,
                    FOLDER_CHOOSER_REQUEST_CODE);
        }
    }

    private void fileSaveAs(FileEvent event, String destPath) {
        if (event != null) {
            boolean ret = Utils.fileSaveAs(destPath, event.fileName, event.tempFilePathName);

            if (ret) {
                Toast.makeText(mContext, R.string.file_saved_success, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleConnectionDisconnected() {
        mSendBtn.setEnabled(false);
        mMsgEditText.setEnabled(false);
        mChattingListView.setEnabled(false);
        mFileChooseBtn.setEnabled(false);
    }

    private void updateChatScreen() {
        mSendBtn.setEnabled(mConnClientInfo != null);
        mMsgEditText.setEnabled(mConnClientInfo != null);
    }

    private class ChattingAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mChatEventList != null ? mChatEventList.size() : 0;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup container) {
            Log.d(this, "getView, position:" + position);
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(R.layout.chatting_item,
                        container,
                        false);

                viewHolder = new ViewHolder(convertView);
                convertView.setTag(viewHolder);

            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            Event prevEvent = null;
            if (position > 0) {
                prevEvent = getItem(position - 1);
            }

            Event event = getItem(position);
            if (event != null) {
                event.isShown = true;

                long prevMsgTime = prevEvent != null ? prevEvent.createTime : 0L;

                Log.d(this, "prevMsgTime:" + prevMsgTime + ", cur event createTime:" + event.createTime);
                if (event.createTime - prevMsgTime > TIME_DISPLAY_TIMESTAMP) {
                    viewHolder.time.setVisibility(View.VISIBLE);
                    viewHolder.time.setText(getTimeString(event.createTime));
                } else {
                    viewHolder.time.setVisibility(View.GONE);
                }

                String msg = "";
                if (Event.EVENT_TYPE_CHAT == event.getEventType()) {
                    msg = ((ChatMessageEvent)event).message;
                } else if (Event.EVENT_TYPE_FILE == event.getEventType()) {
                    msg = mContext.getString(R.string.file) + ((FileEvent)event).fileName;
                }

                if (event.isReceived) {
                    viewHolder.textMsgLeft.setText(msg);
                    viewHolder.textMsgLeft.setVisibility(View.VISIBLE);

                    viewHolder.textMsgRight.setVisibility(View.GONE);
                } else {
                    viewHolder.textMsgRight.setText(msg);
                    viewHolder.textMsgRight.setVisibility(View.VISIBLE);

                    viewHolder.textMsgLeft.setVisibility(View.GONE);
                }

                if (event.mState == Event.STATE_TIMEOUT
                        || event.mState == Event.STATE_FAILED) {
                    viewHolder.statusTextView.setText(R.string.chat_send_failed);
                    viewHolder.statusTextView.setVisibility(View.VISIBLE);

                    convertView.setEnabled(false);
                } else {
                    viewHolder.statusTextView.setVisibility(View.GONE);
                    convertView.setEnabled(true);
                }
            } else {

                // attention dirty data display in textview
                convertView.setVisibility(View.GONE);
            }

            if (position == (getCount() - 1)) {
                mChattingListView.setSelection(position);
            }

            return convertView;
        }

        public Event getItem(int position) {
            int size = mChatEventList != null ? mChatEventList.size() : 0;


            return (position >= 0 && 0 < size) ? mChatEventList.get(position) : null;
        }

        public void delete(int position) {
            Event event = getItem(position);

            if (event != null) {
                mChatEventList.remove(event);
            }

            notifyDataSetChanged();
        }

        public void openFile(int position) {
            Event event = getItem(position);

            if (event instanceof FileEvent) {
                String filePathName = ((FileEvent)event).tempFilePathName;
                Intent intent = FileOpenIntentUtils.getOpenFileActvityIntent(filePathName);

                Log.d(this, "openFile:" + filePathName);
                if (intent != null) {
                    mContext.startActivity(intent);
                } else {
                    Toast.makeText(mContext, R.string.file_not_existed, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    class ViewHolder {
        TextView owner;
        TextView time;
        TextView textMsgLeft;
        TextView textMsgRight;
        TextView statusTextView;

        ViewHolder(View parent) {
            owner = (TextView) parent.findViewById(R.id.chat_owner_name);
            time = (TextView) parent.findViewById(R.id.chat_msg_time);
            textMsgLeft = (TextView) parent.findViewById(R.id.chat_message_left);
            textMsgRight = (TextView) parent.findViewById(R.id.chat_message_right);
            statusTextView = (TextView) parent.findViewById(R.id.chat_msg_status_text_view);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mLayoutInflater = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mParentActivity = getActivity();
        Intent intent = mParentActivity.getIntent();

        mContext = mParentActivity.getApplicationContext();

        mConnId = intent.getIntExtra(INTENT_EXTRA_CONNECTION_INDEX, -1);

        init();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mParentActivity = null;
        mContext = null;

        super.onDestroy();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
