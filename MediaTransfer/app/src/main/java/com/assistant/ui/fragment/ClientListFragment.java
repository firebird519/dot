package com.assistant.ui.fragment;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.assistant.connection.Connection;
import com.assistant.connection.ConnectionManager;
import com.assistant.mediatransfer.ClientInfo;
import com.assistant.mediatransfer.MediaTransferManager;

import org.w3c.dom.Text;

import mediatransfer.assistant.com.mediatransfer.R;

/**
 * Created by liyong on 17-8-17.
 */

public class ClientListFragment extends Fragment {
    private ListView mListView;
    private TextView mIndicatorTextView;
    private ConnectionManager mConnManager;
    private LayoutInflater mLayoutInflater = null;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.client_list_layout, container, false);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mListView = (ListView) view.findViewById(R.id.client_list_view);
        mIndicatorTextView = (TextView)view.findViewById(R.id.indicator_text);

        mListView.setAdapter(new ClientListAdapter());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mConnManager = ConnectionManager.getInstance(getActivity().getApplicationContext());
        mLayoutInflater = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public void onResume() {
        MediaTransferManager.getInstance(getActivity()).startSearchHost(
                new ConnectionManager.SearchListener() {
            @Override
            public void onSearchCompleted() {
                hideIndicatorText();
            }

            @Override
            public void onSearchCanceled(int reason) {
                hideIndicatorText();
            }
        });

        showIndicatorText(R.string.searching);

        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void showIndicatorText(int resId) {
        mIndicatorTextView.setText(resId);
        mIndicatorTextView.setVisibility(View.VISIBLE);
    }

    private void hideIndicatorText() {
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

            return view;
        }
    }

    static class ViewHolder {
        TextView clientNameView;

        public ViewHolder(View view) {
            clientNameView = (TextView) view.findViewById(R.id.client_name);
        }
    }
}
