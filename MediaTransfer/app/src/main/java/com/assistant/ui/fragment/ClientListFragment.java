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
import com.assistant.events.FileEvent;
import com.assistant.mediatransfer.MediaTransferManager;
import com.assistant.ui.ChattingActivity;
import com.assistant.ui.view.CircleIndicatorView;
import com.assistant.utils.Log;

import com.assistant.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_CANCEL_INDICATION:
                    hideIndicatorText();
                    break;
                case EVENT_CONNECTION_LIST_UPDATED:
                    mClientListAdapter.updateClientInfo();
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(this, "onCreateView start");
        return inflater.inflate(R.layout.client_list_layout, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        Log.d(this, "onViewCreated start");
        super.onViewCreated(view, savedInstanceState);

        mListView = (ListView) view.findViewById(R.id.client_list_view);
        mIndicatorTextView = (TextView)view.findViewById(R.id.indicator_text);

        mClientListAdapter = new ClientListAdapter();
        mListView.setAdapter(mClientListAdapter);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent();
                intent.setClass(mActivity, ChattingActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                ClientInfoItem item = (ClientInfoItem) mClientListAdapter.getItem(position);
                if (item != null) {
                    intent.putExtra(INTENT_EXTRA_CONNECTION_INDEX, item.connId);
                    mActivity.startActivity(intent);
                }
            }
        });
        Log.d(this, "onViewCreated end");
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(this, "onActivityCreated start");
        super.onActivityCreated(savedInstanceState);

        mActivity = getActivity();

        mSharePreferencesHelper = SharePreferencesHelper.getInstance(mActivity);

        mConnManager = ConnectionManager.getInstance(getActivity().getApplicationContext());
        mLayoutInflater = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mMediaTransferManager = MediaTransferManager.getInstance(
                getActivity().getApplicationContext());

        updateClientListView();

        mMediaTransferManager.addListener(new MediaTransferManager.MediaTransferListener() {
            @Override
            public void onClientAvailable(int id, ClientInfo info) {
                updateClientListView();
            }

            @Override
            public void onClientDisconnected(int id, int reason) {
                updateClientListView();
            }

            @Override
            public void onMessageReceived(int clientId, Event event) {
                Log.d(this, "onEventReceived:" + event);
                updateClientListView();
            }

            @Override
            public void onMessageSendResult(int clientId, int msgId, boolean isSuccess) {
                updateClientListView();
            }
        });

        Log.d(this, "onActivityCreated end");
    }

    @Override
    public void onResume() {
        Log.d(this, "onResume start");
        super.onResume();

        updateClientListView();
        Log.d(this, "onResume end");
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
        mIndicatorTextView.setVisibility(View.GONE);
    }

    private void updateClientListView() {
        if (!mHandler.hasMessages(EVENT_CONNECTION_LIST_UPDATED)) {
            mHandler.sendEmptyMessageDelayed(EVENT_CONNECTION_LIST_UPDATED, 200);
        } else {
            Log.d(this, "updateClientListView, update ignored due to has message in loop.");
        }
    }

    class ClientListAdapter extends BaseAdapter {
        private List<ClientInfoItem> mClintInfos =
                Collections.synchronizedList(new ArrayList<ClientInfoItem>(5));

        @Override
        public int getCount() {
            return mClintInfos.size();
        }

        @Override
        public Object getItem(int position) {
            if (position >= 0 && position < mClintInfos.size()) {
                return mClintInfos.get(position);
            }

            Log.d(this, "getItem, position out of index:" + position
                    + ", item size:" + mClintInfos.size());

            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        private ClientInfoItem getInfoItemByConnId(int connId) {
            if (mClintInfos.size() == 0) {
                return null;
            }

            for(ClientInfoItem item : mClintInfos) {
                if (item.connId == connId) {
                    return item;
                }
            }

            return null;
        }

        public void updateClientInfo() {
            synchronized (mClintInfos) {
                if (mMediaTransferManager == null) {
                    Log.d(this, "updateClientInfo, mMediaTransferManager not init");
                    return;
                }
                List<Integer> connIdList = mMediaTransferManager.getConnectionIds();

                Log.d(this, "updateClientInfo, connIdList size:"
                        + connIdList.size());

                if (mClintInfos.size() > 0) {
                    List<ClientInfoItem> toRemovedItems = new ArrayList<>(mClintInfos.size());
                    for (ClientInfoItem item : mClintInfos) {
                        connIdList.remove(Integer.valueOf(item.connId));

                        // remove ClientInfoItem for connection not available
                        if (!mMediaTransferManager.isClientAvailable(item.connId)) {
                            Log.d(this, "updateClientInfo, remove item for connId:"
                                    + item.connId);
                            toRemovedItems.add(item);
                            continue;
                        }

                        Log.d(this, "updateClientInfo, update item for connId:"
                                + item.connId);
                        item.updateClientItemInfo();
                    }

                    // remove disconnected client item
                    if (toRemovedItems.size() > 0) {
                        for (ClientInfoItem item : toRemovedItems) {
                            mClintInfos.remove(item);
                        }
                    }
                }

                // add ClientInfoItem for new available connection
                if (connIdList.size() > 0) {
                    ClientInfoItem item;
                    Connection connection;
                    for (Integer id: connIdList) {
                        item = getInfoItemByConnId(id);
                        connection = mConnManager.getConnection(id);
                        if (connection != null && item == null) {
                            item = new ClientInfoItem(id,
                                    connection.getIp(),
                                    (ClientInfo) connection.getConnData(),
                                    mMediaTransferManager.getMessageList(id));

                            Log.d(this, "updateClientInfo, add item for connId:"
                                    + item.connId);
                            mClintInfos.add(item);
                            item.updateClientItemInfo();
                        } else {
                            Log.d(this, "updateClientInfo, connection disconnect:" + id);
                        }
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

            String name = getString(R.string.unknown);
            synchronized (mClintInfos) {
                ClientInfoItem item = (ClientInfoItem) getItem(position);

                if (item != null) {
                    if (item.clientInfo != null) {
                        name = item.clientInfo.name;
                    } else {
                        name = item.ipAddress;
                    }

                    holder.lastMsgSummaryView.setText(item.lastestMsg);

                    if (item.unreadMsgCount > 0) {
                        holder.circleIndicatorView.setText(String.valueOf(item.unreadMsgCount));
                        holder.circleIndicatorView.setVisibility(View.VISIBLE);
                    } else {
                        holder.circleIndicatorView.setVisibility(View.GONE);
                    }
                } else {
                    Log.d(this, "Connection not found for position:" + position);
                }
            }

            holder.clientNameView.setText(name);

            SimpleDateFormat formatter = new SimpleDateFormat("MM月dd日 HH:mm:ss");
            Date curDate = new Date(System.currentTimeMillis());
            String str = formatter.format(curDate);

            holder.lastMsgTimeView.setText(str);

            return view;
        }
    }

    class ClientInfoItem {
        int connId;
        String ipAddress;
        ClientInfo clientInfo;
        int unreadMsgCount;
        String lastestMsg;

        List<Event> msgList;

        public ClientInfoItem(int id, String ip, ClientInfo info, List<Event> list) {
            connId = id;
            msgList = list;
            ipAddress = ip;
            clientInfo = info;

            unreadMsgCount = 0;
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

            lastestMsg = mActivity.getResources().getString(R.string.no_messages);
            if (msgList != null && msgList.size() > 0) {
                Event event = msgList.get(msgList.size() - 1);
                if (event instanceof ChatMessageEvent) {
                    lastestMsg = ((ChatMessageEvent)event).message;
                } else if (event instanceof FileEvent) {
                    lastestMsg = getString(R.string.file) + ((FileEvent)event).fileName;
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
