package pjsipapp.com.firebird.pjsip;

import org.pjsip.pjsua2.Account;
import org.pjsip.pjsua2.AccountConfig;
import org.pjsip.pjsua2.BuddyConfig;
import org.pjsip.pjsua2.OnIncomingCallParam;
import org.pjsip.pjsua2.OnInstantMessageParam;
import org.pjsip.pjsua2.OnRegStateParam;

import java.util.ArrayList;

/**
 * Created by liyong on 17-7-20.
 */

public class PjSipAccount extends Account {
    public ArrayList<PjSipBuddy> buddyList = new ArrayList<PjSipBuddy>();
    public AccountConfig cfg;

    PjSipAccount(AccountConfig config) {
        super();
        cfg = config;
    }

    public PjSipBuddy addBuddy(BuddyConfig bud_cfg) {
    /* Create Buddy */
        PjSipBuddy bud = new PjSipBuddy(bud_cfg);
        try {
            bud.create(this, bud_cfg);
        } catch (Exception e) {
            bud.delete();
            bud = null;
        }

        if (bud != null) {
            buddyList.add(bud);
            if (bud_cfg.getSubscribe())
                try {
                    bud.subscribePresence(true);
                } catch (Exception e) {
                }
        }

        return bud;
    }

    public void delBuddy(PjSipBuddy buddy) {
        buddyList.remove(buddy);
        buddy.delete();
    }

    public void delBuddy(int index) {
        PjSipBuddy bud = buddyList.get(index);
        buddyList.remove(index);
        bud.delete();
    }

    @Override
    public void onRegState(OnRegStateParam prm) {
        // TODO: add reg state notify
        //org.pjsip.pjsua2.app.MyApp.observer.notifyRegState(prm.getCode(), prm.getReason(),
        //        prm.getExpiration());
    }

    @Override
    public void onIncomingCall(OnIncomingCallParam prm) {
        System.out.println("======== Incoming call ======== ");
        // TODO: handle incoming call
        //org.pjsip.pjsua2.app.MyCall call = new org.pjsip.pjsua2.app.MyCall(this, prm.getCallId());
        //org.pjsip.pjsua2.app.MyApp.observer.notifyIncomingCall(call);
    }

    @Override
    public void onInstantMessage(OnInstantMessageParam prm) {
        System.out.println("======== Incoming pager ======== ");
        System.out.println("From     : " + prm.getFromUri());
        System.out.println("To       : " + prm.getToUri());
        System.out.println("Contact  : " + prm.getContactUri());
        System.out.println("Mimetype : " + prm.getContentType());
        System.out.println("Body     : " + prm.getMsgBody());
    }
}
