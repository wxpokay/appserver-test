package app.test;

import app.Param;
//import app.auth.RSATokenUtil;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import pkt.cipher.Alg;
import pkt.cipher.ClientPktCipher;
import pkt.cutter.BinPacketCutter;
import pkt.field.values.Omits;
import pkt.java.BasePacket;
import pkt.java.CompressMode;
import pkt.java.CutErrorPacket;
import pkt.java.CutPnoErrorPacket;
import pkt.java.InterfacePacket;
import pkt.lgwcluster.LGWClusterMsgActionType;
import pkt.util.BArray;
import pkts.*;
import pkts.RequestServicePacket.Login;
import pkts.UnionTokenUpdatePacket.Tokens;
import pkts.UnionTokenUpdatePacket.UnionToken;
import share.log.FLog;
import share.threading.ExecutorUtil;
import share.util.DateUtil;
import share.util.FInt;
import share.util.FileUtil;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

/**
 * LoginServer(LTWGW) client<p/>
 *
 * 可以拿來當作 mobile client 發送 event 給 LoginServer，測試 LoginServer 收到 event 後的後續回應是否正確<p/>
 *
 * 注意： Alive 使用 Read Thread，所以 onRead 的 task 不應該過長，避免太長沒有送出 Alive 被斷線
 *
 */
public class UnionSocketClient extends SocketClient
{
	public static HashedWheelTimer m_timer = ExecutorUtil.newTimer("client timer %d");
	static int sm_nAliveTimeout = 5 * 1000;//20 sec
	//-----------------------------------------------------------
	FInt sm_nSeq = new FInt(-1, "");
	private final static String WAIT_CONNECT_NAME = "waitConenct";
	//-----------------------------------------------------------
	String m_sConnectionName;
	FInt m_nSeq = new FInt("");
	BinPacketCutter m_cutter = new BinPacketCutter();
	boolean m_bEnableSendAlive = true;
	int m_nClientSeq;
	FInt m_nPacketNo = new FInt(-1, "");
	FInt m_nSendPacket = new FInt("");
	private boolean m_bConnectStatusDone = false;
	public boolean m_bShowSendPacket = false;
	public boolean m_bShowReceivedPacket = false;
	public LinkedHashSet<String> m_hsOrder = new LinkedHashSet<>();
	public String sOrdid = null;
	public Tokens tokens = null;

	String workingdirectory = System.getProperty("user.dir");
	byte[] m_rsaKey = FileUtil.loadBytes("./conf/RSA/private_key.der");
	ClientPktCipher m_cipher = new ClientPktCipher();
	//-----------------------------------------------------------
	public UnionSocketClient(String sConnectionName)
	{
		m_nClientSeq = sm_nSeq.next();
		m_sConnectionName = sConnectionName;
	}
	public UnionSocketClient()
	{
		m_nClientSeq = sm_nSeq.next();
		m_sConnectionName = "TestClient_" + m_nClientSeq;
	}
	//-----------------------------------------------------------
	public void onReceivedPacket(BasePacket p)
	{
		if (m_bShowReceivedPacket)
			System.out.println(DateUtil.getDateString(new Date(), "[HH:mm:ss]") + " Client.onReceivedPacket -------" + p.getClass().getName()+" "+p.toJsonString());
		if(null == p)
			return ;
		switch(p.getPacketType())
		{
			case CutError:
				onErrorClientPacket(p);
				break;
			case CutPnoError:
				onErrorClientPacket(p);
				break;
			case UnionTokenUpdate:
				onUnionTokenUpdate(p);
				break;
			case UserUpdate:
				onUserUpdate(p);
				break;
			default:
				onPackets(p);
				break;
		}
	}
	public void onUserUpdate(BasePacket p)
	{
		printPacket(p);
	}
	public void onUnionTokenUpdate(BasePacket p)
	{
		tokens = ((UnionTokenUpdatePacket) p).m_tokens;
		printPacket(p);
		try {
			printTokensPaintText(tokens);
		}catch(Exception e){
			System.out.println("====== printTokensPaintText fail =====");
		}

	}

