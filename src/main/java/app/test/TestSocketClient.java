package app.test;

import app.Param;
//import app.auth.RSATokenUtil;
//import app.auth.TokenFieldType;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import org.json.JSONObject;
import pkt.cipher.Alg;
import pkt.cipher.ClientPktCipher;
import pkt.cutter.BinPacketCutter;
import pkt.field.values.Omits;
import pkt.java.BasePacket;
import pkt.java.CompressMode;
import pkt.java.CutErrorPacket;
import pkt.java.CutPnoErrorPacket;
import pkt.java.InterfacePacket;
import pkt.util.BArray;
import pkts.*;
import pkts.DEVOPSSetSymbolForSearchPacket.Data;
import pkts.GetFollowMastersPositionPacket.Masters;
import pkts.SetSymbolListPacket.Symbols;
import share.log.FLog;
import share.threading.ExecutorUtil;
import share.util.DateUtil;
import share.util.FInt;
import share.util.FileUtil;
import pkts.WmGetQuoteAndRankPacket;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import java.io.FileWriter;
import java.io.BufferedWriter;


/**
 * 注意： Alive 使用 Read Thread，所以 onRead 的 task 不應該過長，避免太長沒有送出 Alive 被斷線
 *
 */
public class TestSocketClient extends SocketClient
{
	public static HashedWheelTimer m_timer = ExecutorUtil.newTimer("client timer %d");
	static int sm_nAliveTimeout = 20 * 1000;//20 sec
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
	String fileName = "./conf/RSA/data";
	String Data = "";

	private List<PacketListener> sendPacketListeners = new ArrayList<>();
	private List<PacketListener> receivePacketListeners = new ArrayList<>();

	public void addSendPacketListener(PacketListener packetListener) {
		sendPacketListeners.add(packetListener);
	}

	public void addReceivePacketListener(PacketListener packetListener) {
		receivePacketListeners.add(packetListener);
	}
	
