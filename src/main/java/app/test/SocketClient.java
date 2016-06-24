package app.test;

import share.util.DateUtil;
import share.util.Waiter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.HashMap;

abstract public class SocketClient
{
	//-------------------------------------------------------------
	abstract public void onRead(byte[] byData, int nOffset, int nLength);
	abstract public void onDataComing(int nBytes);
	abstract public void onReadTimeout();
	abstract public void onPendingReadTimeout();
	abstract public void onException(Exception e);
	abstract public void onClose();
	//-------------------------------------------------------------
	static final int DEFAULT_TIMEOUT = 100;
	//-------------------------------------------------------------
	public String m_sIP;
	int m_nPort;
	private int m_nReadTimeout;
	private int m_nPendingReadTimeout;
	boolean m_bStop = false;
	volatile boolean m_bDisableRead = false;
	Object m_objPendingRead = new Object();
	volatile int m_nUnreadBytes;
	//-----------------------------------------------------------
	private Socket m_sck;
	private InputStream m_in;
	private OutputStream m_out;
	//-----------------------------------------------------------
	HashMap<String, Waiter<Object>> m_hmWaiter = new HashMap<String, Waiter<Object>>();
	long m_lConnectTime;
	long m_lDisonnectTime;
	boolean m_bShowUnread = false; 
	boolean m_bServer = false;
	//--------------------------------------------------------------
	public void start(String sIP, int nPort, int nReadTimeout, int nPendingReadTimeout)
	{
		try
        {
			m_sIP = sIP;
			m_nPort = nPort;
			m_nReadTimeout = nReadTimeout;
			m_nPendingReadTimeout = nPendingReadTimeout;
			
			m_lConnectTime = System.currentTimeMillis();
			m_sck = new Socket(m_sIP, m_nPort);
	        if (m_nReadTimeout > 0)
	        	m_sck.setSoTimeout(m_nReadTimeout);
	        else
	        	m_sck.setSoTimeout(DEFAULT_TIMEOUT);
	        m_in = m_sck.getInputStream();
	        m_out = m_sck.getOutputStream();
	        startRead();
        }
		catch (IOException e)
        {
	        e.printStackTrace();
        }
	}
	public void start(Socket sck, int nReadTimeout, int nPendingReadTimeout)
	{
		try
        {
			m_bServer = true;
			m_sck = sck;

	        InetSocketAddress addr = (InetSocketAddress)sck.getRemoteSocketAddress();
			m_sIP = addr.getHostName();
			m_nPort = addr.getPort();
			m_nReadTimeout = nReadTimeout;
			m_nPendingReadTimeout = nPendingReadTimeout;
	        if (m_nReadTimeout > 0)
	        	m_sck.setSoTimeout(m_nReadTimeout);
	        else
	        	m_sck.setSoTimeout(DEFAULT_TIMEOUT);
			
			m_lConnectTime = System.currentTimeMillis();
	        m_in = m_sck.getInputStream();
	        m_out = m_sck.getOutputStream();
	        startRead();
        }
		catch (IOException e)
        {
	        e.printStackTrace();
        }
	}
	public void start(String sIP, int nPort, int nReadTimeout)
	{
		start(sIP,  nPort, nReadTimeout, nReadTimeout);
	}
	public void stop()
	{
		m_bStop = true;
	}
	public void close() throws IOException
	{
		m_sck.close();
	}
	void close_noException()
	{
		try
        {
	        close();
        }
		catch (IOException e)
        {
			// do nothing
        }
	}
	//--------------------------------------------------------------
	void ensureWaiter(String sWaiterName)
	{
		Waiter<Object> waiter = m_hmWaiter.get(sWaiterName);
		if (waiter == null)
			m_hmWaiter.put(sWaiterName, new Waiter<Object>());
	}
	public void resetWaiter(String sWaiterName)
	{
		m_hmWaiter.put(sWaiterName, new Waiter<Object>());
	}
	public void wakeupWaiter(String sWaiterName, Object obj)
	{
		ensureWaiter(sWaiterName);
		m_hmWaiter.get(sWaiterName).setObject(obj);
	}
	public Object waitWaiter(String sWaiterName)
	{
		ensureWaiter(sWaiterName);
		return m_hmWaiter.get(sWaiterName).waitObject(); 
	}
	public boolean isWaiterComplete(String sWaiterName)
	{
		ensureWaiter(sWaiterName);
		return m_hmWaiter.get(sWaiterName).isComplete();
	}
	//--------------------------------------------------------------
	public void write(byte[] byData, int nOffset, int nLength) throws IOException
	{
		try
		{
			m_out.write(byData, nOffset, nLength);
		}
		catch(SocketException e)
		{
			String sMsg = e.getMessage();
			if (sMsg.startsWith("Software caused connection abort"))
			{
				stop();
			}
			else
			{
				stop();
			}
			throw e;
		}
	}
	public void write(byte[] byData) throws IOException
	{
		write(byData, 0, byData.length);
	}
	public void write(String s) throws IOException
	{
		write(s.getBytes());
	}
	//--------------------------------------------------------------
	public void flush() throws IOException
	{
		m_out.flush();
	}
	
