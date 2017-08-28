package com.assistant.ui.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.assistant.R;
import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionManager;
import com.assistant.mediatransfer.MediaTransferManager;
import com.assistant.mediatransfer.events.ChatMessageEvent;
import com.assistant.mediatransfer.events.ClientInfo;
import com.assistant.mediatransfer.events.Event;
import com.assistant.utils.Log;

import java.util.List;

public class ChatFragment extends Fragment {
    private static final String TAG = "ChatFragment";

    private final static String INTENT_EXTRA_CONNECTION_INDEX = "extra_connection_index";

    private ListView mChattingListView;
    private ChattingAdapter mChattingAdapter;

    private LayoutInflater mLayoutInflater = null;

    private Context mContext;

    private int mConnId = -1;
    private MediaTransferManager mMediaTransManager;
    private ConnectionManager mConnectionManager;

    private ClientInfo mConnClientInfo;
    List<ChatMessageEvent> mChatMessageList;

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

        Log.d(this, "onViewCreated");

        mChattingListView = (ListView)view.findViewById(R.id.chatting_list_view);

        Button btn = (Button)view.findViewById(R.id.msg_send_btn);
        final EditText editText = (EditText)view.findViewById(R.id.msg_input_view);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                editText.setText("");

                ChatMessageEvent event =
                        new ChatMessageEvent(msg,
                                System.currentTimeMillis(),
                                mConnId,
                                mConnClientInfo.uniqueId,
                                false);

            }
        });

        mChattingAdapter = new ChattingAdapter();
        mChattingListView.setAdapter(mChattingAdapter);
    }

    private void init() {
        if (mMediaTransManager == null) {
            mConnectionManager = ConnectionManager.getInstance(mContext.getApplicationContext());
            mConnClientInfo = mConnectionManager.getClientInfo(mConnId);
            mMediaTransManager = MediaTransferManager.getInstance(mContext.getApplicationContext());

            mChatMessageList = mMediaTransManager.getMessageList(mConnId);


            mChattingListView.setSelection(mChatMessageList.size() - 1);

            mMediaTransManager.addListener(new MediaTransferManager.MediaTransferListener() {
                @Override
                public void onClientAvailable(int id, ClientInfo info) {}

                @Override
                public void onMessageReceived(int clientId, Event msg) {
                    mChattingAdapter.notifyDataSetChanged();
                }

                @Override
                public void onMessageSendResult(int clientId, int msgId, boolean isSuccess) {

                }
            });
        }
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

            ChatMessageEvent event = getItem(position);
            if (event != null) {
                // TODO: show connection info in title bar
                viewHolder.owner.setVisibility(View.GONE);

                String msg = event.message;

                if (event.isReceived) {
                    viewHolder.textMsgLeft.setText(msg);
                    viewHolder.textMsgLeft.setVisibility(View.VISIBLE);

                    viewHolder.textMsgRight.setVisibility(View.GONE);
                } else {
                    viewHolder.textMsgRight.setText(msg);
                    viewHolder.textMsgRight.setVisibility(View.VISIBLE);

                    viewHolder.textMsgLeft.setVisibility(View.GONE);
                    viewHolder.owner.setVisibility(View.INVISIBLE);
                }
            } else {

                // attention dirty data display in textview
                convertView.setVisibility(View.GONE);
            }


            return convertView;
        }

        public ChatMessageEvent getItem(int position) {
            return mChatMessageList != null ? mChatMessageList.get(position) : null;
        }
    }

    class ViewHolder {
        TextView owner;
        TextView time;
        TextView textMsgLeft;
        TextView textMsgRight;

        ViewHolder(View parent) {
            owner = (TextView) parent.findViewById(R.id.chat_owner_name);
            time = (TextView) parent.findViewById(R.id.chat_msg_time);
            textMsgLeft = (TextView) parent.findViewById(R.id.chat_message_left);
            textMsgRight = (TextView) parent.findViewById(R.id.chat_message_right);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mLayoutInflater = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        Activity activity = getActivity();
        Intent intent = activity.getIntent();

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