	byte[] m_rsaKey = FileUtil.loadBytes("./conf/RSA/private_key.der");
	ClientPktCipher m_cipher = new ClientPktCipher();
	//-----------------------------------------------------------
	public TestSocketClient(String sConnectionName)
	{
		m_nClientSeq = sm_nSeq.next();
		m_sConnectionName = sConnectionName;
	}
	public TestSocketClient()
	{
		m_nClientSeq = sm_nSeq.next();
		m_sConnectionName = "TestClient_" + m_nClientSeq;
	}
	//-----------------------------------------------------------
	public void onReceivedPacket(BasePacket p)
	{
		for (PacketListener listener : receivePacketListeners) {
			listener.onPacket(p);
		}

		if (m_bShowReceivedPacket) {
			System.out.println(DateUtil.getDateString(new Date(), "[HH:mm:ss]") + " Client.onReceivedPacket -------" + p.getClass().getName() + " " + p.toJsonString());
		}

		switch(p.getPacketType())
		{
			case CutError:
				onErrorClientPacket(p);
				break;
			case CutPnoError:
				onErrorClientPacket(p);
				break;
			case OrderUpdate:
				onOrderUpdate(p);
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
	public void onOrderUpdate(BasePacket p)
	{
		printPacket(p);
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
	public void disconnect()
	{
		DisconnectPacket p = new DisconnectPacket();
		sendPacket(p);
	}
	protected void enableLog(int bEnable)
	{
		EnableLogPacket p = new EnableLogPacket();
		p.m_log = bEnable;
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
	public void getAccount(String sAcc)
	{
		GetAccountPacket p = new GetAccountPacket();
		p.m_seq = m_nSeq.next();
		p.m_account = sAcc;
		sendPacket(p);
	}
	public void getSymbolListbyGroup(String user, String sMarket)
	{
		GetSymbolListPacket p = new GetSymbolListPacket();
		p.set_user(user);
		p.set_group("FX");
		p.set_qtype(2);
		p.m_market = sMarket;
		p.m_seq = m_nSeq.next();
		p.m_txid = "TXID-123456";
		sendPacket(p);
	}
	public void searchSymbol(int i, int category, String keyword, String sMarket)
	{
		SearchSymbolPacket p = new SearchSymbolPacket();
		p.m_type = 2;
		p.m_market = sMarket;
		p.m_seq = m_nSeq.next();
		p.m_keyword = keyword;
//		p.m_keyword = "美";
		p.m_page = i;
		p.m_symppage = 30;
		if(Omits.isOmit(category))
			p.m_omit_category = true;
		else
		{
			p.m_omit_category = false;
			p.m_category = category;
		}
		p.m_txid = "TXID-searchSymbol";
		sendPacket(p);
	}
	public void lookupSymbol(String sMarket, String sKeys, int extend)
	{
		LookupSymbolPacket p = new LookupSymbolPacket();
		p.m_market = sMarket;
		p.m_seq = m_nSeq.next();
		p.m_txid = "TXID-lookupSymbol";
		p.m_qtype = 3;
		p.m_omit_qtype = false;
		if(Omits.isOmit(extend))
		{
			p.m_omit_extend = true;
		}
		else
		{
			p.m_omit_extend = false;
			p.m_extend = extend;
		}
		//p.m_data.add(new LookupSymbolPacket.Symbols("USDJPY.FX"));
		if(!Omits.isOmit(sKeys))
			p.m_data.add(new LookupSymbolPacket.Symbols(sKeys));
		sendPacket(p);
	}
	public void lookupSymbol(String sMarket, String sKeys)
	{
		lookupSymbol(sMarket, sKeys, Omits.OmitInt);
	}
	public void setSymbolListbyGroup(String user, String sMarket)
	{
		SetSymbolListPacket p = new SetSymbolListPacket();
		p.m_user = user;
		p.m_group = "Mobile";
		p.m_market = sMarket;
		p.m_seq = m_nSeq.next();
		p.m_txid = "TXID-999999";
		p.m_qtype = 3;
		p.m_data.add(new Symbols("000591.SZ.SC"));
		p.m_data.add(new Symbols("000919.SZ.SC"));
		p.m_data.add(new Symbols("002237.SZ.SC"));
		p.m_data.add(new Symbols("300193.SZ.SC"));
		sendPacket(p);
	}
	public void getDefaultSymbolListbyGroup(String sMarket)
	{
		GetSymbolListPacket p = new GetSymbolListPacket();
		p.m_market = sMarket;
		p.set_qtype(2);
		p.m_seq = m_nSeq.next();
		p.m_txid = "TXID-222222";
		sendPacket(p);
	}
	public void GetTradeAlert(String sId)
	{
		GetTradeAlertPacket p = new GetTradeAlertPacket();
		p.m_seq = m_nSeq.next();
		p.m_user = sId;
		p.m_txid = "TXID-TRADEALERT";
		sendPacket(p);
	}
	public void GetPriceAlert_Curr(String Id)
	{
		GetPriceAlertPacket p = new GetPriceAlertPacket();
		p.m_seq = m_nSeq.next();
		p.m_user = Id;
		p.m_txid = UUID.randomUUID().toString();
		p.m_type = 4;
		sendPacket(p);
	}
	public void GetPriceAlert_Past(String Id)
	{
		GetPriceAlertPacket p = new GetPriceAlertPacket();
		p.m_seq = m_nSeq.next();
		p.m_user = Id;
		p.m_txid = UUID.randomUUID().toString();
		p.m_type = 5;
		sendPacket(p);
	}
	public void SetPriceAlert(String Id)
	{
		SetPriceAlertPacket p = new SetPriceAlertPacket();
		p.m_seq = m_nSeq.next();
		p.m_sym = "USDJPY.FX";
		p.m_user = Id;
		p.m_type = 1;
		p.m_price = 118.240;
		p.m_txid = UUID.randomUUID().toString();
		sendPacket(p);
	}
	public void GetTickTable()
	{
		GetTickTablePacket p = new GetTickTablePacket();
		p.m_seq = m_nSeq.next();
		sendPacket(p);
		
	}
	public void SetPriceAlert(String Id, String MsgID)
	{
		SetPriceAlertPacket p = new SetPriceAlertPacket();
		p.m_seq = m_nSeq.next();
		p.m_sym = "USDJPY.FX";
		p.m_user = Id;
		p.m_type = 3;
		p.set_msgid(MsgID);
//		p.m_price = 118.240;
		p.m_txid = UUID.randomUUID().toString();
		sendPacket(p);
	}
	public void getLastTradeDate()
	{
		GetLastTradeDatePacket p = new GetLastTradeDatePacket();
		p.m_seq = m_nSeq.next();
		sendPacket(p);
	}
	public void getLastTradeDateQuote(String sMarket)
	{
		GetLastTradeDateQuotePacket p = new GetLastTradeDateQuotePacket();
		p.m_seq = m_nSeq.next();
		p.m_market = sMarket;
		sendPacket(p);
	}
	public void changeUserPassword(String sUser, String sOrigPass, String sPass)
	{
		ChangeUserPasswordPacket p = new ChangeUserPasswordPacket();
		p.m_seq = m_nSeq.next();
		p.m_user = sUser;
		p.m_orgpwd = sOrigPass;
		p.m_pwd = sPass;
		sendPacket(p);
	}
	public void subscribeQuote(String sSymbol, int channel, int nFieldSet,String token)
	{
		SubscribeQuotePacket p = new SubscribeQuotePacket();
		p.m_seq = m_nSeq.next();
		p.m_id = sSymbol;
		p.m_token=token;
		p.set_fs(nFieldSet);
		p.set_ch(channel);
		sendPacket(m_cipher.encryptAES(p, true));
	}

	//SubscribeChart2{"id":"CHFNOK.FX","count":116,"seq":238,"pt":104,"ctype":2,"period":"1D"}
	public void subscribeChart2(String sSymol,int count,int ctype,String period)
	{
		SubscribeChart2Packet p = new SubscribeChart2Packet();
		p.m_seq = m_nSeq.next();
		p.m_id = sSymol;
		p.m_ctype = ctype;
		p.m_period = period;
		p.m_count = count;
		sendPacket(p);
	}
	//client.getChart("601989.SH.SC", ChartType.K, ChartFreq._day, 100);
	public void getChart(String sSymbol,int ctype, String period, int count)
	{
		GetChartPacket p = new GetChartPacket();
		p.m_seq = m_nSeq.next();
		p.m_id = sSymbol;
		p.m_ctype = ctype;
		p.m_period = period;
		p.m_count = count;
		sendPacket(p);
	}

	//WmGetQuoteAndRank(508) ; packetSize[41]{"pt":508,"seq":172,"type":"SZ","categories":[{"id":"AMT","level":0,"desc":0}],"timestamp":0}
	public  void WmGetQuoteAndRank(String type, String id, int level, int desc)
	{
		WmGetQuoteAndRankPacket p = new WmGetQuoteAndRankPacket();
		p.m_seq = m_nSeq.next();
		p.m_type = type;
		WmGetQuoteAndRankPacket.Category tCategory = new WmGetQuoteAndRankPacket.Category();
		tCategory.m_id = id;
		tCategory.m_level = level;
		tCategory.m_desc = desc;
		p.m_categories = new WmGetQuoteAndRankPacket.Categories(tCategory);
		sendPacket(p);

	}
	public void premiumFollow(String sID, String sAcc, String sMarket, String sMaster)
	{
		GetFollowMasterInfoPacket p = new GetFollowMasterInfoPacket();
		p.m_user = sID;
		p.m_account = sAcc;
		p.m_market = sMarket;
		p.m_masterid = sMaster;
		p.m_txid = UUID.randomUUID().toString();
		p.m_seq = m_nSeq.next();
		sendPacket(p);
	}
	public void accountReset(String sID, String sAcc, String sMarket, String sCoin)
	{
		AccountResetPacket p = new AccountResetPacket();
		p.m_user = sID;
		p.m_account = sAcc;
		p.m_market = sMarket;
		p.m_coinid = sCoin;
		p.m_txid = UUID.randomUUID().toString();
		p.m_seq = m_nSeq.next();
		sendPacket(p);
	}
	public void premiumFollowMasters(String sID, String sAcc, String sMarket, String Symbol)
	{
		GetFollowMastersPositionPacket p = new GetFollowMastersPositionPacket();
		p.m_user = sID;
		p.m_acc = sAcc;
		p.m_market = sMarket;
		p.m_seq = m_nSeq.next();
		p.m_txid = UUID.randomUUID().toString();
		p.m_symbol = Symbol;
		p.m_data.add(new Masters("cc"));
		p.m_data.add(new Masters("premiumfollow"));
		sendPacket(p);
	}
	public void accountSetting(String sUser, String account, double stoploss, double defQty, double trailingStop, int tradingType)
	{
		AccountSettingPacket p = new AccountSettingPacket();
		p.m_user = sUser;
		p.m_account = account;
		if(!Omits.isOmit(stoploss))
		{
			p.m_stoploss = stoploss;
			p.m_omit_stoploss = false;
		}
		else
			p.m_omit_stoploss = true;

		if(!Omits.isOmit(defQty))
		{
			p.m_defq = defQty;
			p.m_omit_defq = false;
		}
		else
			p.m_omit_defq = true;
		p.m_seq = m_nSeq.next();

		if (!Omits.isOmit(trailingStop))
		{
			p.m_trailing_stop = trailingStop;
			p.m_omit_trailing_stop = false;
		} else {
			p.m_omit_trailing_stop = true;
		}

		p.set_trading_type(tradingType);

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
		p.m_txid = UUID.randomUUID().toString();
		sendPacket(p);
	}
	public void cancelOrder(String account, String orderID)
	{
		CancelOrderPacket p = new CancelOrderPacket();
		p.m_seq = m_nSeq.next();
		p.m_id = orderID;
		p.m_account = account;
		p.m_txid = UUID.randomUUID().toString();
		sendPacket(p);
	}
	public void closeOrder(String account, String symbol)
	{
		CloseOrderPacket p = new CloseOrderPacket();
		p.m_seq = m_nSeq.next();
		p.m_symbol = symbol;
		p.m_account = account;
		p.m_txid = UUID.randomUUID().toString();
		sendPacket(p);
	}
	public void getOrder(String account)
	{
		GetOrderPacket p = new GetOrderPacket();
		p.m_seq = m_nSeq.next();
		p.set_account(account);
		sendPacket(p);
	}
	public void getUserMapping(String userId, String thirdPartyId)
	{
		GetUserMappingPacket p = new GetUserMappingPacket();
		p.set_user(userId);
		p.m_user_3rd = thirdPartyId;
		p.m_txid = UUID.randomUUID().toString();
		sendPacket(p);
	}

	public void getUserMappingList(String userId, String market, String language)
	{
		GetUserMappingListPacket p = new GetUserMappingListPacket();
		p.m_seq = m_nSeq.next();
		p.m_txid = UUID.randomUUID().toString();
		p.m_user = userId;
		p.m_market = market;
		p.m_language = language;
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
		p.m_lang_code = new String("EN");
		p.m_omit_lang_code = false;
		sendPacket(p);
	}

	public void unionTokenLogin(String token)
	{
		UnionTokenLoginPacket p = new UnionTokenLoginPacket();
		p.m_seq = sm_nSeq.next();
		p.m_token = token;
		sendPacket(p);
	}

	public void useCoin(String userId, String market, int feature, int quantity, int autoExpand)
	{
		UseCoinPacket p = new UseCoinPacket();
		p.m_txid = UUID.randomUUID().toString();
		p.m_seq = sm_nSeq.next();
		p.m_user = userId;
		p.m_market = market;
		p.m_feature = feature;
		p.m_quantity = quantity;
		p.m_auto_expand = autoExpand;

		sendPacket(p);
	}
	public void getMaxOrderQtyAllow(String user, String acc, String market, String symbol, int side, int otype, double price)
	{
		GetMaxOrderQtyAllowPacket p = new GetMaxOrderQtyAllowPacket();
		p.m_txid = UUID.randomUUID().toString();
		p.m_seq = sm_nSeq.next();
		p.m_id = user;
		p.m_acc = acc;
		p.m_market = market;
		p.m_sym = symbol;
		p.m_side = side;
		p.m_otype = otype;
		p.m_price = price;

		sendPacket(p);
	}
	
	public void getGlobalSetting(String market)
	{
		GetGlobalSettingPacket p = new GetGlobalSettingPacket();
		p.m_seq = sm_nSeq.next();
		p.m_market = market;
		
		sendPacket(p);
		
	}

	public void attachUnionId(String openId, String unionId, String market, String language)
	{
		AttachUnionIdPacket p = new AttachUnionIdPacket();
		p.m_txid = UUID.randomUUID().toString();
		p.m_seq = sm_nSeq.next();
		p.m_openid = openId;
		p.m_unionid = unionId;
		p.m_market = market;
		p.m_language = language;

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
	public void rearGetAuthUserInfo(String sUser)
	{
		RearGetAuthUserInfoPacket p = new RearGetAuthUserInfoPacket();
		p.m_seq = sm_nSeq.next();
		p.m_user = sUser;
		sendPacket(p);
	}
	public void rearGetAccountDaily(String sAcc, String sOnDate)
	{
		RearGetAccountDailyPacket p = new RearGetAccountDailyPacket();
		p.m_seq = sm_nSeq.next();
		p.m_account = sAcc;
		p.set_ondate(sOnDate);
		sendPacket(p);
	}
	public void rearGetUsersByMgr(String sMgr, String sLang)
	{
		RearGetUsersByMgrIDPacket p = new RearGetUsersByMgrIDPacket();
		p.m_seq = sm_nSeq.next();
		p.m_mgrid = sMgr;
		if(!Omits.isOmit(sLang))
			p.set_db(sLang);
		sendPacket(p);
		
	}
	public void rearGetMgrsByUser(String sUser)
	{
		RearGetMgrIDsByUserPacket p = new RearGetMgrIDsByUserPacket();
		p.m_seq = sm_nSeq.next();
		p.m_user = sUser;
		sendPacket(p);
	}
	public void rearSetUserToMgrID(String sUser, String sMgrID, int action)
	{
		RearSetUserToMgrIDPacket p = new RearSetUserToMgrIDPacket();
		p.m_seq = sm_nSeq.next();
		p.m_user = sUser;
		p.m_mgrid = sMgrID;
		p.m_action = action;
		sendPacket(p);
	}
	public void rearSetUserAuthInfo(String sUser, int level)
	{
		RearSetUserAuthInfoPacket p = new RearSetUserAuthInfoPacket();
		p.m_seq = sm_nSeq.next();
		p.m_user = sUser;
		p.m_userlvl = level;
		sendPacket(p);
	}
	//public EnterOrderPacket(int seq, String account, String sym, int side, int otype, double price, double qty, int strategy, double strategy_prc1, String txid, String user, int reason, String behalf, double yesterday_qty, double today_qty, String broker) {
	public void EnterOrderPacket(String account, String sym, int side, int otype, double qty, int strategy)
	{
		EnterOrderPacket p = new EnterOrderPacket();
		p.m_seq = sm_nSeq.next();
		p.m_account = account;
		p.m_sym = sym;
		p.m_side = side;
		//p.m_price = price;
		p.m_otype = otype;
		p.m_qty = qty;
		p.m_txid = UUID.randomUUID().toString();
		p.m_strategy = strategy;
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
		pkt.m_omit_snapshot=false;
		pkt.m_country="TW";
		pkt.m_lang_code="TW";
		
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
			for (PacketListener listener : sendPacketListeners) {
				listener.onPacket(p);
			}

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
	public void onClose()
	{
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
//    static public String GenerateToken(String id, String acc, String email, String phone, String parseId, String Country) throws Exception
//    {
//		RSATokenUtil.initKey();
//
//   	 	java.util.Date date= new java.util.Date();
//
////     	Timestamp tCurr = new Timestamp(date.getTime() );
////     	Timestamp tExipred = new Timestamp(date.getTime() + (m_iExpiration * 1000));
//
//    	JSONObject jsonObject = new JSONObject();
//    	jsonObject.put(TokenFieldType.Id.getFieldType(), id);
//    	jsonObject.put(TokenFieldType.email.getFieldType(), email);
//    	jsonObject.put(TokenFieldType.phone.getFieldType(), phone);
//    	jsonObject.put(TokenFieldType.parse.getFieldType(), parseId);
//    	jsonObject.put(TokenFieldType.country.getFieldType(), Country);
//    	jsonObject.put(TokenFieldType.exp.getFieldType(), date.getTime() + (900 * 1000));
//    	if(acc != null)
//    		jsonObject.put(TokenFieldType.acc.getFieldType(), acc);
//
//    	System.out.println("plain  :" + jsonObject.toString());
//    	//GAE only allow decode by private key
//    	return new String(RSATokenUtil.encryptByPublicKey(jsonObject.toString().getBytes(), true));
//    }
//    static public String DeCodeToken(String token)throws Exception
//    {
//		RSATokenUtil.initKey();
//    	return new String(RSATokenUtil.decryptByPrivateKey(token.getBytes(), true));
//
//    }
	static public void testRsaTokenDecode() 
	{
		try{
//			System.out.println("decode :" + DeCodeToken("cczTJrdXAQYYdwwQMHKBwncjLQ1IBfhjwpP5v+3MnOq1k2AbWjkO4bHt26aZEMckiPc5T89Tq/VMfXXT5eo1UbEQM677eUd9nlbg6RVw8Lo7W220YmJ9M5gYP5jrKVUTw0UaJ7s1z8n1C5AzRKvuKvLDZG45GKQkoC7/0LVz/vrj1iIPfUmU28y1zVEZjHjudDg5OAOKewY7H3wyn7PC2Kh0mlaAF523IN1H+DY/dRGbYD+aLIRoa9bFaeeXufMFnsBiSNhmfUt2HmBgfCLPjFUdqomZi6t+8wxWOY30WVMdn0C5oMEYTuox4gYZ3bkh4fczsqn6ofqO1tx5+WhWuQ=="));

			
		} catch(Exception e)
		{
			System.out.printf("token decode error!!" + e.getMessage());
		}
	}
	static public void testRsaToken() 
	{
		try{
	//		System.out.println("cipher :" + GenerateToken("seemo", "semmo-FX", "seemo@hkfdt.com", "123-456-789", "parse-abcde-fghij-lmnop", "TW"));

			
		} catch(Exception e)
		{
			System.out.printf("token Generated error!!" + e.getMessage());
		}
	}
	

//	final static String sID = "josephuat";
//	final static String sAcc = "josephuat-FX";
	final static String sID = "ttt";
	final static String sAcc = "ttt-FX";
	final static String sOldPass = "tttttttt";
	final static String sPass = "tttttttt";
//	final static String sOldPass = "11111111";
//	final static String sPass = "11111111";
	final static String sEmail = "ttt@hkfdt.com";
	final static String sPhone = "123-456-7891";
	final static String sParse = "parseid-test-test1031";
	final static String sCountry = "TW";
	final static String sLanguage = "TW";

/*	
	final static String sID = "shihyuan.lo.8-FB";
	final static String sOrgID = "shihyuan.lo.8";
	final static String sAcc = "shihyuan.lo.8-FB-FX";
	final static String sOldPass = "1234abcd";
	final static String sPass = "1234abcd";
	final static String sEmail = "shihyuan8@facebook.com";
	final static String sPhone = "123-456-789";
	final static String sParse = "parseid-test-shyuan37";
	final static String sCountry = "TW";
	final static String sLanguage = "EN";
	*/
	/*
	final static String sID = "fb-10205966181496226";
	final static String sOrgID = "10205966181496226";
	final static String sAcc = "fb-10205966181496226-FX";
	final static String sOldPass = "1234abcd";
	final static String sPass = "1234abcd";
	final static String sEmail = "memoria.c@gmail.com";
	final static String sPhone = "";
	final static String sParse = "2etiArKIz2";
	final static String sCountry = "TW";
	final static String sLanguage = "EN";
	*/
	/*
	final static String sID = "qq-102059661814962ag";
	final static String sOrgID = "102059661814962ag";
	final static String sAcc = "qq-102059661814962ag-FX";
	final static String sOldPass = "1234abcd";
	final static String sPass = "1234abcd";
	final static String sEmail = "aa.dc@gmail.com";
	final static String sPhone = "";
	final static String sParse = "2etiArKIz2ab";
	final static String sCountry = "TW";
	final static String sLanguage = "EN";
	*/
	// Token: {"exp":1423584323302,"phone":"123-456-789","orgid":"shihyuan.lo.8","email":"shihyuan8@facebook.com","Id":"shihyuan.lo.8-fb","utype":"50","parse":"parseid-test-shyuan37","country":"TW"}
	
	/*
	final static String sID = "rm1028";
	final static String sAcc = "rm1028-FX";
	final static String sOldPass = "wxyz";
	final static String sPass = "wxyz";
	final static String sEmail = "rm1028@FDT.com";
	final static String sPhone = "987-654-321";
	final static String sParse = "parseid-rm-rm1028";
	final static String sCountry = "TW";
	final static String sLanguage = "EN";
	*/
	void testDEVOPSSetSymbolForSearch()
	{
		DEVOPSSetSymbolForSearchPacket pkt = new DEVOPSSetSymbolForSearchPacket();
		pkt.set_seq(0);
		pkt.m_category=2;
		pkt.m_market="FX";
		Data d = new Data();
		pkt.m_data.add( new pkts.DEVOPSSetSymbolForSearchPacket.Symbols("USDJPY.FX"));
		pkt.m_data.add( new pkts.DEVOPSSetSymbolForSearchPacket.Symbols("EURUSD.FX"));
		printPacket(pkt);
	}
	static public void main(String[] argv)
	{
		
		//testRsaToken();
		testRsaTokenDecode(); 
		TestSocketClient client = new TestSocketClient("");
		try{
//			client.testDEVOPSSetSymbolForSearch();
			//client.start("54.169.180.225", 80, -1);
//			client.start("211.154.155.184", 8880, -1);
//			client.start("127.0.0.1", 8880, -1);
//			client.start("192.168.4.152", 8882, -1);
			client.start("192.168.4.141", 7009, -1);
//			client.start("192.168.4.152", 8880, -1);
//			client.start("10.0.1.24", 8880, -1);
//			client.start("10.0.1.27", 8881, -1);
//			client.start("10.0.1.28", 8882, -1);
			//client.start("211.154.155.189", 110, -1); // UAT test lan
			//client.start("54.169.17.48", 80, -1);
			//client.start("10.1.1.56", 8880, -1);
//			client.start("125.227.191.247", 993, -1); // fc test lan
//			client.start("125.227.191.247", 110, -1); // fc test lan
//			client.start("10.0.1.34", 8883, -1); // ft test lan
			//client.start("125.227.191.249", 80, -1); // test lan
//			client.start("125.227.191.252", 8880, -1);  //dev lan
			//client.start("125.227.191.252", 8881, -1);  //dev lan, FuturesMaster
//			client.start("10.0.0.52", 8880, -1);  //dev lan
//			client.start("10.1.2.99", 8883, -1);  //prod FT
//			client.start("10.0.0.52", 8881, -1);  //dev lan
//			client.start("10.0.0.52", 8882, -1);  //dev lan
			//client.start("125.227.191.252", 8881, -1);  //dev lan, FuturesMaster
//			client.start("10.0.0.52", 8881, -1);  //dev2 lan
			//client.start("10.0.0.53", 8880, -1);  //live trading
			//client.start("54.169.33.151", 80, -1);
			client.connect("Test1", EWait.Wait);
			Thread.sleep(100);
			//client.GetTickTable();
			//Thread.sleep(100);
			
//			for(int i = 0 ; i < 100 ; i++)
//			{
//				client.subscribeChart("000506.SZ.SC", ChartType.K, "DC", 96);
//				Thread.sleep(100);
//				client.getChart("600030.SH.SC", ChartType.K, ChartFreq._day, 100);
//				Thread.sleep(100);
//				client.subscribeQuote("000506.SZ.SC;601318.SH.SC;600030.SH.SC", Omits.OmitInt);
//				client.searchSymbol(0, SearchSymbolCategory.AllSymbol.getCode(), "P", "SC");
//				Thread.sleep(100);
//				client.searchSymbol(0, SearchSymbolCategory.AllSymbol.getCode(), "0000", "SC");
//				Thread.sleep(100);
//				client.searchSymbol(0, SearchSymbolCategory.AllSymbol.getCode(), "H", "SC");
//				Thread.sleep(1000);
//				client.subscribeChart("-000506.SZ.SC", ChartType.K, "DC", 96);
//				Thread.sleep(100);
//				client.subscribeQuote("-000506.SZ.SC;-601318.SH.SC;-600030.SH.SC", Omits.OmitInt);
//				
//			}
//			client.subscribeChart("IF1509.CF.FC", ChartType.K, "DC", 96);
//			client.subscribeQuote("TWSETX.FT", Omits.OmitInt);
// 			client.subscribeQuote("ag1509.SHF.FC", Omits.OmitInt);
//			client.subscribeQuote("IC1506.CF.FC", Omits.OmitInt);
//			client.subscribeChart("IC1509.CF.FC", ChartType.K, "DC", 96);
//			client.subscribeQuote("AUDCAD.FX;AUDUSD.FX;USDJPY.FX", Omits.OmitInt, Omits.OmitInt);
//			client.subscribeQuote("USDJPY.FX", 1,1);
//			client.subscribeQuote("AUDUSD.FX", 2,2);
//			client.getQuote("USDJPY.FX");
//			client.subscribeQuote("000506.SZ.SC;601318.SH.SC;600030.SH.SC;", Omits.OmitInt, Omits.OmitInt);
//			client.subscribeQuote("555555.SH.SC;000506.SZ.SC;601318.SH.SC;600030.SH.SC", Omits.OmitInt);
//			client.getQuote("GBPUSD.FX;AUDUSD.FX;USDJPY.FX");
//			client.subscribeChart("GBPUSD.FX;AUDUSD.FX;USDJPY.FX", ChartType.K, ChartFreq._15min, 10);
//			client.subscribeChart("601988.SH.SC", ChartType.K, "3M", 96);
//			doGetChart(client, "IC1509.CF.FC");
//			client.subscribeChart("IF1509.CF.FC", ChartType.K, ChartFreq._5min, 100);
//			client.subscribeChart("601628.SH.SC", ChartType.K, ChartFreq._5min, 100);
//			client.subscribeChart("TXFC6.FT", ChartType.K, "DC", 265);
//			client.subscribeChart("TWSETX.FT;TXFJ5.FT", ChartType.K, "DC", 265);
//			client.subscribeChart("USDJPY.FX", ChartType.K, "1D", 265);
//			client.subscribeChart("USDJPY.FX", ChartType.K, "1D", 265, 20151201, 20151201, 1200);
//			doSubscribeChart(client, "USDJPY.FX");
//			doSubscribeChart(client, "USDCHF.FX");
//			doSubscribeChart(client, "USDCAD.FX");
//			doSubscribeChart(client, "EURUSD.FX");
//			doSubscribeChart(client, "EURJPY.FX");
//			doSubscribeChart(client, "GBPUSD.FX");
//			doSubscribeChart(client, "AUDUSD.FX");
//			doSubscribeChart(client, "NZDUSD.FX");
//			doSubscribeChart(client, "GBPJPY.FX");
//			doSubscribeChart(client, "SGDJPY.FX");
//			doSubscribeChart(client, "USDCNH.FX");
//			doSubscribeChart(client, "AUDNZD.FX");
//			doSubscribeChart(client, "USDSEK.FX");
//			doSubscribeChart(client, "GBPNZD.FX");
//			doSubscribeChart(client, "EURAUD.FX");
//			doSubscribeChart(client, "USDRUB.FX");
//			doSubscribeChart(client, "EURRUB.FX");
//			doSubscribeChart(client, "EURCAD.FX");
			

//            client.getUserMapping("test1", "test1");
//			client.getUserMapping(null, "test1");
//			client.getUserMapping(null, "testXXX");
//			client.getUserMapping("testXXX", "testXXX");

			//Thread.sleep(100);
//			client.subscribeQuote("MXFJ5.FT;MXFF6.FT", Omits.OmitInt);
//			client.subscribeQuote("IF1510.CF.FC;IF1511.CF.FC", Omits.OmitInt);
//			client.subscribeQuote("IC1506.CF.FC", Omits.OmitInt);
			//client.subscribeQuote("601318.SH.SC", Omits.OmitInt);
			//client.subscribeQuote("600005.SH.WM", Omits.OmitInt, Omits.OmitInt);
//			client.subscribeQuote("AUDUSD.FX;USDJPY.FX;EURUSD.FX", Omits.OmitInt, Omits.OmitInt);
			//client.subscribeChart("EURJPY.FX", ChartType.K, ChartFreq._15min, 96);
//			client.subscribeChart("601318.SH.SC", ChartType.K, ChartFreq._60min, 132);
//			client.subscribeChart("ag1509.SHF.FC", ChartType.K, "DC", 132);
			//client.subscribeChart("USDCHF.FX", ChartType.K, ChartFreq._240min, 96);
//			client.subscribeChart("AUDUSD.FX", ChartType.K, ChartFreq._15min, 96);
			//client.subscribeChart("USDJPY.FX", ChartType.K, ChartFreq._15min, 96);
//			Thread.sleep(100);
			client.getChart("601989.SH.WM", 2,"DC", 100);
//			client.getChart("USDJPY.FX", ChartType.Line, ChartFreq._1min, 2);
//			doGetChart(client, "USDJPY.FX");
//			doGetChart(client, "USDCHF.FX");
//			doGetChart(client, "USDCAD.FX");
//			doGetChart(client, "EURUSD.FX");
//			doGetChart(client, "EURJPY.FX");
//			doGetChart(client, "GBPUSD.FX");
//			doGetChart(client, "AUDUSD.FX");
//			doGetChart(client, "NZDUSD.FX");
//			doGetChart(client, "GBPJPY.FX");
//			doGetChart(client, "SGDJPY.FX");
//			doGetChart(client, "USDCNH.FX");
//			doGetChart(client, "AUDNZD.FX");
//			doGetChart(client, "USDSEK.FX");
//			doGetChart(client, "GBPNZD.FX");
//			doGetChart(client, "EURAUD.FX");
//			doGetChart(client, "USDRUB.FX");
//			doGetChart(client, "EURRUB.FX");
//			doGetChart(client, "EURCAD.FX");
//			for(int i=0 ; i<1000 ; i++)
//			{
//				client.createUser("StressTest" + i, "xxx", "StressTest" + i + "@FDT.com", "123-456-789");
//				Thread.sleep(100);
//			}
			//client.createUser(sID, sPass, sEmail, sPhone);
			//Thread.sleep(2000);
//			client.createUser(sID, sPass, sEmail, sPhone, UserCreatedType.Support, sCountry, sLanguage);
//			client.createUser("natalie", "123456", "natalie@hkfdt.com", "123456-879", UserCreatedType.Normal, "TW", "EN");
//			Thread.sleep(10000);
			//ThreadUtil.sleep(2000);
//			for ( int i = 0 ; i < 10000; i++)
//			{
//				client.userLogin(sID, sPass, sParse, sCountry);
//				Thread.sleep(1000);
//			}
			Thread.sleep(2000);
//			client.GetTradeAlert();
//			Thread.sleep(2000);
//			client.GetPriceAlert_Curr();
//			Thread.sleep(2000);
//			client.GetPriceAlert_Past();
//			client.getDefaultSymbolListbyGroup();
//			Thread.sleep(2000);
//			client.getSymbolListbyGroup(sID);
//			Thread.sleep(2000);
//			client.setSymbolListbyGroup(sID);
			//Thread.sleep(2000);
			//client.getAccount("seanlo2-FX");
			//client.createUser("test030", "abcd", "tet030@FDT.com", "11-222-333", UserCreatedType.Normal, "TW");
//			client.userLogin(sID, sPass, sParse, sCountry);
//			client.userLogin("natalie", "123456", "parse-natalie", "TW");
			//client.userLogin("test023", "abcd", "dafagasdg", "TW");
//			client.userCreateLogin(sOrgID, sID, sPass, sEmail, sPhone, UserCreatedType.Facebook, sCountry, sParse, sLanguage);
			//client.userCreateLogin(sOrgID, sID, sPass, sEmail, sPhone, UserCreatedType.QQ, sCountry, sParse, sLanguage);
//			Thread.sleep(10000);
//			client.doGroupList(GroupListCode.GET, sID, "SC");
//			Thread.sleep(2000);
//			client.doGroupList(GroupListCode.SET, sID, "SC");
//			client.setSymbolListbyGroup(sID, "SC");
			Thread.sleep(2000);
//			client.doGroupList(GroupListCode.GET, sID, "SC");
//			client.getMaxOrderQtyAllow(sID, sAcc, "FC", "IF1601.CF.FC",  1, 2, 120);
//			client.getMaxOrderQtyAllow(sID, sAcc, "SC", "601390.SH.SC",  1, 2, 120);
//			client.getMaxOrderQtyAllow(sID, sAcc, "FX", "USDJPY.FX",  1, 2, 120);
//			Thread.sleep(3000);
//			client.getGlobalSetting("FX");
//			Thread.sleep(3000);
			//client.premiumFollow(sID, sAcc, "FX", "premiumfollow");
			//Thread.sleep(3000);
//			client.accountReset(sID, sAcc, "FX", "4545894091325440");
			//client.accountReset(sID, sAcc, "FX", "ab1234");
			//Thread.sleep(3000);
			//client.premiumFollowMasters(sID, sAcc, "FX", "AUDUSD.FX");
			//client.createUser("Group02", "abcd", "grp02@hkfdt.com", "123-456-789", UserCreatedType.GroupUser, "TW", "EN");
			//client.createUser("ttt003", "abcd", "ttt003@hkfdt.com", "123-456-789", UserCreatedType.Normal, "TW", "EN");
			//Thread.sleep(2000);
//			Thread.sleep(2000);
			//client.SetPriceAlert(sID, "A20150130-095906-378-2392");
//			client.setSymbolListbyGroup(sID);
			//client.rearGetAuthUserInfo("ttt003");
			//for(int i = 0 ; i < 10000 ; i ++)
			//{
			//client.rearGetAuthUserInfo("Group02");
			//client.rearGetMgrsByUser("test020");
			//Thread.sleep(20);
			//}
//			client.rearGetAuthUserInfo(sID);
			//client.rearGetAuthUserInfo("seanlo2-FX");
			////Thread.sleep(2000);
			//client.SetPriceAlert(sID);
			//client.SetPriceAlert(sID, "A20150130-173220-492-0322");
			//Thread.sleep(2000);
			//client.GetPriceAlert_Curr(sID);
			//Thread.sleep(2000);
			//client.GetPriceAlert_Past(sID);
//			Thread.sleep(2000);
//			client.getDefaultSymbolListbyGroup("FC");//
//			client.getDefaultSymbolListbyGroup("FX");//
//			client.getDefaultSymbolListbyGroup("SC");//
//			Thread.sleep(2000);
//			client.getSymbolListbyGroup(sID, "FC");
//			client.getSymbolListbyGroup(sID, "FX");
//			client.getSymbolListbyGroup(sID, "SC");
//			client.getSymbolListbyGroup(Omits.OmitString, "FX");
//			Thread.sleep(2000);
			//client.GetTradeAlert(sID);
			//Thread.sleep(2000);
//			client.searchSymbol(0, SearchSymbolCategory.Volatile.getCode(), "ru", "FC");
//			client.searchSymbol(0, SearchSymbolCategory.Popular.getCode(), "russia", "FC");
//			client.searchSymbol(0, SearchSymbolCategory.AllSymbol.getCode(), "if", "FC");
//			client.searchSymbol(0, SearchSymbolCategory.AllSymbol.getCode(), "t", "FT");
//			client.searchSymbol(1, SearchSymbolCategory.AllSymbol.getCode(), "i", "FC");
//			client.searchSymbol(0, SearchSymbolCategory.Volatile.getCode(), "", "FX");
//			client.searchSymbol(0, SearchSymbolCategory.Popular.getCode(), "", "FX");
//			client.searchSymbol(0, SearchSymbolCategory.AllSymbol.getCode(), "555555", "FX");
//			client.searchSymbol(0, SearchSymbolCategory.AllSymbol.getCode(), "u", "FX");
//			client.searchSymbol(0, SearchSymbolCategory.AllSymbol.getCode(), "P", "SC");
//			client.searchSymbol(0, SearchSymbolCategory.Volatile.getCode(), "", "SC");
//			client.searchSymbol(0, SearchSymbolCategory.Popular.getCode(), "", "SC");
//			client.searchSymbol(0, SearchSymbolCategory.AllSymbol.getCode(), "測試用", "SC");
//			client.searchSymbol(1, SearchSymbolCategory.AllSymbol.getCode(), "zg", "SC");
////			client.searchSymbol(3, "FX");
//			client.searchSymbol(4, "FX");
//			client.searchSymbol(0, "SC");
			//client.searchSymbol(0, "FC");
			//Thread.sleep(2000);
			//client.searchSymbol(1, "FC");
			//Thread.sleep(2000);
			//client.lookupSymbol("FX");
//			client.lookupSymbol("FC","");
//			client.lookupSymbol("FC","399001.SZ.FC", 1);
//			client.lookupSymbol("FT","MXFF6.FT", 1);
//			client.lookupSymbol("FT","MXFF6.FT");
//			client.lookupSymbol("FC","IF1511.CF.FC", 1);
//			client.lookupSymbol("FC","IF1511.CF.FC");
//			client.lookupSymbol("FC","IF1504.CF.FC");
//			client.lookupSymbol("FC","IF1504.CF.FC;IF1505.CF.FC");
//			client.lookupSymbol("FX","");
//			client.lookupSymbol("FX","USDJPY.FX");
//			client.lookupSymbol("FX","IF1504.CF.FC;IF1505.CF.FC");
//			Thread.sleep(2000);
			//client.rearGetAccountDaily("seanlo2-FX", Omits.OmitString);
			//client.rearGetMgrsByUser(sID);
			//client.rearGetUsersByMgr("rm_tw_forexmaster","CN");
			//client.rearGetMgrsByUser("RM_FOREXMASTER");
//			client.getLastTradeDate();
//			Thread.sleep(2000);
//			client.getLastTradeDateQuote("FC");
			//client.rearSetUserAuthInfo(sID, 4);
			//Thread.sleep(2000);
			//client.rearGetAuthUserInfo("seanlo2");
			
			//client.rearSetUserToMgrID(sID, "RM_FOREXMASTER", 1);
			//Thread.sleep(2000);
			//client.rearGetUsersByMgr("RM_FOREXMASTER");

			//client.changeUserPassword(sID, sOldPass, sPass);
			//Thread.sleep(2000);
//			client.accountSetting(sID, sAcc, 1234.0, Omits.OmitDouble, Omits.OmitDouble, Omits.OmitInt);
//			Thread.sleep(2000);
//			client.accountSetting(sID, sAcc, Omits.OmitDouble, 3000.0, Omits.OmitDouble, 1);
//			Thread.sleep(2000);
//			client.accountSetting(sID, sAcc, 2345.0, 9000.0, Omits.OmitDouble);
//			ThreadUtil.sleep(1000);
//			client.accountSetting(sID, sAcc, Omits.OmitDouble, Omits.OmitDouble, 1000.0);
//			Thread.sleep(2000);
//			client.accountSetting(sID, sAcc, 5000.0, Omits.OmitDouble, 2000.0);
//			Thread.sleep(2000);
//			client.accountSetting(sID, sAcc, 6000.0, 9000.0, Omits.OmitDouble);
//			ThreadUtil.sleep(1000);
			//client.userLogin("Test1012", "Test1012", "parseid-test-test1012");
			//ThreadUtil.sleep(1000);
			//client.subscribeQuote("AUDUSD.FX;NZDUSD.FX", Omits.OmitInt);
			//ThreadUtil.sleep(1000);
			//client.userLogout();
			
			//client.accountSetting("Test1013", "Test1013", 1234.0);
			//client.enterOrder(sAcc, "GBPJPY.FX", OSide.Buy, OType.Market, OStrategy.STOP, 0.86, 10000000000000.0, 0.89); //Stop Order
			//client.enterOrder(sAcc, "AUDUSD.FX", OSide.Buy, OType.Market, OStrategy.STOP, 0.82, 12345.0, 0.82); //Stop Order
//			client.enterOrder(sAcc, "AUDUSD.FX", OSide.Sell, OType.Market, OStrategy.SDMA, 0.82, 2000000.0, 0.82); 
//			client.enterOrder(sAcc, "AUDUSD.FX", OSide.Buy, OType.Market, OStrategy.SDMA, 0.82, 10000.0, 0.82); 
//			Thread.sleep(3000);
//			client.enterOrder(sAcc, "AUDUSD.FX", OSide.Sell, OType.Market, OStrategy.SDMA, 0.82, 20000.0, 0.82); 
//			Thread.sleep(3000);
//			client.GetTradeAlert(sID);
			//Thread.sleep(2000);
			//client.GetPriceAlert_Curr(sID);
			//Thread.sleep(2000);
			//client.GetPriceAlert_Past(sID);
//			client.enterOrder(sAcc, "USDJPY.FX", OSide.Buy, OType.Market, OStrategy.SDMA, 0.82, 10000.0, 0.82, 1); 
//			Thread.sleep(3000);
//			client.enterOrder(sAcc, "USDCHF.FX", OSide.Buy, OType.Market, OStrategy.SDMA, 0.82, 10000.0, 0.82); 
//			Thread.sleep(3000);
			//client.enterOrder(sAcc, "AUDUSD.FX", OSide.Buy, OType.Limit, OStrategy.SDMA, 0.82, 10000.0, 0.82); 
			//Thread.sleep(3000);
			//client.closeOrder(sAcc, "AUDUSD.FX"); //Close Order
//			client.closeOrder(sAcc, "USDCHF.FX"); //Close Order
//			Thread.sleep(3000);
			//client.closeOrder(sAcc, "AUDUSD.FX"); //Close Order
//			client.RefreshToken();
//			ThreadUtil.sleep(3000);
			//if(client.sOrdid != null);
			//	client.amendOrder("Test1013", client.sOrdid, 0.85, 1234, 0.85);
//			client.enterOrder("Test1012", "AUDUSD.FX", OSide.Buy, OType.Market, OStrategy.SDMA, Omits.OmitDouble, 2000.0);
//			client.enterOrder("Test101", "AUDUSD.FX", OSide.Buy, OType.Market, Omits.OmitDouble, 2000.0);
			//client.enterOrder("Test101", "AUDUSD.FX", OSide.Sell, OType.Market, Omits.OmitDouble, 2000.0);
			//client.amendOrder(sAcc, client.m_hsOrder.toArray(new String[0])[0], 0.8, 1999.0, 1998.0);
			//Thread.sleep(3000);
			//client.amendOrder("Test1", client.m_hsOrder.toArray(new String[0])[0], 0.9, 1999.0);
			//client.cancelOrder(sAcc, client.m_hsOrder.toArray(new String[0])[0]);
//			Thread.sleep(3000);
//			client.getOrder(Omits.OmitString);
			//client.getOrder(sAcc);
			//client.createUser("Test1", "Test1");
//			client.suspend();
			//System.out.println("suspend");
			//ThreadUtil.sleep(3000);
			//System.out.println("resume");
//			Thread.sleep(3000);
//			client.resume();
//			Thread.sleep(3000);
			
			
		}catch(Throwable e){
			e.printStackTrace();
		}
	}
}