	public static void printTokensPaintText(Tokens tokens) throws Exception
	{
		for(UnionToken token: tokens )
		{
//			System.out.println("TOKENS: ["+token.m_market+"] ["+DeCodeToken(token.m_token)+"]");
		}
	}
	public static String getToken(String market, Tokens tokens)
	{
		for(UnionToken token: tokens )
		{
			if(token.m_market.equals(market))
				return token.m_token;
		}
		return null;
	}
	public void onPackets(BasePacket p)
	{
		printPacket(p);
	}
	protected void printPacket(BasePacket p)
	{
		System.out.println(p.getPacketType().name() + " " + p.toJsonString());
	}

	/**
	 * 得到 client 端的 packet，表示 server 送錯了！
	 * @param p
	 */
	public void onErrorClientPacket(BasePacket p)
	{
		String sMsg = "client 端收到錯誤的 packet，p=" + p.toJsonString();
		if (p instanceof CutErrorPacket)
			sMsg += "  " + ((CutErrorPacket)p).getMsg();
		else if (p instanceof CutPnoErrorPacket)
			sMsg += "  " + ((CutPnoErrorPacket)p).getMsg();
			
		FLog.assertFalse(sMsg);
		System.err.println(sMsg);
	}
	//-----------------------------------------------------------
	public void connect(String sClient, EWait wait) throws IOException
	{
		setConnectionName(sClient);
		connect("FDTApp", sClient, "1.0");
		if (wait.isWait())
			waitConnect();
	}
	public void connect(String sAppID, String sClientID, String sVer) throws IOException
	{
		ConnectPacket p = new ConnectPacket(m_nSeq.next(), sAppID, sClientID, sVer);
		sendPacket(p);
	}
	public void alive()
	{
		AlivePacket alive = new AlivePacket();
		alive.m_seq = m_nSeq.next();
		sendPacket(alive);
	}
	public void requestService()
	{
		RequestServicePacket p = new RequestServicePacket(sm_nSeq.next(), Login.Omit);
		sendPacket(p);
	}
	public void requestService(String sUser, String sPassword)
	{
		RequestServicePacket p = new RequestServicePacket(sm_nSeq.next(), new Login(sUser, sPassword));
		sendPacket(p);
	}
	public void disconnect()
	{
		DisconnectPacket p = new DisconnectPacket();
		sendPacket(p);
	}
	public void suspend()
	{
		disableSendAlive();
		SuspendPacket p = new SuspendPacket();
		p.m_timeout = 30;
		sendPacket(p);
	}
	public void resume()
	{
		ResumePacket p = new ResumePacket();
		p.m_seq = m_nSeq.next();
		sendPacket(p);
		enableSendAlive();
	}
	public void subscribeQuote(String sSymbol, int nFieldSet)
	{
		SubscribeQuotePacket p = new SubscribeQuotePacket();
		p.m_seq = m_nSeq.next();
		p.m_id = sSymbol;
		p.set_fs(nFieldSet);
		sendPacket(m_cipher.encryptAES(p, true));
	}
	public void getQuote(String sSymbol)
	{
		GetQuotePacket p = new GetQuotePacket();
		p.m_seq = m_nSeq.next();
		p.set_id(sSymbol);
		sendPacket(p);
	}
	public void getUserAuthInfo(String sUser)
	{
		RearGetAuthUserInfoPacket p = new RearGetAuthUserInfoPacket();
		p.m_user = sUser;
		p.m_seq = m_nSeq.next();
		p.m_omit_db = true;
		sendPacket(p);
		
	}
	public void reactivateServer()
	{
		LGWReActivatePacket p = new LGWReActivatePacket();
		p.m_seq = m_nSeq.next();
		sendPacket(p);
		
	}
	public void getLocaleMap()
	{
		LGWGetLocaleMapPacket p = new LGWGetLocaleMapPacket();
		p.m_seq = m_nSeq.next();
		p.m_locale = "unknown";
		sendPacket(p);
	}
	public void setLocaleMap(LGWClusterMsgActionType action)
	{
		LGWSetLocaleMapPacket p = new LGWSetLocaleMapPacket();
		if(action.equals(LGWClusterMsgActionType.Insert))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_acode = new String("001");
			p.m_locale = new String("unknown");
			
		}
		else if(action.equals(LGWClusterMsgActionType.Delete))
		{
			// m_acode must invloved in "setAreaMeta", Foreign Key
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_acode = new String("001");
			p.m_locale = new String("unknown");
			
		}
		else if(action.equals(LGWClusterMsgActionType.Update))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_acode = new String("002");
			p.m_locale = new String("unknown");
		}
		sendPacket(p);
		
	}
	public void getAreaMeta()
	{
		LGWGetAreaMetaPacket p = new LGWGetAreaMetaPacket();
		p.m_seq = m_nSeq.next();
		p.m_acode = "001";
		sendPacket(p);
		
	}	
	public void setAreaMeta(LGWClusterMsgActionType action)
	{
		LGWSetAreaMetaPacket p = new LGWSetAreaMetaPacket();
		if(action.equals(LGWClusterMsgActionType.Insert))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_acode = "001";
			p.m_aname = "Default";
			p.m_omit_aname = false;
		}
		else if(action.equals(LGWClusterMsgActionType.Delete))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_acode = "001";
			
		}
		else if(action.equals(LGWClusterMsgActionType.Update))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_acode = "001";
			p.m_aname = "AWS-1";
			p.m_omit_aname = false;
			
		}
		sendPacket(p);
		
	}
	public void getBulletin()
	{
		LGWGetBulletinPacket p = new LGWGetBulletinPacket();
		p.m_seq = m_nSeq.next();
		p.m_bullid = "0000";
		p.m_omit_bullid = false;
		sendPacket(p);
		
	}	
	public void setBulletin(LGWClusterMsgActionType action)
	{
		LGWSetBulletinPacket p = new LGWSetBulletinPacket();
		if(action.equals(LGWClusterMsgActionType.Insert))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_bullid = new String("0000");
			p.m_content = new String("Welcome to FDT Family!!");
		}
		else if(action.equals(LGWClusterMsgActionType.Delete))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_bullid = new String("0000");
			p.m_content = "";
			
		}
		else if(action.equals(LGWClusterMsgActionType.Update))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_bullid = new String("0000");
			p.m_content = new String("This is a Sample Bullet");
		}
		sendPacket(p);
		
	}
	public void getServerMeta()
	{
		LGWGetServerMetaPacket p = new LGWGetServerMetaPacket();
		p.m_seq = m_nSeq.next();
		p.m_omit_acode = true;
		p.m_omit_bulletid = true;
		p.m_omit_defflag = true;
		p.m_omit_ip = true;
		p.m_omit_market = true;
		p.m_omit_port = true;
		p.m_omit_server = true;
		p.m_omit_status = true;
		p.m_omit_ulevel = true;
		sendPacket(p);
		
		
	}
	public void setServerMeta(LGWClusterMsgActionType action)
	{
		/*
		LGWSetServerMeta(8005, new PtField(),
				new IntField(FieldInfo.Integer, 2, "seq", "seq"),
				new IntField(FieldInfo.Integer, 3, "action", "action"),
				new StringField(FieldInfo.String, 4, "server", "server"),
				new StringField(FieldInfo.String, 5, "market", "market"),
				new StringField(FieldInfo.String_opt, 6, "acode", "acode"),
				new StringField(FieldInfo.String_opt, 7, "ip", "ip"),
				new StringField(FieldInfo.String_opt, 8, "port", "port"),
				new IntField(FieldInfo.Integer_opt, 9, "ulevel", "ulevel"),					
				new IntField(FieldInfo.Integer_opt, 10, "status", "status"),
				new IntField(FieldInfo.Integer_opt, 11, "bullexist", "bullexist"),
				new IntField(FieldInfo.Integer_opt, 12, "defflag", "defflag")),
*/
		LGWSetServerMetaPacket p = new LGWSetServerMetaPacket();
		if(action.equals(LGWClusterMsgActionType.Insert))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_server = "AWS-1";
			p.m_market = "FX";
			
			// acode must invloved in "setAreaMeta", Foreign Key
			p.m_acode = "001";
			p.m_omit_acode = false;
			
			p.m_ip = "125.227.191.252";
			p.m_omit_ip = false;
			
			p.m_port = "80";
			p.m_omit_port = false;
			
			p.m_ulevel = 0;
			p.m_omit_ulevel = false;
			
			p.m_status = 1;
			p.m_omit_status = false;
			
			// m_bullexist must invloved in "setBullet", Foreign Key
			// must define a default "none announce" msg for all
			p.m_bulletid = "0000";
			p.m_omit_bulletid = false;
			
			p.m_defflag = 1;
			p.m_omit_defflag = true;
		}
		else if(action.equals(LGWClusterMsgActionType.Delete))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_server = "AWS-1";
			p.m_market = "FX";
			
			
		}
		else if(action.equals(LGWClusterMsgActionType.Update))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_server = "AWS-1";
			p.m_market = "FX";
			
			p.m_port = "8080";
			p.m_omit_port = false;
			
		}
		sendPacket(p);
		
	}
	public void setUSerToServer(LGWClusterMsgActionType action)
	{
		LGWSetUserToServerPacket p = new LGWSetUserToServerPacket();
		if(action.equals(LGWClusterMsgActionType.Insert))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_acode = "001";
			p.m_omit_acode = false;
			p.m_ulevel = 0;
			p.m_omit_ulevel = false;
			p.m_userid = "cc";
			p.m_market = "FX";
		}
		else if(action.equals(LGWClusterMsgActionType.Delete))
		{
			p.m_seq = m_nSeq.next();
			p.m_action = action.getType();
			p.m_acode = "001";
			p.m_omit_acode = false;
			p.m_userid = "cc";
			p.m_market = "FX";
			
		}
		else if(action.equals(LGWClusterMsgActionType.Update))
		{
			
		}
		sendPacket(p);
		
	}
	public void getServerForConnect(String sUser)
	{
		LGWGetServerForConnectPacket p = new LGWGetServerForConnectPacket();
		p.m_seq = m_nSeq.next();
		p.m_market = "FX";
		if(Omits.isOmit(sUser))
		{
			p.m_omit_userid = true;
		}
		else
		{
			p.m_userid = "cc";
			p.m_omit_userid = false;
		}
		sendPacket(p);
		
	}
	public void getUSerToServer()
	{
		LGWGetUserToServerPacket p = new LGWGetUserToServerPacket();
		p.m_seq = m_nSeq.next();
		p.m_market = "FX";
		p.m_omit_market = false;
		p.m_userid = "cc";
		sendPacket(p);
		
	}
	public void accountSetting(String sUser, String account, double stoploss)
	{
		AccountSettingPacket p = new AccountSettingPacket();
		p.m_user = sUser;
		p.m_account = account;
		p.set_stoploss(stoploss);
		p.m_seq = m_nSeq.next();
		sendPacket(p);
	}
	public void amendOrder(String account, String orderID, double price, double qty, double strategy_prc1)
	{
		AmendOrderPacket p = new AmendOrderPacket();
		p.m_seq = m_nSeq.next();
		p.m_id = orderID;
		p.m_account = account;
		p.m_price = price;
		p.m_qty = qty;
		p.m_strategy_prc1 = strategy_prc1;
		sendPacket(p);
	}
	public void cancelOrder(String account, String orderID)
	{
		CancelOrderPacket p = new CancelOrderPacket();
		p.m_seq = m_nSeq.next();
		p.m_id = orderID;
		p.m_account = account;
		sendPacket(p);
	}
	public void closeOrder(String account, String symbol)
	{
		CloseOrderPacket p = new CloseOrderPacket();
		p.m_seq = m_nSeq.next();
		p.m_symbol = symbol;
		p.m_account = account;
		sendPacket(p);
	}
	public void getOrder(String account)
	{
		GetOrderPacket p = new GetOrderPacket();
		p.m_seq = m_nSeq.next();
		p.set_account(account);
		sendPacket(p);
	}
	public void unionCreate(String sUser, String sPwd, String sEmail, String sPhone, String coutnry, String lang)
	{
		UnionCreatPacket p = new UnionCreatPacket();
		p.m_seq = sm_nSeq.next();
		p.m_user = sUser;
		p.m_pwd = sPwd;
		p.m_email = sEmail;
		p.m_phone = sPhone;
		p.m_utype = 1;
		p.m_country = coutnry;
		p.m_lang_code = lang;
		sendPacket(p);
	}
	public void userLogin(String sUser, String sPwd, String sParseId, String sCountry)
	{
		UserLoginPacket p = new UserLoginPacket();
		p.m_seq = sm_nSeq.next();
		p.m_user = sUser;
		p.m_pwd = sPwd;
		p.m_parseid = sParseId;
		p.m_country = sCountry;
		sendPacket(p);
	}

	/**
	 *
	 *
	 * @param unionType 50:Facebook Open Id, 51:QQ, 54: WeChat, 55:Facebook Union Id
	 * @param unionId
	 * @param openId
	 * @param userMail
	 * @param userPhone
	 * @param userCountry
     * @param userLang
     */
	public void unionCreateLogin(int unionType, String unionId, String openId, String userMail, String userPhone,
                                 String userCountry, String userLang) {
		UnionCreateLoginPacket p = new UnionCreateLoginPacket();
		p.m_seq = sm_nSeq.next();

		switch (unionType){
			// Facebook Open Id
			case 50:
				p.m_union_id = Omits.OmitString;
				p.m_omit_union_id = true;
				p.m_open_id = openId;
				p.m_utype = 50;
				break;

			// QQ
			case 51:
				p.m_union_id = Omits.OmitString;
				p.m_omit_union_id = true;
				p.m_open_id = openId;
				p.m_utype = 51;
				break;

			// WeChat
			case 54:
				p.m_union_id = unionId;
				p.m_omit_union_id = false;
				p.m_open_id = openId;
				p.m_utype = 54;
				break;

			// Facebook Union Id
			case 55:
				p.m_union_id = unionId;
				p.m_omit_union_id = false;
				p.m_open_id = Omits.OmitString;
				p.m_utype = 55;
				break;
		}

		p.m_country = userCountry;
		p.m_lang_code = userLang;
		p.m_email = userMail;
		p.m_phone = userPhone;
		p.m_phone_country = Omits.OmitString;

		sendPacket(p);
	}

	public void unionLogin(String user, String pwd, String country, String lang) {

		UnionLoginPacket p = new UnionLoginPacket();
		p.m_seq = sm_nSeq.next();
		p.m_user = user;
		p.m_pwd = pwd;
		p.m_country = country;
		p.m_lang_code = lang;
		p.m_login_type = 0;
		sendPacket(p);
	}

	public void refreshUnionToken()
	{
		RefreshUnionTokenPacket p = new RefreshUnionTokenPacket();
		p.m_seq = sm_nSeq.get();
		sendPacket(p);
	}

	public void RefreshToken()
	{
		RefreshTokenPacket p = new RefreshTokenPacket();
		p.m_seq = sm_nSeq.next();
		sendPacket(p);
	}
	public void userLogout()
	{
		UserLogoutPacket p = new UserLogoutPacket();
		p.m_seq = sm_nSeq.next();
		sendPacket(p);
	}
	//-----------------------------------------------------------
	/**
	 * 取得是否 ConnectStatus 已經取得
	 * @return
	 */
	public boolean isConnectSucess()
	{
		return m_bConnectStatusDone;
	}
	/**
	 * 等待連線成功
	 */
	public void waitConnect()
	{
		waitWaiter(WAIT_CONNECT_NAME);
	}
	/**
	 * 取消送出 Alive
	 */
	public void disableSendAlive()
	{
		m_bEnableSendAlive = false;
	}
	/**
	 * 啟動送出 Alive
	 */
	public void enableSendAlive()
	{
		m_bEnableSendAlive = true;
	}
	public String getConnectionName()
	{
		return m_sConnectionName;
	}
	public void setConnectionName(String sName)
	{
		m_sConnectionName = sName;
	}
	public int getClientSeq()
	{
		return m_nClientSeq;
	}
	//-----------------------------------------------------------
	void wakeupWaitConnect()
	{
		wakeupWaiter(WAIT_CONNECT_NAME, null);
	}
	BasePacket handleEncryptPacket(EncryptPacket p)
	{
		return m_cipher.decrypt(p, true);
	}
	void handleConnectChallenge(ConnectChallengePacket p)
	{
		printPacket(p);
		
		m_cipher.initAsymKey(Alg.RSA, m_rsaKey);
		
		ConnectResponsePacket pkt = new ConnectResponsePacket();
		pkt.m_seq = sm_nSeq.next();
		pkt.m_r = p.m_r;
		pkt.m_log = 1;
		pkt.m_snapshot = 1;
		
		sendPacket(m_cipher.encryptAsym(pkt, Alg.RSA, true));
	}
	void handleConnectStatus(ConnectStatusPacket p)
	{
		printPacket(p);
		
		if (!m_bConnectStatusDone)
		{
			m_bConnectStatusDone = true;
			m_cipher.initAESKey(p.m_key);
			wakeupWaitConnect();
			checkAliveTime();
		}
		else // 表示來了兩次
		{
			FLog.assertFalse("LoginStatus occurn multiple times!");
		}
	}
	protected void checkAliveTime()
	{
		if (m_bConnectStatusDone) // 尚未 connect 成功，不可以送出 Alive
		{
			try{
				if (m_bEnableSendAlive)
					alive();
			}catch(Throwable e){
				FLog.assertException(e);
			}
		}
		
		m_timer.newTimeout(new TimerTask()
		{
			@Override
			public void run(Timeout arg0) throws Exception
			{
				checkAliveTime();
			}
		}, sm_nAliveTimeout, TimeUnit.MILLISECONDS);
	}
	//-----------------------------------------------------------
	public void sendPacket(BasePacket p)
	{
		synchronized (m_nPacketNo) // synchronized: 避免不同 thread call sendPacket, 造成 p_no 大的會比較早送出
		{
			printPacket(p);
			int nPno = m_nPacketNo.nextOrClear(Param.sm_nPacketNoMax, 0);
			InterfacePacket ip = new InterfacePacket(Param.sm_nServerVersion, nPno, p);
    		if (m_bShowSendPacket)
    		{
    			System.out.println("-----------------------------------------------------------------------------");
    			System.out.println(DateUtil.getDateString(new Date(), "[HH:mm:ss]") + String.format(" ** %d [sendPacket] %s=%s", m_nSendPacket.next(), p.getPacketType().name(), ip.toJsonString()));
    		}
    		try{
    			write(ip.toBinary(CompressMode.AutoSelect));
    			flush();
    		}catch(Throwable e){
    			e.printStackTrace();
    		}
		}
	}
	//-----------------------------------------------------------
	@Override
    public void onRead(byte[] byData, int nOffset, int nLength)
    {
		if (nLength == byData.length)
			m_cutter.putData(byData);
		else
			m_cutter.putData(new BArray(byData, nOffset, nLength).getBytes());
		BasePacket[] ap = m_cutter.cut();
		for (BasePacket p: ap)
		{
			if(p instanceof EncryptPacket)
				p = handleEncryptPacket((EncryptPacket)p);
			
			if (p instanceof ConnectChallengePacket)
				handleConnectChallenge((ConnectChallengePacket)p);
			else if(p instanceof ConnectStatusPacket)
				handleConnectStatus((ConnectStatusPacket)p);
			else
				onReceivedPacket(p);
		}
    }
	@Override
	public void fireOnReadTimeout()
	{
		super.fireOnReadTimeout();
	}
	@Override
	public void fireOnPendingReadTimeout()
	{
		super.fireOnPendingReadTimeout();
	}
	@Override
	public void onException(Exception e)
	{
		FLog.assertException(e);
	}
	@Override
	public void onClose() {
		System.out.printf("[%s] live_time=%,d\r\n", m_sConnectionName, liveTime());
	}
	@Override
    public void onReadTimeout()
    {
    }
	@Override
    public void onPendingReadTimeout()
    {
    }
	@Override
    public void onDataComing(int nBytes)
    {
    }
	public void getSymbolListbyGroup(String user, String sMarket)
	{
		GetSymbolListPacket p = new GetSymbolListPacket();
		p.set_user(user);
		p.set_group("FFFFFFCCCCCCC");
		p.set_qtype(2);
		p.m_market = sMarket;
		p.m_seq = m_nSeq.next();
		p.m_txid = "TXID-123456";
		p.m_allowemp = 0;
		p.m_omit_allowemp = false;
		sendPacket(p);
	}
