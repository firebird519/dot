package com.assistant.ui.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionManager;
import com.assistant.datastorage.SharePreferencesHelper;
import com.assistant.events.ChatMessageEvent;
import com.assistant.events.ClientInfo;
import com.assistant.events.Event;
import com.assistant.mediatransfer.MediaTransferManager;
import com.assistant.ui.ChattingActivity;
import com.assistant.ui.view.CircleIndicatorView;
import com.assistant.utils.Log;

import com.assistant.R;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientListFragment extends Fragment {

    private final static String INTENT_EXTRA_CONNECTION_INDEX = "extra_connection_index";

    private ListView mListView;
    private ClientListAdapter mClientListAdapter;

    private TextView mIndicatorTextView;
    private ConnectionManager mConnManager;
    private MediaTransferManager mMediaTransferManager;
    private LayoutInflater mLayoutInflater = null;

    private SharePreferencesHelper mSharePreferencesHelper;

    private Activity mActivity;

    private static final int EVENT_CANCEL_INDICATION = 0;
    private static final int EVENT_CONNECTION_LIST_UPDATED = 1;
    private static final int EVENT_CONNECTION_AVAILABLE = 2;
    private static final int EVENT_CONNECTION_REMOVED = 3;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_CANCEL_INDICATION:
                    hideIndicatorText();
                    break;
                case EVENT_CONNECTION_AVAILABLE:
                case EVENT_CONNECTION_LIST_UPDATED:
                    mClientListAdapter.updateClientInfo();
                    break;
                case EVENT_CONNECTION_REMOVED:
                    mClientListAdapter.removeClient(msg.arg1);
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.client_list_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mListView = (ListView) view.findViewById(R.id.client_list_view);
        mIndicatorTextView = (TextView)view.findViewById(R.id.indicator_text);

        mClientListAdapter = new ClientListAdapter();
        mListView.setAdapter(mClientListAdapter);
        mClientListAdapter.updateClientInfo();

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent();
                intent.setClass(mActivity, ChattingActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                Connection conn = (Connection) mClientListAdapter.getItem(position);
                if (conn != null) {
                    intent.putExtra(INTENT_EXTRA_CONNECTION_INDEX, conn.getId());
                    mActivity.startActivity(intent);
                }
            }
        });
    }

    private boolean isNetworkSettingsOn() {
        boolean isOn = false;

        if (mSharePreferencesHelper != null) {
            isOn = mSharePreferencesHelper.getInt(SharePreferencesHelper.SP_KEY_NETWORK_ON, 1) == 1;
        }

        return isOn;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = getActivity();

        mSharePreferencesHelper = SharePreferencesHelper.getInstance(mActivity);

        mConnManager = ConnectionManager.getInstance(getActivity().getApplicationContext());
        mLayoutInflater = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mMediaTransferManager = MediaTransferManager.getInstance(
                getActivity().getApplicationContext());

        mMediaTransferManager.addListener(new MediaTransferManager.MediaTransferListener() {
            @Override
            public void onClientAvailable(int id, ClientInfo info) {
                mHandler.sendEmptyMessage(EVENT_CONNECTION_AVAILABLE);
            }

            @Override
            public void onClientDisconnected(int id, int reason) {
                mHandler.obtainMessage(EVENT_CONNECTION_REMOVED, id, reason).sendToTarget();
            }

            @Override
            public void onMessageReceived(int clientId, Event event) {
                Log.d(this, "onEventReceived:" + event);
                mHandler.removeMessages(EVENT_CONNECTION_LIST_UPDATED);
                mHandler.sendEmptyMessage(EVENT_CONNECTION_LIST_UPDATED);
            }

            @Override
            public void onMessageSendResult(int clientId, int msgId, boolean isSuccess) {
                mHandler.removeMessages(EVENT_CONNECTION_LIST_UPDATED);
                mHandler.sendEmptyMessage(EVENT_CONNECTION_LIST_UPDATED);
            }
        });
    }

    @Override
    public void onResume() {
        if (isNetworkSettingsOn()) {
            mMediaTransferManager.startListen();

            showIndicatorText(R.string.searching);
            mMediaTransferManager.startSearchHost(
                    new ConnectionManager.SearchListener() {
                        @Override
                        public void onSearchCompleted() {
                            Log.d(this, "onSearchCompleted");
                            mHandler.removeMessages(EVENT_CANCEL_INDICATION);
                            mHandler.sendEmptyMessage(EVENT_CANCEL_INDICATION);
                        }

                        @Override
                        public void onSearchCanceled(int reason) {
                            Log.d(this, "onSearchCanceled");
                            mHandler.removeMessages(EVENT_CANCEL_INDICATION);
                            mHandler.sendEmptyMessage(EVENT_CANCEL_INDICATION);
                        }
                    });
        }

        mClientListAdapter.updateClientInfo();

        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void showIndicatorText(int resId) {
        Log.d(this, "showIndicatorText");
        mIndicatorTextView.setText(resId);
        mIndicatorTextView.setVisibility(View.VISIBLE);
    }

    private void hideIndicatorText() {
        Log.d(this, "hideIndicatorText");
        mIndicatorTextView.setText("");
        mIndicatorTextView.setVisibility(View.INVISIBLE);
    }


    class ClientListAdapter extends BaseAdapter {
        Integer[] mConnIds = null;

        private Map<Integer, ClientInfoItem> mClintInfos =
                Collections.synchronizedMap(new HashMap<Integer, ClientInfoItem>(10));

        @Override
        public int getCount() {
            return mConnIds != null ? mConnIds.length : 0;
        }

        @Override
        public Object getItem(int position) {
            int id = -1;

            if (position >= 0 && mConnIds != null && position < mConnIds.length) {
                id = mConnIds[position];
            }
            Connection conn = null;

            if (id >= 0) {
                conn = mConnManager.getConnection(id);
            }
            return conn;
        }

        @Override
        public long getItemId(int position) {
            int id = -1;
            if (position >= 0 && mConnIds != null && position < mConnIds.length) {
                id = mConnIds[position];
            }

            return id;
        }

        public void removeClient(int connId) {
            synchronized (mClintInfos) {
                mClintInfos.remove(connId);

                updateClientInfo();
            }
        }

        public void updateClientInfo() {
            synchronized (mClintInfos) {
                if (mMediaTransferManager == null) {
                    return;
                }
                mConnIds = mMediaTransferManager.getConnectionIds();

                if (mConnIds == null || mConnIds.length == 0) {
                    Log.d(this, "updateClientInfo, no connection found.");
                    mClintInfos.clear();
                } else {
                    for (Integer id : mConnIds) {
                        Log.d(this, "updateClientInfo, id:" + id);

                        ClientInfoItem item = mClintInfos.get(id);

                        if (item == null) {
                            item = new ClientInfoItem(id, mMediaTransferManager.getMessageList(id));
                            mClintInfos.put(id, item);
                        }

                        item.updateClientItemInfo();
                    }
                }

                notifyDataSetChanged();
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = null;
            ViewHolder holder = null;

            if (convertView == null || convertView.getTag() == null) {
                view = mLayoutInflater.inflate(R.layout.client_item_info, null);
                holder = new ViewHolder(view);
                view.setTag(holder);
            } else {
                view = convertView;
                holder = (ViewHolder) convertView.getTag();
            }

            Connection conn = (Connection) getItem(position);

            String name = "";
            if (conn != null) {
                ClientInfo info = (ClientInfo) conn.getConnData();
                if (info != null) {
                    name = info.name;
                } else {
                    name = conn.getIp();
                }
            } else {
                Log.d(this, "Connection not found for position:" + position);
                return null;
            }

            holder.clientNameView.setText(name);

            SimpleDateFormat formatter = new SimpleDateFormat("MM月dd日 HH:mm:ss");
            Date curDate = new Date(System.currentTimeMillis());//获取当前时间
            String str = formatter.format(curDate);

            synchronized (mClintInfos) {
                ClientInfoItem item = mClintInfos.get(conn.getId());
                if (item != null) {
                    holder.lastMsgTimeView.setText(str);

                    holder.lastMsgSummaryView.setText(item.lastMsg);

                    if (item.unreadMsgCount > 0) {
                        holder.circleIndicatorView.setText(String.valueOf(item.unreadMsgCount));
                        holder.circleIndicatorView.setVisibility(View.VISIBLE);
                    } else {
                        holder.circleIndicatorView.setVisibility(View.GONE);
                    }
                }
            }

            return view;
        }
    }

    class ClientInfoItem {
        int connId;
        int unreadMsgCount;
        String lastMsg;

        List<Event> msgList;

        public ClientInfoItem(int id, List<Event> list) {
            connId = id;
            msgList = list;

            unreadMsgCount = getUnreadMsgCount();
        }

        public int getUnreadMsgCount() {
            int count = 0;
            if (msgList != null && msgList.size() > 0) {
                for (Event event : msgList) {
                    if (!event.isShown) {
                        count++;
                    }
                }
            }

            return count;
        }

        public void updateClientItemInfo() {
            unreadMsgCount = getUnreadMsgCount();

            Log.d(this, "updateClientItemInfo, unreadMsgCount:" + unreadMsgCount);

            if (msgList == null || msgList.size() == 0) {
                lastMsg = mActivity.getResources().getString(R.string.no_messages);
            } else {
                Event event = msgList.get(msgList.size() - 1);
                if (event instanceof ChatMessageEvent) {
                    lastMsg = ((ChatMessageEvent)event).message;
                }
            }
        }
    }

    static class ViewHolder {
        TextView clientNameView;
        TextView lastMsgTimeView;
        TextView lastMsgSummaryView;
        CircleIndicatorView circleIndicatorView;

        public ViewHolder(View view) {
            clientNameView = (TextView) view.findViewById(R.id.client_name);
            lastMsgTimeView = (TextView) view.findViewById(R.id.last_msg_time);
            lastMsgSummaryView = (TextView) view.findViewById(R.id.last_msg_summary);
            circleIndicatorView = (CircleIndicatorView) view.findViewById(R.id.indicator_text);
        }
    }
}
