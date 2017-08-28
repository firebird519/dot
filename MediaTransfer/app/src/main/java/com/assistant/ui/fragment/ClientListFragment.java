package com.assistant.ui.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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
import com.assistant.mediatransfer.events.ClientInfo;
import com.assistant.mediatransfer.MediaTransferManager;
import com.assistant.ui.ChattingActivity;
import com.assistant.ui.view.CircleIndicatorView;
import com.assistant.utils.Log;

import com.assistant.R;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by liyong on 17-8-17.
 */

public class ClientListFragment extends Fragment {

    private final static String INTENT_EXTRA_CONNECTION_INDEX = "extra_connection_index";

    private ListView mListView;
    private ClientListAdapter mClientListAdapter;

    private TextView mIndicatorTextView;
    private ConnectionManager mConnManager;
    private MediaTransferManager mMediaTransferManager;
    private LayoutInflater mLayoutInflater = null;

    private Activity mActivity;

    private static final int EVENT_CANCEL_INDICATION = 0;
    private static final int EVENT_CONNECTION_LIST_UPDATED = 1;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_CANCEL_INDICATION:
                    hideIndicatorText();
                    break;
                case EVENT_CONNECTION_LIST_UPDATED:
                    mClientListAdapter.notifyDataSetChanged();
                    break;
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

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = getActivity();

        mConnManager = ConnectionManager.getInstance(getActivity().getApplicationContext());
        mLayoutInflater = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mMediaTransferManager = MediaTransferManager.getInstance(
                getActivity().getApplicationContext());


        mConnManager.listen(mMediaTransferManager.getDefaultPort());

        mConnManager.addListener(new ConnectionManager.ConnectionManagerListenerBase() {
            @Override
            public void onConnectionAdded(int id) {
                mHandler.sendEmptyMessage(EVENT_CONNECTION_LIST_UPDATED);
            }

            @Override
            public void onConnectionRemoved(int id, int reason) {
                mHandler.sendEmptyMessage(EVENT_CONNECTION_LIST_UPDATED);
            }
        });
    }

    @Override
    public void onResume() {
        showIndicatorText(R.string.searching);

        mMediaTransferManager.startSearchHost(
                new ConnectionManager.SearchListener() {
            @Override
            public void onSearchCompleted() {
                Log.d(this, "onSearchCompleted");
                mHandler.sendEmptyMessage(EVENT_CANCEL_INDICATION);
            }

            @Override
            public void onSearchCanceled(int reason) {
                Log.d(this, "onSearchCanceled");
                mHandler.sendEmptyMessage(EVENT_CANCEL_INDICATION);
            }
        });

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
        Integer[] mConnKeys = null;

        @Override
        public int getCount() {
            if (mConnManager == null) {
                return 0;
            }

            mConnKeys = mConnManager.getConnectionIds();
            return mConnKeys != null ? mConnKeys.length : 0;
        }

        @Override
        public Object getItem(int position) {
            int id = -1;

            if (position >= 0 && mConnKeys != null && position < mConnKeys.length) {
                id = mConnKeys[position];
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
            if (position >= 0 && mConnKeys != null && position < mConnKeys.length) {
                id = mConnKeys[position];
            }

            return id;
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
            }

            holder.clientNameView.setText(name);


            SimpleDateFormat formatter = new SimpleDateFormat("MM月dd日 HH:mm:ss");
            Date curDate = new Date(System.currentTimeMillis());//获取当前时间
            String str = formatter.format(curDate);

            holder.lastMsgTimeView.setText(str);

            holder.lastMsgSummaryView.setText("message summary");

            holder.circleIndicatorView.setText(String.valueOf(position));

            return view;
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