	public void suspendRead()
	{
		m_bDisableRead = true;
		unpendingRead();
	}
	public void resumeRead()
	{
		m_bDisableRead = false;
	}
	public long liveTime()
	{
		if (m_lDisonnectTime != 0)
			return m_lDisonnectTime - m_lConnectTime;
		return -1;
	}
	public int unreadBytes()
	{
		return m_nUnreadBytes;
	}
	public String getLastIPByte()
	{
		return m_sIP.substring(m_sIP.lastIndexOf(".") + 1);
	}
	public void setShowUnread(boolean bValue)
	{
		m_bShowUnread = bValue;
	}
	//------------------------------------------------------------
	void pendingRead()
	{
		synchronized(m_objPendingRead)
		{
			long lStart = System.currentTimeMillis();
			while (true)
			{
				try
				{
					long lLeft = m_nPendingReadTimeout - (System.currentTimeMillis() - lStart);
					if (lLeft <= 0)
					{
						fireOnPendingReadTimeout();
						break;
					}
					m_objPendingRead.wait(lLeft);
				}
				catch (InterruptedException e)
				{
				}
				if (!m_bDisableRead)
					break;
			}
		}
	}
	void unpendingRead()
	{
		synchronized(m_objPendingRead)
		{
			m_objPendingRead.notifyAll();
		}
	}
	void fireOnClose()
	{
		m_lDisonnectTime = System.currentTimeMillis();
		//wakeup all waiter
		for (String s: m_hmWaiter.keySet())
		{
			wakeupWaiter(s, null);
		}
		onClose();
	}
	public void fireOnReadTimeout()
	{
		onReadTimeout();
	}
	public void fireOnPendingReadTimeout()
	{
		onPendingReadTimeout();
	}
	public void fireOnRead(byte[] byData, int nOffset, int nLength)
	{
		onRead(byData, nOffset, nLength);
	}
	//--------------------------------------------------------------
	void startRead()
	{
		new Thread(new Runnable()
		{
			public void run()
			{
				doRead();
			}
		}).start();
	}
	void doRead()
	{
		byte[] byBuf = new byte[2048];
		while (!m_bStop)
		{
			try
			{
				int nOldUnread = m_nUnreadBytes; 
				m_nUnreadBytes = m_in.available();
				if (m_bShowUnread)
				{
					//System.out.println("***    m_nUnreadBytes="+m_nUnreadBytes+"   sck="+m_sck+" m_bDisableRead="+m_bDisableRead);
					System.out.println("***[" + DateUtil.getDateString(new Date(), "HH:mm:ss") + "]    m_nUnreadBytes="+m_nUnreadBytes+" m_bDisableRead="+m_bDisableRead+"  is_close="+m_sck.isClosed());
				}
				
				if (m_bDisableRead)
				{
					if (nOldUnread != m_nUnreadBytes)
						onDataComing(m_nUnreadBytes);
					pendingRead();
					continue;
				}
				int nRead = m_in.read(byBuf);
//System.out.println("***    m_nUnreadBytes="+m_nUnreadBytes+"  nRead="+nRead);
				if (nRead < 0) 
				{
					close();
					break;
				}
				else if (nRead > 0)
				{
					fireOnRead(byBuf, 0, nRead);
				}
			}
			catch(SocketTimeoutException e)
			{
				if (m_nReadTimeout > 0)
					fireOnReadTimeout();
			}
			catch(SocketException e)
			{
				if (!m_sck.isClosed())
					onException(e);
				close_noException();
				break;
			}
			catch(IOException e)
			{
				if (!m_sck.isClosed())
					onException(e);
				close_noException();
				break;
			}
		}//while
		fireOnClose();
	}
}
