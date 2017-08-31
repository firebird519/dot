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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.assistant.R;
import com.assistant.connection.ConnectionManager;
import com.assistant.events.ChatMessageEvent;
import com.assistant.events.ClientInfo;
import com.assistant.events.Event;
import com.assistant.events.EventHead;
import com.assistant.mediatransfer.MediaTransferManager;
import com.assistant.ui.FileChooserActivity;
import com.assistant.utils.Log;
import com.assistant.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.List;

import static android.app.Activity.RESULT_OK;
import static com.assistant.ui.FileChooserActivity.EXTRA_FILE_CHOOSER;

public class ChatFragment extends Fragment {
    private static final String TAG = "ChatFragment";

    private final static String INTENT_EXTRA_CONNECTION_INDEX = "extra_connection_index";

    private final static int FILE_CHOOSER_REQUEST_CODE = 1;

    private ListView mChattingListView;
    private ChattingAdapter mChattingAdapter;

    private Button mSendBtn;
    private EditText mMsgEditText;
    private ImageButton mFileChooseBtn;

    private LayoutInflater mLayoutInflater = null;

    private Context mContext;

    private int mConnId = -1;
    private MediaTransferManager mMediaTransManager;
    private ConnectionManager mConnectionManager;

    private ClientInfo mConnClientInfo;
    List<Event> mChatMessageList;

    //2 mins, and 10s for test mode
    private static final long TIME_DISPLAY_TIMESTAMP = Utils.DEBUG ? 10*1000 : 2*60*1000;

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
        mMsgEditText = (EditText)view.findViewById(R.id.msg_input_view);
        mFileChooseBtn = (ImageButton)view.findViewById(R.id.msg_file_choose_btn);
        mFileChooseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileChooseActivity();
            }
        });
        mSendBtn = (Button)view.findViewById(R.id.msg_send_btn);
        mSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = mMsgEditText.getText().toString();

                if (msg.length() > 0) {
                    mMsgEditText.setText("");

                    ChatMessageEvent event =
                            new ChatMessageEvent(msg,
                                    System.currentTimeMillis(),
                                    mConnId,
                                    mConnClientInfo.uId,
                                    false);

                    mMediaTransManager.sendEvent(mConnId, event);

                    mHandler.sendEmptyMessage(EVENT_LIST_UPDATE);
                }

            }
        });

        mChattingAdapter = new ChattingAdapter();
        mChattingListView.setAdapter(mChattingAdapter);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                String filePathName = data.getStringExtra(EXTRA_FILE_CHOOSER);

                if (!TextUtils.isEmpty(filePathName)) {
                    mMediaTransManager.sendFile(mConnId, filePathName);
                } else {

                }
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void showFileChooseActivity() {
        Intent intent = new Intent();
        intent.setClass(mContext, FileChooserActivity.class);

        startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
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

            mMediaTransManager = MediaTransferManager.getInstance(mContext.getApplicationContext());

            mChatMessageList = mMediaTransManager.getMessageList(mConnId);

            if (mChatMessageList != null) {
                mChattingListView.setSelection(mChatMessageList.size() - 1);
            }

            mHandler.sendEmptyMessage(EVENT_SCREEN_UPDATE);

            mMediaTransManager.addListener(new MediaTransferManager.MediaTransferListener() {
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
                public void onMessageReceived(int clientId, Event msg) {
                    mHandler.sendEmptyMessage(EVENT_LIST_UPDATE);
                }

                @Override
                public void onMessageSendResult(int clientId, int msgId, boolean isSuccess) {

                }
            });
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
            return mChatMessageList != null ? mChatMessageList.size() : 0;
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
                long prevMsgTime = prevEvent != null ? prevEvent.time : 0L;

                Log.d(this, "prevMsgTime:" + prevMsgTime + ", cur event time:" + event.time);
                if (event.time - prevMsgTime > TIME_DISPLAY_TIMESTAMP) {
                    viewHolder.time.setVisibility(View.VISIBLE);
                    viewHolder.time.setText(getTimeString(event.time));
                } else {
                    viewHolder.time.setVisibility(View.GONE);
                }

                String msg = "";
                if (Event.EVENT_TYPE_CHAT == event.getEventType()) {
                    msg = ((ChatMessageEvent)event).message;
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


            return convertView;
        }

        public Event getItem(int position) {
            return mChatMessageList != null ? mChatMessageList.get(position) : null;
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

        Activity activity = getActivity();
        Intent intent = activity.getIntent();

        mContext = activity.getApplicationContext();

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
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