//	static public String DeCodeToken(String token)throws Exception
//	{
//		RSATokenUtil.initKey();
//		return new String(RSATokenUtil.decryptByPrivateKey(token.getBytes(), true));
//
//	}

	static public void oldMain(String[] argv)
	{
		UnionSocketClient loginServerClient = new UnionSocketClient(""); // login server client
//		TestSocketClient appServerClient = new TestSocketClient(""); // app server client
		try {
//			System.out.println("[FX]:" + DeCodeToken("K/IUmAWIs3grBefkGVHV8puU/KXigigmHdYOgxV91B5pH98odufyoeADhpSJel8D932qplGP/g3RKIcnfmj5A4qTJYCmE7DBM7+zVyqvZp/rYSjph3/zYxp6Nhp2u8m7Sfe+5C7gD7VAZ4wCE3oy1pebL/Scg55cONzIEhqSoNMuHoB4mjeVropaGMk1cFgj4zdtwPk2RLOhUTVD/t8D+0ERAWgrNq4s6CtPwCZ2rHxxLIdWCGGguGK8SS1izNm5xwOP/NjFVFQau9yR4aSiUvj06W67VfFa64ixE06Qa1vq0Ok+8HMyMYW1RPv/7EviikBM+/HkgKtV4u0NhJRB+g=="));
//			System.out.println("[FC]:" + DeCodeToken("Ox4d9bhrKpeubyHoq3Q1O2bVGmZpC00LCl4JdNYeyPXhu831vBgv0q5V9B5aDLaUNxqSRqKFzS5/KMZ+BAJTS+ZXA9zEZ1GwwxK4eVLz9IGQc5kyZ4kMgtlPdERAGAhb4Wi3NlfLl+7TF7/cDby1MOes163fR5/DklY32IiH2e/uq2Mv1llb37LSFxCorgxj79RH1J3f4SdgVFddtoWBx0q1qdPSyzeJ17jOD2WmedwHYI1D+q0Shbx+VHSBhYF37mQgK4lJMQMPJnuIvxNS5iryj2vmiJjyXgyKmYtOsX2Jmm8lGXL5MYm9DFvrSB+xR0De3ieLtrEDYWlvxWn2dg=="));
//			System.out.println("[SC]:" + DeCodeToken("RBMTbQa9O2gW4vwWeSv7YNh48h8i+vSXlPUyotxLzE796oFbtVm+Dj1FCBxntmewV3FwkvXc1Z44N90nDj1Pc4u5D4uQYO5hGv3gOF5F4KWfXbETOt9CxDTsg9Jnngkz/rXm/GMucYg+jd/U56vPYznmnnv/X9vA1jRI43nFIJvgRbQbfNMZtFtKPbBa+8v3u/8wjwk3k90yul2BnX+W+P2wibPYzkZbxHjVeEaOF4ZnT9B5yYw6JzcOmROhhBnhIS1GYuAndxAQd1/8mv65ko2doMX9/WN/teHCCMd9+2zG1VBU8fTlkCHgJ+l1cN2JdRZKZ1MRIqE3+ZV6B7DP4g=="));

//			loginServerClient.start("54.169.180.225", 80, -1);
//			loginServerClient.start("192.168.4.71", 8888, -1);
//			loginServerClient.start("125.227.191.252", 80, -1);
//			loginServerClient.start("54.169.33.151", 80``, -1);
			loginServerClient.start("127.0.0.1", 8888, -1); // connect to  local login Server
			loginServerClient.connect("UnionTest1", EWait.Wait);

//			appServerClient.start("192.168.4.71", 8881, -1);
//			appServerClient.connect("UnionTest2", EWait.Wait);

//			loginServerClient.unionCreate("aaaaaaaaaa", "aaaaaaaaaa", "aaaaaaaaaa@abc.com", "12345678");
//			loginServerClient.requestService();
//			loginServerClient.requestService("phoenixsu", "jackal31");
//			loginServerClient.getQuote("abcd1234");
//			loginServerClient.getUserAuthInfo("cc");

//			Thread.sleep(2000);
//			loginServerClient.unionLogin("david01", "12345678", "TW", "TW");
//			loginServerClient.unionLogin("victest01", "fdttest01", "EN", "US");
//			loginServerClient.unionCreateLogin();
//			Thread.sleep(6000);

//			if(loginServerClient.tokens != null)
//			{
//				appServerClient.unionTokenLogin(UnionSocketClient.getToken("FC",loginServerClient.tokens));
//				appServerClient.getSymbolListbyGroup("qq000000093", "FC");
//			}

//			loginServerClient.setAreaMeta(LGWClusterMsgActionType.Delete);
//			loginServerClient.getAreaMeta();
//			loginServerClient.setAreaMeta(LGWClusterMsgActionType.Insert);
//			loginServerClient.getAreaMeta();
			
//			loginServerClient.setBulletin(LGWClusterMsgActionType.Update);
//			loginServerClient.getBulletin();
//			loginServerClient.setBulletin(LGWClusterMsgActionType.Insert);
//			loginServerClient.getBulletin();

//			loginServerClient.setServerMeta(LGWClusterMsgActionType.Insert);
//			loginServerClient.setServerMeta(LGWClusterMsgActionType.Update);
//			loginServerClient.setServerMeta(LGWClusterMsgActionType.Delete);
//			loginServerClient.getServerMeta();
			
//			loginServerClient.setLocaleMap(LGWClusterMsgActionType.Insert);
//			loginServerClient.getLocaleMap();
//			loginServerClient.setLocaleMap(LGWClusterMsgActionType.Update);
//			loginServerClient.getLocaleMap();
//			loginServerClient.setLocaleMap(LGWClusterMsgActionType.Delete);
//			loginServerClient.getLocaleMap();
			
//			loginServerClient.setUSerToServer(LGWClusterMsgActionType.Delete);
//			loginServerClient.getUSerToServer();
//			loginServerClient.setUSerToServer(LGWClusterMsgActionType.Insert);
//			loginServerClient.getUSerToServer();
			
//			loginServerClient.getServerForConnect(Omits.OmitString);
//			loginServerClient.getServerForConnect("cc");
			
//			loginServerClient.reactivateServer();
		}catch(Throwable e){
			e.printStackTrace();
		}
	}

	/**
	 * test UnionCreateLoginPacket & UnionCreatPacket & UnionLoginPacket & RefreshUnionTokenPacket
	 *
	 * @param client
	 *
	 * @throws InterruptedException
     */
	private static void testUnionCreateAndLoginPacket(UnionSocketClient client) throws InterruptedException {
		String userId = "tester-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()).toString();
		String userPwd = userId + "-pwd";
		String userMail = userId + "@test.com";
		String userPhone = "12345678";
		String userCountry = "TW";
		String userLang = "CN";

		// test UnionCreateLoginPacket
		client.unionCreateLogin(51, null, "qq-" + userPwd, userMail, userPhone, userCountry, userLang);
		// 追查 PJ-523 bug
		//client.unionCreateLogin(54, "un-o0hvet8g1sb5wpestn7m_xm48lhm", "un-omhdqs-qxf2jftzw0zzt_6xeacos", userMail, userPhone, userCountry, userLang);
		Thread.sleep(2000);

		// test UnionCreatPacket
		userId = "tester-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()).toString();
		userPwd = userId + "-pwd";
		userMail = userId + "@test.com";
		client.unionCreate(userId, userPwd, userMail, userPhone, userCountry, userLang);
		Thread.sleep(1000);

		// test UnionLoginPacket
		client.unionLogin(userId, userPwd, userCountry, userLang);
		Thread.sleep(1000);

		// test RefreshUnionTokenPacket
		client.refreshUnionToken();
	}

	public static void main(String[] args) {
		try {
			// connect to LoginServer
			UnionSocketClient client = new UnionSocketClient(""); // login server client
			client.start("192.168.4.71", 8888, -1); // connect to  Test-CN login Server
//			client.start("127.0.0.1", 8888, -1); // connect to  local login Server
			client.connect("UnionTest1", EWait.Wait);

			// 測試 LoginServer 相關 packet
			testUnionCreateAndLoginPacket(client);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
